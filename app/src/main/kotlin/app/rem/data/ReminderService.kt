package app.rem.data

import app.rem.alarm.AlarmScheduler
import app.rem.alarm.Notifier
import app.rem.core.model.Reminder
import app.rem.core.scheduling.SnoozePreset
import app.rem.core.scheduling.ReminderPlanner
import java.time.ZoneId

/** The single place that changes reminders, so the database and the alarms never disagree. */
class ReminderService(
    private val repository: ReminderRepository,
    private val planner: ReminderPlanner,
    private val scheduler: AlarmScheduler,
    private val notifier: Notifier,
    /** Called after any change (used to refresh home-screen widgets). */
    private val onChanged: suspend () -> Unit = {},
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    suspend fun save(reminder: Reminder): Long {
        val id = repository.upsert(reminder)
        repository.get(id)?.let { scheduler.schedule(it, zone) }
        onChanged()
        return id
    }

    /** Returns the reminder as it was before completion (for Undo). */
    suspend fun complete(id: Long): Reminder? {
        val before = repository.get(id) ?: return null
        val after = planner.complete(before, zone)
        repository.upsert(after)
        notifier.cancel(id)
        scheduler.cancel(id)
        scheduler.schedule(after, zone)
        onChanged()
        return before
    }

    suspend fun snooze(id: Long, preset: SnoozePreset) {
        val r = repository.get(id) ?: return
        val snoozed = planner.snooze(r, preset, zone)
        repository.upsert(snoozed)
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
        repository.activeScheduled().forEach { scheduler.schedule(it, zone) }
        onChanged()
    }
}
