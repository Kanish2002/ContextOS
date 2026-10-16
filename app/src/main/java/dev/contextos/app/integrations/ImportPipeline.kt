package dev.contextos.app.integrations

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.Locale

object TimelineParser {
    /** Handles both older Timeline timelineObjects and modern semanticSegments exports. */
    fun parse(raw: String): List<KnowledgeEntry> {
        val result = mutableListOf<KnowledgeEntry>()
        val root = JSONObject(raw)
        val old = root.optJSONArray("timelineObjects") ?: JSONArray()
        val modern = root.optJSONArray("semanticSegments") ?: JSONArray()
        fun append(title: String, detail: String, start: String?, end: String?, source: String) {
            val started = runCatching { Instant.parse(start).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            val duration = runCatching {
                Duration.between(Instant.parse(start), Instant.parse(end)).toMinutes().toInt()
            }.getOrNull()?.takeIf { it in 1..1440 }
            result.add(KnowledgeEntry(title = title.take(150), body = detail.take(4000),
                source = source, category = "COMMUTE", createdAt = started, durationMinutes = duration))
        }
        for (i in 0 until minOf(old.length(), 4000)) {
            val node = old.optJSONObject(i) ?: continue
            val activity = node.optJSONObject("activitySegment")
            val place = node.optJSONObject("placeVisit")
            if (activity != null) {
                val start = activity.optJSONObject("duration")?.optString("startTimestamp")
                val end = activity.optJSONObject("duration")?.optString("endTimestamp")
                append("Past travel: " + activity.optString("activityType", "trip"),
                    "Distance meters: " + activity.optString("distance", "?") +
                        ". Start: " + (start ?: "") + ". End: " + (end ?: ""),
                    start, end, "Timeline export")
            } else if (place != null) {
                val location = place.optJSONObject("location")
                append("Visit: " + (location?.optString("name") ?: "Place"),
                    "From manually imported Timeline export.", place.optJSONObject("duration")?.optString("startTimestamp"),
                    place.optJSONObject("duration")?.optString("endTimestamp"), "Timeline export")
            }
        }
        for (i in 0 until minOf(modern.length(), 4000)) {
            val node = modern.optJSONObject(i) ?: continue
            val activity = node.optJSONObject("activity")
            val visit = node.optJSONObject("visit")
            if (activity != null || visit != null) append(
                if (activity != null) "Travel: " + activity.optString("topCandidate", "trip").take(90)
                else "Visited location",
                "Imported Timeline segment; " + (activity ?: visit).toString().take(700),
                node.optString("startTime"), node.optString("endTime"), "Timeline export")
        }
        return result.takeLast(100)
    }
}

class ImportPipeline(private val context: Context) {
    private fun title(uri: Uri): String = context.contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
    )?.use { if (it.moveToFirst()) it.getString(0) else null }
        ?.take(150) ?: (uri.lastPathSegment?.take(150) ?: "Selected file")

    private fun boundedBytes(uri: Uri, max: Int = 16 * 1024 * 1024): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open selected file.")
        return input.use { stream ->
            val bytes = java.io.ByteArrayOutputStream()
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                require(bytes.size() + n <= max) { "Selected file is too large (16 MB limit)." }
                bytes.write(buf, 0, n)
            }
            bytes.toByteArray()
        }
    }

    suspend fun read(uri: Uri): List<KnowledgeEntry> = withContext(Dispatchers.IO) {
        val filename = title(uri)
        val ext = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(ext in setOf("txt","md","csv","json","pdf","html","htm","ics","vcf")) {
            "Select a PDF, text, Markdown, CSV, JSON, iCalendar or HTML file."
        }
        val bytes = boundedBytes(uri)
        val text = when(ext) {
            "pdf" -> {
                PDFBoxResourceLoader.init(context)
                PDDocument.load(bytes).use { pdf ->
                    PDFTextStripper().apply { endPage = minOf(pdf.numberOfPages, 80) }.getText(pdf)
                }
            }
            else -> String(bytes, Charsets.UTF_8).let {
                if (ext in setOf("html", "htm")) it.replace(Regex("<[^>]+>"), " ") else it
            }
        }.trim()
        require(text.isNotBlank()) { "No extractable text found. Scanned PDFs may require OCR." }
        if (ext == "json" && (text.contains("timelineObjects") || text.contains("semanticSegments"))) {
            val trips = runCatching { TimelineParser.parse(text) }.getOrDefault(emptyList())
            if (trips.isNotEmpty()) return@withContext trips
        }
        text.chunked(KnowledgeSearch.MAX_TEXT).take(12).mapIndexed { index, chunk ->
            KnowledgeEntry(title = filename + if (index > 0) " (part " + (index + 1) + ")" else "",
                body = chunk, source = "Selected file", category = "DOCUMENT")
        }
    }

    suspend fun readSelectedPhoto(uri: Uri): KnowledgeEntry = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognized = recognizer.process(image).await().text.trim()
            require(recognized.isNotBlank()) { "No text recognized in the selected image." }
            KnowledgeEntry(title = "Photo OCR: " + title(uri), body = recognized.take(16000),
                source = "Selected photo (offline OCR)", category = "DOCUMENT")
        } finally { recognizer.close() }
    }
}
