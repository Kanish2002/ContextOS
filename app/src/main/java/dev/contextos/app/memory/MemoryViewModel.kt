package dev.contextos.app.memory

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class MemoryState(
    val notes: List<MemoryNote> = emptyList(),
    val includeInChat: Boolean = false,
    val embeddingReady: Boolean = false,
    val embeddingStatus: String = "Optional semantic embedding model not imported.",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val status: String = "Opening encrypted memory vault...",
    val error: String? = null
)

/** User controls all persistence and model-context use. Never auto-save conversations. */
class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val vault = EncryptedMemoryVault(application)
    private val semantic = LocalSemanticRetriever(application)
    private val mutex = Mutex()
    private val prefs = application.getSharedPreferences("contextos_memory_choices", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        MemoryState(includeInChat = prefs.getBoolean("include_saved_memories_in_chat", false),
            embeddingReady = semantic.available(), embeddingStatus = if (semantic.available())
                "On-device semantic embeddings available" else "Import a compatible .tflite Text Embedder model")
    )
    val state: StateFlow<MemoryState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val notes = vault.read()
                    _state.update { it.copy(notes = notes, loading = false, error = null,
                        status = "Encrypted and stored only on this device.") }
                } catch (_: Exception) {
                    // Never treat decrypt failures as an empty vault or overwrite unreadable content.
                    _state.update { it.copy(loading = false,
                        error = "Cannot unlock the encrypted memory vault. Nothing has been erased.",
                        status = "Vault could not be opened.") }
                }
            }
        }
    }

    fun updateChatConsent(value: Boolean) {
        prefs.edit().putBoolean("include_saved_memories_in_chat", value).apply()
        _state.update { it.copy(includeInChat = value) }
    }

    /**
     * Synchronous read from already loaded, app-private in-memory state.
     * Called ONLY as part of an explicitly sent chat prompt.
     */
    fun recallForPrompt(prompt: String): String? {
        val snapshot = _state.value
        if (!snapshot.includeInChat || snapshot.loading || snapshot.error != null) return null
        return MemoryPolicy.promptContext(prompt, snapshot.notes)
    }

    suspend fun recallForPromptAsync(prompt: String): String? {
        val snapshot = _state.value
        if (!snapshot.includeInChat || snapshot.loading || snapshot.error != null) return null
        val match = semantic.retrieve(prompt, snapshot.notes)
        return match?.takeIf { _state.value.includeInChat && _state.value.error == null }
    }

    fun importEmbeddingModel(uri: Uri) {
        if (_state.value.loading || _state.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busy = true, embeddingStatus = "Validating imported Text Embedder model...") }
            try {
                semantic.importModel(uri)
                _state.update { it.copy(embeddingReady = true,
                    embeddingStatus = "Semantic retrieval enabled. Everything runs on device.",
                    status = "Text Embedder model imported successfully.") }
            } catch (error: Exception) {
                _state.update { it.copy(embeddingReady = semantic.available(),
                    embeddingStatus = "Import failed: " + (error.message ?: "incompatible model").take(100)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun add(raw: String, kind: MemoryKind = MemoryKind.NOTE) {
        if (_state.value.loading || _state.value.busy || _state.value.error != null) return
        edit("Memory saved to your encrypted vault.") { old ->
            MemoryPolicy.add(old, raw, UUID.randomUUID().toString(), System.currentTimeMillis(), kind)
        }
    }

    fun updateMemory(id: String, text: String, kind: MemoryKind) {
        if (_state.value.loading || _state.value.busy || _state.value.error != null) return
        edit("Memory updated in encrypted vault.") { old ->
            require(old.any { it.id == id }) { "Memory no longer exists." }
            old.map { item -> if (item.id == id)
                item.copy(text = MemoryPolicy.normalize(text), kind = kind) else item }
        }
    }

    fun remove(id: String) {
        if (_state.value.loading || _state.value.busy || _state.value.error != null) return
        edit("Memory deleted from your encrypted vault.") { old -> old.filterNot { it.id == id } }
    }

    fun clearAll() {
        if (_state.value.loading || _state.value.busy) return
        // Explicit recovery/deletion can overwrite an unreadable vault only after UI confirmation.
        edit("All personal memories erased from the vault.", allowRecovery = true) { emptyList() }
    }

    private fun edit(
        successMessage: String, allowRecovery: Boolean = false,
        change: (List<MemoryNote>) -> List<MemoryNote>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                _state.update { it.copy(busy = true) }
                try {
                    val old = if (allowRecovery) emptyList() else vault.read()
                    val updated = change(old)
                    vault.write(updated)
                    semantic.clearIndex()
                    _state.update { it.copy(notes = updated, busy = false,
                        error = null, status = successMessage) }
                } catch (failure: Exception) {
                    _state.update { it.copy(busy = false,
                        error = failure.message?.take(160) ?: "Could not modify encrypted vault.") }
                }
            }
        }
    }

    fun exportPlaintext(uri: Uri) {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.busy || snapshot.error != null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // SAF destination is user-chosen: may be shared/synced by external document providers.
                val content = buildString {
                    appendLine("ContextOS personal memory export (UNENCRYPTED)")
                    appendLine("Exported by your explicit request. Keep this file private.")
                    appendLine()
                    snapshot.notes.forEachIndexed { index, note ->
                        appendLine((index + 1).toString() + ". " + note.text)
                    }
                }
                val output = app.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Could not open the selected destination.")
                output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                _state.update { it.copy(status = "Unencrypted memory export written to your chosen file.") }
            } catch (_: Exception) {
                _state.update { it.copy(error = "Could not write the selected export file.") }
            }
        }
    }
}
