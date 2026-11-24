package app.rem.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.rem.container
import app.rem.core.model.Status
import app.rem.core.scheduling.ReminderPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId

/** An alarm fired: show the notification (or full-screen alarm) and plan what comes next. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1)
        val nagCount = intent.getIntExtra(AlarmScheduler.EXTRA_NAG_COUNT, 0)
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
        val isDue = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_DUE, true)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(context, id, nagCount, scheduledAt, isDue)
            } finally {
                pending.finish()
            }
        }
    }

    internal suspend fun handle(context: Context, id: Long, nagCount: Int, scheduledAt: Long = 0L, isDue: Boolean = true) {
        val c = context.container
        val reminder = c.repository.get(id) ?: return
        if (reminder.status != Status.ACTIVE) return
        val zone = ZoneId.systemDefault()
        val now = c.clock.instant()
        if (scheduledAt > 0 && nagCount == 0) c.service.recordFired(id, java.time.Instant.ofEpochMilli(scheduledAt))

        // Hold the alarm during panic snooze, a phone call or (optionally) a calendar meeting.
        DeliveryGuard.holdUntil(context, c.settings.value, now)?.let { until ->
            c.scheduler.cancel(id)
            c.scheduler.set(app.rem.core.scheduling.PlannedAlarm(id, until, isNag = nagCount > 0, nagCount = nagCount, isDue = isDue), reminder.alertStyle)
            return
        }

        if (isDue && reminder.alertStyle == app.rem.core.model.AlertStyle.ALARM) {
            // Ring like a real alarm: looping sound on the alarm stream, screen on or off.
            // If Android refuses (e.g. an inexact fallback alarm), fall back to the alarm notification.
            runCatching { RingService.start(context, id, isNag = nagCount > 0) }
                .onFailure { c.notifier.show(reminder, isNag = nagCount > 0, ring = true) }
        } else if (reminder.kind == app.rem.core.model.Kind.SHOPPING_LIST) {
            reminder.extras[app.rem.data.SpacesService.LIST_ID]?.toLongOrNull()?.let { c.spaces.listText(it) }?.let { text ->
                c.notifier.show(reminder, isNag = false, ring = false, textOverride = text.substringAfter('\n'))
            }
        } else {
            c.notifier.show(reminder, isNag = nagCount > 0, ring = isDue)
        }

        // Nag only once the reminder is actually due (not for "1 day before" heads-ups).
        val dueInstant = reminder.dueAt?.let { ReminderPlanner.toInstant(it, zone) }
        val isDueAlert = dueInstant == null || !now.isBefore(dueInstant.minusSeconds(60))
        val nag = if (isDueAlert) c.planner.nextNag(reminder, now, nagCount) else null
        val nextAlert = c.planner.nextAlarm(reminder, zone)
        val next = listOfNotNull(nag, nextAlert).minByOrNull { it.fireAt }
        c.scheduler.cancel(id)
        if (next != null) c.scheduler.set(next, reminder.alertStyle)
        app.rem.widget.Widgets.refresh(context) // "late" labels change once something is due
    }
}
