package app.rem.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.rem.data.AppSettings
import java.time.Instant

/**
 * "Don't interrupt me": decides whether an alarm should be held, and until when.
 *  - Panic snooze: everything waits until it ends.
 *  - On a call (phone or VoIP): retry in 5 minutes.
 *  - In a calendar meeting (opt-in, needs calendar permission): wait until it ends.
 */
object DeliveryGuard {
    fun holdUntil(context: Context, settings: AppSettings, now: Instant): Instant? {
        settings.panicUntil?.takeIf { it.isAfter(now) }?.let { return it }
        if (settings.respectCalls && inCall(context)) return now.plusSeconds(5 * 60)
        if (settings.respectMeetings) meetingEnd(context, now)?.let { return it.plusSeconds(60) }
        return null
    }

    private fun inCall(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    /** End of a busy calendar event happening right now, if any. */
    private fun meetingEnd(context: Context, now: Instant): Instant? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, now.toEpochMilli() - 1)
            android.content.ContentUris.appendId(it, now.toEpochMilli() + 1)
        }.build()
        val projection = arrayOf(CalendarContract.Instances.END, CalendarContract.Instances.AVAILABILITY, CalendarContract.Instances.ALL_DAY)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                var end: Long? = null
                while (c.moveToNext()) {
                    val busy = c.getInt(1) == CalendarContract.Instances.AVAILABILITY_BUSY
                    val allDay = c.getInt(2) == 1
                    if (busy && !allDay) end = maxOf(end ?: 0L, c.getLong(0))
                }
                end?.let(Instant::ofEpochMilli)
            }
        }.getOrNull()
    }
}
