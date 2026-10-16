package dev.contextos.app.integrations

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedStreamTest {
    @Test fun acceptsItemsWithinLimit() {
        val bytes = ByteArray(50) { it.toByte() }
        val read = ByteArrayInputStream(bytes).readBoundedBytes(50)
        assertArrayEquals(bytes, read)
    }

    @Test fun rejectsOversizedItemsWithoutUnboundedRead() {
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(ByteArray(100_000)).readBoundedBytes(100)
        }
    }
}
