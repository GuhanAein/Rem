package app.rem.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.rem.core.model.Category
import app.rem.ui.components.CategoryBubble
import app.rem.ui.components.GradientButton
import app.rem.ui.theme.Rem
import kotlinx.coroutines.launch

data class Slide(val title: String, val body: String)

val SLIDES = listOf(
    Slide("Remember\neverything", "Tasks, medicine, bills, birthdays, documents, your car, your pets. One calm place for all of it."),
    Slide("Reminders you\ncan't miss", "Important ones ring like an alarm and keep nudging you until they're done. No more swiping them away."),
    Slide("Just type it", "“Pay rent every 1st at 9am”. Rem understands dates, repeats and priorities, right on your phone. Private, offline, no account."),
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page < SLIDES.size) {
                SlidePage(page, SLIDES[page])
            } else {
                Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 40.dp, bottom = 140.dp)) {
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
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(SLIDES.size + 1) { i ->
                    val w by animateDpAsState(if (pager.currentPage == i) 22.dp else 7.dp, label = "dot")
                    val c by animateColorAsState(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "dotc")
                    Box(Modifier.height(7.dp).width(w).clip(CircleShape).background(c))
                }
            }
            Spacer(Modifier.height(18.dp))
            val last = pager.currentPage == SLIDES.size
            GradientButton(
                text = if (last) "Start using Rem" else "Continue",
                onClick = {
                    if (last) {
                        setOnboarded(context)
                        onFinished()
                    } else {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!last) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(SLIDES.size) } }) { Text("Skip") }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun SlidePage(index: Int, slide: Slide) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp).padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            when (index) {
                0 -> Constellation()
                1 -> HeroIcon(Icons.Rounded.Alarm, Rem.extras.alarmGradient)
                else -> TypingDemo()
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(slide.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(14.dp))
        Text(slide.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Constellation() {
    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(240.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)))
        Box(Modifier.size(170.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)))
        HeroIcon(Icons.Rounded.NotificationsActive, Rem.extras.heroGradient, size = 104.dp)
        val spots = listOf(
            Category.MEDICINE to (-110 to -80), Category.BILL to (105 to -95), Category.BIRTHDAY to (-125 to 40),
            Category.VEHICLE to (120 to 30), Category.PET to (-60 to 120), Category.DOCUMENT to (70 to 118),
            Category.HEALTH to (0 to -135),
        )
        spots.forEach { (cat, xy) ->
            Box(
                Modifier.offset(xy.first.dp, xy.second.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(6.dp),
            ) { CategoryBubble(cat, size = 40.dp) }
        }
    }
}

@Composable
private fun HeroIcon(icon: ImageVector, brush: androidx.compose.ui.graphics.Brush, size: androidx.compose.ui.unit.Dp = 150.dp) {
    Box(
        Modifier.size(size).shadow(30.dp, CircleShape, ambientColor = Color(0xFF5B4BFF), spotColor = Color(0xFF5B4BFF)).clip(CircleShape).background(brush),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.46f)) }
}

@Composable
private fun TypingDemo() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("You type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                Text("Pay rent every 1st at 9am !1", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoChip("1 Dec · 9:00 AM", MaterialTheme.colorScheme.primary)
                    DemoChip("Monthly", Color(0xFF06B6D4))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoChip("Bill", Color(0xFF8B5CF6))
                    DemoChip("High priority", Color(0xFFEF4444))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Understood on your phone. Nothing leaves it.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DemoChip(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp),
    )
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
    Text("Make Rem\nunmissable", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(10.dp))
    Text(
        "Android can delay or block reminders to save battery. These switches make sure Rem rings exactly on time.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    PermissionRow(Icons.Rounded.Notifications, "Notifications", "So Rem can remind you at all", status.notifications, onNotifications)
    PermissionRow(Icons.Rounded.Schedule, "Exact alarms", "Ring at 9:00, not 9:14", status.exactAlarms, onExact)
    PermissionRow(Icons.Rounded.Fullscreen, "Full-screen alarms", "Show alarm reminders over the lock screen", status.fullScreen, onFullScreen)
    PermissionRow(Icons.Rounded.BatteryChargingFull, "Unrestricted battery", "Stop Android from putting Rem to sleep", status.batteryUnrestricted, onBattery)
    tip?.let {
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = Rem.extras.warning.copy(alpha = 0.12f)) {
            Row(Modifier.padding(14.dp)) {
                Icon(Icons.Rounded.Lightbulb, null, tint = Rem.extras.warning)
                Spacer(Modifier.width(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !granted,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (granted) Rem.extras.success.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Rem.extras.success), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, "Allowed", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            } else {
                Text(
                    "Allow",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Rem.extras.accentGradient).padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** The same permission checklist, reachable later from the home screen. */
@Composable
fun SetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readSetupStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = readSetupStatus(context)
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { status = readSetupStatus(context) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
        }
        Spacer(Modifier.height(8.dp))
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
