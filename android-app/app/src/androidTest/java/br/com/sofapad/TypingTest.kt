package br.com.sofapad

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TypingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun cancelNeverSendsAndShortcutDoesNotEditDraft() {
        val simulator = Simulator()
        simulator.show(Screen.TYPING)
        compose.setContent { MaterialTheme { TypingScreen(simulator, Modifier.width(340.dp).height(340.dp)) } }
        compose.onNodeWithTag("text_entry").performTextInput("Olá, ação! 😀")
        compose.onNodeWithTag("shortcut_CTRL_A").performClick()
        compose.onNodeWithTag("text_entry").assertTextContains("Olá, ação! 😀")
        compose.onNodeWithTag("cancel_text").performClick()
        compose.runOnIdle {
            assertEquals("", simulator.state.sentText)
            assertEquals(Screen.CONTROL, simulator.state.screen)
        }
    }

    @Test fun sendPreservesAccentsWithoutAppendingEnter() {
        val simulator = Simulator()
        simulator.show(Screen.TYPING)
        compose.setContent { MaterialTheme { TypingScreen(simulator, Modifier.width(340.dp).height(340.dp)) } }
        compose.onNodeWithTag("text_entry").performTextInput("Olá, ação! 😀")
        compose.onNodeWithTag("send_text").performClick()
        compose.runOnIdle { assertEquals("Olá, ação! 😀", simulator.state.sentText) }
    }

    @Test fun liveTypingMirrorsEachEditAndRefusesUnsafeDeletes() {
        val simulator = Simulator()
        simulator.show(Screen.TYPING)
        compose.setContent { MaterialTheme { TypingScreen(simulator, Modifier.width(340.dp).height(340.dp)) } }
        compose.onNodeWithTag("live_typing").performClick()
        compose.onNodeWithTag("send_text").assertIsNotEnabled()
        val field = compose.onNodeWithTag("text_entry")
        field.performTextInput("Olá")
        compose.runOnIdle {
            assertEquals("Olá", simulator.state.liveText)
            assertEquals("Digitando em tempo real (simulação).", simulator.state.event)
        }
        field.performTextInput(" 👍🏽")
        val sent = compose.runOnIdle { simulator.state.eventCount }
        field.performTextClearance()
        compose.runOnIdle {
            assertEquals("apagar emoji composto é recusado", sent, simulator.state.eventCount)
            assertEquals("Olá 👍🏽", simulator.state.liveText)
            assertEquals(LiveTyping.COMPLEX_DELETE, simulator.state.liveWarning)
        }
        field.assertTextContains("Olá 👍🏽")
        // Quebra de linha com o cursor fora do fim é recusada; no fim, vira Enter.
        field.performTextInputSelection(TextRange(0))
        field.performTextInput("\n")
        compose.runOnIdle { assertEquals(LiveTyping.MIDDLE_NEWLINE, simulator.state.liveWarning) }
        field.performTextInputSelection(TextRange("Olá 👍🏽".length))
        field.performTextInput("\n")
        compose.runOnIdle {
            assertEquals("Atalho no PC simulado: Enter", simulator.state.event)
            assertEquals("", simulator.state.liveText)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun backspaceKeyOnEmptyLiveFieldDeletesOnThePc() {
        val simulator = Simulator()
        simulator.show(Screen.TYPING)
        compose.setContent { MaterialTheme { TypingScreen(simulator, Modifier.width(340.dp).height(340.dp)) } }
        compose.onNodeWithTag("live_typing").performClick()
        val field = compose.onNodeWithTag("text_entry")
        field.performTextInput("a")
        val before = compose.runOnIdle { simulator.state.eventCount }
        // Com texto, a tecla apaga no campo e o espelho manda um Backspace só.
        field.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Backspace) }
        compose.runOnIdle { assertEquals("", simulator.state.liveText); assertEquals(before + 1, simulator.state.eventCount) }
        // Vazio: cada tecla vai direto ao PC, para apagar o que já estava lá.
        field.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Backspace) }
        field.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Backspace) }
        compose.runOnIdle {
            assertEquals(before + 3, simulator.state.eventCount)
            assertEquals("Atalho no PC simulado: ⌫", simulator.state.event)
        }
    }

    @Test fun liveTypingCannotStartOverAnUnsentDraft() {
        val simulator = Simulator()
        simulator.show(Screen.TYPING)
        compose.setContent { MaterialTheme { TypingScreen(simulator, Modifier.width(340.dp).height(340.dp)) } }
        compose.onNodeWithTag("text_entry").performTextInput("rascunho")
        compose.onNodeWithTag("live_typing").assertIsNotEnabled()
        compose.onNodeWithTag("text_entry").performTextClearance()
        compose.onNodeWithTag("live_typing").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(simulator.state.liveTyping) }
    }

    @Test fun compactTypingKeepsActionsAndScrollableShortcutsReachable() {
        val simulator = Simulator()
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.setContent { MaterialTheme { TypingScreen(simulator, Modifier.width(650.dp).height(64.dp)) } }
        compose.onNodeWithTag("text_entry").performTextInput("teste")
        compose.onNodeWithTag("send_text").assertIsDisplayed()
        compose.onNodeWithTag("cancel_text").assertIsDisplayed()
        compose.onNodeWithTag("shortcut_ESC").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("shortcut_ENTER").performScrollTo().assertIsDisplayed()
    }
}
