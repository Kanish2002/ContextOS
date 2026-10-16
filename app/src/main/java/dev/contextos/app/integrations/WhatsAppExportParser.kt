package dev.contextos.app.integrations

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Parses ONLY a user-exported WhatsApp text file, never another app's private storage.
 * Handles the common "[DD/MM/YY, HH:mm] Name:" and "DD/MM/YY, HH:mm - Name:" formats.
 * Locale assumption is clearly visible: day/month/year for ambiguous dates.
 * Conversation groups contain one day of messages, capped to protect the archive.
 */
object WhatsAppExportParser {
    private val header = Regex(
        """^\[?(\d{1,2}/\d{1,2}/\d{2,4}),\s*(\d{1,2}:\d{2})(?::\d{2})?\s*([AaPp][Mm])?\]?\s*(?:-\s*)?([^:]{1,80}):\s*(.*)$"""
    )
    private data class Parsed(val date: LocalDate, val speaker: String, val message: String)

    fun parse(text: String): List<KnowledgeEntry> {
        val day = linkedMapOf<LocalDate, StringBuilder>()
        var last: Parsed? = null
        var recognized = 0
        for (raw in text.lineSequence().take(80_000)) {
            val line = raw.trimStart('\u200e', '\ufeff')
            val found = header.matchEntire(line)
            if (found != null) {
                val date = parseDate(found.groupValues[1]) ?: continue
                val speaker = found.groupValues[4].trim()
                val message = found.groupValues[5].trim()
                if (speaker.isEmpty() || message.isEmpty()) continue
                val previous = day.getOrPut(date) { StringBuilder() }
                if (previous.length >= KnowledgeSearch.MAX_TEXT - 350) continue
                val time = found.groupValues[2] + " " + found.groupValues[3].uppercase(Locale.ROOT)
                previous.append(time.trim()).append(" · ")
                    .append(speaker.take(80)).append(": ")
                    .append(message.take(1200)).append('\n')
                last = Parsed(date, speaker, message)
                recognized++
            } else if (last != null && line.isNotBlank()) {
                val builder = day[last.date] ?: continue
                if (builder.length < KnowledgeSearch.MAX_TEXT - 100) {
                    builder.append("  ").append(line.take(500)).append('\n')
                }
            }
        }
        if (recognized < 2) return emptyList()
        return day.entries.takeLast(40).map { (date, builder) ->
            KnowledgeEntry(
                title = "Imported chat · $date",
                body = builder.toString().take(KnowledgeSearch.MAX_TEXT),
                source = "User-selected WhatsApp chat export (DD/MM/YYYY)",
                createdAt = date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault())
                    .toInstant().toEpochMilli(),
                category = "CONVERSATION"
            )
        }
    }

    private fun parseDate(raw: String): LocalDate? = runCatching {
        val parts = raw.split('/')
        val a = parts[0].toInt()
        val b = parts[1].toInt()
        val year = parts[2].toInt().let { if (it < 100) 2000 + it else it }
        // Assume DD/MM/YYYY except clear US-only date (e.g. 11/24/2026).
        val day = if (b > 12 && a <= 12) b else a
        val month = if (b > 12 && a <= 12) a else b
        LocalDate.of(year, month, day)
    }.getOrNull()
}
