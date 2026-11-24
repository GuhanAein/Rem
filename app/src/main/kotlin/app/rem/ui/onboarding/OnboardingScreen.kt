package app.rem.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.WaterDrop
import app.rem.container
import app.rem.ui.components.Caption
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
import app.rem.ui.components.noirSurface
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import app.rem.ui.theme.Rem
import kotlinx.coroutines.launch
import kotlin.math.abs

data class Slide(val title: String, val body: String)

val SLIDES = listOf(
    Slide("Remember\n*everything.*", "Tasks, medicine, bills, birthdays, documents, your car and your pets, in one calm place."),
    Slide("Never miss\n*what matters.*", "Important reminders ring like an alarm and gently repeat until they’re done."),
    Slide("Just\n*say it.*", "Tap the mic and talk. Rem understands the day, the time and how often, right on your phone."),
)

/** "*word*" segments render in italic gold serif. */
fun editorial(text: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    text.split('*').forEachIndexed { i, part ->
        if (i % 2 == 1) {
            withStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = app.rem.ui.theme.RemPalette.current.gold)) { append(part) }
        } else {
            append(part)
        }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    // Tour slides, then "make it yours", then permissions.
    val prefsPage = SLIDES.size
    val permsPage = SLIDES.size + 1
    val pager = rememberPagerState { SLIDES.size + 2 }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(readSetupStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = readSetupStatus(context)
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { status = readSetupStatus(context) }
    val c = Rem.colors

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            if (page < SLIDES.size) {
                SlidePage(page, SLIDES[page], offset)
            } else if (page == prefsPage) {
                Column(
                    Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).padding(top = 48.dp, bottom = 160.dp),
                ) { PreferencesContent() }
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
                repeat(SLIDES.size + 2) { i ->
                    val on = pager.currentPage == i
                    val w by animateDpAsState(if (on) 18.dp else 7.dp, label = "dot")
                    val col by animateColorAsState(if (on) c.gold else c.tertiaryLabel, label = "dotc")
                    Box(Modifier.height(7.dp).width(w).clip(CircleShape).background(col))
                }
            }
            Spacer(Modifier.height(20.dp))
            val last = pager.currentPage == permsPage
            PrimaryButton(
                text = if (last) "Get Started" else "Continue",
                onClick = {
                    if (last) {
                        setOnboarded(context)
                        scope.launch { runCatching { app.rem.jobs.DailyJobs.schedule(context) } }
                        onFinished()
                    } else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                if (pager.currentPage < prefsPage) TextButton(onClick = { scope.launch { pager.animateScrollToPage(prefsPage) } }) { Text("Skip", color = c.secondaryLabel) }
            }
        }
    }
}

