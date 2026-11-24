package app.rem.jobs

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.rem.RemApplication
import app.rem.capture.Captured
import app.rem.capture.CapturedNote
import app.rem.capture.SmsBills
import app.rem.core.life.DetectedBill
import app.rem.core.life.Templates
import app.rem.core.model.Category
import app.rem.core.model.Reminder
import app.rem.core.model.Space
import app.rem.core.spaces.Wish
import app.rem.core.spaces.WishStatus
import app.rem.data.Settings
import app.rem.data.ThemeMode
import app.rem.ui.screens.applyTemplate
import app.rem.widget.workNext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LifeJobsTest {
    private val app = ApplicationProvider.getApplicationContext<RemApplication>()
    private val c get() = app.container
    private val nm get() = app.getSystemService(NotificationManager::class.java)

    @org.junit.Before fun grant() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun bedtimeSummaryOffersToMoveWhatsLeft() = runBlocking {
        val now = LocalDateTime.now()
        val missedId = c.service.save(Reminder(title = "Call the bank", dueAt = now.minusHours(2)))
        c.service.save(Reminder(title = "Tomorrow thing", dueAt = now.toLocalDate().plusDays(1).atTime(10, 0)))
        LifeJobs.runBedtime(app)
        val posted = shadowOf(nm).allNotifications.firstOrNull { it.extras.getString("android.title")?.contains("left from today") == true }
        assertNotNull(posted)
        assertEquals("Move it to tomorrow", posted!!.actions.single().title)

        assertEquals(1, LifeJobs.moveMissed(app))
        val moved = c.repository.get(missedId)!!
        assertEquals(now.toLocalDate().plusDays(1), moved.dueAt!!.toLocalDate())
        assertEquals(now.minusHours(2).toLocalTime().withNano(0), moved.dueAt!!.toLocalTime().withNano(0))
    }

    @Test fun bedtimeSummaryCanBeTurnedOff() = runBlocking {
        c.settings.update { it.copy(bedtimeSummary = false) }
        c.service.save(Reminder(title = "Missed", dueAt = LocalDateTime.now().minusHours(1)))
        LifeJobs.runBedtime(app)
        assertTrue(shadowOf(nm).allNotifications.none { it.extras.getString("android.title")?.contains("left from today") == true })
        c.settings.update { it.copy(bedtimeSummary = true) }
    }

    @Test fun waterReminderCountsGlassesAndStopsAtGoal() {
        c.settings.update { it.copy(waterEnabled = true, waterGlasses = 2) }
        LifeJobs.runWater(app)
        assertTrue(shadowOf(nm).allNotifications.any { it.extras.getString("android.text") == "0 of 2 glasses today" })
        LifeJobs.drank(app)
        LifeJobs.drank(app)
        assertEquals(2, c.water.today())
        nm.cancelAll()
        LifeJobs.runWater(app)
        assertTrue(shadowOf(nm).allNotifications.none { it.extras.getString("android.title") == "Time for some water" })
        c.settings.update { it.copy(waterEnabled = false) }
    }

    @Test fun stillWantItButtonsAnswerTheWish() = runBlocking {
        val id = c.spaces.saveWish(Wish(title = "Headphones", addedOn = LocalDate.now().minusDays(31), askOn = LocalDate.now().minusDays(1)))
        c.spaces.wishlistNudges()
        val n = shadowOf(nm).allNotifications.first { it.extras.getString("android.title") == "Still want Headphones?" }
        assertEquals(listOf("Keep", "Bought", "Let go"), n.actions.map { it.title.toString() })
        LifeJobs.answerWish(app, id, "BOUGHT")
        assertEquals(WishStatus.BOUGHT, c.database.wishes().get(id)!!.toModel().status)
    }

    @Test fun countdownIsPinnedUntilDone() = runBlocking {
        val id = c.service.save(Reminder(title = "Flight", dueAt = LocalDateTime.now().plusHours(5), extras = mapOf(Countdown.PIN to "true")))
        Countdown.sync(app)
        assertTrue(nm.activeNotifications.any { it.tag == "countdown" && it.id == id.toInt() })
        c.service.complete(id)
        Countdown.sync(app)
        assertTrue(nm.activeNotifications.none { it.tag == "countdown" })
    }

    @Test fun smsBillBecomesReminderOnce() = runBlocking {
        val b = DetectedBill("Airtel", 599.0, LocalDate.now().plusDays(4), "Airtel bill Rs 599 due")
        val id = SmsBills.add(app, b)
        assertNotNull(id)
        val r = c.repository.get(id!!)!!
        assertEquals(Category.BILL, r.category)
        assertEquals("₹599", r.amount)
        assertNull(SmsBills.add(app, b))
    }

    @Test fun templatesAddRemindersOrAList() = runBlocking {
        val before = c.repository.allActive().size
        assertEquals(5, applyTemplate(app, Templates.byId("bills")!!))
        assertEquals(before + 5, c.repository.allActive().size)
        assertEquals(12, applyTemplate(app, Templates.byId("packing")!!))
        val list = c.database.shopping().lists().first { it.name == "Travel packing" }
        assertEquals(12, c.database.shopping().items(list.id).size)
    }

    @Test fun capturedNotificationsSkipOtpsAndKeepTheLatest() {
        assertFalse(Captured.accept("com.bank", app.packageName, false, false, "Bank", "Your OTP is 123456"))
        assertFalse(Captured.accept(app.packageName, app.packageName, false, false, "Rem", "x"))
        assertTrue(Captured.accept("com.whatsapp", app.packageName, false, false, "Ravi", "Send the deck tomorrow"))
        Captured.clear(app)
        Captured.add(app, CapturedNote("k1", "WhatsApp", "com.whatsapp", "Ravi", "Send the deck tomorrow", 1L))
        Captured.add(app, CapturedNote("k2", "Gmail", "com.google.android.gm", "Invoice", "Due on Friday", 2L))
        val items = Captured.load(app)
        assertEquals(listOf("k2", "k1"), items.map { it.key })
        assertEquals("Ravi: Send the deck tomorrow", items[1].asReminderText)
        Captured.remove(app, "k2")
        assertEquals(1, Captured.load(app).size)
    }

    @Test fun workWidgetShowsDoingFirstAndSkipsWaiting() {
        val now = LocalDateTime.now()
        val list = listOf(
            Reminder(id = 1, title = "Todo later", space = Space.WORK, workStatus = "TODO", dueAt = now.plusDays(2)),
            Reminder(id = 2, title = "Doing", space = Space.WORK, workStatus = "DOING"),
            Reminder(id = 3, title = "Waiting", space = Space.WORK, workStatus = "WAITING"),
            Reminder(id = 4, title = "Personal", dueAt = now),
            Reminder(id = 5, title = "Todo soon", space = Space.WORK, workStatus = "TODO", dueAt = now.plusHours(1)),
        )
        assertEquals(listOf("Doing", "Todo soon", "Todo later"), workNext(list, now).map { it.title })
    }

    @Test fun waterLogResetsEachDay() {
        val prefs = app.getSharedPreferences("rem_water", Context.MODE_PRIVATE)
        prefs.edit().putString("date", LocalDate.now().minusDays(1).toString()).putInt("count", 6).apply()
        assertEquals(0, c.water.today())
        assertEquals(1, c.water.add())
    }

    @Test fun lightThemeSettingPersists() {
        c.settings.update { it.copy(theme = ThemeMode.LIGHT) }
        assertEquals(ThemeMode.LIGHT, Settings(app).value.theme)
        c.settings.update { it.copy(theme = ThemeMode.DARK) }
    }
}
