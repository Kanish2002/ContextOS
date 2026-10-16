package dev.contextos.app.integrations

import org.junit.Assert.*
import org.junit.Test

class CommuteReminderSchedulerTest {
    @Test fun schedulesFiveMinutesAheadWithoutExactAlarmPromise() {
        val now = 1_800_000_000_000L
        assertEquals(30 * 60_000L,
            CommuteReminderScheduler.waitMillis(now, now + 35 * 60_000L))
        assertEquals(0L, CommuteReminderScheduler.waitMillis(now, now + 2 * 60_000L))
    }

    @Test fun rejectsPastAndVeryDistantDepartures() {
        val now = 1_800_000_000_000L
        assertThrows(IllegalArgumentException::class.java) {
            CommuteReminderScheduler.waitMillis(now, now - 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommuteReminderScheduler.waitMillis(now, now + 15L * 24 * 60 * 60_000L)
        }
    }
}
