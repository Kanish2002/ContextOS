package dev.contextos.app.memory

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.components.containers.Embedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Optional real on-device TextEmbedder (trained .tflite model + tokenizer metadata).
 * No hidden download; user chooses the model file via Android's document picker.
 * Embeddings are cached in RAM only. Encrypted vault remains source of truth.
 */
class LocalSemanticRetriever(private val app: Context) {
    private val mutex = Mutex()
    private val destination = File(app.filesDir, "contextos_text_embedder.tflite")
    private val cache = HashMap<String, Pair<String, Embedding>>()

    fun available(): Boolean = destination.isFile && destination.length() > 0L

    suspend fun importModel(uri: Uri) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val stage = File(app.filesDir, "contextos_embedding_import.tmp")
            try {
                var size = 0L
                app.contentResolver.openInputStream(uri)?.use { input ->
                    stage.outputStream().buffered().use { output ->
                        val buffer = ByteArray(65_536)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            size += read
                            require(size <= 160L * 1024 * 1024) { "Embedding model exceeds 160 MB." }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: error("Cannot open selected model.")
                require(size > 100_000L) { "This is not a valid embedding model." }
                // Validate model + tokenizer before replacing the old one.
                TextEmbedder.createFromFile(app, stage).use { it.embed("hello") }
                val backup = File(app.filesDir, "contextos_text_embedder.previous")
                backup.delete()
                if (destination.exists()) require(destination.renameTo(backup)) { "Cannot back up old model." }
                if (!stage.renameTo(destination)) {
                    backup.renameTo(destination)
                    error("Cannot install embedding model.")
                }
                backup.delete()
                cache.clear()
            } finally {
                stage.delete()
            }
        }
    }

    suspend fun retrieve(prompt: String, notes: List<MemoryNote>): String? = mutex.withLock {
        if (notes.isEmpty()) return@withLock null
        if (!available()) return@withLock MemoryPolicy.promptContext(prompt, notes)
        withContext(Dispatchers.Default) {
            // The model is only opened while the user explicitly sends a message.
            // If a model is incompatible, retrieval falls back to lexical, never networking.
            try {
                val lexical = MemoryPolicy.matching(prompt, notes).map { it.id }.toSet()
                TextEmbedder.createFromFile(app, destination).use { engine ->
                    val query = engine.embed(prompt).embeddingResult().embeddings().first()
                    val ranked = notes.mapNotNull { note ->
                        val vec = cache[note.id]?.takeIf { it.first == note.text }?.second
                            ?: engine.embed(note.text).embeddingResult().embeddings().first().also {
                                cache[note.id] = note.text to it
                            }
                        val score = TextEmbedder.cosineSimilarity(query, vec)
                        if (!score.isFinite()) null
                        else note to (score + if (note.id in lexical) 0.10 else 0.0)
                    }.filter { (_, value) -> value >= 0.55 }
                        .sortedWith(compareByDescending<Pair<MemoryNote, Double>> { it.second }
                            .thenByDescending { it.first.createdAtMs })
                        .take(MemoryPolicy.MAX_RETRIEVED_NOTES).map { it.first }
                    if (ranked.isEmpty()) MemoryPolicy.promptContext(prompt, notes)
                    else ranked.joinToString("\n") { "- " + it.text }
                        .take(MemoryPolicy.MAX_PROMPT_CONTEXT)
                }
            } catch (_: Exception) {
                MemoryPolicy.promptContext(prompt, notes)
            }
        }
    }

    suspend fun clearIndex() = mutex.withLock { cache.clear() }
}
