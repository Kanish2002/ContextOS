package dev.contextos.app.integrations

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Compatible with Android 8+; never materializes an unbounded document/API response. */
internal fun InputStream.readBoundedBytes(limit: Int): ByteArray {
    require(limit in 1..32_000_000)
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(result.size() + count <= limit) {
            "Selected item exceeds " + limit / 1024 + " KB limit."
        }
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}
