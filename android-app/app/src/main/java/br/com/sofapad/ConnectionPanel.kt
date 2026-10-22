package br.com.sofapad

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConnectionPanel(simulator: Simulator) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("49375") }
    var fingerprint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf(emptyList<DiscoveredPc>()) }
    val discovery = remember { PcDiscovery(context) { found -> handler.post { devices = found } } }
    DisposableEffect(discovery) { onDispose { discovery.close() } }
    Text("Conectar ao PC", style = MaterialTheme.typography.titleMedium)
    Text("No agente Windows, autorize uma sessão e copie os dados da janela. Nesta versão, a autorização é temporária e precisa ser refeita ao reiniciar o agente.", fontSize = 13.sp)
    OutlinedButton(onClick = { discovery.start() }) { Text("Buscar PCs na rede") }
    devices.forEach { pc ->
        TextButton(onClick = { host = pc.host; port = pc.port.toString() }) { Text("${pc.name} · ${pc.host}") }
    }
    Text("Se o PC não aparecer, digite o endereço. Descobrir o PC não concede autorização.", fontSize = 12.sp)
    Text("Use o mesmo Wi-Fi do PC. Redes de convidados ou isolamento entre aparelhos podem impedir a conexão.", fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = host, onValueChange = { host = it.trim() }, label = { Text("IP do PC") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Porta") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp))
    }
    OutlinedTextField(value = fingerprint, onValueChange = { fingerprint = it.filterNot(Char::isWhitespace) }, label = { Text("Identidade SHA-256 exibida no PC") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = token, onValueChange = { token = it.filterNot(Char::isWhitespace) }, label = { Text("Código privado da sessão") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    Button(onClick = {
        val config = runCatching { ConnectionSettings(host, port.toInt(), fingerprint, token) }.getOrNull()
        if (config == null) error = "Confira IP local, porta e os dois códigos completos de 64 caracteres."
        else { simulator.connect(config) { action -> handler.post { action() } }; token = ""; simulator.show(Screen.CONTROL) }
    }, modifier = Modifier.fillMaxWidth()) { Text("Conectar com autorização do PC") }
    if (simulator.state.remoteMode) {
        OutlinedButton(onClick = { simulator.reconnect() }, enabled = !simulator.state.textPending) { Text("Tentar novamente") }
        OutlinedButton(onClick = { simulator.simulation() }) { Text("Desconectar e usar simulação") }
    }
}
