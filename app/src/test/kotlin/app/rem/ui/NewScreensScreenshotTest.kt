package app.rem.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import app.rem.ui.theme.Rem
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.test.core.app.ApplicationProvider
import app.rem.RemApplication
import app.rem.core.insights.Person
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Kind
import app.rem.core.model.Reminder
import app.rem.core.model.TimeWindow
import app.rem.core.model.Trigger
import app.rem.data.LogEntity
import app.rem.data.ReminderService
import app.rem.ui.edit.EditContent
import app.rem.ui.edit.EditUiState
import app.rem.ui.edit.RepeatForm
import app.rem.ui.home.HomeContent
import app.rem.ui.home.SmartList
import app.rem.ui.home.buildHomeState
import app.rem.ui.screens.InsightsScreen
import app.rem.ui.screens.LifeScreen
import app.rem.ui.screens.MorningCard
import app.rem.ui.screens.PeopleScreen
import app.rem.ui.screens.SettingsScreen
import app.rem.ui.screens.savePerson
import app.rem.jobs.DailyJobs
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class NewScreensScreenshotTest {
    private val app = ApplicationProvider.getApplicationContext<RemApplication>()
    private val c get() = app.container
    private val out = "../docs/screenshots"
    private val today = LocalDate.now()

    private fun seedLife() = runBlocking {
        val s: ReminderService = c.service
        s.save(Reminder(title = "Netflix", kind = Kind.SUBSCRIPTION, category = Category.BILL, amount = "₹649", recurrence = "FREQ=MONTHLY", dueAt = today.plusDays(4).atTime(9, 0), extras = mapOf("trialEnd" to today.plusDays(2).toString())))
        s.save(Reminder(title = "Spotify", kind = Kind.SUBSCRIPTION, category = Category.BILL, amount = "₹119", recurrence = "FREQ=MONTHLY", dueAt = today.plusDays(12).atTime(9, 0)))
        s.save(Reminder(title = "Amazon Prime", kind = Kind.SUBSCRIPTION, category = Category.BILL, amount = "₹1,499", recurrence = "FREQ=YEARLY", dueAt = today.plusDays(90).atTime(9, 0)))
        s.save(Reminder(title = "Washing machine", kind = Kind.WARRANTY, category = Category.DOCUMENT, dueAt = today.plusDays(200).atTime(9, 0), extras = mapOf("product" to "Washing machine")))
        s.save(Reminder(title = "Car service", kind = Kind.CAR_SERVICE, category = Category.VEHICLE, extras = mapOf("intervalKm" to "5000", "lastServiceKm" to "20000", "firstOdometerKm" to "21000", "firstOdometerDate" to today.minusDays(10).toString(), "odometerKm" to "22000", "odometerDate" to today.toString())))
        s.save(Reminder(title = "Monstera", kind = Kind.PLANT, category = Category.HOME, recurrence = "FREQ=DAILY;INTERVAL=3", dueAt = today.plusDays(1).atTime(9, 0)))
        s.save(Reminder(title = "Drill", kind = Kind.LEND, dueAt = today.plusDays(9).atTime(9, 0), extras = mapOf("person" to "Ravi", "item" to "Drill")))
        s.save(Reminder(title = "Return headphones", kind = Kind.RETURN, category = Category.SHOPPING, dueAt = today.plusDays(2).atTime(9, 0)))
        s.save(Reminder(title = "Take out the bins", kind = Kind.CHORE, category = Category.HOME, recurrence = "FREQ=WEEKLY;BYDAY=SA", dueAt = today.plusDays(3).atTime(9, 0), extras = mapOf("members" to "Asha, Ravi, Meera", "rotationStart" to today.toString())))
    }

    @Test fun life() {
        seedLife()
        captureRoboImage("$out/life.png") { StaticTheme(true) { LifeScreen({}, {}, {}) } }
    }

    @Test fun people() {
        runBlocking {
            savePerson(app, Person(name = "Grandma", cadenceDays = 14, lastContact = today.minusDays(16), birthday = LocalDate.of(1951, today.plusDays(9).monthValue, today.plusDays(9).dayOfMonth), giftIdeas = listOf("Warm shawl", "Photo album"), phone = "+910000000000"))
            savePerson(app, Person(name = "Ravi", cadenceDays = 30, lastContact = today.minusDays(5), anniversary = LocalDate.of(2016, 12, 24)))
            savePerson(app, Person(name = "Meera", birthday = LocalDate.of(1996, 3, 12)))
        }
        captureRoboImage("$out/people.png") { StaticTheme(true) { PeopleScreen {} } }
    }

    @Test fun insights() {
        runBlocking {
            val id = c.service.save(Reminder(title = "Morning run", recurrence = "FREQ=DAILY", dueAt = LocalDateTime.now().plusHours(20)))
            val gym = c.service.save(Reminder(title = "Gym", recurrence = "FREQ=DAILY", dueAt = today.plusDays(1).atTime(8, 0)))
            val log = c.database.log()
            for (d in 0..11) if (d != 4) log.insert(LogEntity(reminderId = id, title = "Morning run", category = "HEALTH", at = today.minusDays(d.toLong()).atTime(6, 40).toString() + ":00", type = "DONE"))
            listOf(30, 25, 35).forEachIndexed { i, m -> log.insert(LogEntity(reminderId = gym, title = "Gym", category = "HEALTH", at = today.minusDays(i + 1L).atTime(8, 0).toString() + ":00", type = "SNOOZED", minutes = m)) }
            listOf("Pay electricity bill", "Call mom").forEachIndexed { i, t -> log.insert(LogEntity(reminderId = 99L + i, title = t, category = "TASK", at = today.atTime(9 + i, 15).toString() + ":00", type = "DONE")) }
        }
        captureRoboImage("$out/insights.png") { StaticTheme(true) { InsightsScreen({}, {}, {}) } }
    }

    @Test fun morning() {
        val m = DailyJobs.Morning(
            today = listOf("Team standup", "Pay electricity bill", "Call mom"),
            birthdays = listOf("Grandma's birthday"),
            bills = listOf("Netflix · ₹649"),
            trials = listOf("Spotify: trial ends soon. Cancel if you don't want it."),
            nudges = listOf("You haven't been in touch with Ravi in 3 weeks"),
            gifts = emptyList(),
        )
        captureRoboImage("$out/morning.png") {
            StaticTheme(true) {
                Column(Modifier.fillMaxSize().background(Rem.colors.canvas).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp(), vertical = 60.dp())) { MorningCard(m, today) }
            }
        }
    }

    @Test fun settings() {
        captureRoboImage("$out/settings.png") { StaticTheme(true) { SettingsScreen({}, {}) } }
    }

    @Test fun edit_smart() {
        captureRoboImage("$out/edit_smart.png") {
            StaticTheme(true) {
                EditContent(
                    state = EditUiState(
                        loaded = true, isNew = true, title = "Call the bank about the card", category = Category.TASK,
                        date = today, time = LocalTime.of(11, 0), repeat = RepeatForm(), window = TimeWindow.businessHours(),
                        followUp = "Card delivery", firstStep = "Find the card number", alertOffsets = setOf(0), style = AlertStyle.NOTIFICATION,
                    ),
                    today = today,
                    listState = androidx.compose.foundation.lazy.LazyListState(firstVisibleItemIndex = 4),
                )
            }
        }
    }

    @Test fun home_focus() {
        val reminders = sampleReminders(today).map { if (it.id == 7L) it.copy(trigger = Trigger.ARRIVE_PLACE) else it }
        captureRoboImage("$out/home_focus.png") {
            StaticTheme(true) { HomeContent(buildHomeState(reminders, emptyList(), LocalDateTime.of(today, LocalTime.of(9, 41)), SmartList.TODAY, null, false), focusMode = true) }
        }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

