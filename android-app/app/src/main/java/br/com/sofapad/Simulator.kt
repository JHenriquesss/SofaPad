package br.com.sofapad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Screen { CONTROL, TYPING, SETTINGS }

data class SimulationState(
    val screen: Screen = Screen.CONTROL,
    val cursorX: Float = 0.5f,
    val cursorY: Float = 0.5f,
    val leftDown: Boolean = false,
    val rightDown: Boolean = false,
    val sentText: String = "",
    val event: String = "Deslize para mover o cursor simulado.",
    val eventCount: Long = 0,
    val remoteMode: Boolean = false,
    val link: LinkState = LinkState.DISCONNECTED,
    val textPending: Boolean = false,
    val deliveryWarning: String = "",
    /** Digitação em tempo real ligada; desligada por padrão. */
    val liveTyping: Boolean = false,
    /** O que o PC recebeu nesta sessão de tempo real (o campo mostra exatamente isto). */
    val liveText: String = "",
    val liveWarning: String = "",
    /** Motivo da última falha da conexão; some depois de um tempo com a conexão estável. */
    val linkProblem: String = "",
    /** Versão do agente do PC nesta conexão; nula antes do primeiro contato, vazia se ele não informa. */
    val agentVersion: String? = null,
) {
    /** O agente do PC é mais antigo do que este app precisa. */
    val agentOutdated: Boolean get() = agentVersion != null && AgentVersion.outdated(agentVersion)
}

class Simulator : ViewModel() {
    var state by mutableStateOf(SimulationState())
        private set
    private var seq = 0L
    private var remote: RemoteConnection? = null
    private var connectionGeneration = 0L
    /** Últimos problemas da conexão, com hora, para a pessoa relatar (Configurações). */
    var problemLog by mutableStateOf(emptyList<String>())
        private set
    private var problemEpoch = 0L
    var pairedPc by mutableStateOf<ConnectionSettings?>(null)
        private set
    var credentialError by mutableStateOf("")
        private set
    var pairingBusy by mutableStateOf(false)
        private set
    var pairingStage by mutableStateOf(PairingStage.CONNECTING)
        private set
    var pairingError by mutableStateOf("")
        private set
    private var activePairing: PairingClient? = null
    private var credentialsLoaded = false
    private var credentialStore: CredentialStore? = null
    private val credentialMutex = Mutex()
    private var foreground = false
    private var latestDevices = emptyList<DiscoveredPc>()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private fun post(action: () -> Unit) { main.post { action() } }

    fun loadCredentials(context: Context) {
        if (credentialsLoaded) return
        credentialsLoaded = true
        credentialStore = CredentialStore(context.applicationContext)
        viewModelScope.launch {
            val restored = credentialMutex.withLock { withContext(Dispatchers.IO) { runCatching { credentialStore!!.read() } } }
            restored.onSuccess { config ->
                pairedPc = config
                if (config != null) { connect(config, ::post); if (!foreground) pause() else discovered(latestDevices) }
            }.onFailure { credentialError = "Não foi possível abrir a autorização salva. Esqueça este PC e faça um novo pareamento." }
        }
    }
    fun paired(config: ConnectionSettings) {
        pairedPc = config; credentialError = ""
        connect(config, ::post)
    }
    fun reportPairingError(message: String) {
        pairingError = message
    }
    fun startPairing(config: ConnectionSettings, code: String, name: String) {
        if (pairingBusy || state.textPending) return
        // O agente atende uma conexão por vez; encerrar o controle antes de parear novamente.
        simulation()
        pairingBusy = true
        pairingError = ""
        pairingStage = PairingStage.CONNECTING
        lateinit var client: PairingClient
        client = PairingClient { stage -> post { if (activePairing === client) pairingStage = stage } }
        activePairing = client
        viewModelScope.launch {
            try {
                val pairedConfig = withContext(Dispatchers.IO) { client.pair(config, code, name) }
                if (activePairing === client) pairingStage = PairingStage.SAVING
                savePair(pairedConfig)
                if (!foreground) pause()
                show(Screen.CONTROL)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                pairingError = (error as? PairingException)?.message ?: PairingFailure.UNKNOWN.message
            } finally {
                client.close()
                if (activePairing === client) activePairing = null
                pairingBusy = false
            }
        }
    }
    fun cancelPairing() {
        activePairing?.close()
    }
    suspend fun savePair(config: ConnectionSettings) {
        try {
            credentialMutex.withLock { withContext(Dispatchers.IO) { checkNotNull(credentialStore).save(config) } }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            credentialError = PairingFailure.STORAGE.message
            throw PairingException(PairingFailure.STORAGE)
        }
        paired(config)
    }
    fun discovered(devices: List<DiscoveredPc>) {
        latestDevices = devices
        val current = pairedPc ?: return
        if (!foreground || !state.remoteMode || state.link == LinkState.REJECTED || state.link == LinkState.BLOCKED || state.textPending) return
        val pc = devices.firstOrNull { it.fingerprint == current.fingerprint } ?: return
        if (pc.host == current.host && pc.port == current.port) return
        val updated = runCatching { current.at(pc.host, pc.port) }.getOrNull() ?: return
        pairedPc = updated
        connect(updated, ::post)
        viewModelScope.launch { credentialMutex.withLock { withContext(Dispatchers.IO) { runCatching { credentialStore?.save(updated) } } } }
    }
    fun forgetPc() {
        simulation(); pairedPc = null
        viewModelScope.launch {
            val result = credentialMutex.withLock { withContext(Dispatchers.IO) { runCatching { credentialStore?.forget() } } }
            credentialError = if (result.isSuccess) "" else "Não foi possível apagar a autorização local. Tente novamente."
        }
    }

