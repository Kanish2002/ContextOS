package dev.contextos.app.memory

import java.util.Locale

/** A user-authored item, never extracted from chats or sensors automatically. */
data class MemoryNote(val id: String, val text: String, val createdAtMs: Long)

/** Pure rules, independently unit tested without a model or Android runtime. */
object MemoryPolicy {
    const val MAX_NOTES = 60
    const val MAX_NOTE_LENGTH = 480
    const val MAX_RETRIEVED_NOTES = 3
    const val MAX_PROMPT_CONTEXT = 850

    private val tokenPattern = Regex("[\\p{L}\\p{N}]{2,}")
    private val stopWords = setOf(
        "a", "an", "and", "are", "can", "could", "do", "for", "from", "have",
        "how", "into", "is", "it", "me", "my", "of", "on", "or", "please", "tell",
        "that", "the", "this", "to", "was", "what", "when", "where", "which",
        "will", "with", "would", "you", "your"
    )

    fun normalize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").also {
            require(it.isNotBlank()) { "Write a memory before saving." }
            require(it.length <= MAX_NOTE_LENGTH) { "Memory exceeds $MAX_NOTE_LENGTH characters." }
        }

    fun add(existing: List<MemoryNote>, text: String, id: String, timestamp: Long): List<MemoryNote> {
        require(existing.size < MAX_NOTES) { "Memory vault is full (limit: $MAX_NOTES)." }
        require(existing.none { it.id == id }) { "Memory ID already exists." }
        return existing + MemoryNote(id, normalize(text), timestamp)
    }

    private fun tokens(s: String): Set<String> = tokenPattern
        .findAll(s.lowercase(Locale.ROOT)).map { it.value }
        .filter { it !in stopWords }.toSet()

    /**
     * Phase 4 retrieval baseline: local lexical RAG, not a semantic embedding model.
     * Only user-authored notes sharing meaningful words with the prompt are returned.
     * Stable ordering ensures repeatable explanations and tests.
     */
    fun matching(question: String, notes: List<MemoryNote>): List<MemoryNote> {
        val request = tokens(question)
        if (request.isEmpty()) return emptyList()
        return notes.mapNotNull { note ->
            val matched = request.intersect(tokens(note.text))
            if (matched.isEmpty()) null
            else note to matched.sumOf { 1.0 + (it.length.coerceAtMost(12) / 12.0) }
        }.sortedWith(
            compareByDescending<Pair<MemoryNote, Double>> { it.second }
                .thenByDescending { it.first.createdAtMs }.thenBy { it.first.id }
        ).take(MAX_RETRIEVED_NOTES).map { it.first }
    }

    fun promptContext(question: String, notes: List<MemoryNote>): String? =
        matching(question, notes)
            .joinToString("\n") { "- " + it.text }
            .take(MAX_PROMPT_CONTEXT)
            .takeIf { it.isNotBlank() }
}
