package br.com.sofapad

import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.*
import okhttp3.*

class ConnectionSettings(val host: String, val port: Int, val fingerprint: String, val token: String, val stableIdentity: Boolean = false) {
    init {
        require(host.matches(Regex("[0-9.]+")) && host.split('.').size == 4)
        val ip = InetAddress.getByName(host)
        require(ip.isSiteLocalAddress || ip.isLoopbackAddress) { "Use um endereço IP da rede local." }
        require(port in 1..65535)
        require(fingerprint.matches(Regex("[a-fA-F0-9]{64}")))
        require(token.matches(Regex("[a-fA-F0-9]{64}")))
    }
    val url: String get() = "wss://${if (stableIdentity) "sofapad.local" else host}:$port/v1/control"
    fun at(host: String, port: Int) = ConnectionSettings(host, port, fingerprint, token, stableIdentity)
    override fun toString() = "ConnectionSettings(redacted)"
}

fun pinnedClient(config: ConnectionSettings): OkHttpClient {
    val trust = PinnedTrust(config.fingerprint.lowercase())
    val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    val builder = OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust)
        .proxy(java.net.Proxy.NO_PROXY)
        .socketFactory(NoDelaySocketFactory)
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
    // A identidade DNS do certificado permanece estável quando o IP do PC muda.
    // O verificador de hostname padrão e o pin SHA-256 continuam obrigatórios.
    if (config.stableIdentity) builder.dns(object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            require(hostname == "sofapad.local")
            return listOf(InetAddress.getByName(config.host))
        }
    })
    return builder.build()
}

/**
 * O OkHttp não desliga o algoritmo de Nagle. Com ele ligado, cada movimento pequeno espera a
 * confirmação TCP do anterior e os movimentos chegam ao PC em lotes, o cursor anda aos saltos.
 */
object NoDelaySocketFactory : SocketFactory() {
    private val system: SocketFactory = getDefault()
    private fun Socket.fast() = apply { tcpNoDelay = true }
    override fun createSocket(): Socket = system.createSocket().fast()
    override fun createSocket(host: String, port: Int): Socket = system.createSocket(host, port).fast()
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = system.createSocket(host, port, localHost, localPort).fast()
    override fun createSocket(host: InetAddress, port: Int): Socket = system.createSocket(host, port).fast()
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = system.createSocket(address, port, localAddress, localPort).fast()
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
    /** Parado de propósito: o PC recusou comandos repetidamente ou não entendeu um deles. */
    BLOCKED("Conexão interrompida"),
}

/** Versão do agente do PC, comparada com a mínima que este app precisa. */
object AgentVersion {
    /** Primeira versão do agente que entende tudo o que este app envia e informa a própria versão. */
    const val REQUIRED = "0.3.9"

    fun parse(text: String): List<Int>? {
        val parts = text.split('.')
        if (parts.size != 3 || text.length > 20) return null
        return parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..9999 } ?: return null }
    }

    /** Vazio (agente antigo, sem versão) ou menor que [REQUIRED]. */
    fun outdated(text: String): Boolean {
        val (current, required) = (parse(text) ?: return true) to parse(REQUIRED)!!
        for (i in 0..2) if (current[i] != required[i]) return current[i] < required[i]
        return false
    }
}

/** Motivo de uma falha da conexão, para mostrar à pessoa em vez de reconectar em silêncio. */
enum class LinkProblem(val message: String) {
    INPUT("O PC recusou um comando. Causas comuns: antivírus bloqueando o SofaPad no PC, janela de administrador em primeiro plano ou tecla Ctrl, Shift, Alt ou Windows presa."),
    RATE("Os comandos chegaram em rajada ao PC. Costuma ser Wi-Fi instável: aproxime-se do roteador."),
    PROTOCOL("O SofaPad do PC não entendeu um comando. Atualize o agente do PC para a versão que acompanha este app."),
    SLOW("O PC demorou a responder: Wi-Fi lento ou PC ocupado."),
    BUSY("O PC está em uso por outro celular ou com um pareamento em andamento."),
    UNAUTHORIZED("O PC não reconheceu este celular. Faça um novo pareamento em Configurações."),
    UNREACHABLE("Não foi possível alcançar o PC. Confira se o SofaPad está aberto no PC e se os dois estão no mesmo Wi-Fi."),
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
    private val onProblem: (LinkProblem) -> Unit = {},
    /** Versão informada pelo agente ao conectar; vazia em agentes anteriores à 0.3.9. */
    private val onAgent: (String) -> Unit = {},
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
    // Instantes das últimas recusas do PC: várias seguidas param a reconexão automática.
    private val refusals = ArrayDeque<Long>()
    // Envio imediato a cada toque (sem esperar um relógio fixo, que causava trancos
    // quando dessincronizado da taxa de quadros da tela) com teto abaixo do limite
    // de 240 comandos/s do PC. Movimentos que chegam durante o teto são somados.
    private val drainPending = AtomicBoolean(false)
    private var tokens = BURST
    private var tokensAt = System.nanoTime()

    private fun startPump() {
        if (pumpTask == null || pumpTask?.isCancelled == true) pumpTask = executor.scheduleWithFixedDelay({ runCatching { pump() }.onFailure { fail(false) } }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS)
    }

