package dev.contextos.app.memory

import dev.contextos.app.model.ChatMessage
import dev.contextos.app.model.Role

/** Explicit transcript-to-proposal boundary. Nothing is persisted without review. */
object MemorySummaryPolicy {
    fun transcript(messages: List<ChatMessage>): String =
        messages.filter { it.content.isNotBlank() }
            .takeLast(8).joinToString("\n") {
                val role = if (it.role == Role.USER) "User" else "Assistant"
                role + ": " + it.content.replace("\n", " ").take(260)
            }.take(2500)

    fun cleanProposal(raw: String): String = raw.trim()
        .removePrefix("Memory:")
        .replace(Regex("\\s+"), " ").take(MemoryPolicy.MAX_NOTE_LENGTH)
}
