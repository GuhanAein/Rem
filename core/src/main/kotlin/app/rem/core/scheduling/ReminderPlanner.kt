package app.rem.core.scheduling

import app.rem.core.model.Reminder
import app.rem.core.model.Status
import app.rem.core.recurrence.RecurrenceRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class PlannedAlarm(val reminderId: Long, val fireAt: Instant, val isNag: Boolean = false, val nagCount: Int = 0)

enum class SnoozePreset(val label: String) {
    MIN_5("5 min"),
    MIN_15("15 min"),
    HOUR_1("1 hour"),
    TONIGHT("Tonight"),
    TOMORROW_MORNING("Tomorrow"),
}

/** Decides *when* things fire. Pure logic: no Android, so it is fully unit-tested. */
class ReminderPlanner(private val clock: Clock) {

    /** The earliest upcoming alarm for [r], or null if nothing is left to fire. */
    fun nextAlarm(r: Reminder, zone: ZoneId): PlannedAlarm? {
        if (r.status != Status.ACTIVE) return null
        val now = clock.instant()
        r.snoozedUntil?.let { if (it.isAfter(now)) return PlannedAlarm(r.id, it) }
        val due = r.dueAt ?: return null
        val dueInstant = toInstant(due, zone)
        return r.alertOffsets.ifEmpty { listOf(0) }
            .map { dueInstant.minusSeconds(it * 60L) }
            .filter { it.isAfter(now) }
            .minOrNull()
            ?.let { PlannedAlarm(r.id, it) }
    }

    fun snoozeUntil(preset: SnoozePreset, zone: ZoneId): Instant {
        val now = ZonedDateTime.now(clock.withZone(zone))
        return when (preset) {
            SnoozePreset.MIN_5 -> now.plusMinutes(5)
            SnoozePreset.MIN_15 -> now.plusMinutes(15)
            SnoozePreset.HOUR_1 -> now.plusHours(1)
            SnoozePreset.TONIGHT -> {
                val tonight = now.toLocalDate().atTime(20, 0).atZone(zone)
                if (tonight.isAfter(now)) tonight else tonight.plusDays(1)
            }
            SnoozePreset.TOMORROW_MORNING -> now.toLocalDate().plusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone)
        }.withSecond(0).withNano(0).toInstant()
    }

    fun snooze(r: Reminder, preset: SnoozePreset, zone: ZoneId): Reminder = r.copy(snoozedUntil = snoozeUntil(preset, zone))

    /**
     * Marks [r] done. A recurring reminder advances to its next occurrence on the series grid
     * that is after now (so a late completion doesn't drift), or becomes DONE when the series ends.
     */
    fun complete(r: Reminder, zone: ZoneId): Reminder {
        val nowInstant = clock.instant()
        val done = r.copy(status = Status.DONE, completedAt = nowInstant, snoozedUntil = null)
        val rule = RecurrenceRule.parseOrNull(r.recurrence) ?: return done
        val due = r.dueAt ?: return done
        val newCount = r.occurrenceCount + 1
        val now = LocalDateTime.ofInstant(nowInstant, zone)
        var next = rule.next(due, due, newCount) ?: return done.copy(occurrenceCount = newCount)
        var guard = 0
        while (!next.isAfter(now) && guard++ < 10_000) {
            next = rule.next(due, next, newCount) ?: return done.copy(occurrenceCount = newCount)
        }
        return r.copy(dueAt = next, occurrenceCount = newCount, snoozedUntil = null, completedAt = nowInstant)
    }

    /** The next nag repeat after an alarm fired at [firedAt], or null when nagging is off or exhausted. */
    fun nextNag(r: Reminder, firedAt: Instant, repeatsSoFar: Int): PlannedAlarm? {
        val interval = r.nagMinutes ?: return null
        if (r.status != Status.ACTIVE || repeatsSoFar >= MAX_NAGS) return null
        return PlannedAlarm(r.id, firedAt.plusSeconds(interval * 60L), isNag = true, nagCount = repeatsSoFar + 1)
    }

    companion object {
        const val MAX_NAGS = 12

        /** DST-safe: a time in a spring-forward gap shifts forward; an overlap uses the earlier offset. */
        fun toInstant(local: LocalDateTime, zone: ZoneId): Instant = ZonedDateTime.ofLocal(local, zone, null).toInstant()
    }
}
