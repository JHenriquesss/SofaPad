package br.com.sofapad

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test

class RustInteropTest {
    @Test fun kotlinClientAndRustServerExchangeUnicodeOverAuthenticatedWss() {
        val raw = System.getenv("SOFAPAD_TEST_INTEROP")
        assumeNotNull(raw)
        val config = Json.parseToJsonElement(raw!!).jsonObject
        val states = LinkedBlockingQueue<LinkState>()
        val acks = LinkedBlockingQueue<Command>()
        val connection = RemoteConnection({ states.offer(it) }, { acks.offer(it) }, {})
        try {
            var settings = ConnectionSettings(config.getValue("host").jsonPrimitive.content, config.getValue("port").jsonPrimitive.int, config.getValue("fingerprint").jsonPrimitive.content, config.getValue("token").jsonPrimitive.content, config.containsKey("code"))
            if (config.containsKey("code")) {
                settings = PairingClient().use { it.pair(settings, config.getValue("code").jsonPrimitive.content, "Celular de teste Kotlin") }
            }
            connection.connect(settings)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            var ready = false
            while (System.nanoTime() < deadline && !ready) ready = states.poll(200, TimeUnit.MILLISECONDS) == LinkState.READY
            assertTrue("Handshake Kotlin/Rust deve autorizar a sessão", ready)
            assertTrue(connection.submit(Command.Text("Olá, ação! 😀")))
            assertEquals(Command.Text("Olá, ação! 😀"), acks.poll(5, TimeUnit.SECONDS))
            connection.pause()
        } finally { connection.close() }
    }
}
