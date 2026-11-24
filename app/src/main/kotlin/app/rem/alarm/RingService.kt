package app.rem.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.rem.R
import app.rem.container
import app.rem.core.model.Reminder
import app.rem.core.scheduling.SnoozePreset
import app.rem.ui.MainActivity
import app.rem.ui.format.describeDue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Rings an alarm-style reminder like a real alarm clock.
 *
 * A foreground service is the only reliable way to keep a looping alarm sound going on modern
 * Android: a full-screen notification alone opens only when the phone is locked, and on
 * Android 14+ it may not be allowed at all. This service plays on the ALARM audio stream (so it
 * sounds even in silent/vibrate mode, at alarm volume), vibrates, shows the full-screen alarm when
 * locked and a heads-up with Done/Snooze when unlocked, and stops after a minute or when you act.
 */
class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ID, -1) ?: -1
        val isNag = intent?.getBooleanExtra(EXTRA_NAG, false) ?: false
        if (id < 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Go foreground immediately (Android requires it within seconds).
        goForeground(id, placeholder(id))
        scope.launch {
            val reminder = runCatching { container.repository.get(id) }.getOrNull()
            if (reminder == null) {
                stopRinging()
                return@launch
            }
            currentId = id
            goForeground(id, notification(reminder, isNag))
            startRinging(reminder)
        }
        return START_NOT_STICKY
    }

    private fun goForeground(id: Long, n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        runCatching { ServiceCompat.startForeground(this, notificationId(id), n, type) }
            .onFailure {
                // If the system refuses the exempt type, fall back to media playback.
                if (Build.VERSION.SDK_INT >= 29) {
                    runCatching { ServiceCompat.startForeground(this, notificationId(id), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) }
                }
            }
    }

    private fun startRinging(r: Reminder) {
        stopSound()
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rem:ring")?.apply { acquire(RING_MILLIS + 5_000) }

        val custom = container.settings.value.sounds[r.category]?.let(Uri::parse)
        val uri = custom
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@RingService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

        vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))

        // Ring for a minute, then go quiet; the nag (if set) brings it back later.
        handler.postDelayed({ stopRinging(keepNotification = true) }, RING_MILLIS)
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    /** Stops the sound. When the minute runs out we keep a regular notification so you still see it. */
    private fun stopRinging(keepNotification: Boolean = false) {
        stopSound()
        val id = currentId
        ServiceCompat.stopForeground(this, if (keepNotification) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (keepNotification && id >= 0) {
            scope.launch {
                container.repository.get(id)?.let { container.notifier.show(it, isNag = false, ring = false, textOverride = "Missed alarm · " + describeDue(it.dueAt)) }
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- Notification

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while an alarm-style reminder is ringing"
                setSound(null, null) // the service plays the sound itself
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun placeholder(id: Long): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reminder")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(open(id))
            .build()
    }

    private fun notification(r: Reminder, isNag: Boolean): Notification {
        ensureChannel()
        val id = r.id
        val fullScreen = PendingIntent.getActivity(
            this, notificationId(id), AlarmActivity.intent(this, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD9BC83.toInt())
            .setContentTitle(if (r.isPrivate) "Private reminder" else r.title)
            .setContentText(listOfNotNull(if (isNag) "Still waiting" else null, describeDue(r.dueAt), r.amount).joinToString(" · "))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Done", action(ActionReceiver.ACTION_DONE, id, 1))
            .addAction(0, "Snooze 5 min", action(ActionReceiver.ACTION_SNOOZE, id, 2, SnoozePreset.MIN_5))
            .addAction(0, "Stop", action(ActionReceiver.ACTION_DISMISS, id, 4))
            .build()
    }

    private fun open(id: Long) = PendingIntent.getActivity(
        this, notificationId(id), Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(action: String, id: Long, slot: Int, snooze: SnoozePreset? = null): PendingIntent {
        val i = Intent(this, ActionReceiver::class.java).setAction(action).putExtra(AlarmScheduler.EXTRA_ID, id)
        snooze?.let { i.putExtra(ActionReceiver.EXTRA_SNOOZE, it.name) }
        return PendingIntent.getBroadcast(this, (id * 10 + slot).toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL = "ringing"
        const val EXTRA_NAG = "isNag"
        const val RING_MILLIS = 60_000L

        /** Same id as the reminder's normal notification, so the ring replaces it. */
        fun notificationId(reminderId: Long) = reminderId.toInt()

        fun start(context: Context, reminderId: Long, isNag: Boolean) {
            val i = Intent(context, RingService::class.java).putExtra(AlarmScheduler.EXTRA_ID, reminderId).putExtra(EXTRA_NAG, isNag)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RingService::class.java))
        }
    }
}
