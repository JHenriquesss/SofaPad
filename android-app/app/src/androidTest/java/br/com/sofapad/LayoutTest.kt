package br.com.sofapad

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun layout(landscape: Boolean, leftHanded: Boolean = false) {
        val simulator = Simulator()
        compose.setContent {
            MaterialTheme {
                ControlSurface(simulator, UiPreferences(leftHanded = leftHanded), landscape,
                    Modifier.width(if (landscape) 340.dp else 300.dp).height(360.dp))
            }
        }
    }

    @Test fun portraitKeepsTouchpadAboveSideBySideButtons() {
        layout(false)
        val pad = compose.onNodeWithTag("touchpad").fetchSemanticsNode().boundsInRoot
        val left = compose.onNodeWithTag("left_button").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("right_button").fetchSemanticsNode().boundsInRoot
        assertTrue(pad.bottom <= left.top)
        assertTrue(left.right <= right.left)
        assertEquals(left.top, right.top, 1f)
    }

    @Test fun landscapeKeepsSideBySideButtonsToTheLeft() {
        layout(true)
        val pad = compose.onNodeWithTag("touchpad").fetchSemanticsNode().boundsInRoot
        val left = compose.onNodeWithTag("left_button").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("right_button").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right <= right.left)
        assertTrue(right.right <= pad.left)
        assertEquals(left.top, right.top, 1f)
    }

    @Test fun leftHandedLandscapeReversesGroups() {
        layout(true, true)
        val pad = compose.onNodeWithTag("touchpad").fetchSemanticsNode().boundsInRoot
        val left = compose.onNodeWithTag("left_button").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("right_button").fetchSemanticsNode().boundsInRoot
        assertTrue(pad.right <= left.left)
        assertTrue(left.right <= right.left)
    }

    @Test fun cancelledButtonGestureReleasesItsState() {
        val simulator = Simulator()
        compose.setContent { MaterialTheme { ControlSurface(simulator, UiPreferences(), false, Modifier.width(300.dp).height(360.dp)) } }
        compose.onNodeWithTag("left_button").performTouchInput { down(center) }
        compose.runOnIdle { assertTrue(simulator.state.leftDown) }
        compose.onNodeWithTag("left_button").performTouchInput { cancel() }
        compose.runOnIdle { assertFalse(simulator.state.leftDown) }
    }
}
