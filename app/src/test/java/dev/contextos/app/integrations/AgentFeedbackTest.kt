package dev.contextos.app.integrations

import org.junit.Assert.*
import org.junit.Test

class AgentFeedbackTest {
    @Test fun ratingsSuppressAndBoostWithoutChangingOriginalRules() {
        val suggestions = listOf(
            AgentSuggestion("A","rule",95,"a"),
            AgentSuggestion("B","rule",75,"b"),
            AgentSuggestion("C","rule",85,"c")
        )
        val ranked = AgentFeedbackPolicy.rank(suggestions, mapOf("a" to -1, "b" to 1))
        assertEquals(listOf("b", "c"), ranked.map { it.id })
        assertEquals(95, suggestions.first().priority)
    }

    @Test fun preparationUsesOnlyIncompleteSelectedGoals() {
        val entries = listOf(
            KnowledgeEntry(title="Backend interview", body="Java, Kafka, SQL",
                source="user", category="CAREER"),
            KnowledgeEntry(title="Completed React", body="React", source="user",
                category="LEARNING", done=true)
        )
        val topics = CareerPreparation.nextTopics(entries).joinToString(" ")
        assertTrue(topics.contains("Java"))
        assertTrue(topics.contains("Kafka"))
        assertTrue(topics.contains("SQL"))
        assertFalse(topics.contains("React"))
    }
}