@Composable
fun SlidePage(index: Int, slide: Slide, pageOffset: Float = 0f) {
    val c = Rem.colors
    Column(
        Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().padding(horizontal = 30.dp).padding(top = 80.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            Modifier.height(240.dp).fillMaxWidth().graphicsLayer {
                translationX = pageOffset * size.width * 0.3f
                alpha = 1f - abs(pageOffset).coerceIn(0f, 1f)
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            when (index) {
                0 -> CategoryGrid()
                1 -> AppIcon(Icons.Rounded.Alarm, c.warning)
                else -> VoiceDemo()
            }
        }
        Spacer(Modifier.height(48.dp))
        Text("0${index + 1} / 03", style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
        Spacer(Modifier.height(14.dp))
        Text(editorial(slide.title), style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp, lineHeight = 54.sp), color = c.label)
        Spacer(Modifier.height(18.dp))
        Text(slide.body, style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid() {
    val cats = listOf(Category.TASK, Category.MEDICINE, Category.BILL, Category.BIRTHDAY, Category.HEALTH, Category.DOCUMENT, Category.VEHICLE, Category.PET, Category.HOME)
    FlowRow(
        Modifier.width(220.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        maxItemsInEachRow = 3,
    ) { cats.forEach { CategoryGlyph(it, size = 56.dp) } }
}

@Composable
private fun AppIcon(icon: ImageVector, @Suppress("UNUSED_PARAMETER") color: Color) {
    val c = Rem.colors
    Box(Modifier.size(120.dp).noirSurface(RoundedCornerShape(34.dp), elevated = true), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.gold, modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun VoiceDemo() {
    val c = Rem.colors
    Column(horizontalAlignment = Alignment.Start) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(c.ivory), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Mic, null, tint = c.onIvory, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "\u201CCall mom tomorrow at six\u201D",
            style = MaterialTheme.typography.headlineMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            color = c.label,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Token("Tomorrow \u00B7 6:00 PM", Icons.Rounded.CalendarMonth, tint = c.gold)
            Token("Task", null)
        }
    }
}

private val WAKE_TIMES = listOf(6, 7, 8, 9).map { java.time.LocalTime.of(it, 0) }
private val BED_TIMES = listOf(java.time.LocalTime.of(22, 0), java.time.LocalTime.of(22, 30), java.time.LocalTime.of(23, 0), java.time.LocalTime.of(23, 30), java.time.LocalTime.of(0, 0))

/** Theme, daily rhythm and the gentle daily helpers, chosen once. The theme previews live. */
@Composable
fun PreferencesContent() {
    val context = LocalContext.current
    val c = Rem.colors
    val s by context.container.settings.state.collectAsState()
    fun update(t: (app.rem.data.AppSettings) -> app.rem.data.AppSettings) = context.container.settings.update(t)

    Text(editorial("Make it\n*yours.*"), style = MaterialTheme.typography.displaySmall, color = c.label, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(8.dp))
    Text("You can change all of this later in Settings.", style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel, modifier = Modifier.padding(start = 4.dp))

    Caption("Look")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        app.rem.data.ThemeMode.entries.forEach { m ->
            ThemeSwatch(m, selected = s.theme == m, modifier = Modifier.weight(1f)) { update { it.copy(theme = m) } }
        }
    }

    Caption("Your day")
    GroupedCard {
        TimeChoice("I wake up around", WAKE_TIMES, s.wakeTime) { t -> update { it.copy(wakeTime = t) } }
        RowDivider()
        TimeChoice("I go to bed around", BED_TIMES, s.bedtime) { t -> update { it.copy(bedtime = t) } }
    }

    Caption("Little helpers")
    GroupedCard {
        app.rem.ui.components.SwitchRow("Morning card", s.morningCardEnabled, { on -> update { it.copy(morningCardEnabled = on) } },
            icon = Icons.Rounded.WbSunny, iconColor = c.secondaryLabel, subtitle = "Today at a glance, every morning")
        RowDivider(54.dp)
        app.rem.ui.components.SwitchRow("Bedtime summary", s.bedtimeSummary, { on -> update { it.copy(bedtimeSummary = on) } },
            icon = Icons.Rounded.Bedtime, iconColor = c.secondaryLabel, subtitle = "What's left today, and one tap to move it to tomorrow")
        RowDivider(54.dp)
        app.rem.ui.components.SwitchRow("Water reminders", s.waterEnabled, { on -> update { it.copy(waterEnabled = on) } },
            icon = Icons.Rounded.WaterDrop, iconColor = c.secondaryLabel, subtitle = "${s.waterGlasses} glasses, spread across your day")
    }
}

@Composable
private fun ThemeSwatch(mode: app.rem.data.ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Rem.colors
    val (bg, card, ink) = when (mode) {
        app.rem.data.ThemeMode.DARK -> Triple(app.rem.ui.theme.Noir.canvas, app.rem.ui.theme.Noir.surface, app.rem.ui.theme.Noir.label)
        app.rem.data.ThemeMode.LIGHT -> Triple(app.rem.ui.theme.Ivory.canvas, app.rem.ui.theme.Ivory.surface, app.rem.ui.theme.Ivory.label)
        app.rem.data.ThemeMode.SYSTEM -> Triple(app.rem.ui.theme.Noir.canvas, app.rem.ui.theme.Ivory.surface, app.rem.ui.theme.Noir.label)
    }
    val border by animateColorAsState(if (selected) c.gold else c.separator, label = "swatch")
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(8.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(card).padding(8.dp)) {
                Box(Modifier.width(34.dp).height(5.dp).clip(CircleShape).background(ink.copy(alpha = 0.8f)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(50.dp).height(4.dp).clip(CircleShape).background(ink.copy(alpha = 0.35f)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(26.dp).height(4.dp).clip(CircleShape).background(Color(0xFFD9BC83)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(mode.label, style = MaterialTheme.typography.labelMedium, color = if (selected) c.label else c.secondaryLabel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeChoice(title: String, options: List<java.time.LocalTime>, selected: java.time.LocalTime, onPick: (java.time.LocalTime) -> Unit) {
    val c = Rem.colors
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.label)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { t ->
                val on = t == selected
                Text(
                    app.rem.ui.format.formatTime(t), style = MaterialTheme.typography.labelLarge, color = if (on) c.onIvory else c.label,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.ivory else c.fill).clickable { onPick(t) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
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
    Text(editorial("Make Rem\n*unmissable.*"), style = MaterialTheme.typography.displaySmall, color = c.label, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(8.dp))
    Text(
        "Android can delay reminders to save battery. Allow these so Rem is always on time.",
        style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel, modifier = Modifier.padding(start = 4.dp),
    )
    Spacer(Modifier.height(24.dp))
    GroupedCard {
        PermissionRow(Icons.Rounded.Notifications, c.secondaryLabel, "Notifications", status.notifications, onNotifications)
        RowDivider(54.dp)
        PermissionRow(Icons.Rounded.Schedule, c.secondaryLabel, "Exact alarms", status.exactAlarms, onExact)
        RowDivider(54.dp)
        PermissionRow(Icons.Rounded.Fullscreen, c.secondaryLabel, "Full-screen alarms", status.fullScreen, onFullScreen)
        RowDivider(54.dp)
        PermissionRow(Icons.Rounded.BatteryChargingFull, c.secondaryLabel, "Unrestricted battery", status.batteryUnrestricted, onBattery)
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
            Icon(Icons.Rounded.Check, "Allowed", tint = c.gold, modifier = Modifier.size(20.dp))
        } else {
            Text(
                "Allow", style = MaterialTheme.typography.labelMedium, color = c.onIvory,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.ivory).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
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
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.label, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            Text("Back", style = MaterialTheme.typography.bodyLarge, color = c.label)
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
