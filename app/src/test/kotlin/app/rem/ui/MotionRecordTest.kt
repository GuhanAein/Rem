package app.rem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.rem.core.parser.QuickAddParser
import app.rem.ui.add.QuickAddContent
import app.rem.ui.home.HomeContent
import app.rem.ui.home.SmartList
import app.rem.ui.home.buildHomeState
import app.rem.ui.motion.LocalAmbientMotion
import app.rem.ui.theme.Rem
import app.rem.ui.theme.RemTheme
import app.rem.ui.voice.VoiceContent
import app.rem.ui.voice.VoicePhase
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
import kotlin.math.abs
import kotlin.math.sin

/**
 * Records real frames (virtual clock, 30 fps) into build/motion/<scene>/; scripts/make-motion.sh
 * turns them into docs/motion/showreel.mp4.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-mdpi")
class MotionRecordTest {
    @get:Rule val rule = createComposeRule()
    private val now = LocalDateTime.parse("2026-11-24T09:41")
    private val clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())

    private fun record(scene: String, frames: Int, beforeFrame: (Int) -> Unit = {}) {
        val dir = File("build/motion/$scene").apply { mkdirs() }
        for (i in 0 until frames) {
            beforeFrame(i)
            rule.mainClock.advanceTimeBy(33)
            rule.onRoot().captureRoboImage(File(dir, "f%04d.png".format(i)).path)
        }
    }

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { CompositionLocalProvider(LocalAmbientMotion provides true) { RemTheme(darkTheme = false) { block() } } }
    }

    @androidx.compose.runtime.Composable
    private fun Sheet(content: @androidx.compose.runtime.Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
            Box(
                Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Rem.colors.card).padding(top = 28.dp),
            ) { content() }
        }
    }

    @Test fun scene1_home() {
        val state = buildHomeState(sampleReminders(now.toLocalDate()), emptyList(), now, SmartList.TODAY, null, false)
        content { HomeContent(state) }
        val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        record("1_home", 60) { i -> if (i == 25) rule.onAllNodes(checkbox)[2].performClick() }
    }

    @Test fun scene2_voice() {
        val words = "Remind me to call mom tomorrow at 6 pm".split(" ")
        var heard by mutableStateOf("")
        var phase by mutableStateOf(VoicePhase.LISTENING)
        var level by mutableStateOf(0f)
        val parser = QuickAddParser(clock)
        content {
            val parsed = remember(heard) { parser.parse(heard) }
            Sheet { VoiceContent(phase, heard, null, level, parsed, now, {}, {}, {}) }
        }
        record("2_voice", 95) { i ->
            val n = ((i - 10) / 6).coerceIn(0, words.size)
            if (i >= 10) heard = words.take(n).joinToString(" ")
            level = if (i in 10..62) abs(sin(i * 0.7f)) * 0.9f else 0f
            if (i == 66) phase = VoicePhase.DONE
        }
    }

    @Test fun scene3_quick_add() {
        val full = "Pay rent every 1st at 9am"
        var text by mutableStateOf("")
        val parser = QuickAddParser(clock)
        content {
            val parsed = remember(text) { parser.parse(text) }
            Sheet {
                QuickAddContent(
                    text = text, onTextChange = {}, parsed = parsed, category = parsed.category, due = parsed.dueAt, alarm = false,
                    onCategory = {}, onQuickPick = {}, onAlarm = {}, onSave = {}, onMoreOptions = {}, now = now,
                )
            }
        }
        record("3_quick_add", 70) { i -> if (i >= 6) text = full.take(((i - 6) / 2).coerceIn(0, full.length)) }
    }
}
