package dev.contextos.app.integrations

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId

class AgentPlannerTest {
    @Test fun deadlinesAreRankedWithoutSecretContext() {
        val now = LocalDate.of(2026, 10, 16)
        val tomorrow = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val tasks = listOf(
            KnowledgeEntry(title = "Interview", body = "Practice Java", source = "user",
                category = "CAREER", dueAt = tomorrow),
            KnowledgeEntry(title = "Finished app", body = "Already done", source = "user",
                category = "PROJECT", done = true, dueAt = tomorrow)
        )
        val suggestions = AgentPlanner.propose(tasks, now)
        assertTrue(suggestions.first().title.contains("Interview"))
        assertFalse(suggestions.any { it.title.contains("Finished app") })
    }

    @Test fun medianCommuteUsesOnlyImportedHistoricalRecords() {
        val journeys = listOf(22,40,30).map { duration ->
            KnowledgeEntry(title = "Trip", body = "Historic", source = "user", category = "COMMUTE",
                durationMinutes = duration)
        }
        val suggestions = AgentPlanner.propose(journeys)
        assertTrue(suggestions.any { it.reason.contains("30 minutes") })
        assertTrue(suggestions.any { it.reason.contains("NOT current traffic") })
    }
}
