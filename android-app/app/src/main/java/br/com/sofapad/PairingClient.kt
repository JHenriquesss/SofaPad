package br.com.sofapad

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.serialization.json.*
import okhttp3.*

enum class PairingStage(val label: String) {
    CONNECTING("Conectando ao PC com segurança…"),
    WAITING_PC("Código aceito. Confirme a janela de autorização no Windows."),
    SAVING("Autorização recebida. Salvando neste celular…"),
}

enum class PairingFailure(val message: String) {
    SETUP("Não foi possível preparar a conexão segura neste Android (P01). Informe esse diagnóstico para verificarmos a compatibilidade do aparelho."),
    NETWORK("Não foi possível acessar o PC. Confira o IP em Status no agente, a mesma rede Wi-Fi e a permissão do SofaPad no firewall da rede privada."),
    IDENTITY("A verificação segura da identidade do PC falhou. Confira os 64 caracteres na janela do PC e a data/hora dos aparelhos."),
    CODE("O PC recusou o código: ele pode estar incorreto, vencido ou já usado. Gere outro em Parear celular. Após várias tentativas, aguarde um minuto."),
    PC_CLOSED("O PC encerrou o pareamento sem enviar a autorização. Gere outro código e confirme Sim no Windows. Se já confirmou, o agente pode não ter conseguido salvar a autorização."),
    CONFIRMATION_TIMEOUT("O tempo para autorizar terminou. Gere outro código e confirme a janela no Windows em até um minuto; ela pode estar atrás da janela que mostra o código."),
    INVALID_REPLY("O PC enviou uma resposta de pareamento incompatível. Confira se está usando o agente SofaPad correto."),
    CANCELLED("Pareamento cancelado."),
    STORAGE("O PC autorizou, mas o Android não conseguiu salvar a autorização protegida. Use Esquecer este PC e tente um novo pareamento."),
    UNKNOWN("Não foi possível concluir o pareamento. Tente novamente com um novo código do PC."),
}

class PairingException(val failure: PairingFailure, diagnosticType: String? = null) : IOException(
    failure.message + (diagnosticType?.takeIf { it.matches(Regex("[A-Za-z0-9_$]{1,80}")) }?.let { " [$it]" } ?: "")
)

class PairingClient(private val onStage: (PairingStage) -> Unit = {}) : AutoCloseable {
    @Volatile private var socket: WebSocket? = null
    @Volatile private var closed = false
    private val done = CountDownLatch(1)
    @Volatile private var token: String? = null
    @Volatile private var opened = false
    private val failure = AtomicReference<PairingFailure?>()
    private fun finish(error: PairingFailure) { if (token == null) failure.compareAndSet(null, error); done.countDown() }

    // Executado no dispatcher IO, nunca na thread de interface.
    fun pair(config: ConnectionSettings, code: String, name: String): ConnectionSettings {
        require(code.matches(Regex("[a-f0-9]{8}")))
        require(config.stableIdentity)
        require(name.isNotBlank() && name.length <= 48 && name.none(Char::isISOControl))
        check(!closed)
        onStage(PairingStage.CONNECTING)
        val http = try { pinnedClient(config) }
        catch (error: Exception) { throw PairingException(PairingFailure.SETUP, error.javaClass.simpleName) }
        try {
            val request = Request.Builder().url(config.url.replace("/v1/control", "/v1/pair"))
                .header("Authorization", "Bearer $code").build()
            socket = http.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (closed) { webSocket.cancel(); return }
                    opened = true
                    onStage(PairingStage.WAITING_PC)
                    if (!webSocket.send(buildJsonObject { put("v", 1); put("type", "pair"); put("name", name) }.toString())) finish(PairingFailure.NETWORK)
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (token != null || failure.get() != null) return
                    if (text.length <= 512) runCatching {
                        val value = Json.parseToJsonElement(text).jsonObject
                        require(value.keys == setOf("v", "type", "token"))
                        require(value.getValue("v").jsonPrimitive.int == 1 && value.getValue("type").jsonPrimitive.content == "paired")
                        token = value.getValue("token").jsonPrimitive.content.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
                    }
                    if (token == null) finish(PairingFailure.INVALID_REPLY) else done.countDown()
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    finish(when {
                        t is SSLHandshakeException || t is SSLPeerUnverifiedException -> PairingFailure.IDENTITY
                        response?.code in listOf(401, 403) -> PairingFailure.CODE
                        response != null -> PairingFailure.INVALID_REPLY
                        else -> PairingFailure.NETWORK
                    })
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { finish(PairingFailure.PC_CLOSED) }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null); finish(PairingFailure.PC_CLOSED) }
            })
            val completed = done.await(60, TimeUnit.SECONDS)
            if (closed) throw PairingException(PairingFailure.CANCELLED)
            if (!completed) throw PairingException(if (opened) PairingFailure.CONFIRMATION_TIMEOUT else PairingFailure.NETWORK)
            return ConnectionSettings(config.host, config.port, config.fingerprint.lowercase(), token ?: throw PairingException(failure.get() ?: PairingFailure.UNKNOWN), true)
        } finally {
            socket?.cancel(); socket = null
            http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown()
        }
    }
    override fun close() { closed = true; socket?.cancel(); done.countDown() }
}
