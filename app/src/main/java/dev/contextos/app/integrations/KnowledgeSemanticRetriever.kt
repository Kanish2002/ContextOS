package dev.contextos.app.integrations

import android.content.Context
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device semantic search for explicitly imported, encrypted knowledge.
 * Reuses the optional MediaPipe .tflite model installed through the Memory tab.
 * No network, vector persistence, or automatic background inference.
 */
class KnowledgeSemanticRetriever(private val context: Context) {
    private val model = File(context.filesDir, "contextos_text_embedder.tflite")
    private val embeddings = HashMap<String, Pair<String, Embedding>>()

    fun available(): Boolean = model.isFile && model.length() > 100_000L

    fun clear() { embeddings.clear() }

    suspend fun matching(query: String, items: List<KnowledgeEntry>): List<KnowledgeEntry> =
        withContext(Dispatchers.Default) {
            if (query.isBlank() || items.isEmpty()) return@withContext emptyList()
            val lexical = KnowledgeSearch.search(query, items)
            if (!available() || KnowledgeSearch.structuredQuery(query)) return@withContext lexical
            try {
                TextEmbedder.createFromFile(context, model).use { embedder ->
                    val question = embedder.embed(query.take(700)).embeddingResult()
                        .embeddings().first()
                    val overlap = lexical.map { it.id }.toSet()
                    val scored = items.mapNotNull { entry ->
                        val source = (entry.title + "\n" + entry.body.take(1900))
                        val vec = embeddings[entry.id]
                            ?.takeIf { it.first == source }?.second
                            ?: embedder.embed(source).embeddingResult().embeddings().first()
                                .also { embeddings[entry.id] = source to it }
                        val similarity = TextEmbedder.cosineSimilarity(question, vec)
                        if (!similarity.isFinite()) null
                        else entry to (similarity + if (entry.id in overlap) 0.07 else 0.0)
                    }
                    // Conservative threshold helps prevent unrelated private documents
                    // from being injected into a chat prompt.
                    val relevant = scored.filter { it.second >= 0.58 }
                        .sortedWith(compareByDescending<Pair<KnowledgeEntry, Double>> { it.second }
                            .thenByDescending { it.first.createdAt })
                        .take(KnowledgeSearch.MAX_RESULTS).map { it.first }
                    if (relevant.isEmpty()) lexical else relevant
                }
            } catch (_: Exception) {
                // Incompatible/replaced model; never network or return all user data.
                lexical
            }
        }
}
