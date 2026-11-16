package br.com.sofapad

import br.com.sofapad.LiveTyping.Plan
import org.junit.Assert.*
import org.junit.Test

class LiveTypingTest {
    private fun send(backspaces: Int, insert: String, enter: Boolean = false) = Plan.Send(backspaces, insert, enter)

    @Test fun typingAtTheEndSendsOnlyTheNewLetters() {
        assertEquals(send(0, "O"), LiveTyping.plan("", "O"))
        assertEquals(send(0, "lá"), LiveTyping.plan("O", "Olá"))
    }

    @Test fun autocorrectReplacesOnlyTheChangedTail() {
        // Teclado troca "ola" por "olá " ao apertar espaço.
        assertEquals(send(1, "á "), LiveTyping.plan("ola", "olá "))
        assertEquals(send(1, ""), LiveTyping.plan("abc", "ab"))
    }

    @Test fun editInTheMiddleRetypesTheTail() {
        assertEquals(send(10, "allo mundo"), LiveTyping.plan("hello mundo", "hallo mundo"))
    }

    @Test fun emojiCountsAsOneBackspaceAndIsNeverSplit() {
        assertEquals(send(1, ""), LiveTyping.plan("oi 😀", "oi "))
        // Troca de um emoji por outro com o mesmo par alto: apaga o emoji inteiro.
        assertEquals(send(1, "😁"), LiveTyping.plan("😀", "😁"))
    }

    @Test fun deletingComposedEmojiOrLooseAccentIsRefused() {
        assertEquals(Plan.Reject(LiveTyping.COMPLEX_DELETE), LiveTyping.plan("oi 👍🏽", "oi "))
        assertEquals(Plan.Reject(LiveTyping.COMPLEX_DELETE), LiveTyping.plan("á", "a"))
        assertEquals(Plan.Reject(LiveTyping.COMPLEX_DELETE), LiveTyping.plan("🇧🇷", ""))
        // Digitar esses caracteres é permitido; só apagar é que não.
        assertEquals(send(0, "👍🏽"), LiveTyping.plan("", "👍🏽"))
    }

    @Test fun largeDeleteIsRefused() {
        val text = "x".repeat(LiveTyping.MAX_BACKSPACES + 1)
        assertEquals(Plan.Reject(LiveTyping.LARGE_DELETE), LiveTyping.plan(text, ""))
        assertEquals(send(LiveTyping.MAX_BACKSPACES, ""), LiveTyping.plan(text, "x"))
    }

    @Test fun enterOnlyAtTheEnd() {
        assertEquals(send(0, "", enter = true), LiveTyping.plan("oi", "oi\n"))
        assertEquals(send(0, "!", enter = true), LiveTyping.plan("oi", "oi!\n"))
        assertEquals(Plan.Reject(LiveTyping.MIDDLE_NEWLINE), LiveTyping.plan("abcd", "ab\ncd"))
        assertEquals(Plan.Reject(LiveTyping.MIDDLE_NEWLINE), LiveTyping.plan("", "a\nb\n"))
    }

    @Test fun simulatorMirrorsAcceptedEditsAndRefusesUnsafeOnes() {
        val simulator = Simulator()
        assertFalse("desligado por padrão", simulator.liveEdit("a"))
        simulator.setLiveTyping(true)
        assertTrue(simulator.liveEdit("ola"))
        assertTrue(simulator.liveEdit("olá "))
        assertEquals("olá ", simulator.state.liveText)
        assertTrue(simulator.liveEdit("olá 👍🏽"))
        val before = simulator.state.eventCount
        assertFalse(simulator.liveEdit("olá "))
        assertEquals("nada enviado na recusa", before, simulator.state.eventCount)
        assertEquals("olá 👍🏽", simulator.state.liveText)
        assertEquals(LiveTyping.COMPLEX_DELETE, simulator.state.liveWarning)
        assertTrue(simulator.liveEdit("olá 👍🏽\n"))
        assertEquals("Enter zera o espelho", "", simulator.state.liveText)
        assertEquals("Atalho no PC simulado: Enter", simulator.state.event)
    }

    @Test fun leavingTheScreenOrTheAppRestartsLiveText() {
        val simulator = Simulator()
        simulator.setLiveTyping(true)
        simulator.liveEdit("abc")
        simulator.show(Screen.CONTROL)
        assertEquals("", simulator.state.liveText)
        assertTrue("preferência continua", simulator.state.liveTyping)
        simulator.liveEdit("abc")
        simulator.pause()
        assertEquals("", simulator.state.liveText)
    }
}
