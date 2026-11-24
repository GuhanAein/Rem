package app.rem.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.rem.R
import app.rem.core.model.AlertStyle
import app.rem.core.model.Reminder
import app.rem.core.scheduling.SnoozePreset
import app.rem.ui.MainActivity
import app.rem.ui.format.describeDue

class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Your reminders"
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, "Alarm reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen reminders that ring like an alarm"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            },
        )
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canUseFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun show(reminder: Reminder, isNag: Boolean) {
        if (!canPost()) return
        val id = reminder.id.toInt()
        val isAlarm = reminder.alertStyle == AlertStyle.ALARM
        val content = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ID, reminder.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val subtitle = buildString {
            if (isNag) append("Still waiting · ")
            append(describeDue(reminder.dueAt))
            reminder.amount?.let { append(" · ").append(it) }
        }
        val builder = NotificationCompat.Builder(context, if (isAlarm) CHANNEL_ALARMS else CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD9BC83.toInt())
            .setContentTitle(reminder.title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(subtitle, reminder.notes).filter { it.isNotBlank() }.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(content)
            .setAutoCancel(!isAlarm)
            .setOngoing(isAlarm)
            .addAction(0, "Done", action(ActionReceiver.ACTION_DONE, reminder.id, 1))
            .addAction(0, "Snooze 15m", action(ActionReceiver.ACTION_SNOOZE, reminder.id, 2, SnoozePreset.MIN_15))
            .addAction(0, "1 hour", action(ActionReceiver.ACTION_SNOOZE, reminder.id, 3, SnoozePreset.HOUR_1))
            .setDeleteIntent(action(ActionReceiver.ACTION_DISMISS, reminder.id, 4))
        if (isAlarm && canUseFullScreen()) {
            val fullScreen = PendingIntent.getActivity(
                context, id,
                AlarmActivity.intent(context, reminder.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setFullScreenIntent(fullScreen, true)
        }
        @Suppress("MissingPermission")
        manager.notify(id, builder.build())
    }

    fun cancel(reminderId: Long) = manager.cancel(reminderId.toInt())

    private fun action(action: String, id: Long, slot: Int, snooze: SnoozePreset? = null): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(AlarmScheduler.EXTRA_ID, id)
        snooze?.let { intent.putExtra(ActionReceiver.EXTRA_SNOOZE, it.name) }
        return PendingIntent.getBroadcast(
            context, (id * 10 + slot).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_ALARMS = "alarms"
    }
}
