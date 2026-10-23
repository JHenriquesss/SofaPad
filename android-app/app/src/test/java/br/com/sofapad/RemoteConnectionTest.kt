package br.com.sofapad

import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class RemoteConnectionTest {
    private val token = "a".repeat(64)
    private fun awaitState(states: LinkedBlockingQueue<LinkState>, expected: LinkState) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (System.nanoTime() < deadline) { if (states.poll(200, TimeUnit.MILLISECONDS) == expected) return }
        fail("Estado esperado: $expected")
    }

    @Test fun coalescingPreservesButtonBoundariesAndBoundsQueue() {
        val buffer = CommandBuffer()
        buffer.add(Command.Move(1f, 2f)); buffer.add(Command.Move(3f, 4f))
        buffer.add(Command.Button(MouseButton.LEFT, true)); buffer.add(Command.Move(1f, 0f))
        assertEquals(Command.Move(4f, 6f), buffer.poll())
        assertEquals(Command.Button(MouseButton.LEFT, true), buffer.poll())
        assertEquals(Command.Move(1f, 0f), buffer.poll())
        repeat(64) { assertTrue(buffer.add(Command.Key(Shortcut.ENTER))) }
        assertFalse(buffer.add(Command.Key(Shortcut.ENTER)))
    }

    @Test fun localAddressValidationAndSecretRedaction() {
        assertThrows(IllegalArgumentException::class.java) { ConnectionSettings("8.8.8.8", 443, token, token) }
        assertThrows(IllegalArgumentException::class.java) { ConnectionSettings("127.0.0.1", 0, token, token) }
        assertFalse(ConnectionSettings("127.0.0.1", 443, token, token).toString().contains(token))
    }

    private fun tlsServer(): Pair<MockWebServer, String> {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val server = MockWebServer()
        server.useHttps(tls.sslSocketFactory(), false)
        server.start()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(cert.certificate.encoded).joinToString("") { "%02x".format(it) }
        return server to fingerprint
    }

    @Test fun pinnedWssWaitsForAcknowledgementBeforeConfirmingText() {
        val (server, fingerprint) = tlsServer()
        val states = LinkedBlockingQueue<LinkState>()
        val acks = LinkedBlockingQueue<Command>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("""{"v":1,"type":"hello","mode":"control","authorized":true}""") }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val seq = Json.parseToJsonElement(text).jsonObject.getValue("seq").jsonPrimitive.long
                webSocket.send("""{"v":1,"type":"ack","seq":$seq,"mode":"control"}""")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        }))
        val connection = RemoteConnection({ states.offer(it) }, { acks.offer(it) }, {})
        try {
            connection.connect(ConnectionSettings("127.0.0.1", server.port, fingerprint, token))
            awaitState(states, LinkState.READY)
            assertEquals("Bearer $token", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
            assertTrue(connection.submit(Command.Text("Olá, ação! 😀")))
            assertEquals(Command.Text("Olá, ação! 😀"), acks.poll(5, TimeUnit.SECONDS))
            connection.pause()
            awaitState(states, LinkState.PAUSED)
            assertFalse(connection.submit(Command.Key(Shortcut.ENTER)))
        } finally { connection.close(); server.close() }
    }

    @Test fun sendsImmediatelyButStaysUnderAgentRateLimit() {
        val (server, fingerprint) = tlsServer()
        val arrivals = java.util.concurrent.ConcurrentLinkedQueue<Long>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("""{"v":1,"type":"hello","mode":"control","authorized":true}""") }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val value = Json.parseToJsonElement(text).jsonObject
                if (value.getValue("type").jsonPrimitive.content == "shortcut") arrivals.add(System.nanoTime())
                webSocket.send("""{"v":1,"type":"ack","seq":${value.getValue("seq")},"mode":"control"}""")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        }))
        val states = LinkedBlockingQueue<LinkState>()
        val acks = LinkedBlockingQueue<Command>()
        val connection = RemoteConnection({ states.offer(it) }, { acks.offer(it) }, {})
        try {
            connection.connect(ConnectionSettings("127.0.0.1", server.port, fingerprint, token))
            awaitState(states, LinkState.READY)
            val started = System.nanoTime()
            assertTrue(connection.submit(Command.Key(Shortcut.ESC)))
            assertNotNull(acks.poll(5, TimeUnit.SECONDS))
            assertTrue("um comando isolado não espera um ciclo fixo", (System.nanoTime() - started) / 1_000_000 < 500)
            repeat(5) {
                repeat(50) { assertTrue(connection.submit(Command.Key(Shortcut.ESC))) }
                repeat(50) { assertNotNull(acks.poll(5, TimeUnit.SECONDS)) }
            }
            val times = arrivals.toList().sorted()
            assertEquals(251, times.size)
            val second = TimeUnit.SECONDS.toNanos(1)
            val busiest = times.indices.maxOf { i -> times.count { it >= times[i] && it - times[i] < second } }
            assertTrue("pico de $busiest comandos/s excede o limite do PC", busiest <= 240)
        } finally { connection.close(); server.close() }
    }

    @Test fun rejectsWrongCertificateAndRejectedAuthorization() {
        val (server, fingerprint) = tlsServer()
        server.enqueue(MockResponse().setResponseCode(403))
        val states = LinkedBlockingQueue<LinkState>()
        val connection = RemoteConnection({ states.offer(it) }, {}, {})
        try {
            connection.connect(ConnectionSettings("127.0.0.1", server.port, "0".repeat(64), token))
            awaitState(states, LinkState.REJECTED)
            connection.connect(ConnectionSettings("127.0.0.1", server.port, fingerprint, token))
            awaitState(states, LinkState.REJECTED)
        } finally { connection.close(); server.close() }
    }

    @Test fun reconnectDoesNotReplayUnconfirmedText() {
        val (server, fingerprint) = tlsServer()
        val received = AtomicInteger()
        val uncertain = LinkedBlockingQueue<Boolean>()
        val states = LinkedBlockingQueue<LinkState>()
        repeat(2) {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("""{"v":1,"type":"hello","mode":"control","authorized":true}""") }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val value = Json.parseToJsonElement(text).jsonObject
                    if (value.getValue("type").jsonPrimitive.content == "text_insert") { received.incrementAndGet(); webSocket.close(1001, "teste") }
                    else webSocket.send("""{"v":1,"type":"ack","seq":${value.getValue("seq")},"mode":"control"}""")
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            }))
        }
        val connection = RemoteConnection({ states.offer(it) }, {}, { uncertain.offer(true) })
        try {
            connection.connect(ConnectionSettings("127.0.0.1", server.port, fingerprint, token))
            awaitState(states, LinkState.READY)
            assertTrue(connection.submit(Command.Text("uma vez")))
            assertEquals(true, uncertain.poll(5, TimeUnit.SECONDS))
            awaitState(states, LinkState.RECONNECTING)
            awaitState(states, LinkState.READY)
            assertEquals(1, received.get())
        } finally { connection.close(); server.close() }
    }
}
