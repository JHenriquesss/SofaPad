package br.com.sofapad

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class MouseButton(val wire: String) { LEFT("left"), RIGHT("right") }
enum class Shortcut(val label: String) {
    ALT_TAB("Alt+Tab"), CTRL_C("Ctrl+C"), CTRL_V("Ctrl+V"), CTRL_A("Ctrl+A"),
    ENTER("Enter"), ESC("Esc"), BACKSPACE("⌫")
}
enum class Media { PLAY_PAUSE, VOLUME_UP, VOLUME_DOWN }

sealed interface Command {
    data class Move(val dx: Float, val dy: Float) : Command
    data class Button(val button: MouseButton, val down: Boolean) : Command
    data class Scroll(val vertical: Float) : Command
    data class Text(val text: String) : Command
    data class Key(val name: Shortcut) : Command
    data class MediaKey(val name: Media) : Command
    data object ReleaseAll : Command
    data object Ping : Command
}

object Protocol {
    const val MAX_TEXT_BYTES = 4096
    const val MAX_SEQ = 9_007_199_254_740_991L

    fun encode(seq: Long, command: Command): JsonObject {
        require(seq in 1..MAX_SEQ)
        return buildJsonObject {
            put("v", 1)
            put("seq", seq)
            when (command) {
                is Command.Move -> {
                    require(command.dx.isFinite() && command.dy.isFinite())
                    require(kotlin.math.abs(command.dx) <= 2048 && kotlin.math.abs(command.dy) <= 2048)
                    put("type", "mouse_move"); put("dx", command.dx); put("dy", command.dy)
                }
                is Command.Button -> {
                    put("type", "mouse_button"); put("button", command.button.wire)
                    put("state", if (command.down) "down" else "up")
                }
                is Command.Scroll -> {
                    require(command.vertical.isFinite() && kotlin.math.abs(command.vertical) <= 120)
                    put("type", "mouse_scroll"); put("vertical", command.vertical)
                }
                is Command.Text -> {
                    require(command.text.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES)
                    put("type", "text_insert"); put("text", command.text)
                }
                is Command.Key -> { put("type", "shortcut"); put("name", command.name.name) }
                is Command.MediaKey -> { put("type", "media"); put("name", command.name.name) }
                Command.ReleaseAll -> put("type", "release_all")
                Command.Ping -> put("type", "ping")
            }
        }
    }
}
