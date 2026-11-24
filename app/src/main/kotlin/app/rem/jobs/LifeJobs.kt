package app.rem.jobs

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.rem.R
import app.rem.container
import app.rem.core.insights.LogEvent
import app.rem.core.life.Bedtime
import app.rem.core.life.WaterPlan
import app.rem.core.spaces.WishStatus
import app.rem.data.ReminderService
import app.rem.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Bedtime summary, water reminders and the wishlist "Still want it?" buttons. */
object LifeJobs {
    const val ACTION_BEDTIME = "app.rem.life.BEDTIME"
    const val ACTION_MOVE_MISSED = "app.rem.life.MOVE_MISSED"
    const val ACTION_WATER = "app.rem.life.WATER"
    const val ACTION_DRANK = "app.rem.life.DRANK"
    const val ACTION_WISH = "app.rem.life.WISH"
    const val EXTRA_WISH = "wishId"
    const val EXTRA_STATUS = "status"

    const val ID_BEDTIME = 9401
    const val ID_WATER = 9402
    private const val CHANNEL = "life"

    fun schedule(context: Context) {
        val s = context.container.settings.value
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val bedtimePi = pi(context, ACTION_BEDTIME, 9411)
        if (s.bedtimeSummary) {
            // Half an hour before bed, so there's still time to deal with anything.
            am.setWindow(AlarmManager.RTC_WAKEUP, millis(nextAt(s.bedtime.minusMinutes(30))), 5 * 60_000L, bedtimePi)
        } else am.cancel(bedtimePi)
        scheduleWater(context)
    }

    fun scheduleWater(context: Context) {
        val c = context.container
        val s = c.settings.value
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val waterPi = pi(context, ACTION_WATER, 9412)
        if (!s.waterEnabled) {
            am.cancel(waterPi)
            NotificationManagerCompat.from(context).cancel(ID_WATER)
            return
        }
        val now = LocalDateTime.now(c.clock)
        val todayNext = WaterPlan.next(now.toLocalTime(), s.wakeTime, s.bedtime, s.waterGlasses, c.water.today())
            ?.let { now.toLocalDate().atTime(it) }?.takeIf { it.isAfter(now) }
        val at = todayNext ?: now.toLocalDate().plusDays(1).atTime(WaterPlan.times(s.wakeTime, s.bedtime, s.waterGlasses).first())
        am.setWindow(AlarmManager.RTC_WAKEUP, millis(at), 3 * 60_000L, waterPi)
    }

    internal suspend fun runBedtime(context: Context) {
        val c = context.container
        if (!c.settings.value.bedtimeSummary || !c.notifier.canPost()) return
        val now = LocalDateTime.now(c.clock)
        val from = now.toLocalDate().atStartOfDay().format(ReminderService.LOG_TIME)
        val done = c.database.log().since(from).count { it.type == LogEvent.Type.DONE.name }
        val summary = Bedtime.summary(c.repository.allActive(), done, now)
        if (summary.isEmpty) return
        val b = builder(context, summary.title, summary.body.ifBlank { "Sleep well." })
            .setContentIntent(open(context, null, ID_BEDTIME))
        if (summary.missed.isNotEmpty()) {
            b.addAction(0, "Move ${if (summary.missed.size == 1) "it" else "all"} to tomorrow", pi(context, ACTION_MOVE_MISSED, 9413))
            b.setStyle(NotificationCompat.InboxStyle().also { st ->
                summary.missed.take(5).forEach { st.addLine(it.title) }
                if (summary.missed.size > 5) st.setSummaryText("+${summary.missed.size - 5} more")
            })
        }
        notify(context, ID_BEDTIME, b)
    }

    internal suspend fun moveMissed(context: Context): Int {
        val c = context.container
        val now = LocalDateTime.now(c.clock)
        val missed = Bedtime.summary(c.repository.allActive(), 0, now).missed
        missed.forEach { c.service.save(Bedtime.moveToTomorrow(it, now)) }
        NotificationManagerCompat.from(context).cancel(ID_BEDTIME)
        return missed.size
    }

    internal fun runWater(context: Context) {
        val c = context.container
        val s = c.settings.value
        if (!s.waterEnabled || !c.notifier.canPost()) return
        val drunk = c.water.today()
        if (drunk >= s.waterGlasses) return
        val b = builder(context, "Time for some water", "$drunk of ${s.waterGlasses} glasses today")
            .setContentIntent(open(context, MainActivity.ACTION_LIFE, ID_WATER))
            .addAction(0, "Drank a glass", pi(context, ACTION_DRANK, 9414))
            .setProgress(s.waterGlasses, drunk, false)
            .setOnlyAlertOnce(false)
            .setTimeoutAfter(90 * 60_000L)
        notify(context, ID_WATER, b)
    }

