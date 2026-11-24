package app.rem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.rem.RemApplication
import app.rem.core.life.Documents
import app.rem.core.model.Category
import app.rem.core.model.Kind
import app.rem.core.model.Reminder
import app.rem.core.spaces.WorkStatus
import app.rem.data.ThemeMode
import app.rem.ui.home.HomeContent
import app.rem.ui.home.SmartList
import app.rem.ui.home.buildHomeState
import app.rem.ui.onboarding.PreferencesContent
import app.rem.ui.screens.LifeScreen
import app.rem.ui.screens.TemplatesScreen
import app.rem.ui.spaces.LocalBottomInset
import app.rem.ui.spaces.TAB_BAR_HEIGHT
import app.rem.ui.spaces.Tab
import app.rem.ui.spaces.TabBar
import app.rem.ui.spaces.WorkScreen
import app.rem.ui.theme.Rem
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class FeaturesScreenshotTest {
    private val app = ApplicationProvider.getApplicationContext<RemApplication>()
    private val c get() = app.container
    private val out = "../docs/screenshots"
    private val today = LocalDate.now()

    @get:org.junit.Rule val rule = androidx.compose.ui.test.junit4.createComposeRule()

    private fun capture(path: String, content: @Composable () -> Unit) {
        rule.setContent(content)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        Thread.sleep(300)
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(path)
    }

    @Test fun home_light() {
        val now = LocalDateTime.of(today, java.time.LocalTime.of(9, 41))
        capture("$out/home_light.png") {
            StaticTheme(false) { HomeContent(buildHomeState(sampleReminders(today), emptyList(), now, SmartList.TODAY, null, false)) }
        }
    }

    @Test fun kanban() {
        runBlocking {
            val launch = c.spaces.addProject("Q1 launch")
            val t1 = c.spaces.addTask("Finalise pricing deck today 4pm", launch)
            c.spaces.setStatus(c.repository.get(t1)!!, WorkStatus.DOING)
            c.spaces.addTask("Review Meera's PR tomorrow 11am", launch)
            c.spaces.addTask("Book offsite venue friday", null)
            val t4 = c.spaces.addTask("Legal sign-off on the contract", launch)
            c.spaces.setStatus(c.repository.get(t4)!!, WorkStatus.WAITING, "Ravi")
            val t5 = c.spaces.addTask("Send status update", null)
            c.spaces.setStatus(c.repository.get(t5)!!, WorkStatus.DONE)
        }
        capture("$out/kanban.png") {
            StaticTheme(true) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalBottomInset provides TAB_BAR_HEIGHT) { WorkScreen(initialBoard = true) {} }
                    TabBar(Tab.WORK, {}, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    private fun seedLife() = runBlocking {
        c.settings.update { it.copy(waterEnabled = true, waterGlasses = 8) }
        c.water.add(3)
        val d = today.plusDays(4).atTime(10, 0)
        c.service.save(Reminder(title = "Pay electricity bill", category = Category.BILL, amount = "₹2,340", dueAt = d))
        c.service.save(Reminder(title = "Credit card bill", category = Category.BILL, amount = "₹18,450", dueAt = today.plusDays(9).atTime(10, 0)))
        c.service.save(Reminder(title = "Netflix", kind = Kind.SUBSCRIPTION, category = Category.BILL, amount = "₹649", recurrence = "FREQ=MONTHLY", dueAt = today.plusDays(2).atTime(10, 0)))
        c.service.save(Reminder(title = "Spotify", kind = Kind.SUBSCRIPTION, category = Category.BILL, amount = "₹119", recurrence = "FREQ=MONTHLY", dueAt = today.plusDays(16).atTime(10, 0)))
        c.service.save(Documents.reminder("Passport", "Me", today.plusDays(40), LocalDateTime.now()))
        c.service.save(Documents.reminder("Vehicle insurance", "", today.plusDays(200), LocalDateTime.now()))
    }

    @Test fun life() {
        seedLife()
        capture("$out/life_admin.png") { StaticTheme(true) { LifeScreen({}, {}, {}) } }
    }

    @Test fun life_light() {
        seedLife()
        capture("$out/life_admin_light.png") { StaticTheme(false) { LifeScreen({}, {}, {}) } }
    }

    @Test fun templates() {
        capture("$out/templates.png") { StaticTheme(true) { TemplatesScreen {} } }
    }

    @Test fun onboarding_preferences() {
        c.settings.update { it.copy(theme = ThemeMode.LIGHT) }
        capture("$out/onboarding_prefs.png") {
            StaticTheme(false) {
                Column(Modifier.fillMaxSize().background(Rem.colors.canvas).verticalScroll(rememberScrollState()).padding(16.dp).padding(top = 40.dp)) { PreferencesContent() }
            }
        }
        c.settings.update { it.copy(theme = ThemeMode.DARK) }
    }
}
