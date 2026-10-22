package br.com.sofapad

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test fun sharedFixturesMatchAndroidEncoder() {
        val fixtures = javaClass.classLoader!!.getResource("valid.json")!!.readText()
        Json.parseToJsonElement(fixtures).jsonArray.forEach { element ->
            val obj = element.jsonObject
            val type = obj.getValue("type").jsonPrimitive.content
            val command = when (type) {
                "mouse_move" -> Command.Move(obj.getValue("dx").jsonPrimitive.float, obj.getValue("dy").jsonPrimitive.float)
                "mouse_button" -> Command.Button(MouseButton.entries.first { it.wire == obj.getValue("button").jsonPrimitive.content }, obj.getValue("state").jsonPrimitive.content == "down")
                "mouse_scroll" -> Command.Scroll(obj.getValue("vertical").jsonPrimitive.float)
                "text_insert" -> Command.Text(obj.getValue("text").jsonPrimitive.content)
                "shortcut" -> Command.Key(Shortcut.valueOf(obj.getValue("name").jsonPrimitive.content))
                "media" -> Command.MediaKey(Media.valueOf(obj.getValue("name").jsonPrimitive.content))
                "release_all" -> Command.ReleaseAll
                "ping" -> Command.Ping
                else -> error("Fixture sem cobertura")
            }
            val encoded = Protocol.encode(obj.getValue("seq").jsonPrimitive.long, command)
            assertEquals(obj.keys, encoded.keys)
            obj.forEach { (key, value) ->
                if (key in listOf("dx", "dy", "vertical")) assertEquals(value.jsonPrimitive.double, encoded.getValue(key).jsonPrimitive.double, 0.0001)
                else assertEquals(value, encoded[key])
            }
        }
    }

    @Test fun textSizeUsesUtf8AndNeverAddsEnter() {
        val text = "é".repeat(2048)
        assertEquals(text, Protocol.encode(1, Command.Text(text)).getValue("text").jsonPrimitive.content)
        assertThrows(IllegalArgumentException::class.java) { Protocol.encode(1, Command.Text(text + "é")) }
        assertThrows(IllegalArgumentException::class.java) { Protocol.encode(1, Command.Move(Float.NaN, 0f)) }
        assertThrows(IllegalArgumentException::class.java) { Protocol.encode(0, Command.Ping) }
        assertThrows(IllegalArgumentException::class.java) { Protocol.encode(1, Command.Scroll(121f)) }
    }

    @Test fun screenChangesAndStopReleaseButtons() {
        val simulator = Simulator()
        simulator.dispatch(Command.Button(MouseButton.LEFT, true))
        simulator.dispatch(Command.Button(MouseButton.RIGHT, true))
        simulator.show(Screen.TYPING)
        assertFalse(simulator.state.leftDown)
        assertFalse(simulator.state.rightDown)
        simulator.dispatch(Command.Button(MouseButton.LEFT, true))
        simulator.releaseAll()
        assertFalse(simulator.state.leftDown)
    }

    @Test fun sendComposesTextLocallyAndShortcutDoesNotChangeIt() {
        val simulator = Simulator()
        simulator.sendText("Olá, ação! 😀")
        assertEquals("Olá, ação! 😀", simulator.state.sentText)
        assertEquals(Screen.CONTROL, simulator.state.screen)
        simulator.dispatch(Command.Key(Shortcut.CTRL_A))
        assertEquals("Olá, ação! 😀", simulator.state.sentText)
        simulator.click()
        assertFalse(simulator.state.leftDown)
    }

    @Test fun touchpadTapDoesNotReleaseAnActiveDrag() {
        val simulator = Simulator()
        simulator.dispatch(Command.Button(MouseButton.LEFT, true))
        simulator.click()
        assertTrue(simulator.state.leftDown)
    }
}
