package dev.contextos.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.security.MessageDigest

/** Imports user-picked models into app-private storage without broad storage permissions. */
class ModelStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("local-model", Context.MODE_PRIVATE)
    private val modelsDir get() = File(context.filesDir, "models").also { it.mkdirs() }

    fun selected(): File? = prefs.getString("filename", null)?.let { name ->
        File(modelsDir, name).takeIf { it.isFile && it.length() > MIN_MODEL_BYTES }
    }

    /** Optional model packaged in the APK under assets/models/. No network required. */
    fun bundledAssetName(): String? = context.assets.list("models")
        ?.firstOrNull { it.endsWith(".litertlm", ignoreCase = true) }

    fun isBundledSelected(): Boolean = prefs.getString("filename", "")?.startsWith("bundled-") == true

    /** Atomic first-launch extraction: LiteRT-LM requires a real filesystem path. */
    suspend fun prepareBundledModel(progress: (Int?) -> Unit): File = withContext(Dispatchers.IO) {
        // Keep any selected user-imported model instead of overriding their choice.
        selected()?.let { return@withContext it }
        val assetName = bundledAssetName() ?: error("No bundled model found in the APK.")
        val name = "bundled-$assetName"
        val target = File(modelsDir, name)
        val staged = File(modelsDir, "$name.part")
        try {
            // A freshly installed app may have a valid copy but no saved preference after a crash.
            if (!target.isFile || target.length() <= MIN_MODEL_BYTES) {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(1024 * 1024)
                context.assets.open("models/$assetName").use { input ->
                    staged.outputStream().buffered().use { output ->
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            total += count
                            // Model is ~347MB; exact size comes from artifact integrity check at build.
                            progress((total * 100 / EXPECTED_BUNDLED_BYTES).toInt().coerceIn(0, 99))
                        }
                    }
                }
                check(staged.length() > MIN_MODEL_BYTES) { "Bundled model asset is too small." }
                if (assetName == DEFAULT_BUNDLED_MODEL) {
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    check(hash.equals(DEFAULT_SHA256, ignoreCase = true)) {
                        "Bundled model integrity check failed (SHA-256 mismatch)."
                    }
                }
                check(staged.renameTo(target)) { "Cannot save bundled model to private storage." }
            }
            prefs.edit().putString("filename", name).apply()
            progress(100)
            target
        } catch (t: Throwable) {
            staged.delete()
            throw t
        }
    }

    fun labelFor(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "model.litertlm"

    suspend fun importFromUri(uri: Uri, progress: (Int?) -> Unit): File = withContext(Dispatchers.IO) {
        val label = labelFor(uri)
        require(label.lowercase().endsWith(".litertlm")) {
            "Select a .litertlm model file, not a .task or .tflite file."
        }
        val length = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        val name = "model-${UUID.randomUUID()}.litertlm"
        val staged = File(modelsDir, "$name.part")
        val dest = File(modelsDir, name)
        try {
            context.contentResolver.openInputStream(uri)?.use { source ->
                staged.outputStream().buffered().use { target ->
                    val chunk = ByteArray(1024 * 1024)
                    var copied = 0L
                    while (true) {
                        val size = source.read(chunk)
                        if (size < 0) break
                        target.write(chunk, 0, size)
                        copied += size
                        if (length != null && length > 0L) {
                            progress((copied * 100 / length).toInt().coerceIn(0, 100))
                        } else progress(null)
                    }
                }
            } ?: error("Cannot read the selected model file.")
            require(staged.length() > MIN_MODEL_BYTES) { "Selected model file is unexpectedly small." }
            check(staged.renameTo(dest)) { "Could not finalize copied model file." }
            // Only switch selected model after a full, successful copy.
            val old = selected()
            prefs.edit().putString("filename", name).apply()
            if (old != null && old.absolutePath != dest.absolutePath) old.delete()
            progress(100)
            dest
        } catch (e: Exception) {
            staged.delete()
            dest.delete()
            throw e
        }
    }
    companion object {
        private const val MIN_MODEL_BYTES = 4L * 1024 * 1024
        private const val EXPECTED_BUNDLED_BYTES = 347_000_000L
        private const val DEFAULT_BUNDLED_MODEL = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
        private const val DEFAULT_SHA256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139"
    }
}
