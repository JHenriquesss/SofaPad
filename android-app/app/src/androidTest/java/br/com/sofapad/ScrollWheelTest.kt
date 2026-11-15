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

class ScrollWheelTest {
    @get:Rule val compose = createComposeRule()
    private val simulator = Simulator()

    private fun wheel(): SemanticsNodeInteraction {
        // Relógio manual: a inércia da roda só avança quando o teste manda.
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { ControlSurface(simulator, UiPreferences(), true, Modifier.width(340.dp).height(360.dp)) } }
        compose.mainClock.advanceTimeBy(100)
        return compose.onNodeWithTag("scroll_wheel")
    }
    private fun count() = compose.runOnIdle { simulator.state.eventCount }
    private fun state() = compose.runOnIdle { simulator.state }

    /** Desliza e para o dedo antes de soltar, para não deixar a roda girando. */
    private fun SemanticsNodeInteraction.slowDrag(step: Float) = performTouchInput {
        down(center)
        repeat(8) { moveBy(Offset(0f, step)) }
        advanceEventTime(200)
        up()
    }

    @Test fun fingerDownScrollsDownLikeAMouseWheel() {
        val wheel = wheel()
        val before = count()
        wheel.slowDrag(20f)
        assertTrue(count() > before)
        assertEquals("Rolagem para baixo (simulação).", state().event)
        assertFalse(state().leftDown || state().rightDown)
    }

    @Test fun fingerUpScrollsUp() {
        val wheel = wheel()
        wheel.slowDrag(-20f)
        assertEquals("Rolagem para cima (simulação).", state().event)
    }

    @Test fun restingOrStoppedFingerDoesNotScroll() {
        val wheel = wheel()
        val before = count()
        wheel.performTouchInput { down(center); advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(300)
        assertEquals("pousar o dedo não rola", before, count())
        wheel.slowDrag(20f)
        val stopped = count()
        compose.mainClock.advanceTimeBy(500)
        assertEquals("dedo parado antes de soltar não deixa inércia", stopped, count())
    }

    @Test fun flickKeepsRollingUntilTheWheelIsTouchedAgain() {
        val wheel = wheel()
        wheel.performTouchInput { down(center); repeat(6) { moveBy(Offset(0f, 30f)) }; up() }
        val released = count()
        compose.mainClock.advanceTimeBy(200)
        val coasting = count()
        assertTrue("a roda continua girando por inércia", coasting > released)
        assertEquals("Rolagem para baixo (simulação).", state().event)
        wheel.performTouchInput { down(center) }
        val held = count()
        compose.mainClock.advanceTimeBy(400)
        assertEquals("tocar segura a roda", held, count())
        wheel.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(5000)
        assertEquals(held, count())
    }

    @Test fun inertiaEndsByItself() {
        val wheel = wheel()
        wheel.performTouchInput { down(center); repeat(6) { moveBy(Offset(0f, 30f)) }; up() }
        compose.mainClock.advanceTimeBy(6000)
        val settled = count()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(settled, count())
    }
}
