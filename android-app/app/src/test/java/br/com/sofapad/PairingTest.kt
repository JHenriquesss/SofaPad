package br.com.sofapad

import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class PairingTest {
    private val fakeToken = "b".repeat(64)
    private fun server(hostname: String = "sofapad.local"): Pair<MockWebServer, ConnectionSettings> {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName(hostname).build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val server = MockWebServer()
        server.useHttps(tls.sslSocketFactory(), false); server.start()
        val pin = MessageDigest.getInstance("SHA-256").digest(cert.certificate.encoded).joinToString("") { "%02x".format(it) }
        return server to ConnectionSettings("127.0.0.1", server.port, pin, "0".repeat(64), true)
    }
    @Test fun pairsUsingPinnedStableHostnameAndOneTimeCode() {
        val (server, config) = server()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val request = Json.parseToJsonElement(text).jsonObject
                assertEquals("pair", request.getValue("type").jsonPrimitive.content)
                assertEquals("Meu celular", request.getValue("name").jsonPrimitive.content)
                webSocket.send("""{"v":1,"type":"paired","token":"$fakeToken"}""")
            }
        }))
        try {
            PairingClient().use { client ->
                val saved = client.pair(config, "1234abcd", "Meu celular")
                assertEquals(fakeToken, saved.token)
                assertTrue(saved.stableIdentity)
                assertEquals(config.fingerprint, saved.at("192.168.1.21", 49375).fingerprint)
                assertEquals("wss://sofapad.local:${server.port}/v1/control", saved.url)
            }
            val request = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/v1/pair", request.path)
            assertEquals("Bearer 1234abcd", request.getHeader("Authorization"))
        } finally { server.close() }
    }
    @Test fun rejectsWrongHostnameEvenWhenFingerprintMatches() {
        val (server, config) = server("another.local")
        server.enqueue(MockResponse().setResponseCode(403))
        try { PairingClient().use { client -> assertThrows(IOException::class.java) { client.pair(config, "1234abcd", "Celular") } } }
        finally { server.close() }
    }
    @Test fun rejectedCodeAndInvalidReplyNeverProduceCredential() {
        val (server, config) = server()
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { webSocket.send("""{"v":1,"type":"paired","token":"invalid"}""") }
        }))
        try {
            repeat(2) { PairingClient().use { client -> assertThrows(IOException::class.java) { client.pair(config, "1234abcd", "Celular") } } }
        } finally { server.close() }
    }
}
