package app.rem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.rem.alarm.AlarmContent
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.core.model.Reminder
import app.rem.core.parser.QuickAddParser
import app.rem.ui.add.QuickAddContent
import app.rem.ui.home.HomeContent
import app.rem.ui.home.buildHomeState
import app.rem.ui.motion.LocalAmbientMotion
import app.rem.ui.onboarding.SLIDES
import app.rem.ui.onboarding.SlidePage
import app.rem.ui.theme.RemTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Records real animation frames (virtual clock, 30 fps) into build/motion/<scene>/.
 * scripts/make-motion.sh turns them into the showreel video in docs/motion/.
 * Run: ./gradlew :app:testDebugUnitTest --tests '*MotionRecordTest*' -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-mdpi")
class MotionRecordTest {
    @get:Rule val rule = createComposeRule()
    private val now = LocalDateTime.parse("2026-11-24T09:41")
    private val today = now.toLocalDate()
    private val frameMs = 33L

    private fun record(scene: String, frames: Int, startAt: Int = 0, beforeFrame: (Int) -> Unit = {}): Int {
        val dir = File("build/motion/$scene").apply { mkdirs() }
        for (i in 0 until frames) {
            beforeFrame(i)
            rule.mainClock.advanceTimeBy(frameMs)
            rule.onRoot().captureRoboImage(File(dir, "f%04d.png".format(startAt + i)).path)
        }
        return startAt + frames
    }

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { CompositionLocalProvider(LocalAmbientMotion provides true) { RemTheme(darkTheme = false) { block() } } }
    }

    private fun sample(): List<Reminder> {
        fun r(id: Long, title: String, due: LocalDateTime?, cat: Category, rrule: String? = null, style: AlertStyle = AlertStyle.NOTIFICATION, p: Priority = Priority.NONE, amount: String? = null, nag: Int? = null) =
            Reminder(id = id, title = title, dueAt = due, category = cat, recurrence = rrule, alertStyle = style, priority = p, amount = amount, nagMinutes = nag)
        return listOf(
            r(1, "Renew car insurance", today.minusDays(1).atTime(18, 0), Category.VEHICLE, p = Priority.HIGH),
            r(2, "Take vitamin D", today.atTime(8, 30), Category.MEDICINE, "FREQ=DAILY", AlertStyle.ALARM, nag = 5),
            r(3, "Team standup", today.atTime(10, 30), Category.WORK, "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"),
            r(4, "Pay electricity bill", today.atTime(19, 0), Category.BILL, "FREQ=MONTHLY", p = Priority.MEDIUM, amount = "₹2,340"),
            r(5, "Mom's birthday", today.plusDays(1).atTime(9, 0), Category.BIRTHDAY, "FREQ=YEARLY"),
            r(6, "Gym: leg day", today.plusDays(1).atTime(7, 0), Category.HEALTH, "FREQ=WEEKLY;BYDAY=MO,WE,FR"),
        )
    }

    @Test fun scene1_home() {
        val state = buildHomeState(sample(), emptyList(), now, null, null, false)
        content { HomeContent(state) }
        val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        record("1_home", 75) { i ->
            if (i == 50) rule.onAllNodes(checkbox)[1].performClick()
        }
    }

    @Test fun scene2_quick_add() {
        val clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
        val parser = QuickAddParser(clock)
        val full = "Pay rent every 1st at 9am !1"
        var text by mutableStateOf("")
        content {
            val parsed = remember(text) { parser.parse(text) }
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
        record("2_quick_add", 80) { i ->
            val chars = ((i - 8) / 2).coerceIn(0, full.length)
            if (i >= 8) text = full.take(chars)
        }
    }

    @Test fun scene3_onboarding() {
        content { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { SlidePage(0, SLIDES[0]) } }
        record("3_onboarding", 60)
    }

    @Test fun scene4_typing() {
        content { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { SlidePage(2, SLIDES[2]) } }
        record("4_typing", 90)
    }

    @Test fun scene5_alarm() {
        content {
            CompositionLocalProvider(LocalAmbientMotion provides true) {
                RemTheme(darkTheme = true) {
                    AlarmContent(
                        reminder = Reminder(id = 2, title = "Take vitamin D", dueAt = now, category = Category.MEDICINE, alertStyle = AlertStyle.ALARM),
                        now = now, onDone = {}, onSnooze = {}, onDismiss = {},
                    )
                }
            }
        }
        record("5_alarm", 75)
    }
}
