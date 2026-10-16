package dev.contextos.app.integrations

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Transparent, opt-in adaptive ranking; no profiling, prediction, or background AI. */
object AgentFeedbackPolicy {
    fun rank(suggestions: List<AgentSuggestion>, ratings: Map<String, Int>): List<AgentSuggestion> =
        suggestions.filter { ratings[it.id] != -1 }
            .sortedWith(compareByDescending<AgentSuggestion> {
                it.priority + if (ratings[it.id] == 1) 25 else 0
            }.thenBy { it.title }).take(4)
}

class AgentFeedback(context: Context) {
    private val prefs = context.getSharedPreferences("contextos_agent_feedback", Context.MODE_PRIVATE)
    var revision by mutableIntStateOf(0)
        private set

    fun rating(id: String): Int {
        require(id.length <= 180) { "Invalid suggestion ID" }
        return prefs.getInt("rated_$id", 0).coerceIn(-1, 1)
    }

    fun set(id: String, value: Int) {
        require(id.isNotBlank() && id.length <= 180 && value in -1..1)
        val all = prefs.getStringSet("ids", emptySet()).orEmpty().toSet() + id
        prefs.edit().putInt("rated_$id", value).putStringSet("ids", all).apply()
        revision++
    }

    fun reset() {
        val all = prefs.getStringSet("ids", emptySet()).orEmpty().toSet()
        val editor = prefs.edit().remove("ids")
        all.forEach { editor.remove("rated_$it") }
        editor.apply()
        revision++
    }

    fun personalize(suggestions: List<AgentSuggestion>): List<AgentSuggestion> {
        revision // Compose observes feedback changes.
        return AgentFeedbackPolicy.rank(suggestions, suggestions.associate {
            it.id to rating(it.id)
        })
    }
}

/** Only user-authored or individually saved career goals are inspected. */
object CareerPreparation {
    private val topicHints = listOf(
        "java" to "Review Java collections and concurrency",
        "kafka" to "Revisit Kafka partitions, offsets and consumer recovery",
        "spring" to "Practice Spring Boot transactions and microservice behavior",
        "sql" to "Review SQL joins, indexes and query plans",
        "system design" to "Prepare a high-level design with scalability trade-offs",
        "kubernetes" to "Review container rollout and autoscaling fundamentals",
        "python" to "Practice Python implementation and debugging",
        "react" to "Review React component and state patterns",
        "algorithm" to "Practice one algorithm with complexity analysis"
    )
    fun nextTopics(items: List<KnowledgeEntry>): List<String> {
        val evidence = items.asSequence()
            .filter { !it.done && it.category in setOf("CAREER", "LEARNING", "PROJECT") }
            .joinToString(" ") { (it.title + " " + it.body).lowercase() }
        return topicHints.filter { (key, _) -> key in evidence }
            .map { it.second }.take(4)
    }
}
