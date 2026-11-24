package app.rem.alarm

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import app.rem.RemApplication
import app.rem.core.model.AlertStyle
import app.rem.core.model.Reminder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDateTime

/** "Ring as alarm" must actually ring, screen on or off, with or without full-screen permission. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmRingTest {
    private val app = ApplicationProvider.getApplicationContext<Application>() as RemApplication
    private val c get() = app.container

    @Before fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun alarmStyleReminderStartsTheRingingService() = runBlocking {
        val id = c.service.save(Reminder(title = "Take insulin", dueAt = LocalDateTime.now().minusSeconds(5), alertStyle = AlertStyle.ALARM))
        AlarmReceiver().handle(app, id, 0)
        val started = shadowOf(app).nextStartedService
        assertNotNull("A foreground ringing service must start", started)
        assertEquals(RingService::class.java.name, started.component?.className)
        assertEquals(id, started.getLongExtra(AlarmScheduler.EXTRA_ID, -1))
    }

    @Test fun earlyHeadsUpDoesNotRing() = runBlocking {
        val id = c.service.save(Reminder(title = "Tax filing", dueAt = LocalDateTime.now().plusDays(3), alertStyle = AlertStyle.ALARM))
        AlarmReceiver().handle(app, id, 0, isDue = false)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun normalRemindersDoNotRing() = runBlocking {
        val id = c.service.save(Reminder(title = "Buy milk", dueAt = LocalDateTime.now().minusSeconds(5)))
        AlarmReceiver().handle(app, id, 0)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun doneFromNotificationStopsRinging() = runBlocking {
        val id = c.service.save(Reminder(title = "Take insulin", dueAt = LocalDateTime.now().minusSeconds(5), alertStyle = AlertStyle.ALARM))
        ActionReceiver().handle(app, ActionReceiver.ACTION_DONE, id, null)
        val stopped = shadowOf(app).nextStoppedService
        assertNotNull("Completing must stop the ringing", stopped)
        assertEquals(RingService::class.java.name, stopped.component?.className)
    }

    @Test fun exactAlarmsAreGrantedWithoutAPrompt() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue("USE_EXACT_ALARM keeps reminders on time on Android 13+", info.requestedPermissions!!.contains("android.permission.USE_EXACT_ALARM"))
        assertTrue(info.requestedPermissions!!.contains("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"))
    }
}
