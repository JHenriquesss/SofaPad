package br.com.sofapad

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pareamento em três passos curtos, um por tela: achar o PC, conferir que é ele e digitar o
 * código de uso único. A identidade completa de 64 caracteres só aparece se a pessoa pedir.
 */
@Composable
fun PairingWizard(simulator: Simulator, modifier: Modifier = Modifier) {
    var confirmed by rememberSaveable { mutableStateOf(false) }
    val pc = simulator.probedPc
    // Sem PC sondado não há o que conferir: qualquer volta ao passo 1 zera a confirmação.
    LaunchedEffect(pc) { if (pc == null) confirmed = false }
    val step = if (pc == null) 1 else if (!confirmed) 2 else 3
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Parear PC · passo $step de 3", Modifier.testTag("pairing_step"), style = MaterialTheme.typography.titleLarge)
        LinearProgressIndicator(progress = { step / 3f }, modifier = Modifier.fillMaxWidth())
        when {
            pc == null -> FindStep(simulator)
            !confirmed -> CheckStep(simulator, pc) { confirmed = true }
            else -> CodeStep(simulator, pc) { confirmed = false }
        }
    }
}

@Composable
private fun FindStep(simulator: Simulator) {
    var manual by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("49375") }
    val busy = simulator.probeBusy
    Text("No PC, clique no sofá do SofaPad perto do relógio e escolha Parear celular. Deixe a janela aberta.")
    val found = simulator.discoveredPcs
    if (found.isEmpty()) Text("Procurando PCs na rede… O PC e o celular precisam estar no mesmo Wi-Fi.", fontSize = 13.sp)
    else Text("Toque no seu PC:", fontSize = 13.sp)
    found.forEach { device ->
        Button(onClick = { simulator.probePc(device.host, device.port, device.fingerprint) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("found_pc")) {
            Text("${device.name} · ${device.host}", maxLines = 1)
        }
    }
    if (!manual) TextButton(onClick = { manual = true }, modifier = Modifier.testTag("manual_address")) { Text("Não encontrou? Digitar o endereço") }
    else {
        Text("O endereço aparece em Status, no menu do sofá do PC.", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = host, onValueChange = { host = it.trim() }, enabled = !busy, label = { Text("IP do PC") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("pc_host"))
            OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, enabled = !busy, label = { Text("Porta") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp))
        }
        Button(onClick = { simulator.probePc(host, port.toIntOrNull() ?: 0) }, enabled = !busy && host.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("probe_pc")) { Text("Continuar") }
    }
    if (busy) Text("Falando com o PC…", fontSize = 13.sp)
    if (simulator.probeError.isNotEmpty()) Text(simulator.probeError, Modifier.testTag("probe_error"), color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = { simulator.show(Screen.SETTINGS) }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
}

@Composable
private fun CheckStep(simulator: Simulator, pc: DiscoveredPc, onConfirmed: () -> Unit) {
    var details by rememberSaveable { mutableStateOf(false) }
    Text("A janela do PC mostra um código de conferência. Ele é igual a este?")
    Text(
        IdentityProbe.checkCode(pc.fingerprint),
        Modifier.fillMaxWidth().testTag("check_code"),
        fontSize = 26.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary,
    )
    Text("Isso garante que o celular está falando com o seu PC, e não com outro aparelho da rede. Se o PC ainda mostra só a identidade longa, compare com os 16 primeiros caracteres dela.", fontSize = 13.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { simulator.rejectProbe() }, modifier = Modifier.weight(1f).testTag("check_differs")) { Text("É diferente") }
        Button(onClick = onConfirmed, modifier = Modifier.weight(1f).testTag("check_matches")) { Text("É igual") }
    }
    TextButton(onClick = { details = !details }) { Text(if (details) "Ocultar identidade completa" else "Ver identidade completa") }
    if (details) Text("${pc.host}:${pc.port}\n${pc.fingerprint.chunked(8).joinToString(" ")}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun CodeStep(simulator: Simulator, pc: DiscoveredPc, onBack: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("Meu celular") }
    val busy = simulator.pairingBusy
    val ready = code.matches(Regex("[a-f0-9]{8}")) && name.isNotBlank() && name.none(Char::isISOControl)
    Text("Digite o código de uso único de 8 caracteres mostrado no PC.")
    OutlinedTextField(value = code, onValueChange = { code = it.filterNot(Char::isWhitespace).lowercase().take(8) }, enabled = !busy, label = { Text("Código do PC") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("pair_code"))
    OutlinedTextField(value = name, onValueChange = { name = it.take(48) }, enabled = !busy, label = { Text("Nome deste celular") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (busy) Text(simulator.pairingStage.label, fontSize = 13.sp)
    if (simulator.pairingError.isNotEmpty()) Text(simulator.pairingError, Modifier.testTag("pairing_error"), color = MaterialTheme.colorScheme.error)
    Button(onClick = {
        val config = runCatching { ConnectionSettings(pc.host, pc.port, pc.fingerprint, "0".repeat(64), true) }.getOrNull()
        if (config == null) simulator.reportPairingError(PairingFailure.UNKNOWN.message) else simulator.startPairing(config, code, name)
    }, enabled = ready && !busy && !simulator.state.textPending, modifier = Modifier.fillMaxWidth().testTag("pair_now")) { Text("Parear") }
    Text("Depois, confirme o nome do celular na janela que abre no PC.", fontSize = 13.sp)
    if (busy) OutlinedButton(onClick = { simulator.cancelPairing() }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar pareamento") }
    else OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
}
