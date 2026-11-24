package app.rem.core.scheduling

import app.rem.core.model.RandomTimes
import app.rem.core.model.Reminder
import app.rem.core.model.Status
import app.rem.core.recurrence.RecurrenceRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * One scheduled firing. [isDue] is the on-time alert (which may ring as an alarm); earlier alerts
 * ("3 days before") are always gentle notifications, so deadlines escalate naturally.
 */
data class PlannedAlarm(
    val reminderId: Long,
    val fireAt: Instant,
    val isNag: Boolean = false,
    val nagCount: Int = 0,
    val isDue: Boolean = true,
)

enum class SnoozePreset(val label: String) {
    MIN_5("5 min"),
    MIN_15("15 min"),
    HOUR_1("1 hour"),
    TONIGHT("Tonight"),
    TOMORROW_MORNING("Tomorrow"),
    HOURS_2("2 hours"),
}

/** Decides *when* things fire. Pure logic: no Android, so it is fully unit-tested. */
class ReminderPlanner(private val clock: Clock) {

    /** The earliest upcoming alarm for [r], or null if nothing is left to fire. */
    fun nextAlarm(r: Reminder, zone: ZoneId): PlannedAlarm? {
        if (r.status != Status.ACTIVE) return null
        if (r.trigger != null && !r.trigger.timeBased) return null // fired by the event, not the clock
        val now = clock.instant()
        r.snoozedUntil?.let { if (it.isAfter(now)) return PlannedAlarm(r.id, it) }
        val due = effectiveDue(r, zone) ?: return null
        val dueInstant = toInstant(due, zone)
        return r.alertOffsets.ifEmpty { listOf(0) }
            .map { offset -> offset to dueInstant.minusSeconds(offset * 60L) }
            .filter { (_, at) -> at.isAfter(now) }
            .minByOrNull { (_, at) -> at }
            ?.let { (offset, at) -> PlannedAlarm(r.id, at, isDue = offset == 0) }
    }

    /** The due time after applying the reminder's time window. */
    fun effectiveDue(r: Reminder, zone: ZoneId): LocalDateTime? {
        val due = r.dueAt ?: return null
        return r.window?.adjust(due) ?: due
    }

    fun snoozeUntil(preset: SnoozePreset, zone: ZoneId): Instant {
        val now = ZonedDateTime.now(clock.withZone(zone))
        return when (preset) {
            SnoozePreset.MIN_5 -> now.plusMinutes(5)
            SnoozePreset.MIN_15 -> now.plusMinutes(15)
            SnoozePreset.HOUR_1 -> now.plusHours(1)
            SnoozePreset.HOURS_2 -> now.plusHours(2)
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
     * Random-time reminders move to their next random slot.
     */
    fun complete(r: Reminder, zone: ZoneId): Reminder {
        val nowInstant = clock.instant()
        val done = r.copy(status = Status.DONE, completedAt = nowInstant, snoozedUntil = null)
        val now = LocalDateTime.ofInstant(nowInstant, zone)
        r.randomTimes?.let { rt ->
            val next = nextRandomSlot(r.id, rt, maxOf(now, r.dueAt ?: now)) ?: return done
            return r.copy(dueAt = next, occurrenceCount = r.occurrenceCount + 1, snoozedUntil = null, completedAt = nowInstant)
        }
        val rule = RecurrenceRule.parseOrNull(r.recurrence) ?: return done
        val due = r.dueAt ?: return done
        val newCount = r.occurrenceCount + 1
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

        /**
         * Deterministic "random" moments for a day: the same reminder gets the same times on the same
         * date (so rescheduling is stable), different every day, spread so no two are too close.
         */
        fun randomSlots(reminderId: Long, rt: RandomTimes, date: LocalDate): List<LocalDateTime> {
            val rnd = Random(reminderId * 1_000_003L + date.toEpochDay())
            val span = java.time.Duration.between(rt.start, rt.end).toMinutes().toInt()
            val bucket = span / rt.perDay
            return (0 until rt.perDay).map { i ->
                val minute = i * bucket + if (bucket > 1) rnd.nextInt(bucket) else 0
                date.atTime(rt.start.plusMinutes(minute.toLong())).withSecond(0).withNano(0)
            }
        }

        fun nextRandomSlot(reminderId: Long, rt: RandomTimes, after: LocalDateTime): LocalDateTime? {
            var d = after.toLocalDate()
            repeat(3) {
                randomSlots(reminderId, rt, d).firstOrNull { it.isAfter(after) }?.let { return it }
                d = d.plusDays(1)
            }
            return null
        }
    }
}
