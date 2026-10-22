package br.com.sofapad

import java.net.InetAddress
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.*
import okhttp3.*

class ConnectionSettings(val host: String, val port: Int, val fingerprint: String, val token: String) {
    init {
        require(host.matches(Regex("[0-9.]+")) && host.split('.').size == 4)
        val ip = InetAddress.getByName(host)
        require(ip.isSiteLocalAddress || ip.isLoopbackAddress) { "Use um endereço IP da rede local." }
        require(port in 1..65535)
        require(fingerprint.matches(Regex("[a-fA-F0-9]{64}")))
        require(token.matches(Regex("[a-fA-F0-9]{64}")))
    }
    val url: String get() = "wss://$host:$port/v1/control"
    override fun toString() = "ConnectionSettings(redacted)"
}

class PinnedTrust(private val fingerprint: String) : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw CertificateException("Somente cliente TLS") }
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val cert = chain.firstOrNull() ?: throw CertificateException("Certificado ausente")
        cert.checkValidity()
        val expected = fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (!MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(cert.encoded))) throw CertificateException("Identidade do PC diferente")
    }
}

enum class LinkState(val label: String) {
    DISCONNECTED("Sem conexão com o PC"), CONNECTING("Conectando…"), READY("PC conectado"),
    RECONNECTING("Tentando reconectar…"), PAUSED("Conexão pausada"), REJECTED("Confira identidade e código no PC"),
}

class CommandBuffer {
    private val queue = ArrayDeque<Command>()
    @Synchronized fun add(command: Command): Boolean {
        val previous = queue.lastOrNull()
        if (command is Command.Move && previous is Command.Move && kotlin.math.abs(command.dx + previous.dx) <= 2048 && kotlin.math.abs(command.dy + previous.dy) <= 2048) {
            queue.removeLast(); queue.addLast(Command.Move(command.dx + previous.dx, command.dy + previous.dy)); return true
        }
        if (command is Command.Scroll && previous is Command.Scroll && kotlin.math.abs(command.vertical + previous.vertical) <= 120) {
            queue.removeLast(); queue.addLast(Command.Scroll(command.vertical + previous.vertical)); return true
        }
        if (queue.size >= 64) return false
        queue.addLast(command); return true
    }
    @Synchronized fun poll(): Command? = queue.removeFirstOrNull()
    @Synchronized fun clear(): Boolean { val text = queue.any { it is Command.Text }; queue.clear(); return text }
}

