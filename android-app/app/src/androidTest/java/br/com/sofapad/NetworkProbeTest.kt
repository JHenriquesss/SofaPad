package br.com.sofapad

import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NetworkProbeTest {
    @Test fun reachesPinnedAgentAndRejectsMissingAuthorization() {
        val arguments = InstrumentationRegistry.getArguments()
        val host = arguments.getString("sofapadHost").orEmpty()
        val fingerprint = arguments.getString("sofapadFingerprint").orEmpty()
        assumeTrue("Diagnóstico físico opt-in: informe sofapadHost e sofapadFingerprint públicos.", host.isNotBlank() && fingerprint.isNotBlank())
        val port = arguments.getString("sofapadPort")?.toInt() ?: 49375
        val config = ConnectionSettings(host, port, fingerprint, "0".repeat(64), true)

        try {
            Socket().use { it.connect(InetSocketAddress(config.host, config.port), 3_000) }
        } catch (error: Exception) {
            throw AssertionError("TCP do próprio aplicativo não alcançou o agente.", error)
        }

        val client = pinnedClient(config)
        val done = CountDownLatch(1)
        val status = AtomicInteger(0)
        val failure = AtomicReference<Throwable?>()
        // Sem código ou token real: a única resposta aceita é a recusa HTTP 403.
        val socket = client.newWebSocket(
            Request.Builder().url(config.url.replace("/v1/control", "/v1/pair")).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    status.set(response.code)
                    webSocket.cancel()
                    done.countDown()
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    status.set(response?.code ?: 0)
                    failure.set(t)
                    done.countDown()
                }
            },
        )
        try {
            assertTrue("A conexão TLS/WSS não respondeu em 10 segundos.", done.await(10, TimeUnit.SECONDS))
            if (status.get() == 0) throw AssertionError("TCP passou, mas TLS/WSS do aplicativo falhou.", failure.get())
            assertEquals("O agente deve recusar o pareamento sem autorização.", 403, status.get())
        } finally {
            socket.cancel()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
