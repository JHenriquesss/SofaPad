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

    @Test fun backspaceOnEmptyFieldReachesTextThatWasAlreadyOnThePc() {
        val simulator = Simulator()
        assertFalse("sem tempo real, nada", simulator.liveBackspaceBeyond())
        simulator.setLiveTyping(true)
        simulator.liveEdit("ab")
        assertFalse("com texto no campo, o Backspace segue o espelho", simulator.liveBackspaceBeyond())
        simulator.liveEdit("")
        val before = simulator.state.eventCount
        assertTrue(simulator.liveBackspaceBeyond())
        assertTrue(simulator.liveBackspaceBeyond())
        assertEquals(before + 2, simulator.state.eventCount)
        assertEquals("Atalho no PC simulado: ⌫", simulator.state.event)
    }

    @Test fun oldOrSilentAgentIsFlaggedAsOutdated() {
        assertTrue("agente sem versão é anterior à 0.3.9", AgentVersion.outdated(""))
        assertTrue(AgentVersion.outdated("0.3.7"))
        assertTrue(AgentVersion.outdated("0.2.99"))
        assertFalse(AgentVersion.outdated("0.3.9"))
        assertFalse(AgentVersion.outdated("0.3.10"))
        assertFalse(AgentVersion.outdated("1.0.0"))
        assertTrue("texto estranho não é versão", AgentVersion.outdated("0.3"))
        val simulator = Simulator()
        assertFalse("antes de conectar não há aviso", simulator.state.agentOutdated)
        simulator.reportAgent("")
        assertTrue(simulator.state.agentOutdated)
        simulator.reportAgent("0.3.9")
        assertFalse(simulator.state.agentOutdated)
    }

    @Test fun linkProblemsAreShownAndLoggedNewestFirst() {
        val simulator = Simulator()
        simulator.reportLinkProblem(LinkProblem.SLOW)
        simulator.reportLinkProblem(LinkProblem.INPUT)
        assertEquals(LinkProblem.INPUT.message, simulator.state.linkProblem)
        assertEquals(2, simulator.problemLog.size)
        assertTrue(simulator.problemLog.first().endsWith(LinkProblem.INPUT.message))
        repeat(30) { simulator.reportLinkProblem(LinkProblem.RATE) }
        assertEquals("o registro é limitado", 20, simulator.problemLog.size)
        simulator.reconnect()
        assertEquals("tentar de novo limpa o aviso, não o registro", "", simulator.state.linkProblem)
        assertEquals(20, simulator.problemLog.size)
    }

    @Test fun shortcutsKeepTheMirrorInStepWithThePc() {
        val simulator = Simulator()
        simulator.setLiveTyping(true)
        simulator.liveEdit("oi 😀")
        simulator.liveShortcut(Shortcut.BACKSPACE)
        assertEquals("⌫ apaga o último caractere do espelho", "oi ", simulator.state.liveText)
        simulator.liveShortcut(Shortcut.CTRL_C)
        assertEquals("copiar não mexe no texto", "oi ", simulator.state.liveText)
        simulator.liveShortcut(Shortcut.CTRL_A)
        assertEquals("depois de Ctrl+A o PC está diferente: recomeça", "", simulator.state.liveText)
        val before = simulator.state.eventCount
        simulator.liveShortcut(Shortcut.BACKSPACE)
        assertEquals("com o campo vazio, ⌫ vai direto ao PC", before + 1, simulator.state.eventCount)
        simulator.liveEdit("fim")
        simulator.liveShortcut(Shortcut.ENTER)
        assertEquals("", simulator.state.liveText)
        assertEquals("Atalho no PC simulado: Enter", simulator.state.event)
    }
}
