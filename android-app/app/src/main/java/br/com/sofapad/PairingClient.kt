package br.com.sofapad

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*
import okhttp3.*

class PairingClient : AutoCloseable {
    @Volatile private var socket: WebSocket? = null
    @Volatile private var closed = false
    private val done = CountDownLatch(1)
    @Volatile private var token: String? = null

    // Executado no dispatcher IO, nunca na thread de interface.
    fun pair(config: ConnectionSettings, code: String, name: String): ConnectionSettings {
        require(code.matches(Regex("[a-f0-9]{8}")))
        require(config.stableIdentity)
        require(name.isNotBlank() && name.length <= 48 && name.none(Char::isISOControl))
        check(!closed)
        val http = pinnedClient(config)
        try {
            val request = Request.Builder().url(config.url.replace("/v1/control", "/v1/pair"))
                .header("Authorization", "Bearer $code").build()
            socket = http.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (closed) { webSocket.cancel(); return }
                    webSocket.send(buildJsonObject { put("v", 1); put("type", "pair"); put("name", name) }.toString())
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.length <= 512) runCatching {
                        val value = Json.parseToJsonElement(text).jsonObject
                        require(value.keys == setOf("v", "type", "token"))
                        require(value.getValue("v").jsonPrimitive.int == 1 && value.getValue("type").jsonPrimitive.content == "paired")
                        token = value.getValue("token").jsonPrimitive.content.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
                    }
                    done.countDown()
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { done.countDown() }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { done.countDown() }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null); done.countDown() }
            })
            if (!done.await(60, TimeUnit.SECONDS) || closed) throw IOException("Pareamento cancelado ou sem confirmação no PC.")
            return ConnectionSettings(config.host, config.port, config.fingerprint.lowercase(), token ?: throw IOException("Pareamento recusado. Confira a identidade e gere outro código no PC."), true)
        } finally {
            socket?.cancel(); socket = null
            http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown()
        }
    }
    override fun close() { closed = true; socket?.cancel(); done.countDown() }
}
