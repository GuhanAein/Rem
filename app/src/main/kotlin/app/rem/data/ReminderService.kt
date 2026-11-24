package app.rem.data

import app.rem.alarm.AlarmScheduler
import app.rem.alarm.Notifier
import app.rem.core.insights.ConflictDetector
import app.rem.core.insights.LogEvent
import app.rem.core.model.Reminder
import app.rem.core.model.Status
import app.rem.core.model.Trigger
import app.rem.core.scheduling.ReminderPlanner
import app.rem.core.scheduling.SnoozePreset
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The single place that changes reminders, so the database, the log and the alarms never disagree. */
class ReminderService(
    private val repository: ReminderRepository,
    private val planner: ReminderPlanner,
    private val scheduler: AlarmScheduler,
    private val notifier: Notifier,
    private val logDao: LogDao? = null,
    private val firedDao: FiredDao? = null,
    private val settings: Settings? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    /** The phone's next alarm-clock time, if the user has one set (for "when I wake up"). */
    private val nextSystemAlarm: () -> Instant? = { null },
    /** Called after any change (used to refresh widgets and context triggers). */
    private val onChanged: suspend () -> Unit = {},
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    suspend fun save(reminder: Reminder): Long {
        val id = repository.upsert(snapToRule(resolveTimeTrigger(reminder)))
        repository.get(id)?.let { scheduler.schedule(it, zone) }
        onChanged()
        return id
    }

    /** Returns the reminder as it was before completion (for Undo and the follow-up prompt). */
    suspend fun complete(id: Long): Reminder? {
        val before = repository.get(id) ?: return null
        val after = resolveTimeTrigger(planner.complete(before, zone))
        repository.upsert(after)
        log(before, LogEvent.Type.DONE)
        notifier.cancel(id)
        scheduler.cancel(id)
        scheduler.schedule(after, zone)
        onChanged()
        return before
    }

    /** Skips this occurrence of a repeating reminder (counts against streaks, unlike Done). */
    suspend fun skip(id: Long) {
        val before = repository.get(id) ?: return
        if (!before.isRecurring) return
        val after = planner.complete(before, zone).copy(completedAt = before.completedAt)
        repository.upsert(after)
        log(before, LogEvent.Type.SKIPPED)
        notifier.cancel(id)
        scheduler.schedule(after, zone)
        onChanged()
    }

    suspend fun snooze(id: Long, preset: SnoozePreset) {
        val r = repository.get(id) ?: return
        val snoozed = planner.snooze(r, preset, zone)
        repository.upsert(snoozed)
        val base = r.dueAt?.let { ReminderPlanner.toInstant(it, zone) }?.takeIf { it.isBefore(clock.instant()) } ?: clock.instant()
        log(r, LogEvent.Type.SNOOZED, Duration.between(base, snoozed.snoozedUntil).toMinutes().toInt())
        notifier.cancel(id)
        scheduler.schedule(snoozed, zone)
        onChanged()
    }

    /** Stops the current alarm/nag without completing; the reminder stays in the list. */
    suspend fun dismiss(id: Long) {
        notifier.cancel(id)
        scheduler.cancel(id)
        repository.get(id)?.let { scheduler.schedule(it, zone) }
    }

    suspend fun restore(reminder: Reminder) = save(reminder)

    suspend fun delete(id: Long) {
        scheduler.cancel(id)
        notifier.cancel(id)
        repository.delete(id)
        onChanged()
    }

    suspend fun rescheduleAll() {
        repository.activeScheduled().forEach { r ->
            val resolved = resolveTimeTrigger(r)
            if (resolved != r) repository.upsert(resolved)
            scheduler.schedule(resolved, zone)
        }
        onChanged()
    }

    /** Spreads a crowded time slot 15 minutes apart. */
    suspend fun spread(ids: List<Long>) {
        val crowd = ids.mapNotNull { repository.get(it) }.filter { it.dueAt != null }
        ConflictDetector.spread(crowd).forEach { save(it) }
    }

    /** "Silence everything" for a while; alarms that fall inside are held until it ends. */
    suspend fun panicSnooze(hours: Long) {
        settings?.update { it.copy(panicUntil = clock.instant().plus(Duration.ofHours(hours))) }
        onChanged()
    }

    suspend fun endPanic() {
        settings?.update { it.copy(panicUntil = null) }
        rescheduleAll()
    }

    suspend fun recordFired(id: Long, scheduledAt: Instant) {
        firedDao?.insert(FiredEntity(reminderId = id, scheduledAt = scheduledAt.toEpochMilli(), firedAt = clock.instant().toEpochMilli()))
    }

    /** For "when I wake up" / "before I sleep": put the due time on the next wake or bedtime. */
    fun resolveTimeTrigger(r: Reminder): Reminder {
        if (r.status != Status.ACTIVE) return r
        val s = settings?.value ?: return r
        val now = LocalDateTime.now(clock)
        val target: LocalDateTime = when (r.trigger) {
            Trigger.WAKE_UP -> {
                val alarm = nextSystemAlarm()?.let { LocalDateTime.ofInstant(it, zone) }
                alarm?.takeIf { it.isAfter(now) && it.isBefore(now.plusHours(30)) }?.plusMinutes(2) ?: nextAt(s.wakeTime, now)
            }
            Trigger.BEDTIME -> nextAt(s.bedtime, now)
            else -> return r
        }
        return r.copy(dueAt = target)
    }

    /** "Every Saturday" starting on a Thursday: the first reminder moves to Saturday. */
    fun snapToRule(r: Reminder): Reminder {
        val due = r.dueAt ?: return r
        if (r.randomTimes != null) return r
        val rule = app.rem.core.recurrence.RecurrenceRule.parseOrNull(r.recurrence) ?: return r
        if (rule.byDay.isEmpty() && rule.byMonthDay.isEmpty()) return r
        val first = rule.firstOnOrAfter(due, due) ?: return r
        return if (first == due) r else r.copy(dueAt = first)
    }

    private fun nextAt(t: LocalTime, now: LocalDateTime): LocalDateTime =
        now.toLocalDate().atTime(t).let { if (it.isAfter(now)) it else it.plusDays(1) }

    private suspend fun log(r: Reminder, type: LogEvent.Type, minutes: Int = 0) {
        logDao?.insert(
            LogEntity(
                reminderId = r.id, title = r.title, category = r.category.name,
                at = LocalDateTime.now(clock).format(LOG_TIME), type = type.name, minutes = minutes,
            ),
        )
    }

    companion object {
        val LOG_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
