package br.com.sofapad

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PairingWizardTest {
    @get:Rule val compose = createComposeRule()
    private val identity = "ab12cd34ef567890" + "0".repeat(48)
    private val probes = AtomicInteger()
    private val simulator = Simulator().apply { probe = { _, _ -> probes.incrementAndGet(); identity } }

    private fun open() = compose.setContent { MaterialTheme { PairingWizard(simulator) } }
    private fun reachCheckStep(host: String = "192.168.1.50") {
        compose.onNodeWithTag("manual_address").performClick()
        compose.onNodeWithTag("pc_host").performTextInput(host)
        compose.onNodeWithTag("probe_pc").performClick()
        compose.waitUntil(5_000) { simulator.probedPc != null || simulator.probeError.isNotEmpty() }
    }

    @Test fun codeIsOnlyAskedAfterThePersonConfirmsTheCheckCode() {
        open()
        compose.onNodeWithTag("pairing_step").assertTextContains("passo 1 de 3", substring = true)
        compose.onNodeWithTag("pair_code").assertDoesNotExist()
        reachCheckStep()
        compose.onNodeWithTag("pairing_step").assertTextContains("passo 2 de 3", substring = true)
        // 16 caracteres da identidade em grupos de quatro, nunca os 64 de uma vez.
        compose.onNodeWithTag("check_code").assertTextEquals("AB12 CD34 EF56 7890")
        compose.onNodeWithTag("pair_code").assertDoesNotExist()
        compose.onNodeWithTag("check_matches").performClick()
        compose.onNodeWithTag("pairing_step").assertTextContains("passo 3 de 3", substring = true)
        compose.onNodeWithTag("pair_now").assertIsNotEnabled()
        compose.onNodeWithTag("pair_code").performTextInput("A1B2 C3D4")
        compose.onNodeWithTag("pair_now").assertIsEnabled()
    }

    @Test fun differentCheckCodeGoesBackWithAWarningAndSendsNothing() {
        open()
        reachCheckStep()
        compose.onNodeWithTag("check_differs").performClick()
        compose.onNodeWithTag("pairing_step").assertTextContains("passo 1 de 3", substring = true)
        compose.onNodeWithTag("probe_error").assertTextContains("Não continue", substring = true)
        compose.runOnIdle { assertNull(simulator.probedPc); assertFalse(simulator.pairingBusy) }
    }

    @Test fun addressOutsideTheLocalNetworkIsRefusedBeforeConnecting() {
        open()
        reachCheckStep("8.8.8.8")
        compose.onNodeWithTag("probe_error").assertExists()
        compose.onNodeWithTag("pairing_step").assertTextContains("passo 1 de 3", substring = true)
        assertEquals("nenhuma conexão foi tentada", 0, probes.get())
    }

    @Test fun identityAnnouncedOnTheNetworkMustMatchTheOnePresented() {
        open()
        compose.runOnIdle { simulator.probePc("192.168.1.50", 49375, announced = "f".repeat(64)) }
        compose.waitUntil(5_000) { simulator.probeError.isNotEmpty() }
        compose.runOnIdle { assertNull(simulator.probedPc) }
        compose.onNodeWithTag("pairing_step").assertTextContains("passo 1 de 3", substring = true)
    }

    @Test fun settingsShowOnePairingButtonInsteadOfTheLongForm() {
        compose.setContent { MaterialTheme { androidx.compose.foundation.layout.Column { ConnectionPanel(simulator) } } }
        compose.onNodeWithText("IP do PC").assertDoesNotExist()
        compose.onNodeWithTag("start_pairing").performClick()
        compose.runOnIdle { assertEquals(Screen.PAIRING, simulator.state.screen) }
    }
}
