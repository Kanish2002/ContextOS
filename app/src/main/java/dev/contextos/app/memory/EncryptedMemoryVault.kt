package dev.contextos.app.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * App-private AES-256-GCM vault. Its encryption key is generated and retained by
 * AndroidKeyStore; the key is never exported or included in device backups.
 *
 * Never write user-authored memories to plain SharedPreferences, Logcat or SQLite.
 * Android backup is disabled in the application manifest.
 */
class EncryptedMemoryVault(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "contextos_memory_v1.bin"))
    private val magic = byteArrayOf(0x43, 0x4F, 0x53, 0x34) // COS4
    private val aad = "contextos:memories:v1".toByteArray(Charsets.UTF_8)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun read(): List<MemoryNote> {
        val bytes = try { file.openRead().use { it.readBytes() } }
                    catch (_: java.io.FileNotFoundException) { return emptyList() }
        if (bytes.size !in 32..MAX_FILE_BYTES || !bytes.copyOfRange(0, 4).contentEquals(magic)) {
            throw IOException("The encrypted memory file is invalid. No memories were changed.")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(4, 16)))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(bytes, 16, bytes.size - 16)
        val payload = JSONObject(String(plain, Charsets.UTF_8))
        if (payload.getInt("version") != 1) throw IOException("Unsupported memory vault version.")
        val entries = payload.getJSONArray("notes")
        if (entries.length() > MemoryPolicy.MAX_NOTES) throw IOException("Memory count exceeds safety limit.")
        return List(entries.length()) { index ->
            val record = entries.getJSONObject(index)
            MemoryNote(record.getString("id"), MemoryPolicy.normalize(record.getString("text")),
                record.getLong("createdAtMs"))
        }
    }

    fun write(notes: List<MemoryNote>) {
        require(notes.size <= MemoryPolicy.MAX_NOTES)
        val rows = JSONArray()
        notes.forEach { note ->
            rows.put(JSONObject().put("id", note.id).put("text", MemoryPolicy.normalize(note.text))
                .put("createdAtMs", note.createdAtMs))
        }
        val clear = JSONObject().put("version", 1).put("notes", rows).toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val encrypted = ByteArrayOutputStream().apply {
            write(magic)
            write(cipher.iv)
            write(cipher.doFinal(clear))
        }.toByteArray()
        require(encrypted.size <= MAX_FILE_BYTES) { "Encrypted vault is too large." }
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }

    companion object {
        private const val KEY_ALIAS = "contextos_personal_memories_v1"
        private const val MAX_FILE_BYTES = 256 * 1024
    }
}
