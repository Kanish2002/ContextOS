package dev.contextos.app.integrations

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId

class KnowledgeSearchTest {
    private fun ms(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    @Test fun lastTuesdayFiltersByRealLocalCalendarDate() {
        val today = LocalDate.of(2026, 10, 16)
        val lastTue = LocalDate.of(2026, 10, 13)
        val saved = listOf(
            KnowledgeEntry(title = "Architecture idea", body = "Kafka research", source = "note", createdAt = ms(lastTue)),
            KnowledgeEntry(title = "Other idea", body = "A different day", source = "note", createdAt = ms(today))
        )
        assertEquals(listOf("Architecture idea"), KnowledgeSearch.search(
            "What were the ideas I wrote down last Tuesday?", saved, today).map { it.title })
    }

    @Test fun unfinishedProjectsRespectCompletion() {
        val saved = listOf(
            KnowledgeEntry(title = "JVM Lens", body = "Needs tests", source = "mine", category = "PROJECT"),
            KnowledgeEntry(title = "Finished prototype", body = "done", source = "mine", category = "PROJECT", done = true),
            KnowledgeEntry(title = "Shopping", body = "unfinished", source = "mine", category = "NOTE")
        )
        val matches = KnowledgeSearch.search("Which projects haven't I completed?", saved)
        assertEquals(listOf("JVM Lens"), matches.map { it.title })
    }

    @Test fun unfinishedQuestionVariantsAreFilteredWithoutLeakingCompletedProjects() {
        val items = listOf(
            KnowledgeEntry(title = "Pending platform", body = "needs integration", source = "user", category = "PROJECT"),
            KnowledgeEntry(title = "Finished platform", body = "released", source = "user",
                category = "PROJECT", done = true),
            KnowledgeEntry(title = "Unrelated draft", body = "incomplete", source = "user", category = "NOTE")
        )
        listOf("Which projects haven't I completed?",
               "Which projects haven’t I completed?",
               "Which projects haven't I finished?",
               "Show unfinished projects").forEach { question ->
            assertEquals(question, listOf("Pending platform"),
                KnowledgeSearch.search(question, items).map { it.title })
        }
    }

    @Test fun noMatchDoesNotLeakAllDocuments() {
        val saved = listOf(KnowledgeEntry(title = "Banking", body = "ledger", source = "file"))
        assertNull(KnowledgeSearch.retrievedContext("I enjoy mangoes", saved))
    }

    @Test fun promptRetrievalIsBounded() {
        val saved = List(30) { KnowledgeEntry(title = "Kafka lesson", body = "Kafka " + "x".repeat(4000), source = "note") }
        assertTrue(KnowledgeSearch.retrievedContext("Kafka", saved)!!.length <= 2500)
    }
}