    private fun requestDrain(delayMs: Long = 0) {
        if (!drainPending.compareAndSet(false, true)) return
        val task = Runnable { drainPending.set(false); runCatching { drain() }.onFailure { fail(false) } }
        try {
            if (delayMs == 0L) executor.execute(task) else executor.schedule(task, delayMs, TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.RejectedExecutionException) { drainPending.set(false) }
    }

    private fun drain() {
        while (state == LinkState.READY && pending.size < MAX_IN_FLIGHT) {
            val now = System.nanoTime()
            tokens = minOf(BURST, tokens + (now - tokensAt) / 1e9 * RATE_PER_SECOND); tokensAt = now
            if (tokens < 1.0) { requestDrain(5); return }
            val command = queue.poll() ?: return
            tokens -= 1.0
            if (!write(command)) return
        }
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
        if (!queue.add(command)) { post { fail(false) }; return false }
        requestDrain()
        return true
    }

    /** O Wi-Fi voltou: tenta já, sem esperar o próximo intervalo de nova tentativa. */
    fun networkAvailable() = post {
        if (active && settings != null && socket == null && state == LinkState.RECONNECTING) open()
    }

    fun pause() = executor.execute { pauseInternal() }
    fun resume() = executor.execute { if (!active && settings != null && state != LinkState.REJECTED) { active = true; startPump(); open() } }
    fun reconnect() = executor.execute { if (settings != null) { pauseInternal(); refusals.clear(); active = true; retries = 0; startPump(); open() } }

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
        val http = pinnedClient(config)
        client = http
        openedAt = System.nanoTime()
        update(if (retries == 0) LinkState.CONNECTING else LinkState.RECONNECTING)
        val request = Request.Builder().url(config.url).header("Authorization", "Bearer ${config.token.lowercase()}").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { post { if (generation == current) runCatching { receive(text) }.onFailure { fail(true) } } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { post { if (generation == current) {
                val identity = t is SSLHandshakeException || t is javax.net.ssl.SSLPeerUnverifiedException
                when {
                    response?.code in listOf(401, 403) -> onProblem(LinkProblem.UNAUTHORIZED)
                    response?.code == 409 -> onProblem(LinkProblem.BUSY)
                    response == null && !identity && retries >= 2 -> onProblem(LinkProblem.UNREACHABLE)
                }
                fail(identity || response?.code in listOf(401, 403))
            } } }
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
                onAgent(value["agent"]?.jsonPrimitive?.content?.takeIf { AgentVersion.parse(it) != null }.orEmpty())
                retries = 0; lastSend = System.nanoTime(); update(LinkState.READY)
            }
            "ack", "pong" -> {
                val seq = value["seq"]?.jsonPrimitive?.longOrNull ?: return fail(true)
                val command = pending.remove(seq)?.first ?: return fail(true)
                onAck(command)
                drain()
            }
            "error" -> refused(value["code"]?.jsonPrimitive?.content)
            else -> fail(true)
        }
    }

    /** O PC respondeu com erro: avisa o motivo e decide entre tentar de novo e parar. */
    private fun refused(code: String?) {
        if (code !in RECOVERABLE_ERRORS) { onProblem(LinkProblem.PROTOCOL); fail(true, LinkState.BLOCKED); return }
        // SESSION_CLOSED só ecoa a recusa anterior: mantém o motivo já mostrado.
        if (code == "INPUT_ERROR") onProblem(LinkProblem.INPUT) else if (code == "RATE_LIMIT") onProblem(LinkProblem.RATE)
        val now = System.nanoTime()
        refusals.addLast(now)
        while (refusals.isNotEmpty() && (now - refusals.first()) / 1_000_000 > REFUSAL_WINDOW_MS) refusals.removeFirst()
        // Reconectar sem parar deixaria o cursor aos saltos sem a pessoa saber por quê.
        if (refusals.size >= MAX_REFUSALS) { refusals.clear(); fail(true, LinkState.BLOCKED) } else fail(false)
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
        // Com Wi-Fi lento, segura o envio (movimentos se somam na fila) em vez de derrubar a conexão.
        if (pending.values.any { (System.nanoTime() - it.second) / 1_000_000 > 2500 }) { onProblem(LinkProblem.SLOW); fail(false); return }
        drain()
        if (state == LinkState.READY && pending.size < MAX_IN_FLIGHT && (System.nanoTime() - lastSend) / 1_000_000 >= 1000) write(Command.Ping)
    }
    private fun fail(permanent: Boolean, stopped: LinkState = LinkState.REJECTED) {
        generation++
        socket?.cancel(); socket = null
        discard()
        if (permanent) { active = false; pumpTask?.cancel(false); pumpTask = null; update(stopped); return }
        if (!active) return
        update(LinkState.RECONNECTING)
        // Primeira tentativa quase imediata (queda breve de Wi-Fi), depois espaça até 8 s.
        val wait = RETRY_DELAYS_MS[retries.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
        retries++
        val current = generation
        executor.schedule({ if (active && generation == current) open() }, wait, TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val TICK_MS = 50L
        const val RATE_PER_SECOND = 180.0
        const val BURST = 20.0
        const val MAX_IN_FLIGHT = 32
        val RETRY_DELAYS_MS = longArrayOf(250, 1000, 2000, 4000, 8000)
        val RECOVERABLE_ERRORS = setOf("INPUT_ERROR", "RATE_LIMIT", "SESSION_CLOSED")
        const val MAX_REFUSALS = 3
        const val REFUSAL_WINDOW_MS = 20_000L
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
