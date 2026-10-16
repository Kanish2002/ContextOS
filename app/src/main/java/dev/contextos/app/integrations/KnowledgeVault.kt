package dev.contextos.app.integrations

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class KnowledgeEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
    val category: String = "NOTE",
    val done: Boolean = false,
    val dueAt: Long? = null,
    val durationMinutes: Int? = null
)

object KnowledgeSearch {
    const val MAX_ITEMS = 160
    const val MAX_TEXT = 16000
    const val MAX_RESULTS = 5
    private val word = Regex("[\\p{L}\\p{M}\\p{N}]{2,}")
    private val ignored = setOf("the", "what", "which", "were", "have", "been", "about", "with",
        "from", "your", "mine", "this", "that", "last", "tuesday", "ideas", "wrote", "down", "did", "not", "i")
    fun normalize(s: String): Set<String> = word.findAll(s.lowercase(Locale.ROOT))
        .map { it.value }.filter { it !in ignored }.toSet()
    /** Structured time/task questions must preserve exact filters, not semantic near-matches. */
    fun structuredQuery(query: String): Boolean {
        val q = query.lowercase(Locale.ROOT)
        return "last tuesday" in q ||
            listOf("incomplete", "unfinished", "not completed", "pending",
                "haven't completed").any { it in q } ||
            listOf("overdue", "due tomorrow").any { it in q }
    }

    fun search(query: String, notes: List<KnowledgeEntry>, now: LocalDate = LocalDate.now()): List<KnowledgeEntry> {
        val q = query.lowercase(Locale.ROOT)
        val tokens = normalize(query)
        val lastTuesday = if ("last tuesday" in q)
            now.with(TemporalAdjusters.previous(DayOfWeek.TUESDAY)) else null
        val unfinished = listOf("incomplete", "unfinished", "not completed", "pending", "haven't completed")
            .any { it in q }
        val projects = listOf("project", "projects", "learning", "course").any { it in q }
        return notes.asSequence().filter { note ->
            (!unfinished || !note.done) &&
                (lastTuesday == null || Instant.ofEpochMilli(note.createdAt)
                    .atZone(ZoneId.systemDefault()).toLocalDate() == lastTuesday) &&
                (!projects || note.category in setOf("PROJECT", "LEARNING", "CAREER"))
        }.map { note ->
            val haystack = normalize(note.title + " " + note.body.take(5000))
            val overlap = tokens.count { it in haystack }
            val exact = if (q.length >= 4 && (note.title + " " + note.body)
                .lowercase(Locale.ROOT).contains(q)) 4 else 0
            val contextual = if (lastTuesday != null || unfinished) 1 else 0
            note to (overlap * 2 + exact + contextual)
        }.filter { it.second > 0 || (tokens.isEmpty() && (lastTuesday != null || unfinished)) }
            .sortedWith(compareByDescending<Pair<KnowledgeEntry, Int>> { it.second }
                .thenByDescending { it.first.createdAt })
            .take(MAX_RESULTS).map { it.first }.toList()
    }

    fun formatContext(entries: List<KnowledgeEntry>): String? =
        entries.joinToString("\n") {
            "[" + it.category + "; " + it.source + "; " +
                LocalDate.ofInstant(Instant.ofEpochMilli(it.createdAt), ZoneId.systemDefault()) + "] " +
                it.title.take(100) + ": " + it.body.take(750)
        }.take(2500).takeIf { it.isNotBlank() }

    fun retrievedContext(query: String, entries: List<KnowledgeEntry>): String? =
        formatContext(search(query, entries))
}

/** Atomic AES-256-GCM; key never exported from Android Keystore. */
class KnowledgeVault(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "contextos_knowledge_v1.enc"))
    private val header = byteArrayOf(0x4B, 0x4E, 0x4F, 0x31)
    private val associated = "contextos:knowledge:v1".toByteArray(Charsets.UTF_8)
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): List<KnowledgeEntry> {
        val bytes = try { file.openRead().use { it.readBytes() } }
            catch (_: java.io.FileNotFoundException) { return emptyList() }
        require(bytes.size in 32..2_600_000 && bytes.copyOfRange(0, 4).contentEquals(header)) {
            "Invalid encrypted knowledge vault; nothing has been overwritten."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(4, 16)))
        cipher.updateAAD(associated)
        val json = JSONObject(String(cipher.doFinal(bytes, 16, bytes.size - 16), Charsets.UTF_8))
        require(json.getInt("version") == 1)
        val array = json.getJSONArray("items")
        require(array.length() <= KnowledgeSearch.MAX_ITEMS)
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            KnowledgeEntry(o.getString("id"), o.getString("title"), o.getString("body"),
                o.getString("source"), o.getLong("createdAt"), o.optString("category", "NOTE"),
                o.optBoolean("done", false), if (o.has("dueAt") && !o.isNull("dueAt"))
                    o.getLong("dueAt") else null,
                if (o.has("durationMinutes") && !o.isNull("durationMinutes"))
                    o.getInt("durationMinutes") else null)
        }
    }
    fun write(items: List<KnowledgeEntry>) {
        require(items.size <= KnowledgeSearch.MAX_ITEMS)
        val rows = JSONArray()
        items.forEach {
            require(it.title.isNotBlank() && it.body.isNotBlank() &&
                it.body.length <= KnowledgeSearch.MAX_TEXT && it.title.length <= 160)
            rows.put(JSONObject().put("id", it.id).put("title", it.title)
                .put("body", it.body).put("source", it.source).put("createdAt", it.createdAt)
                .put("category", it.category).put("done", it.done)
                .put("dueAt", it.dueAt).put("durationMinutes", it.durationMinutes))
        }
        val plaintext = JSONObject().put("version", 1).put("items", rows)
            .toString().toByteArray(Charsets.UTF_8)
        require(plaintext.size <= 2_400_000) { "Knowledge vault storage limit reached." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(associated)
        val bytes = header + cipher.iv + cipher.doFinal(plaintext)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (t: Throwable) { file.failWrite(stream); throw t }
    }
    companion object { private const val ALIAS = "contextos_local_knowledge_v1" }
}
