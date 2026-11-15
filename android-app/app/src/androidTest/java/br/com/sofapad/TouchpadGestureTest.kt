package br.com.sofapad

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TouchpadGestureTest {
    @get:Rule val compose = createComposeRule()
    private val simulator = Simulator()

    private fun pad(): SemanticsNodeInteraction {
        // Relógio manual: permite conferir que o clique do toque espera a janela do toque duplo.
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { ControlSurface(simulator, UiPreferences(), false, Modifier.width(300.dp).height(360.dp)) } }
        compose.mainClock.advanceTimeBy(100)
        return compose.onNodeWithTag("touchpad")
    }
    private fun count() = compose.runOnIdle { simulator.state.eventCount }
    private fun state() = compose.runOnIdle { simulator.state }

    @Test fun tapClicksOnceAfterTheDoubleTapWindow() {
        val pad = pad()
        val before = count()
        pad.performTouchInput { click() }
        assertEquals("clique adiado enquanto pode vir um segundo toque", before, count())
        compose.mainClock.advanceTimeBy(300)
        assertEquals(before + 2, count())
        assertEquals("Clique esquerdo: liberado.", state().event)
    }

    @Test fun tapThenTouchAndHoldDragsWithLeftButtonWithoutClickingFirst() {
        val pad = pad()
        val before = count()
        pad.performTouchInput { click(); advanceEventTime(100); down(center) }
        assertTrue(state().leftDown)
        assertEquals("só o botão pressionado, sem clique antes", before + 1, count())
        val x = state().cursorX
        pad.performTouchInput { repeat(6) { moveBy(Offset(15f, 0f)) } }
        assertTrue(state().leftDown)
        assertTrue(state().cursorX > x)
        assertEquals("Arrastando (simulação).", state().event)
        pad.performTouchInput { up() }
        assertFalse(state().leftDown)
        val after = count()
        compose.mainClock.advanceTimeBy(300)
        assertEquals("nenhum clique extra depois do arrasto", after, count())
    }

    @Test fun quickDoubleTapIsADoubleClick() {
        val pad = pad()
        val before = count()
        pad.performTouchInput { click(); advanceEventTime(100); click() }
        assertEquals(before + 4, count())
        compose.mainClock.advanceTimeBy(300)
        assertEquals(before + 4, count())
        assertFalse(state().leftDown)
    }

    @Test fun twoFingerTapIsARightClick() {
        val pad = pad()
        val before = count()
        pad.performTouchInput {
            down(0, center - Offset(60f, 0f)); down(1, center + Offset(60f, 0f))
            advanceEventTime(80); up(0); up(1)
        }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(before + 2, count())
        assertEquals("Clique direito: liberado.", state().event)
        assertFalse(state().rightDown)
    }

    @Test fun twoFingerSwipeScrollsWithoutRightClick() {
        val pad = pad()
        pad.performTouchInput {
            down(0, center - Offset(60f, 60f)); down(1, center + Offset(60f, -60f))
            repeat(8) { updatePointerBy(0, Offset(0f, 15f)); updatePointerBy(1, Offset(0f, 15f)); move() }
            up(0); up(1)
        }
        compose.mainClock.advanceTimeBy(300)
        assertTrue(state().event, state().event.startsWith("Rolagem"))
        assertFalse(state().rightDown)
    }
}
