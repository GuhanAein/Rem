package app.rem.alarm

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import app.rem.RemApplication
import app.rem.backup.Backup
import app.rem.core.insights.LogEvent
import app.rem.core.model.Category
import app.rem.core.model.Kind
import app.rem.core.model.Reminder
import app.rem.core.model.Trigger
import app.rem.core.scheduling.SnoozePreset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeaturesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>() as RemApplication
    private val c get() = app.container
    private val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

    @Before fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        c.settings.update { it.copy(panicUntil = null) }
    }

    private fun reminderAlarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.filter { a ->
        a.operation?.let { shadowOf(it).savedIntent.action } == AlarmScheduler.ACTION_FIRE
    }

    @Test fun completeSkipAndSnoozeAreLogged() = runBlocking {
        val id = c.service.save(Reminder(title = "Stretch", dueAt = now.minusMinutes(5), recurrence = "FREQ=DAILY"))
        c.service.snooze(id, SnoozePreset.MIN_15)
        c.service.skip(id)
        c.service.complete(id)
        val log = c.database.log().forReminder(id).map { it.toEvent() }
        assertEquals(setOf(LogEvent.Type.DONE, LogEvent.Type.SKIPPED, LogEvent.Type.SNOOZED), log.map { it.type }.toSet())
        assertTrue(log.first { it.type == LogEvent.Type.SNOOZED }.minutes in 14..21)
    }

    @Test fun panicSnoozeHoldsAlarmsUntilItEnds() = runBlocking {
        val id = c.service.save(Reminder(title = "Pill", dueAt = now.minusSeconds(5)))
        c.service.panicSnooze(2)
        AlarmReceiver().handle(app, id, 0)
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        assertEquals(0, nm.allNotifications.size)
        val held = reminderAlarms().single()
        assertTrue(held.triggerAtMs >= System.currentTimeMillis() + 115 * 60_000L)
    }

    @Test fun eventTriggersAreNotOnTheClockButWakeUpIs() = runBlocking {
        val charging = c.service.save(Reminder(title = "Set out clothes", trigger = Trigger.CHARGING_TONIGHT))
        assertTrue(reminderAlarms().none { shadowOf(it.operation).savedIntent.getLongExtra(AlarmScheduler.EXTRA_ID, -1) == charging })
        c.settings.update { it.copy(wakeTime = LocalTime.of(7, 0)) }
        val wake = c.service.save(Reminder(title = "Drink water", trigger = Trigger.WAKE_UP, recurrence = "FREQ=DAILY"))
        val due = c.repository.get(wake)!!.dueAt!!
        assertEquals(LocalTime.of(7, 0), due.toLocalTime())
        assertTrue(due.isAfter(LocalDateTime.now()))
    }

    @Test fun backupRoundTripRestoresEverything() = runBlocking {
        c.service.save(Reminder(title = "Netflix", kind = Kind.SUBSCRIPTION, amount = "₹649", recurrence = "FREQ=MONTHLY", dueAt = now.plusDays(3), extras = mapOf("trialEnd" to "2026-12-01")))
        val json = Backup.exportJson(app)
        val before = c.repository.allActive().size
        val restored = Backup.importJson(app, json)
        assertEquals(before, restored)
        val copy = c.repository.allActive().filter { it.title == "Netflix" }
        assertEquals(2, copy.size)
        assertTrue(copy.all { it.kind == Kind.SUBSCRIPTION && it.extras["trialEnd"] == "2026-12-01" })
    }

    @Test fun exportsProduceFiles() = runBlocking {
        c.service.save(Reminder(title = "Renew passport", dueAt = now.plusDays(30), category = Category.DOCUMENT))
        val ics = Backup.exportIcs(app)
        assertTrue(ics.readText().contains("SUMMARY:Renew passport"))
        val pdf = Backup.exportPdf(app)
        assertTrue(pdf.length() > 500 && pdf.readBytes().copyOfRange(0, 4).contentEquals("%PDF".toByteArray()))
        val nightly = Backup.nightly(app)
        assertNotNull(Backup.latestNightly(app))
        assertTrue(nightly.readText().contains("Renew passport"))
    }

    @Test fun choreNotificationSaysWhoseTurn() = runBlocking {
        val id = c.service.save(
            Reminder(title = "Take out the bins", kind = Kind.CHORE, dueAt = now.minusSeconds(5),
                extras = mapOf("members" to "Asha, Ravi", "rotationStart" to now.toLocalDate().toString())),
        )
        AlarmReceiver().handle(app, id, 0)
        val n = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Take out the bins · Asha's turn", n.extras.getString(android.app.Notification.EXTRA_TITLE))
    }

    @Test fun privateRemindersHideTheirTitle() = runBlocking {
        val id = c.service.save(Reminder(title = "Therapy", dueAt = now.minusSeconds(5), isPrivate = true))
        AlarmReceiver().handle(app, id, 0)
        val n = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Private reminder", n.extras.getString(android.app.Notification.EXTRA_TITLE))
    }

    @Test fun firedTimesAreRecorded() = runBlocking {
        val id = c.service.save(Reminder(title = "Call", dueAt = now.minusSeconds(5)))
        AlarmReceiver().handle(app, id, 0, scheduledAt = Instant.now().minusSeconds(600).toEpochMilli())
        val fired = c.database.fired().since(0).single()
        assertTrue(fired.firedAt - fired.scheduledAt >= 590_000)
    }
}
