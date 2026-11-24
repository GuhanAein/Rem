package app.rem.alarm

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.rem.RemApplication
import app.rem.core.model.AlertStyle
import app.rem.core.model.Reminder
import app.rem.core.model.Status
import app.rem.core.scheduling.ReminderPlanner
import app.rem.core.scheduling.SnoozePreset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderServiceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>() as RemApplication
    private val c get() = app.container
    private val alarmManager get() = app.getSystemService(AlarmManager::class.java)
    private val zone = ZoneId.systemDefault()
    private val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

    @Before fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun scheduled() = shadowOf(alarmManager).scheduledAlarms
    private fun millis(t: LocalDateTime) = ReminderPlanner.toInstant(t, zone).toEpochMilli()

    @Test fun saveSchedulesExactAlarmAtEarliestAlert() = runBlocking {
        val due = now.plusDays(2)
        c.service.save(Reminder(title = "Pay rent", dueAt = due, alertOffsets = listOf(1440, 0)))
        val alarms = scheduled()
        assertEquals(1, alarms.size)
        assertEquals(millis(due.minusDays(1)), alarms[0].triggerAtMs)
    }

    @Test fun alarmStyleUsesAlarmClock() = runBlocking {
        c.service.save(Reminder(title = "Pill", dueAt = now.plusHours(1), alertStyle = AlertStyle.ALARM))
        assertNotNull(alarmManager.nextAlarmClock)
    }

    @Test fun fallsBackToInexactWhenExactDenied() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        c.service.save(Reminder(title = "Water", dueAt = now.plusHours(3)))
        assertEquals(1, scheduled().size)
    }

    @Test fun completingOneTimeMarksDoneAndCancels() = runBlocking {
        val id = c.service.save(Reminder(title = "Call", dueAt = now.plusHours(2)))
        c.service.complete(id)
        assertEquals(Status.DONE, c.repository.get(id)!!.status)
        assertTrue(scheduled().isEmpty())
    }

    @Test fun completingRecurringAdvancesAndReschedules() = runBlocking {
        val due = now.plusHours(2)
        val id = c.service.save(Reminder(title = "Vitamins", dueAt = due, recurrence = "FREQ=DAILY"))
        c.service.complete(id)
        val after = c.repository.get(id)!!
        assertEquals(Status.ACTIVE, after.status)
        assertEquals(due.plusDays(1), after.dueAt)
        assertEquals(millis(due.plusDays(1)), scheduled().single().triggerAtMs)
    }

    @Test fun snoozeSchedulesAtSnoozeTime() = runBlocking {
        val id = c.service.save(Reminder(title = "Stretch", dueAt = now.minusMinutes(5)))
        c.service.snooze(id, SnoozePreset.MIN_15)
        val snoozedUntil = c.repository.get(id)!!.snoozedUntil!!
        assertEquals(snoozedUntil.toEpochMilli(), scheduled().single().triggerAtMs)
    }

    @Test fun rescheduleAllRestoresAlarmsAfterReboot() = runBlocking {
        val a = c.service.save(Reminder(title = "A", dueAt = now.plusHours(1)))
        val b = c.service.save(Reminder(title = "B", dueAt = now.plusHours(2)))
        c.scheduler.cancel(a)
        c.scheduler.cancel(b)
        assertTrue(scheduled().isEmpty())
        c.service.rescheduleAll()
        assertEquals(2, scheduled().size)
    }

    @Test fun firingPostsNotificationAndSchedulesNag() = runBlocking {
        val id = c.service.save(Reminder(title = "Insulin", dueAt = now.minusSeconds(10), nagMinutes = 5))
        AlarmReceiver().handle(app, id, 0)
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        assertEquals(1, nm.allNotifications.size)
        val nag = scheduled().single()
        assertTrue(nag.triggerAtMs > System.currentTimeMillis() + 4 * 60_000)
    }

    @Test fun deleteRemovesReminderAndAlarm() = runBlocking {
        val id = c.service.save(Reminder(title = "Gone", dueAt = now.plusHours(1)))
        c.service.delete(id)
        assertNull(c.repository.get(id))
        assertTrue(scheduled().isEmpty())
    }

    @Test fun bootReceiverIsRegisteredForTimeChanges() {
        val receivers = app.packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_TIME_CHANGED).setPackage(app.packageName), 0)
        assertTrue(receivers.any { it.activityInfo.name == BootReceiver::class.java.name })
    }
}
