package app.rem.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.rem.core.model.Category
import app.rem.ui.components.CategoryGlyph
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.PrimaryButton
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SettingRow
import app.rem.ui.components.Token
import app.rem.ui.theme.Rem
import kotlinx.coroutines.launch
import kotlin.math.abs

data class Slide(val title: String, val body: String)

val SLIDES = listOf(
    Slide("Remember\neverything", "Tasks, medicine, bills, birthdays, documents, your car and your pets, in one calm place."),
    Slide("Never miss\nwhat matters", "Important reminders ring like an alarm and gently repeat until they’re done."),
    Slide("Just say it", "Tap the mic and talk. Rem understands the day, the time and how often, right on your phone."),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState { SLIDES.size + 1 }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(readSetupStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = readSetupStatus(context)
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { status = readSetupStatus(context) }
    val c = Rem.colors

    Box(Modifier.fillMaxSize().background(c.card)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            if (page < SLIDES.size) {
                SlidePage(page, SLIDES[page], offset)
            } else {
                Column(
                    Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).padding(top = 48.dp, bottom = 160.dp),
                ) {
                    PermissionsContent(
                        status = status,
                        onNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else context.startActivity(notificationSettingsIntent(context))
                        },
                        onExact = { context.startActivity(exactAlarmSettingsIntent(context)) },
                        onFullScreen = { context.startActivity(fullScreenSettingsIntent(context)) },
                        onBattery = { runCatching { context.startActivity(batterySettingsIntent(context)) }.onFailure { context.startActivity(appSettingsIntent(context)) } },
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(SLIDES.size + 1) { i ->
                    val on = pager.currentPage == i
                    val w by animateDpAsState(if (on) 18.dp else 7.dp, label = "dot")
                    val col by animateColorAsState(if (on) c.label else c.tertiaryLabel, label = "dotc")
                    Box(Modifier.height(7.dp).width(w).clip(CircleShape).background(col))
                }
            }
            Spacer(Modifier.height(20.dp))
            val last = pager.currentPage == SLIDES.size
            PrimaryButton(
                text = if (last) "Get Started" else "Continue",
                onClick = {
                    if (last) { setOnboarded(context); onFinished() } else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                if (!last) TextButton(onClick = { scope.launch { pager.animateScrollToPage(SLIDES.size) } }) { Text("Skip", color = c.secondaryLabel) }
            }
        }
    }
}

@Composable
fun SlidePage(index: Int, slide: Slide, pageOffset: Float = 0f) {
    val c = Rem.colors
    Column(
        Modifier.fillMaxSize().background(c.card).statusBarsPadding().padding(horizontal = 32.dp).padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.height(240.dp).fillMaxWidth().graphicsLayer {
                translationX = pageOffset * size.width * 0.3f
                alpha = 1f - abs(pageOffset).coerceIn(0f, 1f)
            },
            contentAlignment = Alignment.Center,
        ) {
            when (index) {
                0 -> CategoryGrid()
                1 -> AppIcon(Icons.Rounded.Alarm, c.warning)
                else -> VoiceDemo()
            }
        }
        Spacer(Modifier.height(40.dp))
        Text(slide.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, color = c.label)
        Spacer(Modifier.height(14.dp))
        Text(slide.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = c.secondaryLabel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid() {
    val cats = listOf(Category.TASK, Category.MEDICINE, Category.BILL, Category.BIRTHDAY, Category.HEALTH, Category.DOCUMENT, Category.VEHICLE, Category.PET, Category.HOME)
    FlowRow(
        Modifier.width(220.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        maxItemsInEachRow = 3,
    ) { cats.forEach { CategoryGlyph(it, size = 56.dp) } }
}

@Composable
private fun AppIcon(icon: ImageVector, color: Color) {
    Box(Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun VoiceDemo() {
    val c = Rem.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("“Call mom tomorrow at 6 pm”", style = MaterialTheme.typography.titleMedium, color = c.label)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Token("Tomorrow, 6:00 PM", Icons.Rounded.CalendarMonth, tint = c.accent)
            Token("Task", null)
        }
    }
}

@Composable
fun PermissionsContent(
    status: SetupStatus,
    onNotifications: () -> Unit,
    onExact: () -> Unit,
    onFullScreen: () -> Unit,
    onBattery: () -> Unit,
    tip: String? = oemTip(),
) {
    val c = Rem.colors
    Text("Make Rem\nreliable", style = MaterialTheme.typography.headlineLarge, color = c.label, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(8.dp))
    Text(
        "Android can delay reminders to save battery. Allow these so Rem is always on time.",
        style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel, modifier = Modifier.padding(start = 4.dp),
    )
    Spacer(Modifier.height(24.dp))
    GroupedCard {
        PermissionRow(Icons.Rounded.Notifications, c.danger, "Notifications", status.notifications, onNotifications)
        RowDivider(56.dp)
        PermissionRow(Icons.Rounded.Schedule, c.accent, "Exact Alarms", status.exactAlarms, onExact)
        RowDivider(56.dp)
        PermissionRow(Icons.Rounded.Fullscreen, c.warning, "Full-Screen Alarms", status.fullScreen, onFullScreen)
        RowDivider(56.dp)
        PermissionRow(Icons.Rounded.BatteryChargingFull, c.success, "Unrestricted Battery", status.batteryUnrestricted, onBattery)
    }
    tip?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, color: Color, title: String, granted: Boolean, onClick: () -> Unit) {
    val c = Rem.colors
    SettingRow(title, icon = icon, iconColor = color, onClick = if (granted) null else onClick) {
        if (granted) {
            Icon(Icons.Rounded.Check, "Allowed", tint = c.success, modifier = Modifier.size(20.dp))
        } else {
            Text(
                "Allow", style = MaterialTheme.typography.labelLarge, color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.fill).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** The same checklist, reachable later from the home screen. */
@Composable
fun SetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readSetupStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = readSetupStatus(context)
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { status = readSetupStatus(context) }
    val c = Rem.colors
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp)) {
        Row(
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            Text("Back", style = MaterialTheme.typography.bodyLarge, color = c.accent)
        }
        Spacer(Modifier.height(12.dp))
        PermissionsContent(
            status = status,
            onNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !status.notifications) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(notificationSettingsIntent(context))
            },
            onExact = { context.startActivity(exactAlarmSettingsIntent(context)) },
            onFullScreen = { context.startActivity(fullScreenSettingsIntent(context)) },
            onBattery = { runCatching { context.startActivity(batterySettingsIntent(context)) }.onFailure { context.startActivity(appSettingsIntent(context)) } },
        )
    }
}