    fun connect(settings: ConnectionSettings, post: (() -> Unit) -> Unit) {
        val generation = ++connectionGeneration
        remote?.close()
        state = state.copy(remoteMode = true, sentText = "", leftDown = false, rightDown = false, liveText = "", liveWarning = "", linkProblem = "", agentVersion = null)
        remote = RemoteConnection(
            onState = { value -> post { if (generation == connectionGeneration) {
                state = state.copy(
                    link = value, leftDown = false, rightDown = false,
                    textPending = value == LinkState.READY && state.textPending,
                    deliveryWarning = if (value != LinkState.READY && state.textPending) "Envio não confirmado. Confira o texto no PC antes de tentar novamente." else state.deliveryWarning,
                )
                // O aviso fica enquanto a conexão oscila e some após um tempo estável.
                val epoch = ++problemEpoch
                if (value == LinkState.READY && state.linkProblem.isNotEmpty()) main.postDelayed({
                    if (epoch == problemEpoch && state.link == LinkState.READY) state = state.copy(linkProblem = "")
                }, PROBLEM_CLEAR_MS)
            } } },
            onAck = { command -> if (command is Command.Text && !command.live) post {
                if (generation == connectionGeneration) {
                    state = state.copy(textPending = false, deliveryWarning = "", event = "Texto inserido no PC, sem Enter.")
                    show(Screen.CONTROL)
                }
            } },
            onAgent = { version -> post { if (generation == connectionGeneration) reportAgent(version) } },
            onProblem = { problem -> post { if (generation == connectionGeneration) reportLinkProblem(problem) } },
            onUncertainText = { post { if (generation == connectionGeneration) state =
                if (state.textPending || !state.liveTyping) state.copy(textPending = false, deliveryWarning = "Envio não confirmado. Confira o texto no PC antes de tentar novamente.")
                // Não se sabe o que chegou: recomeçar do zero em vez de apagar às cegas no PC.
                else state.copy(liveText = "", liveWarning = LIVE_UNCERTAIN)
            } },
        ).also { it.connect(settings) }
    }

    fun simulation() {
        connectionGeneration++
        remote?.close(); remote = null
        state = SimulationState(screen = state.screen, liveTyping = state.liveTyping)
    }

    fun pause() {
        foreground = false; releaseAll(); remote?.pause()
        // Fora do app o foco do PC pode mudar: ao voltar, o tempo real recomeça com o campo vazio.
        state = state.copy(liveText = "")
    }
    fun resume() { foreground = true; remote?.resume() }
    fun reconnect() { state = state.copy(linkProblem = ""); remote?.reconnect() }

