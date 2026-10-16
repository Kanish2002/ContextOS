package dev.contextos.app.integrations

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

/** Explicit user-selected file import. No broad storage or other-app data access. */
object PersonalDataImport {
    const val MAX_BYTES = 2_000_000
    fun readText(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val length = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        require(length < 0 || length <= MAX_BYTES) { "File exceeds 2 MB limit" }
        val stream = resolver.openInputStream(uri) ?: error("Cannot open selected document")
        return stream.use { input ->
            val bytes = input.readBoundedBytes(MAX_BYTES)
            require(bytes.size <= MAX_BYTES) { "File exceeds 2 MB limit" }
            bytes.toString(Charsets.UTF_8)
        }
    }
}
