package br.com.sofapad

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

private val Background = Color(0xFF111C2B)
private val Panel = Color(0xFF24364A)
private val Pad = Color(0xFFBDD2E1)
private val Accent = Color(0xFF1680BD)

@Composable
fun SofaPadApp(simulator: Simulator, preferences: UiPreferences, onPreferences: (UiPreferences) -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF78C6F0), background = Background, surface = Panel)) {
        val state = simulator.state
        val compactTyping = state.screen == Screen.TYPING &&
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val lifecycle = LocalLifecycleOwner.current
        DisposableEffect(lifecycle, simulator) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) simulator.pause()
                if (event == Lifecycle.Event.ON_RESUME) simulator.resume()
            }
            lifecycle.lifecycle.addObserver(observer)
            onDispose { lifecycle.lifecycle.removeObserver(observer); simulator.releaseAll() }
        }
        BackHandler(enabled = state.screen != Screen.CONTROL) { simulator.show(Screen.CONTROL) }
        Surface(color = Background) {
            Column(
                Modifier.fillMaxSize().background(Background).safeDrawingPadding().imePadding().padding(if (compactTyping) 4.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (compactTyping) {
                    Text(if (state.deliveryWarning.isNotEmpty()) "Envio incerto: confira o PC antes de reenviar." else if (state.remoteMode) "Digitar · ${state.link.label}" else "Digitar · simulação", fontSize = 10.sp)
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SofaPad", fontSize = 18.sp)
                        Text(if (state.remoteMode) "● ${state.link.label}" else "● Simulação · sem conexão com o PC", color = Color(0xFF98DABB), fontSize = 12.sp)
                    }
                    IconButton(onClick = { simulator.show(if (state.screen == Screen.SETTINGS) Screen.CONTROL else Screen.SETTINGS) }, modifier = Modifier.testTag("settings")) {
                        Text("⚙", fontSize = 26.sp, modifier = Modifier.semantics { contentDescription = "Configurações" })
                    }
                }
                when (state.screen) {
                    Screen.CONTROL -> ControlScreen(simulator, preferences, Modifier.weight(1f))
                    Screen.TYPING -> TypingScreen(simulator, Modifier.weight(1f))
                    Screen.SETTINGS -> SettingsScreen(simulator, preferences, onPreferences, { simulator.show(Screen.CONTROL) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ControlScreen(simulator: Simulator, preferences: UiPreferences, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { simulator.show(Screen.TYPING) }, modifier = Modifier.testTag("typing")) { Text("⌨ Digitar") }
                OutlinedButton(onClick = { simulator.dispatch(Command.Key(Shortcut.ENTER)) }) { Text("Enter") }
                OutlinedButton(onClick = { simulator.dispatch(Command.Key(Shortcut.ESC)) }) { Text("Esc") }
                if (landscape) {
                    OutlinedButton(onClick = { simulator.dispatch(Command.MediaKey(Media.VOLUME_DOWN)) }) { Text("Vol −") }
                    OutlinedButton(onClick = { simulator.dispatch(Command.MediaKey(Media.PLAY_PAUSE)) }) { Text("▶ / Ⅱ") }
                    OutlinedButton(onClick = { simulator.dispatch(Command.MediaKey(Media.VOLUME_UP)) }) { Text("Vol +") }
                }
            }
            ControlSurface(simulator, preferences, landscape, Modifier.weight(1f).fillMaxWidth())
            if (!landscape) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { simulator.dispatch(Command.MediaKey(Media.VOLUME_DOWN)) }, modifier = Modifier.weight(1f)) { Text("Vol −") }
                OutlinedButton(onClick = { simulator.dispatch(Command.MediaKey(Media.PLAY_PAUSE)) }, modifier = Modifier.weight(1f)) { Text("▶ / Ⅱ") }
                OutlinedButton(onClick = { simulator.dispatch(Command.MediaKey(Media.VOLUME_UP)) }, modifier = Modifier.weight(1f)) { Text("Vol +") }
            }
            if (!simulator.state.remoteMode) SimulationPreview(simulator.state, compact = landscape)
            else Text(simulator.state.event, fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
internal fun ControlSurface(simulator: Simulator, preferences: UiPreferences, landscape: Boolean, modifier: Modifier = Modifier) {
    if (landscape) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!preferences.leftHanded) MouseButtons(simulator, Modifier.weight(0.36f).fillMaxHeight())
            Touchpad(simulator, preferences.sensitivity, Modifier.weight(0.64f).fillMaxHeight())
            if (preferences.leftHanded) MouseButtons(simulator, Modifier.weight(0.36f).fillMaxHeight())
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Touchpad(simulator, preferences.sensitivity, Modifier.weight(1f).fillMaxWidth())
            MouseButtons(simulator, Modifier.fillMaxWidth().height(80.dp))
        }
    }
}

