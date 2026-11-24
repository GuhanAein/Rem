package app.rem.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.rem.core.model.AlertStyle
import app.rem.core.model.Reminder
import app.rem.core.scheduling.PlannedAlarm
import app.rem.core.scheduling.ReminderPlanner
import app.rem.ui.MainActivity
import java.time.ZoneId

/** Talks to AlarmManager. One pending alarm per reminder (request code = reminder id). */
class AlarmScheduler(private val context: Context, private val planner: ReminderPlanner) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Cancels any pending alarm and sets the next planned one (if any). */
    fun schedule(reminder: Reminder, zone: ZoneId) {
        cancel(reminder.id)
        planner.nextAlarm(reminder, zone)?.let { set(it, reminder.alertStyle) }
    }

    fun set(alarm: PlannedAlarm, style: AlertStyle) {
        val operation = firePendingIntent(alarm)
        val at = alarm.fireAt.toEpochMilli()
        when {
            !canScheduleExact() -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            style == AlertStyle.ALARM -> {
                val show = PendingIntent.getActivity(
                    context, alarm.reminderId.toInt(),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
            }
            else -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    fun cancel(reminderId: Long) {
        alarmManager.cancel(firePendingIntent(PlannedAlarm(reminderId, java.time.Instant.EPOCH)))
    }

    private fun firePendingIntent(alarm: PlannedAlarm): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, alarm.reminderId)
            .putExtra(EXTRA_NAG_COUNT, alarm.nagCount)
        return PendingIntent.getBroadcast(
            context, alarm.reminderId.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ACTION_FIRE = "app.rem.action.FIRE"
        const val EXTRA_ID = "reminderId"
        const val EXTRA_NAG_COUNT = "nagCount"
    }
}