    internal fun reportAgent(version: String) {
        state = state.copy(agentVersion = version)
    }

    internal fun reportLinkProblem(problem: LinkProblem) {
        problemEpoch++
        state = state.copy(linkProblem = problem.message)
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())
        problemLog = (listOf("$time · ${problem.message}") + problemLog).take(MAX_PROBLEM_LOG)
    }
    fun networkAvailable() { if (foreground) remote?.networkAvailable() }

    fun show(screen: Screen) {
        releaseAll()
        state = state.copy(screen = screen, liveText = "", liveWarning = "")
    }

    fun dispatch(command: Command): Boolean {
        Protocol.encode(++seq, command)
        if (state.remoteMode) {
            if (remote?.submit(command) != true) {
                state = state.copy(leftDown = false, rightDown = false, event = "Sem conexão. Nenhum comando foi guardado para repetir.")
                return false
            }
            state = when (command) {
                is Command.Button -> state.copy(leftDown = if (command.button == MouseButton.LEFT) command.down else state.leftDown, rightDown = if (command.button == MouseButton.RIGHT) command.down else state.rightDown)
                is Command.Text -> if (command.live) state else state.copy(textPending = true, deliveryWarning = "", event = "Aguardando confirmação do PC…")
                Command.ReleaseAll -> state.copy(leftDown = false, rightDown = false)
                else -> state
            }
            return true
        }
        val current = state
        state = when (command) {
            is Command.Move -> current.copy(
                cursorX = (current.cursorX + command.dx / 600).coerceIn(0f, 1f),
                cursorY = (current.cursorY + command.dy / 400).coerceIn(0f, 1f),
                event = if (current.leftDown) "Arrastando (simulação)." else "Cursor movido (simulação).",
            )
            is Command.Button -> current.copy(
                leftDown = if (command.button == MouseButton.LEFT) command.down else current.leftDown,
                rightDown = if (command.button == MouseButton.RIGHT) command.down else current.rightDown,
                event = "Clique ${command.button.label}: ${if (command.down) "pressionado" else "liberado"}.",
            )
            is Command.Scroll -> current.copy(event = "Rolagem ${if (command.vertical > 0) "para cima" else "para baixo"} (simulação).")
            is Command.Text -> if (command.live) current.copy(event = "Digitando em tempo real (simulação).")
                else current.copy(sentText = command.text, event = "Texto enviado à prévia, sem Enter.")
            is Command.Key -> current.copy(event = "Atalho no PC simulado: ${command.name.label}")
            is Command.MediaKey -> current.copy(event = when (command.name) {
                Media.PLAY_PAUSE -> "Reproduzir/pausar (simulação)."
                Media.VOLUME_UP -> "Volume + (simulação)."
                Media.VOLUME_DOWN -> "Volume − (simulação)."
            })
            Command.ReleaseAll -> current.copy(leftDown = false, rightDown = false, event = if (current.leftDown || current.rightDown) "Botões liberados." else current.event)
            Command.Ping -> current
        }.copy(eventCount = current.eventCount + 1)
        return true
    }

    fun click() {
        // Não soltar um arrasto que pertence ao botão físico da interface.
        if (state.leftDown) return
        dispatch(Command.Button(MouseButton.LEFT, true))
        dispatch(Command.Button(MouseButton.LEFT, false))
    }

    fun rightClick() {
        if (state.rightDown) return
        dispatch(Command.Button(MouseButton.RIGHT, true))
        dispatch(Command.Button(MouseButton.RIGHT, false))
    }

    /** Clique do meio completo (duplo toque na rodinha): abre links em nova aba, fecha abas. */
    fun middleClick() {
        if (dispatch(Command.Button(MouseButton.MIDDLE, true))) dispatch(Command.Button(MouseButton.MIDDLE, false))
    }

    /** Toque e logo toque-e-segure: pressiona o esquerdo para arrastar, como no touchpad de notebook. */
    fun tapDragStart(): Boolean = !state.leftDown && dispatch(Command.Button(MouseButton.LEFT, true))

    /** Solta o arrasto; se o segundo toque foi rápido e parado, completa um clique duplo. */
    fun tapDragEnd(doubleTap: Boolean) {
        dispatch(Command.Button(MouseButton.LEFT, false))
        if (doubleTap) click()
    }

    fun releaseAll() = dispatch(Command.ReleaseAll)

    /** Liga ou desliga o tempo real. O texto já digitado fica no PC; o campo recomeça vazio. */
    fun setLiveTyping(on: Boolean) {
        if (state.textPending) return
        state = state.copy(liveTyping = on, liveText = "", liveWarning = "")
    }

    /**
     * Novo conteúdo do campo em tempo real. Retorna false quando a edição foi recusada;
     * o campo mostra [SimulationState.liveText], então volta sozinho ao que o PC tem.
     */
    fun liveEdit(next: String): Boolean {
        if (!state.liveTyping || state.textPending) return false
        // O campo repete o valor atual (ex.: logo após uma recusa): nada a enviar, e o aviso fica.
        if (next == state.liveText) return true
        if (state.remoteMode && state.link != LinkState.READY) {
            state = state.copy(liveWarning = "Sem conexão com o PC: nada foi enviado.")
            return false
        }
        val plan = when (val result = LiveTyping.plan(state.liveText, next)) {
            is LiveTyping.Plan.Reject -> { state = state.copy(liveWarning = result.reason); return false }
            is LiveTyping.Plan.Send -> result
        }
        val commands = buildList {
            repeat(plan.backspaces) { add(Command.Key(Shortcut.BACKSPACE)) }
            if (plan.insert.isNotEmpty()) add(Command.Text(plan.insert, live = true))
            if (plan.enter) add(Command.Key(Shortcut.ENTER))
        }
        for (command in commands) if (!dispatch(command)) {
            state = state.copy(liveText = "", liveWarning = LIVE_UNCERTAIN)
            return false
        }
        val mirrored = if (plan.enter) "" else next
        state = state.copy(liveText = mirrored, liveWarning = "", sentText = if (state.remoteMode) state.sentText else mirrored)
        return true
    }

    /**
     * Backspace com o campo vazio: apaga no PC o que já estava lá antes (ou o que o PC
     * completou sozinho). Com texto no campo, o Backspace segue o espelho normal.
     */
    fun liveBackspaceBeyond(): Boolean {
        if (!state.liveTyping || state.textPending || state.liveText.isNotEmpty()) return false
        if (state.remoteMode && state.link != LinkState.READY) {
            state = state.copy(liveWarning = "Sem conexão com o PC: nada foi enviado.")
            return true
        }
        if (dispatch(Command.Key(Shortcut.BACKSPACE))) state = state.copy(liveWarning = "")
        return true
    }

    /** Atalhos com o tempo real ligado, sem deixar o espelho diferente do PC. */
    fun liveShortcut(shortcut: Shortcut) {
        val text = state.liveText
        when (shortcut) {
            Shortcut.BACKSPACE ->
                if (text.isEmpty()) liveBackspaceBeyond()
                else liveEdit(text.substring(0, text.offsetByCodePoints(text.length, -1)))
            // Copiar não muda o texto do PC.
            Shortcut.CTRL_C -> dispatch(Command.Key(shortcut))
            // Os demais mudam o texto ou o foco no PC: o que vier depois recomeça do zero.
            else -> if (dispatch(Command.Key(shortcut))) state = state.copy(liveText = "", liveWarning = "")
        }
    }

    private companion object {
        const val PROBLEM_CLEAR_MS = 10_000L
        const val MAX_PROBLEM_LOG = 20
        const val LIVE_UNCERTAIN = "A conexão falhou durante a digitação. Confira o texto no PC; o campo recomeçou vazio."
    }

    fun sendText(text: String): Boolean {
        if (text.isNotEmpty() && !dispatch(Command.Text(text))) return false
        if (state.remoteMode) return false
        show(Screen.CONTROL)
        return true
    }

    override fun onCleared() { activePairing?.close(); releaseAll(); connectionGeneration++; remote?.close() }
}
