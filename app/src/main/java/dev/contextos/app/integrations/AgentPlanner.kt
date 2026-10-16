package dev.contextos.app.integrations

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class AgentSuggestion(val title: String, val reason: String, val priority: Int,
    val id: String = title)

object AgentPlanner {
    /** Deterministic guidance with evidence; no health/emotional/protected trait inference. */
    fun propose(items: List<KnowledgeEntry>, now: LocalDate = LocalDate.now()): List<AgentSuggestion> {
        val suggestions = mutableListOf<AgentSuggestion>()
        val unfinished = items.filter { !it.done && it.category in setOf("PROJECT","LEARNING","CAREER") }
        unfinished.filter { it.dueAt != null }.forEach { task ->
            val due = Instant.ofEpochMilli(task.dueAt!!).atZone(ZoneId.systemDefault()).toLocalDate()
            val remaining = ChronoUnit.DAYS.between(now, due)
            if (remaining in -7L..3L) {
                suggestions += AgentSuggestion(
                    "Review " + task.title.take(65),
                    "User-saved " + task.category.lowercase() + " deadline: " + due +
                        " (" + (if (remaining < 0) "overdue" else remaining.toString() + " day(s) away") + ").",
                    if (remaining < 0) 100 else 95 - remaining.toInt(),
                    id = "deadline:" + task.id)
            }
        }
        if (unfinished.isNotEmpty()) {
            suggestions += AgentSuggestion("Continue a pending goal",
                unfinished.size.toString() + " user-authored career/project/learning entries remain unfinished.", 60,
                id = "pending_goals")
        }
        val minutes = items.filter { it.category == "COMMUTE" }.mapNotNull { it.durationMinutes }
            .filter { it in 1..240 }.sorted()
        if (minutes.size >= 3) {
            suggestions += AgentSuggestion("Plan a commute buffer",
                "Typical duration from " + minutes.size + " imported trip records: " +
                    minutes[minutes.size / 2] + " minutes. This is historical, NOT current traffic.", 45,
                id = "historical_commute")
        }
        if (suggestions.isEmpty()) {
            suggestions += AgentSuggestion("Build a private plan",
                "Save a goal or due date to receive grounded, optional suggestions.", 10,
                id = "create_plan")
        }
        return suggestions.sortedWith(compareByDescending<AgentSuggestion> { it.priority }
            .thenBy { it.title }).take(10)
    }
}
