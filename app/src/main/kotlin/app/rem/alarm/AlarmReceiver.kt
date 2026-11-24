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
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(context, id, nagCount)
            } finally {
                pending.finish()
            }
        }
    }

    internal suspend fun handle(context: Context, id: Long, nagCount: Int) {
        val c = context.container
        val reminder = c.repository.get(id) ?: return
        if (reminder.status != Status.ACTIVE) return
        val zone = ZoneId.systemDefault()
        val now = c.clock.instant()
        c.notifier.show(reminder, isNag = nagCount > 0)

        // Nag only once the reminder is actually due (not for "1 day before" heads-ups).
        val dueInstant = reminder.dueAt?.let { ReminderPlanner.toInstant(it, zone) }
        val isDueAlert = dueInstant == null || !now.isBefore(dueInstant.minusSeconds(60))
        val nag = if (isDueAlert) c.planner.nextNag(reminder, now, nagCount) else null
        val nextAlert = c.planner.nextAlarm(reminder, zone)
        val next = listOfNotNull(nag, nextAlert).minByOrNull { it.fireAt }
        c.scheduler.cancel(id)
        if (next != null) c.scheduler.set(next, reminder.alertStyle)
    }
}
