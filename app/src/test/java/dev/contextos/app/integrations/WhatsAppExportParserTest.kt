package dev.contextos.app.integrations

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WhatsAppExportParserTest {
    @Test fun groupsMessagesByDateAndPreservesSpeakers() {
        val input = """
            [13/10/2026, 09:15:00] A: Need to finish JVM Lens
            [13/10/2026, 09:16:00] B: Great project
            14/10/2026, 10:31 - A: Shipped phase 1
        """.trimIndent()
        val entries = WhatsAppExportParser.parse(input)
        assertEquals(2, entries.size)
        assertEquals("CONVERSATION", entries[0].category)
        assertTrue(entries[0].body.contains("A: Need to finish"))
        assertTrue(entries[0].body.contains("B: Great project"))
        val day = Instant.ofEpochMilli(entries[0].createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals("2026-10-13", day.toString())
    }

    @Test fun doesNotInferPrivateChatsFromOrdinaryDocument() {
        assertTrue(WhatsAppExportParser.parse("A project note\nNot a WhatsApp export").isEmpty())
    }

    @Test fun rejectsInvalidDatesAndLimitsLength() {
        val text = "[99/99/2026, 12:00] A: Bad\n" +
            "[15/10/2026, 09:00] A: " + "x".repeat(20000) +
            "\n[15/10/2026, 10:00] B: Hi"
        val entries = WhatsAppExportParser.parse(text)
        assertEquals(1, entries.size)
        assertTrue(entries[0].body.length <= KnowledgeSearch.MAX_TEXT)
    }
}