    internal fun drank(context: Context) {
        context.container.water.add()
        NotificationManagerCompat.from(context).cancel(ID_WATER)
        CoroutineScope(Dispatchers.Default).launch { app.rem.widget.Widgets.refresh(context) }
    }

    internal suspend fun answerWish(context: Context, id: Long, status: String?) {
        val spaces = context.container.spaces
        val w = context.container.database.wishes().get(id)?.toModel() ?: return
        when (status) {
            "KEEP" -> spaces.keepWish(w)
            else -> runCatching { WishStatus.valueOf(status.orEmpty()) }.getOrNull()?.let { spaces.setWishStatus(w, it) }
        }
        NotificationManagerCompat.from(context).cancel(8100 + id.toInt())
    }

    /** The three answers to "Still want it?" as notification buttons. */
    fun wishActions(context: Context, id: Long): List<Pair<String, PendingIntent>> = listOf("Keep" to "KEEP", "Bought" to "BOUGHT", "Let go" to "LET_GO").mapIndexed { i, (label, st) ->
        label to PendingIntent.getBroadcast(
            context, (90_000 + id * 4 + i).toInt(),
            Intent(context, LifeReceiver::class.java).setAction(ACTION_WISH).putExtra(EXTRA_WISH, id).putExtra(EXTRA_STATUS, st),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun builder(context: Context, title: String, text: String): NotificationCompat.Builder {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Bedtime & water", NotificationManager.IMPORTANCE_DEFAULT))
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD9BC83.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
    }

    private fun notify(context: Context, id: Int, b: NotificationCompat.Builder) {
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    private fun open(context: Context, action: String?, code: Int) = PendingIntent.getActivity(
        context, code, Intent(context, MainActivity::class.java).apply { action?.let { this.action = it } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun pi(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        context, code, Intent(context, LifeReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun nextAt(t: LocalTime): LocalDateTime {
        val now = LocalDateTime.now()
        return now.toLocalDate().atTime(t).let { if (it.isAfter(now)) it else it.plusDays(1) }
    }

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

class LifeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    LifeJobs.ACTION_BEDTIME -> LifeJobs.runBedtime(context)
                    LifeJobs.ACTION_MOVE_MISSED -> LifeJobs.moveMissed(context)
                    LifeJobs.ACTION_WATER -> LifeJobs.runWater(context)
                    LifeJobs.ACTION_DRANK -> LifeJobs.drank(context)
                    LifeJobs.ACTION_WISH -> LifeJobs.answerWish(context, intent.getLongExtra(LifeJobs.EXTRA_WISH, -1), intent.getStringExtra(LifeJobs.EXTRA_STATUS))
                }
            } finally {
                LifeJobs.schedule(context)
                pending.finish()
            }
        }
    }
}

/** "Pin to lock screen": an ongoing notification counting down to the reminder. */
object Countdown {
    const val PIN = "countdown"
    private const val CHANNEL = "countdown"
    private const val TAG = "countdown"

    suspend fun sync(context: Context) {
        val c = context.container
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val now = LocalDateTime.now(c.clock)
        val pinned = c.repository.allActive().filter { it.extras[PIN] == "true" && it.dueAt?.isAfter(now) == true }
        nm.activeNotifications.filter { it.tag == TAG && it.id.toLong() !in pinned.map { p -> p.id } }.forEach { nm.cancel(TAG, it.id) }
        if (pinned.isEmpty() || !c.notifier.canPost()) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lock-screen countdowns", NotificationManager.IMPORTANCE_LOW))
        pinned.forEach { r ->
            val due = r.dueAt!!
            val at = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val open = PendingIntent.getActivity(
                context, (70_000 + r.id).toInt(), Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ID, r.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFD9BC83.toInt())
                .setContentTitle(r.title)
                .setContentText(app.rem.ui.format.formatDay(due.toLocalDate()) + " · " + app.rem.ui.format.formatTime(due.toLocalTime()))
                .setWhen(at)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setTimeoutAfter((at - System.currentTimeMillis()).coerceAtLeast(60_000L))
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(TAG, r.id.toInt(), n)
        }
    }
}
