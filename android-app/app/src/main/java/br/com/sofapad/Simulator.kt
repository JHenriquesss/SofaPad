package br.com.sofapad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

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
)

class Simulator : ViewModel() {
    var state by mutableStateOf(SimulationState())
        private set
    private var seq = 0L

    fun show(screen: Screen) {
        releaseAll()
        state = state.copy(screen = screen)
    }

    fun dispatch(command: Command) {
        Protocol.encode(++seq, command)
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
    }

    fun click() {
        // Não soltar um arrasto que pertence ao botão físico da interface.
        if (state.leftDown) return
        dispatch(Command.Button(MouseButton.LEFT, true))
        dispatch(Command.Button(MouseButton.LEFT, false))
    }

    fun releaseAll() = dispatch(Command.ReleaseAll)

    fun sendText(text: String) {
        if (text.isNotEmpty()) dispatch(Command.Text(text))
        show(Screen.CONTROL)
    }

    override fun onCleared() { releaseAll() }
}
