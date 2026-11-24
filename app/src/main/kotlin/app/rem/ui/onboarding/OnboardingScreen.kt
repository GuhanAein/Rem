package app.rem.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import app.rem.ui.motion.AuroraBackground
import app.rem.ui.motion.LocalAmbientMotion
import app.rem.ui.motion.floating
import app.rem.ui.motion.ringing
import app.rem.ui.motion.shimmer
import kotlinx.coroutines.delay
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
                val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                SlidePage(page, SLIDES[page], pageOffset = offset)
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
fun SlidePage(index: Int, slide: Slide, pageOffset: Float = 0f) {
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(
            base = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.background),
            blobs = listOf(Color(0xFF8B7CFF).copy(alpha = 0.22f), Color(0xFFFF7AC6).copy(alpha = 0.14f), Color(0xFF38BDF8).copy(alpha = 0.14f)),
            speedMs = 18000,
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp).padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(300.dp).graphicsLayer {
                    translationX = pageOffset * size.width * 0.55f
                    alpha = 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                    val sc = 1f - 0.15f * kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                    scaleX = sc; scaleY = sc
                },
                contentAlignment = Alignment.Center,
            ) {
                when (index) {
                    0 -> Constellation()
                    1 -> AlarmArt()
                    else -> TypingDemo()
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.graphicsLayer { translationX = pageOffset * size.width * 0.25f; alpha = 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(slide.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(14.dp))
                Text(slide.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Constellation() {
    val t = if (LocalAmbientMotion.current) {
        val orbit by rememberInfiniteTransition(label = "orbit").animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "o")
        orbit
    } else 0f
    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        PulseRings(MaterialTheme.colorScheme.primary, 260.dp)
        HeroIcon(Icons.Rounded.NotificationsActive, Rem.extras.heroGradient, size = 104.dp, ring = true)
        val cats = listOf(Category.HEALTH, Category.BILL, Category.VEHICLE, Category.DOCUMENT, Category.PET, Category.BIRTHDAY, Category.MEDICINE)
        cats.forEachIndexed { i, cat ->
            val angle = (t + i / cats.size.toFloat()) * 2f * kotlin.math.PI.toFloat() - kotlin.math.PI.toFloat() / 2
            val radius = if (i % 2 == 0) 128f else 112f
            Box(
                Modifier
                    .offset((kotlin.math.cos(angle) * radius).dp, (kotlin.math.sin(angle) * radius).dp)
                    .floating(4f, 2200 + i * 300, i * 0.17f)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(6.dp),
            ) { CategoryBubble(cat, size = 40.dp) }
        }
    }
}

/** Expanding rings, like sound waves. */
@Composable
private fun PulseRings(color: Color, size: androidx.compose.ui.unit.Dp, count: Int = 3, periodMs: Int = 2600) {
    val t = if (LocalAmbientMotion.current) {
        val wave by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "w")
        wave
    } else 0.4f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        repeat(count) { i ->
            val p = (t + i / count.toFloat()) % 1f
            Box(
                Modifier.size(size).graphicsLayer { scaleX = 0.35f + 0.65f * p; scaleY = 0.35f + 0.65f * p; alpha = (1f - p) * 0.5f }
                    .clip(CircleShape).background(color.copy(alpha = 0.18f)),
            )
        }
    }
}

@Composable
private fun AlarmArt() {
    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        PulseRings(Color(0xFFFF6B6B), 280.dp, count = 4, periodMs = 2000)
        Box(Modifier.floating(5f, 2800)) {
            HeroIcon(Icons.Rounded.Alarm, Rem.extras.alarmGradient, size = 150.dp, ring = true, ringPeriod = 1500, ringDegrees = 20f)
        }
        Text(
            "Still waiting\u2026",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.offset(70.dp, (-110).dp).floating(4f, 2000, 0.5f)
                .shadow(10.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
                .background(Color(0xFFFF6B6B)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Text(
            "Every 5 min",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.offset((-80).dp, 112.dp).floating(4f, 2400, 0.1f)
                .shadow(10.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HeroIcon(
    icon: ImageVector,
    brush: androidx.compose.ui.graphics.Brush,
    size: androidx.compose.ui.unit.Dp = 150.dp,
    ring: Boolean = false,
    ringPeriod: Int = 2600,
    ringDegrees: Float = 16f,
) {
    Box(
        Modifier.size(size).shadow(30.dp, CircleShape, ambientColor = Color(0xFF5B4BFF), spotColor = Color(0xFF5B4BFF)).clip(CircleShape).background(brush).shimmer(3600),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.46f).then(if (ring) Modifier.ringing(ringPeriod, ringDegrees) else Modifier))
    }
}

private const val DEMO_TEXT = "Pay rent every 1st at 9am !1"

@Composable
private fun TypingDemo() {
    val ambient = LocalAmbientMotion.current
    var typed by remember { mutableStateOf(if (ambient) "" else DEMO_TEXT) }
    var chips by remember { mutableStateOf(if (ambient) 0 else 4) }
    if (ambient) {
        LaunchedEffect(Unit) {
            while (true) {
                typed = ""; chips = 0
                delay(500)
                for (n in 1..DEMO_TEXT.length) { typed = DEMO_TEXT.take(n); delay(55) }
                for (c in 1..4) { delay(260); chips = c }
                delay(2600)
            }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth().floating(3f, 3400)) {
            Column(Modifier.padding(20.dp).height(170.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("You type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(typed, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Box(Modifier.padding(start = 2.dp).width(2.dp).height(22.dp).background(MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoPop(chips >= 1) { DemoChip("1 Dec \u00B7 9:00 AM", MaterialTheme.colorScheme.primary) }
                    DemoPop(chips >= 2) { DemoChip("Monthly", Color(0xFF06B6D4)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoPop(chips >= 3) { DemoChip("Bill", Color(0xFF8B5CF6)) }
                    DemoPop(chips >= 4) { DemoChip("High priority", Color(0xFFEF4444)) }
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
private fun DemoPop(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), initialScale = 0.3f) + fadeIn(),
        exit = fadeOut(),
    ) { content() }
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
                val ambient = LocalAmbientMotion.current
                val pop = remember { androidx.compose.animation.core.Animatable(if (ambient) 0f else 1f) }
                LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
                Box(Modifier.size(28.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(Rem.extras.success), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, "Allowed", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            } else {
                Text(
                    "Allow",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Rem.extras.accentGradient).shimmer(2600).padding(horizontal = 14.dp, vertical = 7.dp),
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
