package br.com.sofapad

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConnectionPanel(simulator: Simulator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("49375") }
    var fingerprint by remember { mutableStateOf("") }
    var identityChecked by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("Meu celular") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf<PairingClient?>(null) }
    var pairingStage by remember { mutableStateOf(PairingStage.CONNECTING) }
    var devices by remember { mutableStateOf(emptyList<DiscoveredPc>()) }
    val discovery = remember { PcDiscovery(context) { found -> devices = found } }
    DisposableEffect(discovery) { onDispose { discovery.close(); pairing?.close() } }
    Text("Conexão com o PC", style = MaterialTheme.typography.titleMedium)
    val saved = simulator.pairedPc
    if (saved != null) {
        Text("Autorização salva neste celular. Ao abrir o app, a conexão é automática na mesma rede Wi-Fi.", fontSize = 13.sp)
        Text("Endereço conhecido: ${saved.host}:${saved.port}", fontSize = 12.sp)
        OutlinedButton(onClick = { simulator.paired(saved); simulator.show(Screen.CONTROL) }, enabled = !busy) { Text("Conectar ao PC salvo") }
    } else {
        Text("Nenhum PC autorizado neste celular. Complete o pareamento abaixo para controlar o Windows.", fontSize = 13.sp)
    }
    if (saved != null || simulator.credentialError.isNotEmpty()) {
        OutlinedButton(onClick = { simulator.forgetPc() }, enabled = !busy) { Text("Esquecer este PC") }
        Text("Esquecer apaga a autorização deste celular. Para bloquear um aparelho perdido, use Revogar na bandeja do Windows.", fontSize = 12.sp)
    }
    if (simulator.credentialError.isNotEmpty()) Text(simulator.credentialError, color = MaterialTheme.colorScheme.error)
    HorizontalDivider()
    Text("Parear PC", style = MaterialTheme.typography.titleMedium)
    Text("No PC, clique no ícone SofaPad perto do relógio e escolha Parear celular. Confira a identidade completa e digite o código de 8 caracteres. Depois confirme o nome do celular no PC.", fontSize = 13.sp)
    OutlinedButton(onClick = { discovery.start() }, enabled = !busy) { Text("Buscar PCs na rede") }
    devices.forEach { pc ->
        TextButton(onClick = {
            host = pc.host; port = pc.port.toString()
            fingerprint = pc.fingerprint.takeIf { it.matches(Regex("[a-f0-9]{64}")) }.orEmpty()
            identityChecked = false
        }, enabled = !busy) { Text("${pc.name} · ${pc.host}") }
    }
    Text("Se o PC não aparecer, digite o endereço da janela. A identidade deve ser copiada ou conferida no próprio PC.", fontSize = 12.sp)
    Text("Use o mesmo Wi-Fi do PC. Redes de convidados ou isolamento entre aparelhos podem impedir a conexão.", fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = host, onValueChange = { host = it.trim() }, enabled = !busy, label = { Text("IP do PC") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        OutlinedTextField(value = port, onValueChange = { port = it }, enabled = !busy, label = { Text("Porta") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp))
    }
    OutlinedTextField(value = fingerprint, onValueChange = { fingerprint = it.filterNot(Char::isWhitespace).lowercase(); identityChecked = false }, enabled = !busy, label = { Text("Identidade SHA-256 exibida no PC") }, modifier = Modifier.fillMaxWidth())
    Text("Identidade para conferir: ${fingerprint.chunked(8).joinToString(" ")}", fontSize = 12.sp)
    Row {
        Checkbox(checked = identityChecked, onCheckedChange = { identityChecked = it }, enabled = !busy)
        Text("Conferi os 64 caracteres com a identidade exibida no próprio PC.", fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
    }
    OutlinedTextField(value = code, onValueChange = { code = it.filterNot(Char::isWhitespace).lowercase() }, enabled = !busy, label = { Text("Código de 8 caracteres do PC") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = name, onValueChange = { name = it.take(48) }, enabled = !busy, label = { Text("Nome deste celular") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    if (busy) Text(pairingStage.label, fontSize = 13.sp)
    Button(onClick = {
        val config = runCatching {
            require(identityChecked && code.matches(Regex("[a-f0-9]{8}")) && name.isNotBlank() && name.none(Char::isISOControl))
            ConnectionSettings(host, port.toInt(), fingerprint, "0".repeat(64), true)
        }.getOrNull()
        if (config == null) error = "Confira IP local, porta, identidade completa de 64 caracteres, código e nome. Marque a confirmação da identidade."
        else {
            // O agente atende uma conexão por vez; encerrar o controle antes de parear novamente.
            simulator.simulation()
            busy = true; error = ""
            pairingStage = PairingStage.CONNECTING
            val client = PairingClient { stage -> scope.launch { pairingStage = stage } }; pairing = client
            val pairCode = code; val pairName = name
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { client.pair(config, pairCode, pairName) }
                    }
                    result.mapCatching { pairingStage = PairingStage.SAVING; simulator.savePair(it) }
                        .onSuccess { code = ""; simulator.show(Screen.CONTROL) }
                        .onFailure { error = (it as? PairingException)?.message ?: PairingFailure.UNKNOWN.message }
                } finally { client.close(); pairing = null; busy = false }
            }
        }
    }, enabled = !busy && !simulator.state.textPending, modifier = Modifier.fillMaxWidth()) { Text("Parear e salvar autorização") }
    if (busy) OutlinedButton(onClick = { pairing?.close() }) { Text("Cancelar pareamento") }
    if (simulator.state.remoteMode) {
        OutlinedButton(onClick = { simulator.reconnect() }, enabled = !simulator.state.textPending && !busy) { Text("Tentar novamente") }
        OutlinedButton(onClick = { simulator.simulation() }, enabled = !busy) { Text("Desconectar e usar simulação") }
    }
}
