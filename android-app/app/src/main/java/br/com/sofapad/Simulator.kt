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
)

class Simulator : ViewModel() {
    var state by mutableStateOf(SimulationState())
        private set
    private var seq = 0L
    private var remote: RemoteConnection? = null
    private var connectionGeneration = 0L
    var pairedPc by mutableStateOf<ConnectionSettings?>(null)
        private set
    var credentialError by mutableStateOf("")
        private set
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
        if (!foreground || !state.remoteMode || state.link == LinkState.REJECTED || state.textPending) return
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
        state = state.copy(remoteMode = true, sentText = "", leftDown = false, rightDown = false)
        remote = RemoteConnection(
            onState = { value -> post { if (generation == connectionGeneration) state = state.copy(
                link = value, leftDown = false, rightDown = false,
                textPending = value == LinkState.READY && state.textPending,
                deliveryWarning = if (value != LinkState.READY && state.textPending) "Envio não confirmado. Confira o texto no PC antes de tentar novamente." else state.deliveryWarning,
            ) } },
            onAck = { command -> if (command is Command.Text) post {
                if (generation == connectionGeneration) {
                    state = state.copy(textPending = false, deliveryWarning = "", event = "Texto inserido no PC, sem Enter.")
                    show(Screen.CONTROL)
                }
            } },
            onUncertainText = { post { if (generation == connectionGeneration) state = state.copy(textPending = false, deliveryWarning = "Envio não confirmado. Confira o texto no PC antes de tentar novamente.") } },
        ).also { it.connect(settings) }
    }

    fun simulation() {
        connectionGeneration++
        remote?.close(); remote = null
        state = SimulationState(screen = state.screen)
    }

    fun pause() { foreground = false; releaseAll(); remote?.pause() }
    fun resume() { foreground = true; remote?.resume() }
    fun reconnect() { remote?.reconnect() }
    fun networkAvailable() { if (foreground) remote?.networkAvailable() }

    fun show(screen: Screen) {
        releaseAll()
        state = state.copy(screen = screen)
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
                is Command.Text -> state.copy(textPending = true, deliveryWarning = "", event = "Aguardando confirmação do PC…")
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
                event = "Clique ${if (command.button == MouseButton.LEFT) "esquerdo" else "direito"}: ${if (command.down) "pressionado" else "liberado"}.",
            )
            is Command.Scroll -> current.copy(event = "Rolagem ${if (command.vertical > 0) "para cima" else "para baixo"} (simulação).")
            is Command.Text -> current.copy(sentText = command.text, event = "Texto enviado à prévia, sem Enter.")
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

    /** Toque e logo toque-e-segure: pressiona o esquerdo para arrastar, como no touchpad de notebook. */
    fun tapDragStart(): Boolean = !state.leftDown && dispatch(Command.Button(MouseButton.LEFT, true))

    /** Solta o arrasto; se o segundo toque foi rápido e parado, completa um clique duplo. */
    fun tapDragEnd(doubleTap: Boolean) {
        dispatch(Command.Button(MouseButton.LEFT, false))
        if (doubleTap) click()
    }

    fun releaseAll() = dispatch(Command.ReleaseAll)

    fun sendText(text: String): Boolean {
        if (text.isNotEmpty() && !dispatch(Command.Text(text))) return false
        if (state.remoteMode) return false
        show(Screen.CONTROL)
        return true
    }

    override fun onCleared() { releaseAll(); connectionGeneration++; remote?.close() }
}
