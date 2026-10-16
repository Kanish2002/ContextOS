package dev.contextos.app.memory

import org.junit.Assert.*
import org.junit.Test

class MemoryPolicyTest {
    private val notes = listOf(
        MemoryNote("1", "I prefer Kotlin when writing small Android apps", 1),
        MemoryNote("2", "For finance architecture I prefer event driven Kafka services", 2),
        MemoryNote("3", "I like coffee and quiet music", 3),
        MemoryNote("4", "I use Kafka for event processing in backend services", 4)
    )

    @Test fun emptyPromptOrUnrelatedPromptNeverLeaksNotes() {
        assertNull(MemoryPolicy.promptContext("what is your name", notes))
        assertNull(MemoryPolicy.promptContext("", notes))
    }

    @Test fun matchingNotesAreRelevantAndBounded() {
        val retrieved = MemoryPolicy.matching("Explain Kafka event processing", notes)
        assertEquals(listOf("4", "2"), retrieved.map { it.id })
        assertFalse(retrieved.any { it.id == "1" || it.id == "3" })
        assertTrue(MemoryPolicy.promptContext("Explain Kafka event processing", notes)!!.length
            <= MemoryPolicy.MAX_PROMPT_CONTEXT)
    }

    @Test fun addRejectsEmptyAndOversizedInput() {
        try {
            MemoryPolicy.add(notes, "  ", "5", 5)
            fail("Empty memory should fail")
        } catch (_: IllegalArgumentException) { }
        try {
            MemoryPolicy.add(notes, "x".repeat(481), "5", 5)
            fail("Oversize memory should fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals("two words", MemoryPolicy.normalize(" two  \n words  "))
    }

    @Test fun memoryCapAndDuplicateIdsAreEnforced() {
        try {
            MemoryPolicy.add(List(MemoryPolicy.MAX_NOTES) {
                MemoryNote(it.toString(), "stored", it.toLong())
            }, "more", "new", 10)
            fail("Memory cap should fail")
        } catch (_: IllegalArgumentException) { }
        try {
            MemoryPolicy.add(notes, "another memory", "1", 10)
            fail("Duplicate ID should fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun tamilTermsCanMatchWithoutAnEmbeddingModel() {
        val tamil = listOf(MemoryNote("t", "எனக்கு தமிழ் புத்தகங்கள் பிடிக்கும்", 9))
        assertEquals("t", MemoryPolicy.matching("தமிழ் புத்தகங்கள்", tamil).single().id)
    }
}
