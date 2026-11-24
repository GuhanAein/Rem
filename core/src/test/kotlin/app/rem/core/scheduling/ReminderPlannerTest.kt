package app.rem.core.scheduling

import app.rem.core.model.Reminder
import app.rem.core.model.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlannerTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun clockAt(s: String, zone: ZoneId = ist) = Clock.fixed(LocalDateTime.parse(s).atZone(zone).toInstant(), zone)
    private fun at(s: String, zone: ZoneId = ist): Instant = LocalDateTime.parse(s).atZone(zone).toInstant()
    private val planner = ReminderPlanner(clockAt("2026-11-24T10:00"))
    private fun r(due: String?, vararg offsets: Int, rrule: String? = null) =
        Reminder(id = 7, title = "x", dueAt = due?.let(LocalDateTime::parse), alertOffsets = offsets.toList().ifEmpty { listOf(0) }, recurrence = rrule)

    @Test fun multipleAlertsPicksEarliestFuture() {
        val alarm = planner.nextAlarm(r("2026-11-25T09:00", 1440, 60, 0), ist)
        assertEquals(at("2026-11-25T08:00"), alarm?.fireAt)
    }

    @Test fun snoozedUntilOverridesAlerts() {
        val snoozed = r("2026-11-25T09:00").copy(snoozedUntil = at("2026-11-24T10:15"))
        assertEquals(at("2026-11-24T10:15"), planner.nextAlarm(snoozed, ist)?.fireAt)
    }

    @Test fun snoozePresets() {
        assertEquals(at("2026-11-24T10:15"), planner.snoozeUntil(SnoozePreset.MIN_15, ist))
        assertEquals(at("2026-11-24T20:00"), planner.snoozeUntil(SnoozePreset.TONIGHT, ist))
        assertEquals(at("2026-11-25T09:00"), planner.snoozeUntil(SnoozePreset.TOMORROW_MORNING, ist))
        val late = ReminderPlanner(clockAt("2026-11-24T21:00"))
        assertEquals(at("2026-11-25T20:00"), late.snoozeUntil(SnoozePreset.TONIGHT, ist))
    }

    @Test fun completeRecurringAdvancesFromScheduledTime() {
        val done = planner.complete(r("2026-11-22T07:00", rrule = "FREQ=DAILY").copy(snoozedUntil = at("2026-11-24T11:00")), ist)
        assertEquals(LocalDateTime.parse("2026-11-25T07:00"), done.dueAt)
        assertEquals(Status.ACTIVE, done.status)
        assertNull(done.snoozedUntil)
        assertEquals(1, done.occurrenceCount)
    }

    @Test fun completeFinalOccurrenceMarksDone() {
        val done = planner.complete(r("2026-11-24T09:00", rrule = "FREQ=DAILY;COUNT=2").copy(occurrenceCount = 1), ist)
        assertEquals(Status.DONE, done.status)
        assertEquals(at("2026-11-24T10:00"), done.completedAt)
    }

    @Test fun completeOneTimeMarksDone() {
        assertEquals(Status.DONE, planner.complete(r("2026-11-24T09:00"), ist).status)
    }

    @Test fun dstGapShiftsForwardAndOverlapFiresOnce() {
        val ny = ZoneId.of("America/New_York")
        val p = ReminderPlanner(clockAt("2026-03-07T12:00", ny))
        val alarm = p.nextAlarm(r("2026-03-08T02:30"), ny)
        assertEquals(at("2026-03-08T03:30", ny), alarm?.fireAt)
        // Next day 02:30 exists normally.
        val next = p.complete(r("2026-03-08T02:30", rrule = "FREQ=DAILY"), ny)
        assertEquals(LocalDateTime.parse("2026-03-09T02:30"), next.dueAt)
        // Autumn overlap: 01:30 happens twice; we fire once, at the earlier offset (EDT, UTC-4).
        val p2 = ReminderPlanner(clockAt("2026-10-31T12:00", ny))
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), p2.nextAlarm(r("2026-11-01T01:30"), ny)?.fireAt)
    }

    @Test fun nagRepeatsAndStopsAfter12() {
        val nagging = r("2026-11-24T10:00").copy(nagMinutes = 5)
        val t = at("2026-11-24T10:00")
        val first = planner.nextNag(nagging, t, 0)
        assertEquals(t.plusSeconds(300), first?.fireAt)
        assertTrue(first!!.isNag)
        assertNull(planner.nextNag(nagging, t, 12))
        assertNull(planner.nextNag(r("2026-11-24T10:00"), t, 0))
    }

    @Test fun doneOrUndatedHasNoAlarm() {
        assertNull(planner.nextAlarm(r("2026-11-25T09:00").copy(status = Status.DONE), ist))
        assertNull(planner.nextAlarm(r(null), ist))
        assertNull(planner.nextAlarm(r("2026-11-24T09:00"), ist)) // already past
    }
}
