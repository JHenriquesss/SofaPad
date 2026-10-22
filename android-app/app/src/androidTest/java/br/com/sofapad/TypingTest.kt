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
