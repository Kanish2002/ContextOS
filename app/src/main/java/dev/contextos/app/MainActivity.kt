package dev.contextos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.contextos.app.model.*
import java.util.Locale

private val Navy = Color(0xFF090F1D)
private val Layer = Color(0xFF111C2E)
private val Layer2 = Color(0xFF19283E)
private val Aqua = Color(0xFF75E3C5)
private val Muted = Color(0xFFAFBDD1)

class MainActivity : ComponentActivity() {
    private val viewModel: AssistantViewModel by viewModels()
    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importModel)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Aqua, onPrimary = Navy, background = Navy,
                    surface = Layer, onSurface = Color.White,
                    onBackground = Color.White, secondary = Muted
                )
            ) {
                MainScreen(
                    state = state,
                    chooseModel = { modelPicker.launch(arrayOf("*/*")) },
                    load = viewModel::loadModel,
                    setBackend = viewModel::chooseBackend,
                    send = viewModel::send,
                    newChat = viewModel::newChat,
                    clearError = viewModel::clearError
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: AssistantState, chooseModel: () -> Unit, load: () -> Unit,
    setBackend: (DeviceBackend) -> Unit, send: (String) -> Unit,
    newChat: () -> Unit, clearError: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val messagesState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) messagesState.animateScrollToItem(state.messages.lastIndex)
    }
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Navy, Color(0xFF0D182A), Navy))
        ).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = Aqua.copy(alpha = 0.13f)) {
                Icon(Icons.Default.Psychology, null, tint = Aqua,
                    modifier = Modifier.padding(11.dp).size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ContextOS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Private intelligence · Phase 1", style = MaterialTheme.typography.labelMedium, color = Muted)
            }
            IconButton(onClick = newChat, enabled = !state.busy && !state.generating) {
                Icon(Icons.Default.AddComment, contentDescription = "New chat", tint = Aqua)
            }
        }
        Surface(color = Layer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = Aqua, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("ON-DEVICE MODEL", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = Aqua)
                    Spacer(Modifier.weight(1f))
                    Text(if (state.initialized) "READY" else "NOT LOADED", color =
                        if (state.initialized) Aqua else Muted, style = MaterialTheme.typography.labelSmall)
                }
                Text(if (state.modelName == null) "No local model available" else
                    "On-device model · ${formatBytes(state.modelSize)}", fontWeight = FontWeight.Medium)
                Text(state.status, style = MaterialTheme.typography.bodySmall, color = Muted)
                state.importPercent?.let { LinearProgressIndicator(
                    progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = chooseModel, enabled = !state.busy && !state.generating,
                        modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp)); Text("Import")
                    }
                    Button(onClick = load, enabled = !state.busy && !state.generating && state.modelName != null,
                        modifier = Modifier.weight(1f)) {
                        if (state.busy) CircularProgressIndicator(strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp))
                        else Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp)); Text("Load model")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Backend", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Spacer(Modifier.width(12.dp))
                    listOf(DeviceBackend.CPU, DeviceBackend.GPU).forEach { backend ->
                        FilterChip(
                            selected = state.backend == backend,
                            onClick = { setBackend(backend) },
                            enabled = !state.busy && !state.generating,
                            label = { Text(backend.name) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("RAM ${formatBytes(state.deviceRamBytes)}", color = Muted,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        state.error?.let { err ->
            Surface(color = Color(0xFF4A2430), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(err, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = clearError, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
        if (state.messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AutoAwesome, null, tint = Aqua, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(16.dp))
                Text("Your AI, entirely on your phone", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("With an included model, start chatting after automatic setup. " +
                    "No sensors or Google account access in Phase 1.", color = Muted,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(18.dp))
                Text("TRY A PROMPT", color = Aqua, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                listOf("Help me plan a productive day", "Explain how neural networks work",
                    "Suggest a study routine").forEach { sample ->
                    OutlinedButton(onClick = { draft = sample }, modifier = Modifier.fillMaxWidth()) {
                        Text(sample, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = messagesState,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.messages, key = { it.id }) { msg -> ChatBubble(msg) }
            }
        }
        state.benchmark?.let { metric ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                metric.loadMillis?.let { Stat("Load", "${it}ms") }
                metric.firstChunkMillis?.let { Stat("First chunk", "${it}ms") }
                metric.generateMillis?.let { Stat("Response", "${it}ms") }
                if (metric.characters > 0) Stat("Throughput", "${"%.1f".format(Locale.US, metric.charsPerSecond)} char/s")
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp).background(Layer, RoundedCornerShape(22.dp))
            .padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = draft, onValueChange = { draft = it },
                modifier = Modifier.weight(1f), maxLines = 4,
                placeholder = { Text("Message your local AI...") },
                enabled = state.initialized && !state.busy && !state.generating,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ))
            Spacer(Modifier.width(5.dp))
            FilledIconButton(
                onClick = { val msg = draft; draft = ""; send(msg) },
                enabled = draft.isNotBlank() && state.initialized && !state.busy && !state.generating,
                modifier = Modifier.size(48.dp)
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
        Text("Offline inference · No cloud API · Local session only", color = Muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val user = message.role == Role.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Text(if (user) "YOU" else "CONTEXTOS", style = MaterialTheme.typography.labelSmall,
            color = if (user) Muted else Aqua, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
        Surface(color = if (user) Layer2 else Layer,
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(if (user) .9f else .96f)) {
            Text(if (message.content.isEmpty() && message.generating) "Thinking locally..." else message.content,
                modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = Aqua, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "Unknown"
    return if (bytes >= 1024L * 1024 * 1024)
        "%.1f GB".format(Locale.US, bytes.toDouble() / (1024 * 1024 * 1024))
    else "%.0f MB".format(Locale.US, bytes.toDouble() / (1024 * 1024))
}