class RemoteConnection(
    private val onState: (LinkState) -> Unit,
    private val onAck: (Command) -> Unit,
    private val onUncertainText: () -> Unit,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "sofapad-link").apply { isDaemon = true } }
    private val queue = CommandBuffer()
    private var settings: ConnectionSettings? = null
    private var client: OkHttpClient? = null
    private var socket: WebSocket? = null
    @Volatile private var state = LinkState.DISCONNECTED
    private var active = false
    private var generation = 0L
    private var sequence = 0L
    private var retries = 0
    private var lastSend = 0L
    private var openedAt = 0L
    private val pending = linkedMapOf<Long, Pair<Command, Long>>()
    private var pumpTask: java.util.concurrent.ScheduledFuture<*>? = null

    private fun startPump() {
        if (pumpTask == null || pumpTask?.isCancelled == true) pumpTask = executor.scheduleWithFixedDelay({ runCatching { pump() }.onFailure { fail(false) } }, 16, 16, TimeUnit.MILLISECONDS)
    }

    fun connect(value: ConnectionSettings) = executor.execute {
        pauseInternal()
        settings = value
        active = true
        retries = 0
        startPump()
        open()
    }

    fun submit(command: Command): Boolean {
        if (state != LinkState.READY) return false
        if (!queue.add(command)) { executor.execute { fail(false) }; return false }
        return true
    }

    fun pause() = executor.execute { pauseInternal() }
    fun resume() = executor.execute { if (!active && settings != null && state != LinkState.REJECTED) { active = true; startPump(); open() } }
    fun reconnect() = executor.execute { if (settings != null) { pauseInternal(); active = true; retries = 0; startPump(); open() } }

    private fun update(value: LinkState) { state = value; onState(value) }
    private fun discard() {
        val uncertain = pending.values.any { it.first is Command.Text } || queue.clear()
        queue.clear(); pending.clear()
        if (uncertain) onUncertainText()
    }
    private fun pauseInternal() {
        active = false
        pumpTask?.cancel(false); pumpTask = null
        generation++
        if (state == LinkState.READY) socket?.send(Protocol.encode(++sequence, Command.ReleaseAll).toString())
        socket?.close(1000, "Pausa")
        socket = null
        discard()
        update(LinkState.PAUSED)
    }

    private fun open() {
        val config = settings ?: return
        val current = ++generation
        discard(); sequence = 0
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        val trust = PinnedTrust(config.fingerprint.lowercase())
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val http = OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust)
            .connectTimeout(3, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false).build()
        client = http
        openedAt = System.nanoTime()
        update(if (retries == 0) LinkState.CONNECTING else LinkState.RECONNECTING)
        val request = Request.Builder().url(config.url).header("Authorization", "Bearer ${config.token.lowercase()}").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { post { if (generation == current) runCatching { receive(text) }.onFailure { fail(true) } } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { post { if (generation == current) fail(t is SSLHandshakeException || response?.code in listOf(401,403)) } }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { post { if (generation == current) fail(false) } }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        })
    }

    private fun receive(text: String) {
        if (text.toByteArray(Charsets.UTF_8).size > 8192) { fail(true); return }
        val value = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return fail(true)
        if (value["v"]?.jsonPrimitive?.intOrNull != 1) return fail(true)
        when (value["type"]?.jsonPrimitive?.content) {
            "hello" -> {
                if (state !in listOf(LinkState.CONNECTING, LinkState.RECONNECTING) || value["mode"]?.jsonPrimitive?.content != "control" || value["authorized"]?.jsonPrimitive?.booleanOrNull != true) return fail(true)
                retries = 0; lastSend = System.nanoTime(); update(LinkState.READY)
            }
            "ack", "pong" -> {
                val seq = value["seq"]?.jsonPrimitive?.longOrNull ?: return fail(true)
                val command = pending.remove(seq)?.first ?: return fail(true)
                onAck(command)
            }
            else -> fail(true)
        }
    }

    private fun write(command: Command): Boolean {
        val seq = ++sequence
        val sent = socket?.send(Protocol.encode(seq, command).toString()) == true
        if (sent) { lastSend = System.nanoTime(); pending[seq] = command to lastSend }
        else fail(false)
        return sent
    }
    private fun pump() {
        if (state == LinkState.CONNECTING || state == LinkState.RECONNECTING) {
            if (socket != null && (System.nanoTime() - openedAt) / 1_000_000 > 5000) fail(false)
            return
        }
        if (state != LinkState.READY) return
        if (pending.size >= 64 || pending.values.any { (System.nanoTime() - it.second) / 1_000_000 > 2500 }) { fail(false); return }
        repeat(2) { val command = queue.poll() ?: return@repeat; if (!write(command)) return }
        if ((System.nanoTime() - lastSend) / 1_000_000 >= 1000) write(Command.Ping)
    }
    private fun fail(permanent: Boolean) {
        generation++
        socket?.cancel(); socket = null
        discard()
        if (permanent) { active = false; pumpTask?.cancel(false); pumpTask = null; update(LinkState.REJECTED); return }
        if (!active) return
        update(LinkState.RECONNECTING)
        val wait = (1L shl retries.coerceAtMost(4)).coerceAtMost(15)
        retries++
        val current = generation
        executor.schedule({ if (active && generation == current) open() }, wait, TimeUnit.SECONDS)
    }

    override fun close() {
        executor.execute {
            pauseInternal(); settings = null
            client?.dispatcher?.executorService?.shutdown()
            client?.connectionPool?.evictAll()
        }
        executor.shutdown()
    }

    private fun post(block: () -> Unit) {
        if (!executor.isShutdown) try { executor.execute(block) } catch (_: java.util.concurrent.RejectedExecutionException) { }
    }
}
