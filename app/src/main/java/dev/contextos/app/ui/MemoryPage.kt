package dev.contextos.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.contextos.app.memory.MemoryPolicy
import dev.contextos.app.memory.MemoryKind
import dev.contextos.app.memory.MemoryNote
import dev.contextos.app.model.AssistantState
import dev.contextos.app.memory.MemoryState
import java.text.DateFormat
import java.util.Date

@Composable
fun MemoryPage(
    state: MemoryState,
    add: (String, MemoryKind) -> Unit,
    edit: (String, String, MemoryKind) -> Unit,
    delete: (String) -> Unit,
    clearAll: () -> Unit,
    export: () -> Unit,
    setChatConsent: (Boolean) -> Unit,
    modelIdle: Boolean, ai: AssistantState,
    requestSummary: () -> Unit, dismissSummary: () -> Unit,
    importEmbedding: () -> Unit
) {
    val color = MaterialTheme.colorScheme
    var draft by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(MemoryKind.PREFERENCE.name) }
    var summaryDraft by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryNote?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var editKind by remember { mutableStateOf(MemoryKind.PREFERENCE) }
    if (editing != null) AlertDialog(
        onDismissRequest = { editing = null },
        title = { Text("Edit private memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = editDraft,
                    onValueChange = { editDraft = it.take(MemoryPolicy.MAX_NOTE_LENGTH) },
                    label = { Text("Memory") }, minLines = 3)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemoryKind.entries.forEach { option ->
                        FilterChip(selected = editKind == option, onClick = { editKind = option },
                            label = { Text(option.name.lowercase()) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            editing?.let { edit(it.id, editDraft, editKind) }; editing = null
        }, enabled = editDraft.isNotBlank() && !state.busy) { Text("Save changes") } },
        dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
    )
    LaunchedEffect(ai.memorySuggestion) {
        ai.memorySuggestion?.let { summaryDraft = it }
    }
    if (ai.memorySuggestion != null) AlertDialog(
        onDismissRequest = dismissSummary,
        title = { Text("Review proposed memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Generated offline from your recent chat. Edit before saving. Nothing was saved automatically.")
                OutlinedTextField(value = summaryDraft,
                    onValueChange = { summaryDraft = it.take(MemoryPolicy.MAX_NOTE_LENGTH) },
                    label = { Text("Long-term preference or fact") }, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { add(summaryDraft, MemoryKind.PREFERENCE); dismissSummary() },
                enabled = summaryDraft.isNotBlank() && state.notes.size < MemoryPolicy.MAX_NOTES) {
                Text("Save encrypted memory")
            }
        },
        dismissButton = { TextButton(onClick = dismissSummary) { Text("Discard") } }
    )
    var confirmClear by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Delete all private memories?") },
        text = { Text("This overwrites the encrypted vault with an empty one and resets the current chat if memory was enabled. It cannot be undone.") },
        confirmButton = {
            TextButton(onClick = { clearAll(); confirmClear = false }) { Text("Delete all") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
    )
    if (confirmExport) AlertDialog(
        onDismissRequest = { confirmExport = false },
        title = { Text("Export without encryption?") },
        text = { Text("This creates a readable text file outside the encrypted vault. Your chosen file provider may back it up or share it. Only continue if you trust the destination.") },
        confirmButton = {
            TextButton(onClick = { confirmExport = false; export() }) { Text("Choose file") }
        },
        dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("Cancel") } }
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(2.dp))
        Text("Your private memory", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text("Only things you choose to save. Encrypted on your phone, never uploaded.",
            style = MaterialTheme.typography.bodyMedium, color = color.onSurfaceVariant)

        Surface(color = color.surfaceContainerLow, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = color.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Private memory vault", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= MemoryPolicy.MAX_NOTE_LENGTH) draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading && !state.busy && state.error == null,
                    label = { Text("Remember something") },
                    placeholder = { Text("A preference, goal, or fact you want the assistant to remember") },
                    minLines = 3, maxLines = 6,
                    shape = RoundedCornerShape(17.dp)
                )
                Text("Save as", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MemoryKind.entries.forEach { candidate ->
                        FilterChip(selected = kind == candidate.name,
                            onClick = { kind = candidate.name }, label = {
                                Text(candidate.name.lowercase().replaceFirstChar { it.uppercase() })
                            })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(draft.length.toString() + " / " + MemoryPolicy.MAX_NOTE_LENGTH,
                        color = color.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f))
                    Button(
                        onClick = { add(draft, MemoryKind.valueOf(kind)); draft = "" },
                        enabled = draft.isNotBlank() && !state.loading && !state.busy &&
                            state.error == null && state.notes.size < MemoryPolicy.MAX_NOTES
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Save")
                    }
                }
                HorizontalDivider(color = color.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use matching memories in local chat", fontWeight = FontWeight.Medium)
                        Text("Off by default. Only matching notes are attached when you send a local message. Turning this off resets the current chat.",
                            style = MaterialTheme.typography.bodySmall, color = color.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = state.includeInChat, onCheckedChange = setChatConsent,
                        enabled = !state.loading && state.error == null && modelIdle
                    )
                }
            }
        }

        if (state.loading || state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = color.error)
        }
        Text(state.status, style = MaterialTheme.typography.labelSmall, color = color.onSurfaceVariant)
        Surface(color = color.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI memory and semantic search", fontWeight = FontWeight.SemiBold)
                Text(state.embeddingStatus, color = color.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = importEmbedding, enabled = !state.busy) {
                    Text(if (state.embeddingReady) "Replace embedding model" else "Import Text Embedder .tflite")
                }
                Text("Embedding model is optional; otherwise retrieval falls back to local keywords.",
                    color = color.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Button(onClick = requestSummary,
                    enabled = modelIdle && ai.initialized && ai.messages.isNotEmpty() && !state.busy) {
                    Text("Suggest memory from recent chat")
                }
                Text("On-device LLM creates a draft; you must approve before it is saved.",
                    color = color.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Saved memories (" + state.notes.size + ")", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(
                onClick = { confirmExport = true },
                enabled = state.notes.isNotEmpty() && !state.loading && !state.busy && state.error == null
            ) { Icon(Icons.Default.FileDownload, contentDescription = "Export memories") }
            IconButton(onClick = { confirmClear = true },
                enabled = modelIdle && !state.loading && !state.busy && (state.notes.isNotEmpty() || state.error != null)
            ) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete all memories") }
        }

        if (state.notes.isEmpty()) {
            Surface(color = color.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
                Text("Nothing saved yet. Add a preference or fact above. Conversations are never memorized automatically.",
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    color = color.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            state.notes.asReversed().forEach { note ->
                Surface(color = color.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 5.dp),
                        verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(note.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall, color = color.primary)
                            Text(note.text, style = MaterialTheme.typography.bodyMedium)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(note.createdAtMs)),
                                style = MaterialTheme.typography.labelSmall, color = color.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            editing = note; editDraft = note.text; editKind = note.kind
                        }, enabled = modelIdle && !state.busy && state.error == null) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit memory")
                        }
                        IconButton(onClick = { delete(note.id) }, enabled = modelIdle && !state.busy && state.error == null) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete memory")
                        }
                    }
                }
            }
        }

        Text("Encryption: Android Keystore AES-GCM. Semantic embeddings are available after importing a supported model; no network is required.",
            style = MaterialTheme.typography.bodySmall, color = color.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
    }
}
