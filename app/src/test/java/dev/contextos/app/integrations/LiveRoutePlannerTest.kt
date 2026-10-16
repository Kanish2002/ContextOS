package dev.contextos.app.integrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LiveRoutePlannerTest {
    @Test fun suggestsDepartureFromArrivalAndBuffer() {
        val arrival = 1_800_000_000_000L
        assertEquals(arrival - 40 * 60_000L,
            LiveRoutePlanner.leaveByMillis(arrival, 35 * 60L, 5))
    }

    @Test fun avoidsNegativeBuffer() {
        assertThrows(IllegalArgumentException::class.java) {
            LiveRoutePlanner.leaveByMillis(1000L, 120L, -1)
        }
    }
}