@Composable
private fun MouseButtons(simulator: Simulator, modifier: Modifier) {
    Row(modifier.testTag("mouse_buttons"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MouseButton.entries.forEach { button ->
            val pressed = if (button == MouseButton.LEFT) simulator.state.leftDown else simulator.state.rightDown
            val label = if (button == MouseButton.LEFT) "Clique esquerdo" else "Clique direito"
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .background(if (pressed) Accent else Color(0xFF3A5A76))
                    .testTag(if (button == MouseButton.LEFT) "left_button" else "right_button")
                    .semantics { contentDescription = label }
                    .pointerInput(simulator, button) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            simulator.dispatch(Command.Button(button, true))
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.id == down.id && it.pressed })
                            } finally { simulator.dispatch(Command.Button(button, false)) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text(label, Modifier.padding(8.dp), fontSize = 14.sp) }
        }
    }
}

@Composable
private fun Touchpad(simulator: Simulator, sensitivity: Float, modifier: Modifier) {
    val currentSensitivity by rememberUpdatedState(sensitivity)
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(Pad).testTag("touchpad")
            .semantics { contentDescription = "Touchpad simulado. Deslize para mover; dois dedos para rolar; toque para clicar." }
            .pointerInput(simulator) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var distance = 0f
                    var multiple = false
                    var cancelled = false
                    do {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed && it.previousPressed }
                        if (event.changes.count { it.pressed } >= 2) multiple = true
                        if (event.changes.any { it.isConsumed }) cancelled = true
                        if (!cancelled) {
                            if (active.size >= 2) {
                                val dy = active.map { it.positionChange().y }.average().toFloat()
                                if (dy != 0f) simulator.dispatch(Command.Scroll((-dy / 16).coerceIn(-120f, 120f)))
                            } else if (!multiple && active.size == 1) {
                                val delta = active.first().positionChange()
                                distance += abs(delta.x) + abs(delta.y)
                                if (distance > viewConfiguration.touchSlop) {
                                    simulator.dispatch(Command.Move((delta.x * currentSensitivity).coerceIn(-2048f, 2048f), (delta.y * currentSensitivity).coerceIn(-2048f, 2048f)))
                                }
                            }
                        }
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed } && !multiple && !cancelled && distance <= viewConfiguration.touchSlop && event.changes.maxOf { it.uptimeMillis } - down.uptimeMillis < 300) simulator.click()
                    } while (event.changes.any { it.pressed })
                }
            }, contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TOUCHPAD", color = Color(0xFF294E68), fontSize = 18.sp)
            Text("Deslize · toque para clicar", color = Color(0xFF45677D), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SimulationPreview(state: SimulationState, compact: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Panel).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.width(if (compact) 70.dp else 100.dp).height(if (compact) 24.dp else 46.dp).background(Color(0xFF185080))) {
                drawCircle(Color.White, 4.dp.toPx(), Offset(state.cursorX * (size.width - 8.dp.toPx()) + 4.dp.toPx(), state.cursorY * (size.height - 8.dp.toPx()) + 4.dp.toPx()))
            }
            Text(state.event, Modifier.weight(1f).testTag("event"), fontSize = 12.sp, maxLines = 2)
        }
        if (!compact && state.sentText.isNotEmpty()) Text(state.sentText, Modifier.padding(top = 4.dp).testTag("sent_text"), fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
internal fun TypingScreen(simulator: Simulator, modifier: Modifier = Modifier) {
    var draft by rememberSaveable { mutableStateOf("") }
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val byteCount = draft.toByteArray(Charsets.UTF_8).size
    val valid = byteCount <= Protocol.MAX_TEXT_BYTES
    LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    val editor: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(
            value = draft, onValueChange = { draft = it }, readOnly = simulator.state.textPending,
            modifier = fieldModifier.focusRequester(requester).testTag("text_entry"),
            placeholder = { Text(if (simulator.state.remoteMode) "Digite para o PC…" else "Digite para o PC simulado…") },
            label = if (!valid) { { Text("Máximo: 4 KiB", fontSize = 10.sp) } } else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = true),
            isError = !valid,
        )
    }
    val shortcuts: @Composable (Modifier) -> Unit = { rowModifier ->
        Row(rowModifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Shortcut.entries.forEach { shortcut ->
                OutlinedButton(onClick = { simulator.dispatch(Command.Key(shortcut)) }, modifier = Modifier.testTag("shortcut_${shortcut.name}"), contentPadding = PaddingValues(horizontal = 10.dp)) { Text(shortcut.label, maxLines = 1) }
            }
        }
    }
    val actions: @Composable (Modifier) -> Unit = { rowModifier ->
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { draft = ""; keyboard?.hide(); simulator.show(Screen.CONTROL) }, enabled = !simulator.state.textPending, modifier = Modifier.weight(1f).testTag("cancel_text"), contentPadding = PaddingValues(6.dp)) { Text("Cancelar") }
            Button(onClick = { if (simulator.sendText(draft)) { draft = ""; keyboard?.hide() } }, enabled = valid && draft.isNotEmpty() && !simulator.state.textPending && (!simulator.state.remoteMode || simulator.state.link == LinkState.READY), modifier = Modifier.weight(1f).testTag("send_text"), contentPadding = PaddingValues(6.dp)) { Text(if (simulator.state.textPending) "Enviando" else "Enviar") }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val sideWidth = minOf(360.dp, maxWidth * 0.66f)
        if (maxHeight < 240.dp && maxWidth > 450.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                editor(Modifier.weight(1f).fillMaxHeight())
                Row(Modifier.width(sideWidth), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    actions(Modifier.weight(1f))
                    shortcuts(Modifier.weight(1f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (simulator.state.deliveryWarning.isNotEmpty()) Text(simulator.state.deliveryWarning, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                Text(if (valid) "Texto composto aqui; Enviar não pressiona Enter." else "Texto excede 4 KiB. Reduza para enviar.", fontSize = 12.sp, color = if (valid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                editor(Modifier.weight(1f).fillMaxWidth())
                shortcuts(Modifier.fillMaxWidth())
                actions(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SettingsScreen(simulator: Simulator, preferences: UiPreferences, onPreferences: (UiPreferences) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    var sensitivity by remember(preferences.sensitivity) { mutableFloatStateOf(preferences.sensitivity) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Configurações", style = MaterialTheme.typography.titleLarge)
        Text("Orientação")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Orientation.entries.forEach { orientation ->
                FilterChip(selected = preferences.orientation == orientation, onClick = { onPreferences(preferences.copy(orientation = orientation)) }, label = { Text(orientation.label) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Modo canhoto", Modifier.weight(1f))
            Switch(checked = preferences.leftHanded, onCheckedChange = { onPreferences(preferences.copy(leftHanded = it)) }, modifier = Modifier.testTag("left_handed"))
        }
        Text("Sensibilidade: ${(sensitivity * 100).toInt()}%")
        Slider(value = sensitivity, onValueChange = { sensitivity = it }, onValueChangeFinished = { onPreferences(preferences.copy(sensitivity = sensitivity)) }, valueRange = 0.4f..1.8f)
        HorizontalDivider()
        ConnectionPanel(simulator)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao controle") }
    }
}
