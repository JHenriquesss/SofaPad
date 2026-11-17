package br.com.sofapad

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Resumo da conexão em Configurações. O pareamento em si acontece no assistente em passos. */
@Composable
fun ConnectionPanel(simulator: Simulator) {
    val busy = simulator.pairingBusy
    val saved = simulator.pairedPc
    Text("Conexão com o PC", style = MaterialTheme.typography.titleMedium)
    if (saved != null) {
        Text("PC pareado em ${saved.host}:${saved.port}. A conexão é automática na mesma rede Wi-Fi.", fontSize = 13.sp)
        simulator.state.agentVersion?.let { Text("SofaPad do PC: ${it.ifEmpty { "versão antiga, anterior à 0.3.9" }}", fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (simulator.state.remoteMode) OutlinedButton(onClick = { simulator.reconnect() }, enabled = !simulator.state.textPending && !busy) { Text("Tentar novamente") }
            else OutlinedButton(onClick = { simulator.paired(saved); simulator.show(Screen.CONTROL) }, enabled = !busy) { Text("Conectar ao PC") }
        }
    } else {
        Text("Nenhum PC pareado neste celular.", fontSize = 13.sp)
    }
    Button(onClick = { simulator.beginPairing() }, enabled = !simulator.state.textPending, modifier = Modifier.fillMaxWidth().testTag("start_pairing")) {
        Text(if (busy) "Continuar pareamento" else if (saved != null) "Parear outro PC" else "Parear PC")
    }
    if (simulator.credentialError.isNotEmpty()) Text(simulator.credentialError, color = MaterialTheme.colorScheme.error)
    if (saved != null || simulator.credentialError.isNotEmpty()) {
        OutlinedButton(onClick = { simulator.forgetPc() }, enabled = !busy) { Text("Esquecer este PC") }
        Text("Esquecer apaga a autorização deste celular. Para bloquear um aparelho perdido, use Revogar no menu do SofaPad no PC.", fontSize = 12.sp)
    }
    if (simulator.state.remoteMode) OutlinedButton(onClick = { simulator.simulation() }, enabled = !busy) { Text("Desconectar e usar simulação") }
    if (simulator.problemLog.isNotEmpty()) {
        HorizontalDivider()
        Text("Problemas recentes da conexão", style = MaterialTheme.typography.titleMedium)
        Text("Do mais novo para o mais antigo. Envie uma captura desta lista ao relatar um defeito.", fontSize = 12.sp)
        simulator.problemLog.forEach { Text(it, fontSize = 12.sp) }
    }
}
