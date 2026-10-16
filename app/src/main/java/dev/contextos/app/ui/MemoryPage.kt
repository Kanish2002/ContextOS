package dev.contextos.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.contextos.app.memory.MemoryPolicy
import dev.contextos.app.memory.MemoryState
import java.text.DateFormat
import java.util.Date

@Composable
fun MemoryPage(
    state: MemoryState,
    add: (String) -> Unit,
    delete: (String) -> Unit,
    clearAll: () -> Unit,
    export: () -> Unit,
    setChatConsent: (Boolean) -> Unit,
    modelIdle: Boolean
) {
    val color = MaterialTheme.colorScheme
    var draft by rememberSaveable { mutableStateOf("") }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(draft.length.toString() + " / " + MemoryPolicy.MAX_NOTE_LENGTH,
                        color = color.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f))
                    Button(
                        onClick = { add(draft); draft = "" },
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
                            Text(note.text, style = MaterialTheme.typography.bodyMedium)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(note.createdAtMs)),
                                style = MaterialTheme.typography.labelSmall, color = color.onSurfaceVariant)
                        }
                        IconButton(onClick = { delete(note.id) }, enabled = modelIdle && !state.busy && state.error == null) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete memory")
                        }
                    }
                }
            }
        }

        Text("Encryption: Android Keystore AES-GCM. This first Phase 4 release uses local keyword retrieval; a separate embedding model is not yet included.",
            style = MaterialTheme.typography.bodySmall, color = color.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
    }
}
