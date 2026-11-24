package app.rem.ui

import app.rem.ui.lock.shouldRelock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTest {
    @Test fun briefTripsToPickersDoNotRelock() {
        assertFalse(shouldRelock(1_000, 20_000))
        assertFalse(shouldRelock(null, 999_999))
    }

    @Test fun realAbsenceRelocks() {
        assertTrue(shouldRelock(1_000, 70_000))
    }
}
