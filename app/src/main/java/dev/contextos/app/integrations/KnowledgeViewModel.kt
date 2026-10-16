package dev.contextos.app.integrations

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class KnowledgeState(
    val entries: List<KnowledgeEntry> = emptyList(),
    val results: List<KnowledgeEntry> = emptyList(),
    val busy: Boolean = true,
    val error: String? = null,
    val message: String = "Unlocking offline knowledge archive",
    val query: String = "",
    val includeInChat: Boolean = false,
    val agentEnabled: Boolean = false,
    val semanticAvailable: Boolean = false,
    val searchingSemantic: Boolean = false
)

class KnowledgeViewModel(app: Application) : AndroidViewModel(app) {
    private val vault = KnowledgeVault(app)
    private val parser = ImportPipeline(app)
    private val semantic = KnowledgeSemanticRetriever(app)
    private val gate = Mutex()
    private val prefs = app.getSharedPreferences("contextos_knowledge_choices", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(KnowledgeState(
        includeInChat = prefs.getBoolean("include_chat", false),
        agentEnabled = prefs.getBoolean("agent", false),
        semanticAvailable = semantic.available()))
    val state = _state.asStateFlow()

    private fun markPrivacyRevision() {
        prefs.edit().putString("privacy_revision", UUID.randomUUID().toString()).apply()
    }
    init {
        viewModelScope.launch(Dispatchers.IO) {
            gate.withLock {
                try { _state.update { it.copy(entries = vault.read(), busy = false,
                    message = "Encrypted, searchable and only on your phone.") } }
                catch (_: Exception) { _state.update { it.copy(busy = false,
                    error = "Cannot decrypt the knowledge archive. It has NOT been overwritten.") } }
            }
        }
    }

    fun updateSearch(value: String) {
        val snapshot = _state.value
        _state.update { it.copy(query = value, results = KnowledgeSearch.search(value, snapshot.entries)) }
    }
    fun semanticSearch() {
        val requested = _state.value.query.trim()
        if (requested.isBlank() || _state.value.searchingSemantic) return
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(searchingSemantic = true,
                message = if (semantic.available()) "Searching imported documents on device..."
                else "No embedding model installed; using private keyword search.") }
            try {
                val matches = semantic.matching(requested, _state.value.entries)
                _state.update { current -> if (current.query == requested)
                    current.copy(results = matches,
                        message = if (semantic.available()) "Semantic retrieval completed on device."
                                  else "Local keyword results.") else current }
            } catch (error: Exception) {
                _state.update { it.copy(message = "Search failed: " +
                    (error.message ?: "Unknown error").take(150)) }
            } finally {
                _state.update { it.copy(searchingSemantic = false) }
            }
        }
    }

    fun updateChatConsent(value: Boolean) {
        prefs.edit().putBoolean("include_chat", value).apply()
        markPrivacyRevision()
        _state.update { it.copy(includeInChat = value) }
    }
    fun updateAgentConsent(value: Boolean) {
        prefs.edit().putBoolean("agent", value).apply()
        _state.update { it.copy(agentEnabled = value) }
    }
    fun importDocument(uri: Uri) {
        if (_state.value.busy || _state.value.error != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busy = true, message = "Reading explicitly selected document...") }
            try {
                val items = parser.read(uri)
                append(items, "Imported " + items.size + " entries from selected document.")
            } catch(e: Exception) {
                _state.update { it.copy(error = e.message?.take(160) ?: "Import failed.") }
            } finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun importPhoto(uri: Uri) {
        if (_state.value.busy || _state.value.error != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busy = true, message = "Recognizing text locally...") }
            try {
                append(listOf(parser.readSelectedPhoto(uri)), "Saved selected photo text.")
            } catch(e: Exception) {
                _state.update { it.copy(error = e.message?.take(160) ?: "Image import failed.") }
            } finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun add(title: String, body: String, category: String, dueDate: String?) {
        val due = runCatching {
            dueDate?.takeIf { it.isNotBlank() }?.let {
                LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }.getOrElse {
            _state.update { it.copy(message = "Date must be YYYY-MM-DD.") }
            return
        }
        if (title.isBlank() || body.isBlank()) {
            _state.update { it.copy(message = "Enter a title and description first.") };return
        }
        val record = KnowledgeEntry(title = title.trim().take(160),
            body = body.trim().take(KnowledgeSearch.MAX_TEXT),
            source = "Written by user", category = category, dueAt = due)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { append(listOf(record), "Saved with encryption.") }
                .onFailure { error -> _state.update {
                    it.copy(message = "Could not save: " + (error.message ?: "Unknown error").take(160))
                } }
        }
    }
    fun savePreview(entry: KnowledgeEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { append(listOf(entry), "Saved selected item.") }
                .onFailure { error -> _state.update {
                    it.copy(message = "Could not save selection: " +
                        (error.message ?: "Unknown error").take(160))
                } }
        }
    }
    fun saveCommuteReminder(destination: String, departureMs: Long) {
        if (_state.value.busy || !_state.value.agentEnabled ||
            !prefs.getBoolean("agent_reminders", false)) {
            _state.update { it.copy(message = "Enable agent reminders before scheduling.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommuteReminderScheduler.waitMillis(System.currentTimeMillis(), departureMs)
                val entry = KnowledgeEntry(
                    title = ("Planned commute to " + destination).take(150),
                    body = "User-approved traffic estimate; recheck the route before departure.",
                    source = "User-approved commute plan", category = "COMMUTE_PLAN",
                    dueAt = departureMs, location = destination.take(200)
                )
                append(listOf(entry), "Leave-by plan saved with encryption.")
                // Schedule only after the encrypted archive write succeeds.
                CommuteReminderScheduler.schedule(getApplication(), entry.id, departureMs)
                _state.update { it.copy(message =
                    "Inexact leave-by reminder requested. Android may delay delivery.") }
            } catch (failure: Exception) {
                _state.update { it.copy(message =
                    "Could not schedule commute reminder: " +
                        (failure.message ?: "Unknown error").take(140)) }
            }
        }
    }

    fun toggleDone(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gate.withLock {
                try {
                    val updated = vault.read().map { if (it.id == id) it.copy(done = !it.done) else it }
                    vault.write(updated)
                    semantic.clear()
                    markPrivacyRevision()
                    _state.update { it.copy(entries = updated,
                        results = KnowledgeSearch.search(it.query, updated), message = "Progress updated.") }
                } catch(e: Exception) {
                    _state.update { it.copy(message = e.message ?: "Cannot update progress.") }
                }
            }
        }
    }
    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gate.withLock {
                try {
                    val updated = vault.read().filterNot { it.id == id }
                    vault.write(updated)
                    semantic.clear()
                    markPrivacyRevision()
                    _state.update { it.copy(entries = updated,
                        results = KnowledgeSearch.search(it.query, updated), message = "Deleted from archive.") }
                } catch(e: Exception) {
                    _state.update { it.copy(message = e.message ?: "Cannot delete.") }
                }
            }
        }
    }
    fun deleteGoogleImports() {
        viewModelScope.launch(Dispatchers.IO) {
            gate.withLock {
                try {
                    val updated = vault.read().filterNot { entry ->
                        entry.source.startsWith("Selected Gmail") ||
                            entry.source.startsWith("Selected Google") ||
                            entry.source.startsWith("Explicitly imported Gmail") ||
                            entry.source.startsWith("Explicitly imported Drive")
                    }
                    vault.write(updated)
                    semantic.clear()
                    markPrivacyRevision()
                    _state.update { it.copy(entries = updated,
                        results = KnowledgeSearch.search(it.query, updated),
                        message = "Locally saved Google-imported content erased.") }
                } catch (failure: Exception) {
                    _state.update { it.copy(message = "Could not erase Google imports: " +
                        (failure.message ?: "Unknown error").take(100)) }
                }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            gate.withLock {
                try { vault.write(emptyList()); semantic.clear(); markPrivacyRevision(); _state.update {
                    it.copy(entries = emptyList(), results = emptyList(),
                        message = "Encrypted knowledge archive cleared.")
                } } catch(e: Exception) { _state.update {
                    it.copy(message = "Unable to clear knowledge archive: " + e.message)
                } }
            }
        }
    }
    fun clearMessage() { _state.update { it.copy(error = null) } }
    private suspend fun append(notes: List<KnowledgeEntry>, message: String) {
        gate.withLock {
            val prev = vault.read()
            val remaining = KnowledgeSearch.MAX_ITEMS - prev.size
            require(notes.size <= remaining) { "Archive is full. Remove older entries first." }
            val updated = prev + notes
            vault.write(updated)
            semantic.clear()
            markPrivacyRevision()
            _state.update { it.copy(entries = updated, results = KnowledgeSearch.search(it.query, updated),
                message = message, error = null) }
        }
    }

    companion object {
        suspend fun recall(context: Context, prompt: String): String? =
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences("contextos_knowledge_choices", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("include_chat", false)) return@withContext null
                val entries = runCatching { KnowledgeVault(context).read() }.getOrDefault(emptyList())
                val selected = KnowledgeSemanticRetriever(context).matching(prompt, entries)
                val result = KnowledgeSearch.formatContext(selected)
                if (prefs.getBoolean("include_chat", false)) result else null
            }
    }
}
