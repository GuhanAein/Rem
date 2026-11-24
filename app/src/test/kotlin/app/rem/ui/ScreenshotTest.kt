package app.rem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.rem.alarm.AlarmContent
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.core.model.Reminder
import app.rem.core.parser.QuickAddParser
import app.rem.core.recurrence.Freq
import app.rem.ui.add.QuickAddContent
import app.rem.ui.edit.EditContent
import app.rem.ui.edit.EditUiState
import app.rem.ui.edit.RepeatForm
import app.rem.ui.home.HomeContent
import app.rem.ui.home.buildHomeState
import app.rem.ui.onboarding.PermissionsContent
import app.rem.ui.onboarding.SLIDES
import app.rem.ui.onboarding.SetupStatus
import app.rem.ui.onboarding.SlidePage
import app.rem.ui.theme.RemTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Renders each screen to docs/screenshots via Roborazzi: `./gradlew :app:recordRoborazziDebug`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class ScreenshotTest {
    private val now = LocalDateTime.parse("2026-11-24T09:41")
    private val today = now.toLocalDate()
    private val out = "../docs/screenshots"

    private fun sample(): List<Reminder> {
        fun r(id: Long, title: String, due: LocalDateTime?, cat: Category, rrule: String? = null, style: AlertStyle = AlertStyle.NOTIFICATION, p: Priority = Priority.NONE, amount: String? = null, nag: Int? = null) =
            Reminder(id = id, title = title, dueAt = due, category = cat, recurrence = rrule, alertStyle = style, priority = p, amount = amount, nagMinutes = nag)
        return listOf(
            r(1, "Renew car insurance", today.minusDays(1).atTime(18, 0), Category.VEHICLE, p = Priority.HIGH),
            r(2, "Take vitamin D", today.atTime(8, 30), Category.MEDICINE, "FREQ=DAILY", AlertStyle.ALARM, nag = 5),
            r(3, "Team standup", today.atTime(10, 30), Category.WORK, "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"),
            r(4, "Pay electricity bill", today.atTime(19, 0), Category.BILL, "FREQ=MONTHLY", p = Priority.MEDIUM, amount = "₹2,340"),
            r(5, "Mom's birthday 🎂", today.plusDays(1).atTime(9, 0), Category.BIRTHDAY, "FREQ=YEARLY"),
            r(6, "Gym: leg day", today.plusDays(1).atTime(7, 0), Category.HEALTH, "FREQ=WEEKLY;BYDAY=MO,WE,FR"),
            r(7, "Take Bruno to the vet", today.plusDays(3).atTime(17, 30), Category.PET),
            r(8, "Passport expires", today.plusDays(40).atTime(9, 0), Category.DOCUMENT, p = Priority.HIGH),
            r(9, "Buy a new phone charger", null, Category.SHOPPING),
        )
    }

    private val done = listOf(
        Reminder(id = 20, title = "Water plants", dueAt = today.atTime(7, 0), completedAt = java.time.Instant.now()),
        Reminder(id = 21, title = "Morning run", dueAt = today.atTime(6, 30), completedAt = java.time.Instant.now()),
    )

    @Test fun home_light() = captureRoboImage("$out/home_light.png") {
        RemTheme(darkTheme = false) { HomeContent(buildHomeState(sample(), done, now, null, null, needsSetup = false)) }
    }

    @Test fun home_dark() = captureRoboImage("$out/home_dark.png") {
        RemTheme(darkTheme = true) { HomeContent(buildHomeState(sample(), done, now, null, null, needsSetup = false)) }
    }

    @Test fun home_filtered_bills() = captureRoboImage("$out/home_filter.png") {
        RemTheme(darkTheme = false) { HomeContent(buildHomeState(sample(), done, now, null, Category.MEDICINE, needsSetup = true)) }
    }

    @Test fun home_empty() = captureRoboImage("$out/home_empty.png") {
        RemTheme(darkTheme = false) { HomeContent(buildHomeState(emptyList(), emptyList(), now, null, null, false)) }
    }

    @Test fun quick_add() = captureRoboImage("$out/quick_add.png") {
        val clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
        val text = "Pay rent every 1st at 9am !1"
        val parsed = QuickAddParser(clock).parse(text)
        RemTheme(darkTheme = false) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))) {
                Box(
                    Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(top = 28.dp),
                ) {
                    QuickAddContent(
                        text = text, onTextChange = {}, parsed = parsed, category = parsed.category, due = parsed.dueAt, alarm = false,
                        onCategory = {}, onQuickPick = {}, onAlarm = {}, onSave = {}, onMoreOptions = {}, now = now,
                    )
                }
            }
        }
    }

    @Test fun edit() = captureRoboImage("$out/edit.png") {
        RemTheme(darkTheme = false) {
            EditContent(
                state = EditUiState(
                    loaded = true, isNew = false, title = "Take vitamin D", notes = "After breakfast, with water",
                    category = Category.MEDICINE, priority = Priority.HIGH, date = today, time = LocalTime.of(8, 30),
                    repeat = RepeatForm(Freq.WEEKLY, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
                    alertOffsets = setOf(0, 15), style = AlertStyle.ALARM, nagMinutes = 5,
                ),
                today = today,
            )
        }
    }

    @Test fun alarm() = captureRoboImage("$out/alarm.png") {
        RemTheme(darkTheme = true) {
            AlarmContent(
                reminder = Reminder(id = 2, title = "Take vitamin D", dueAt = now, category = Category.MEDICINE, alertStyle = AlertStyle.ALARM),
                now = now, onDone = {}, onSnooze = {}, onDismiss = {}, animate = false,
            )
        }
    }

    @Test fun onboarding_everything() = captureRoboImage("$out/onboarding_1.png") {
        RemTheme(darkTheme = false) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { SlidePage(0, SLIDES[0]) } }
    }

    @Test fun onboarding_type() = captureRoboImage("$out/onboarding_3.png") {
        RemTheme(darkTheme = false) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { SlidePage(2, SLIDES[2]) } }
    }

    @Test fun setup() = captureRoboImage("$out/setup.png") {
        RemTheme(darkTheme = false) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp).padding(top = 30.dp)) {
                PermissionsContent(SetupStatus(notifications = true, exactAlarms = false, fullScreen = true, batteryUnrestricted = false), {}, {}, {}, {}, tip = "On Samsung: Settings › Battery › Background usage limits › add Rem to “Never sleeping apps”.")
            }
        }
    }
}
