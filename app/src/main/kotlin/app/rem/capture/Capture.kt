package app.rem.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.rem.R
import app.rem.container
import app.rem.core.life.BillSms
import app.rem.core.life.DetectedBill
import app.rem.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A notification from another app, kept so you can turn it into a reminder later. */
data class CapturedNote(val key: String, val app: String, val pkg: String, val title: String, val text: String, val at: Long) {
    /** What the reminder starts as: "Ravi: can you send the deck tomorrow?" */
    val asReminderText: String get() = listOf(title, text).filter { it.isNotBlank() }.joinToString(": ").take(280)
}

/**
 * Notification-to-reminder. Once you allow notification access, Rem keeps the last 40
 * notifications from other apps on the phone (never uploaded) so any of them becomes a
 * reminder with one tap.
 */
object Captured {
    private const val PREFS = "rem_captured"
    private const val MAX = 40
    private val _items = MutableStateFlow<List<CapturedNote>>(emptyList())
    val items: StateFlow<List<CapturedNote>> = _items

    fun load(context: Context): List<CapturedNote> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", "[]")
        val list = runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                CapturedNote(o.getString("k"), o.getString("a"), o.getString("p"), o.getString("t"), o.getString("x"), o.getLong("at"))
            }
        }.getOrDefault(emptyList())
        _items.value = list
        return list
    }

    fun add(context: Context, n: CapturedNote) {
        val list = (listOf(n) + load(context).filterNot { it.key == n.key || (it.title == n.title && it.text == n.text) }).take(MAX)
        save(context, list)
    }

    fun remove(context: Context, key: String) = save(context, load(context).filterNot { it.key == key })

    fun clear(context: Context) = save(context, emptyList())

    private fun save(context: Context, list: List<CapturedNote>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("k", it.key).put("a", it.app).put("p", it.pkg).put("t", it.title).put("x", it.text).put("at", it.at)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("items", a.toString()).apply()
        _items.value = list
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun accessSettings(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Apps whose notifications are noise for reminders. */
    private val skip = setOf("android", "com.android.systemui", "com.android.providers.downloads", "com.google.android.apps.photos")

    fun accept(pkg: String, own: String, ongoing: Boolean, groupSummary: Boolean, title: String, text: String): Boolean =
        pkg != own && pkg !in skip && !ongoing && !groupSummary && (title.isNotBlank() || text.isNotBlank()) &&
            !Regex("""\b(otp|one time password|verification code)\b""", RegexOption.IGNORE_CASE).containsMatchIn("$title $text")
}

class CaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val extras = n.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT))?.toString().orEmpty()
        val summary = n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0
        if (!Captured.accept(sbn.packageName, packageName, sbn.isOngoing, summary, title, text)) return
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        Captured.add(this, CapturedNote(sbn.key, app, sbn.packageName, title.take(120), text.take(400), sbn.postTime))
    }
}

/**
 * Bill detection from SMS. Off until you turn it on in Settings and allow SMS access.
 * Messages are read on the phone with [BillSms]'s pattern rules; nothing is uploaded.
 */
object SmsBills {
    const val ACTION_ADD = "app.rem.sms.ADD"
    private const val CHANNEL = "bills"

    fun canRead(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    fun canReceive(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    /** Bills found in the inbox over the last [days] days, newest first, one per biller and due date. */
    fun scanInbox(context: Context, days: Long = 45): List<DetectedBill> {
        if (!canRead(context)) return emptyList()
        val since = System.currentTimeMillis() - days * 24 * 3600 * 1000
        val out = mutableListOf<DetectedBill>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} DESC",
        )?.use { cur ->
            while (cur.moveToNext() && out.size < 200) {
                val received = Instant.ofEpochMilli(cur.getLong(2)).atZone(ZoneId.systemDefault()).toLocalDate()
                BillSms.parse(cur.getString(0).orEmpty(), cur.getString(1).orEmpty(), received)?.let(out::add)
            }
        }
        val today = LocalDate.now()
        return out.filter { it.dueOn == null || !it.dueOn!!.isBefore(today) }.distinctBy { it.biller.lowercase() to it.dueOn }
    }

    /** A detected bill becomes a reminder unless one for the same bill and day exists. */
    suspend fun add(context: Context, b: DetectedBill): Long? {
        val c = context.container
        val r = BillSms.toReminder(b, LocalDateTime.now(c.clock))
        val dup = c.repository.allActive().firstOrNull { it.title.equals(r.title, true) && it.dueAt?.toLocalDate() == r.dueAt?.toLocalDate() }
        if (dup != null) return null
        return c.service.save(r)
    }

    internal fun offer(context: Context, b: DetectedBill) {
        val prefs = context.getSharedPreferences("rem_sms", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen", emptySet()).orEmpty()
        if (b.key in seen) return
        prefs.edit().putStringSet("seen", (seen + b.key).toList().takeLast(200).toSet()).apply()
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Bills found in messages", NotificationManager.IMPORTANCE_DEFAULT))
        val id = 9500 + (b.key.hashCode() and 0xFFF)
        val add = PendingIntent.getBroadcast(
            context, id,
            Intent(context, SmsReceiver::class.java).setAction(ACTION_ADD)
                .putExtra("biller", b.biller).putExtra("amount", b.amount ?: -1.0).putExtra("due", b.dueOn?.toString()).putExtra("source", b.source.take(300)).putExtra("nid", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_BILLS), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = listOfNotNull(b.amount?.let { app.rem.core.insights.Money.format(it) }, b.dueOn?.let { "due ${app.rem.ui.format.formatDay(it)}" }).joinToString(", ")
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD9BC83.toInt())
            .setContentTitle("${b.biller} bill")
            .setContentText(text.ifBlank { "Found in your messages" })
            .addAction(0, "Remind me", add)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                if (!context.container.settings.value.smsBills) return
                val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
                val sender = msgs.firstOrNull()?.originatingAddress.orEmpty()
                val body = msgs.joinToString("") { it.messageBody.orEmpty() }
                BillSms.parse(sender, body, LocalDate.now())?.let { SmsBills.offer(context, it) }
            }
            SmsBills.ACTION_ADD -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val b = DetectedBill(
                            intent.getStringExtra("biller").orEmpty(),
                            intent.getDoubleExtra("amount", -1.0).takeIf { it >= 0 },
                            intent.getStringExtra("due")?.let(LocalDate::parse),
                            intent.getStringExtra("source").orEmpty(),
                        )
                        SmsBills.add(context, b)
                        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

/** Quick Settings tile: add a reminder from the notification shade. */
class QuickAddTile : android.service.quicksettings.TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = android.service.quicksettings.Tile.STATE_INACTIVE
            label = "Add reminder"
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_QUICK_ADD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 9600, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

fun captureComponent(context: Context) = ComponentName(context, CaptureService::class.java)
