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

    @Test fun detectsRealTrafficDelayWithoutNegativeWarnings() {
        assertEquals(12L, LiveRoutePlanner.delayMinutes(2400L, 1680L))
        assertEquals(0L, LiveRoutePlanner.delayMinutes(1200L, 1500L))
        assertEquals(null, LiveRoutePlanner.delayMinutes(1200L, null))
    }

    @Test fun avoidsNegativeBuffer() {
        assertThrows(IllegalArgumentException::class.java) {
            LiveRoutePlanner.leaveByMillis(1000L, 120L, -1)
        }
    }
}
