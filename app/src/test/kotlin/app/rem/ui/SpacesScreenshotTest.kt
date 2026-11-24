package app.rem.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.rem.RemApplication
import app.rem.core.spaces.ShoppingParser
import app.rem.ui.components.Token
import app.rem.ui.theme.Rem
import app.rem.ui.voice.VoiceInputContent
import app.rem.ui.voice.VoicePhase
import app.rem.core.spaces.Wish
import app.rem.core.spaces.WorkStatus
import app.rem.ui.home.HomeContent
import app.rem.ui.home.SmartList
import app.rem.ui.home.buildHomeState
import app.rem.ui.spaces.LocalBottomInset
import app.rem.ui.spaces.ShoppingScreen
import app.rem.ui.spaces.TAB_BAR_HEIGHT
import app.rem.ui.spaces.Tab
import app.rem.ui.spaces.TabBar
import app.rem.ui.spaces.WishlistScreen
import app.rem.ui.spaces.WorkScreen
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class SpacesScreenshotTest {
    private val app = ApplicationProvider.getApplicationContext<RemApplication>()
    private val c get() = app.container
    private val out = "../docs/screenshots"
    private val today = LocalDate.now()

    @get:org.junit.Rule val rule = androidx.compose.ui.test.junit4.createComposeRule()

    /** Lets data load and entrance animations settle, then captures the screen. */
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

    @Composable
    private fun WithTabs(tab: Tab, content: @Composable () -> Unit) {
        StaticTheme(true) {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalBottomInset provides TAB_BAR_HEIGHT) { content() }
                TabBar(tab, {}, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    @Test fun shopping() {
        runBlocking {
            c.spaces.ensureDefaultLists()
            val id = c.database.shopping().lists().first().id
            c.spaces.addItems(id, "milk 2 L, 6 eggs, bread, 1kg basmati rice, onions, tomatoes, dolo 650, surf excel, coriander")
            val items = c.database.shopping().items(id).map { it.toModel() }
            c.spaces.updateItem(items.first { it.name == "Bread" }.copy(price = 45.0))
            c.spaces.updateItem(items.first { it.name.startsWith("Milk") }.copy(everyDays = 2, price = 34.0))
            c.spaces.toggle(c.database.shopping().items(id).map { it.toModel() }.first { it.name == "Coriander" })
            c.spaces.setListReminder(id, null, LocalDate.now().plusDays(2).atTime(10, 0), "FREQ=WEEKLY;BYDAY=SA")
        }
        capture("$out/shopping.png") { WithTabs(Tab.SHOPPING) { ShoppingScreen() } }
    }

    @Test fun work() {
        runBlocking {
            val launch = c.spaces.addProject("Q1 launch")
            c.spaces.addProject("Hiring")
            val t1 = c.spaces.addTask("Finalise pricing deck today 4pm !1", launch)
            c.spaces.setStatus(c.repository.get(t1)!!, WorkStatus.DOING)
            c.spaces.addTask("Review Meera's PR tomorrow 11am", launch)
            c.spaces.addTask("Book offsite venue friday", null)
            val t4 = c.spaces.addTask("Legal sign-off on the contract", launch)
            c.spaces.setStatus(c.repository.get(t4)!!, WorkStatus.WAITING, "Ravi")
        }
        capture("$out/work.png") { WithTabs(Tab.WORK) { WorkScreen {} } }
    }

    @Test fun wishlist() {
        runBlocking {
            c.spaces.saveWish(Wish(title = "Sony WH-1000XM5", price = 24990.0, targetPrice = 22000.0, saved = 9000.0, addedOn = today.minusDays(12), askOn = today.plusDays(18)))
            c.spaces.saveWish(Wish(title = "Kindle Paperwhite", price = 14999.0, addedOn = today.minusDays(33), askOn = today.minusDays(3)))
            c.spaces.saveWish(Wish(title = "Nike Pegasus 41", price = 11895.0, targetPrice = 12000.0, addedOn = today.minusDays(5)))
            c.settings.update { it.copy(payday = 1, saleDates = listOf(today.plusDays(10))) }
        }
        capture("$out/wishlist.png") { WithTabs(Tab.WISHLIST) { WishlistScreen() } }
    }

    @Test fun today_with_tabs() {
        capture("$out/home_light.png") {
            WithTabs(Tab.TODAY) { HomeContent(buildHomeState(sampleReminders(today), emptyList(), LocalDateTime.of(today, LocalTime.of(9, 41)), SmartList.TODAY, null, false)) }
        }
    }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Test fun voice_shopping() {
        capture("$out/voice_shopping.png") {
            StaticTheme(true) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))) {
                    androidx.compose.foundation.layout.Box(Modifier.align(Alignment.BottomCenter).clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Rem.colors.surface).padding(top = 28.dp)) {
                        VoiceInputContent(
                            caption = "Add to Groceries", example = "", actionLabel = "Add to list",
                            phase = VoicePhase.DONE, transcript = "milk eggs and two bread basmati rice", error = null, level = 0f,
                            onMic = {}, onConfirm = {}, onRetry = {},
                            preview = { text ->
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                                    ShoppingParser.splitSpoken(text).forEach { (n, q, _) -> Token(listOfNotNull(n, q?.toInt()?.toString()).joinToString(" \u00B7 "), null, tint = Rem.colors.gold) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

