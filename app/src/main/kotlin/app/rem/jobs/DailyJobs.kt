package app.rem.jobs

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.rem.R
import app.rem.backup.Backup
import app.rem.container
import app.rem.core.insights.LogEvent
import app.rem.core.insights.Nudges
import app.rem.core.insights.Subscriptions
import app.rem.core.model.Category
import app.rem.core.model.Kind
import app.rem.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Once-a-day work: the morning card, people nudges, accountability, and the nightly backup. */
object DailyJobs {
    const val ACTION_MORNING = "app.rem.daily.MORNING"
    const val ACTION_NIGHTLY = "app.rem.daily.NIGHTLY"
    private const val CHANNEL = "daily"

    fun schedule(context: Context) {
        val s = context.container.settings.value
        set(context, ACTION_MORNING, s.morningCardTime, 9101)
        set(context, ACTION_NIGHTLY, LocalTime.of(2, 30), 9102)
    }

    private fun set(context: Context, action: String, at: LocalTime, code: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()
        val next = now.toLocalDate().atTime(at).let { if (it.isAfter(now)) it else it.plusDays(1) }
        val pi = PendingIntent.getBroadcast(
            context, code, Intent(context, DailyReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // A few minutes of flexibility is fine for a summary and saves battery.
        am.setWindow(AlarmManager.RTC_WAKEUP, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 10 * 60_000L, pi)
    }

    /** Everything the morning card shows; also used by the in-app Morning card screen. */
    data class Morning(
        val today: List<String>,
        val birthdays: List<String>,
        val bills: List<String>,
        val trials: List<String>,
        val nudges: List<String>,
        val gifts: List<String>,
        val reliability: String? = null,
    ) {
        val isEmpty get() = today.isEmpty() && birthdays.isEmpty() && bills.isEmpty() && trials.isEmpty() && nudges.isEmpty()
    }

    suspend fun morning(context: Context, date: LocalDate = LocalDate.now()): Morning {
        val c = context.container
        val active = c.repository.allActive().filter { !it.isPrivate }
        val today = active.filter { it.dueAt?.toLocalDate()?.let { d -> !d.isAfter(date) } == true }.sortedBy { it.dueAt }.map { it.title }
        val soon = active.filter { it.dueAt?.toLocalDate()?.let { d -> !d.isBefore(date) && !d.isAfter(date.plusDays(7)) } == true }
        val people = c.database.people().all().map { it.toModel() }
        return Morning(
            today = today,
            birthdays = soon.filter { it.category == Category.BIRTHDAY }.map { it.title },
            bills = soon.filter { (it.category == Category.BILL || it.kind == Kind.SUBSCRIPTION) && !it.dueAt!!.toLocalDate().isAfter(date.plusDays(3)) }
                .map { listOfNotNull(it.title, it.amount).joinToString(" · ") },
            trials = active.filter { Subscriptions.trialEndingSoon(it, date) }.map { "${it.title}: trial ends soon. Cancel if you don't want it." },
            nudges = people.filter { Nudges.isDue(it, date) }.map { Nudges.message(it, date) },
            gifts = people.filter { Nudges.giftsDue(it, date) }.map { "Gift ideas for ${it.name}: ${it.giftIdeas.joinToString(", ")}" },
            reliability = app.rem.core.insights.Reliability.report(
                c.database.fired().since(System.currentTimeMillis() - 7L * 24 * 3600 * 1000).map { it.toRecord() },
            ).takeIf { !it.healthy }?.let { "Your phone delayed ${it.late} alarms this week. Open Rem \u203A Settings \u203A Reliability to fix it." },
        )
    }

    internal suspend fun runMorning(context: Context) {
        val c = context.container
        if (!c.settings.value.morningCardEnabled || !c.notifier.canPost()) return
        val m = morning(context)
        if (m.isEmpty) return
        ensureChannel(context)
        val lines = buildList {
            if (m.today.isNotEmpty()) add("${m.today.size} for today: ${m.today.take(3).joinToString(", ")}")
            m.birthdays.forEach { add("🎂 $it") }
            m.bills.forEach { add("Due soon: $it") }
            addAll(m.trials)
            addAll(m.nudges)
            addAll(m.gifts)
            m.reliability?.let(::add)
        }
        val open = PendingIntent.getActivity(
            context, 9201, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_MORNING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD9BC83.toInt())
            .setContentTitle("Good morning")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach(s::addLine) })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(9201, n)
        runAccountability(context)
    }

    /** If a habit was skipped 3+ times this week, offer to tell your accountability buddy. */
    private suspend fun runAccountability(context: Context) {
        val c = context.container
        val s = c.settings.value
        if (s.accountabilityPhone.isBlank()) return
        val since = LocalDate.now().minusDays(7).atStartOfDay().toString()
        val skipped = c.database.log().since(since).map { it.toEvent() to it.title }
            .filter { it.first.type == LogEvent.Type.SKIPPED }.groupBy { it.first.reminderId }
            .filter { it.value.size >= 3 }
        val (_, events) = skipped.entries.firstOrNull() ?: return
        val title = events.first().second
        val body = "Hey ${s.accountabilityName.ifBlank { "" }}, I've skipped \"$title\" ${events.size} times this week. Nudge me?".replace("Hey ,", "Hey,")
        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${s.accountabilityPhone}")).putExtra("sms_body", body)
        val pi = PendingIntent.getActivity(context, 9202, sms, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD9BC83.toInt())
            .setContentTitle("Need a nudge with “$title”?")
            .setContentText("Skipped ${events.size} times this week. Let ${s.accountabilityName.ifBlank { "your buddy" }} know.")
            .addAction(0, "Message ${s.accountabilityName.ifBlank { "buddy" }}", pi)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(9202, n)
    }

    internal suspend fun runNightly(context: Context) {
        val c = context.container
        if (c.settings.value.nightlyBackup) runCatching { Backup.nightly(context) }
        c.database.fired().prune(System.currentTimeMillis() - 60L * 24 * 3600 * 1000)
        c.service.rescheduleAll()
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Morning card & nudges", NotificationManager.IMPORTANCE_DEFAULT))
    }
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    DailyJobs.ACTION_MORNING -> DailyJobs.runMorning(context)
                    DailyJobs.ACTION_NIGHTLY -> DailyJobs.runNightly(context)
                }
            } finally {
                DailyJobs.schedule(context)
                pending.finish()
            }
        }
    }
}
