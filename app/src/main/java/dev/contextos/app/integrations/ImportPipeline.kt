package dev.contextos.app.integrations

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
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
                    val extracted = PDFTextStripper().apply {
                        endPage = minOf(pdf.numberOfPages, 80)
                    }.getText(pdf)
                    if (extracted.isNotBlank()) extracted else ocrScannedPdf(pdf)
                }
            }
            else -> String(bytes, Charsets.UTF_8).let {
                if (ext in setOf("html", "htm")) it.replace(Regex("<[^>]+>"), " ") else it
            }
        }.trim()
        require(text.isNotBlank()) { "No extractable text found. Scanned PDFs may require OCR." }
        if (ext == "txt" && (
                text.contains("] ") || text.lineSequence().take(10).any { it.contains(" - ") }
            )) {
            val conversation = WhatsAppExportParser.parse(text)
            if (conversation.isNotEmpty()) return@withContext conversation
        }
        if (ext == "json" && (text.contains("timelineObjects") || text.contains("semanticSegments"))) {
            val trips = runCatching { TimelineParser.parse(text) }.getOrDefault(emptyList())
            if (trips.isNotEmpty()) return@withContext trips
        }
        text.chunked(KnowledgeSearch.MAX_TEXT).take(12).mapIndexed { index, chunk ->
            KnowledgeEntry(title = filename + if (index > 0) " (part " + (index + 1) + ")" else "",
                body = chunk, source = "Selected file", category = "DOCUMENT")
        }
    }

    /**
     * OCR fallback only when a selected PDF has no extractable text layer.
     * Hard cap of 5 pages and 4M pixels per page to avoid unbounded mobile RAM use.
     */
    private suspend fun ocrScannedPdf(pdf: PDDocument): String {
        val engine = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val renderer = PDFRenderer(pdf)
        val pages = mutableListOf<String>()
        try {
            for (index in 0 until minOf(5, pdf.numberOfPages)) {
                val bounds = pdf.getPage(index).mediaBox
                val width = (bounds.width * 96f / 72f).toInt()
                val height = (bounds.height * 96f / 72f).toInt()
                if (width <= 0 || height <= 0 || width.toLong() * height > 4_000_000L)
                    continue
                val bitmap = renderer.renderImageWithDPI(index, 96f)
                try {
                    val words = engine.process(InputImage.fromBitmap(bitmap, 0))
                        .await().text.trim()
                    if (words.isNotBlank()) pages += "Page " + (index + 1) + ":\n" + words
                } finally { bitmap.recycle() }
            }
        } finally { engine.close() }
        return pages.joinToString("\n\n").take(12 * KnowledgeSearch.MAX_TEXT)
    }

    suspend fun readSelectedPhoto(uri: Uri): KnowledgeEntry = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognized = recognizer.process(image).await().text.trim()
            val labels = labeler.process(image).await().filter { it.confidence >= 0.6f }
                .take(8).joinToString(", ") { it.text }
            require(recognized.isNotBlank() || labels.isNotBlank()) {
                "No local text or recognizable objects found in the selected image."
            }
            KnowledgeEntry(title = "Selected photo: " + title(uri),
                body = ("Visible labels (automatic estimates): " + labels +
                    if (recognized.isBlank()) "" else "\nExtracted visible text: " + recognized)
                    .take(16000),
                source = "User-selected photo · on-device OCR and image labels",
                category = "DOCUMENT")
        } finally {
            recognizer.close()
            labeler.close()
        }
    }
}
