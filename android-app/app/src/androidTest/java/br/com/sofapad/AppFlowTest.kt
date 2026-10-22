package br.com.sofapad

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun landscapeActionsStayAboveTheRealKeyboard() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithText("Horizontal").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("typing").performClick()
        compose.waitUntil(10_000) {
            val view = compose.activity.window.decorView
            view.rootWindowInsets?.let { WindowInsetsCompat.toWindowInsetsCompat(it).isVisible(WindowInsetsCompat.Type.ime()) } == true
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val ime = WindowInsetsCompat.toWindowInsetsCompat(view.rootWindowInsets).getInsets(WindowInsetsCompat.Type.ime())
        val keyboardTop = view.height - ime.bottom
        listOf("send_text", "cancel_text", "shortcut_ALT_TAB").forEach { tag ->
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            assertTrue("$tag deve ficar acima do teclado", node.positionInWindow.y + node.size.height <= keyboardTop + 1)
        }
    }
}
