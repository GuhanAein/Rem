package app.rem.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.rem.backup.Backup
import app.rem.container
import app.rem.core.model.Category
import app.rem.data.Accent
import app.rem.data.AppSettings
import app.rem.data.PlaceEntity
import app.rem.ui.components.Caption
import app.rem.ui.components.CategoryGlyph
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.OptionSheet
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SettingRow
import app.rem.ui.components.SwitchRow
import app.rem.ui.edit.FieldRow
import app.rem.ui.format.formatTime
import app.rem.ui.theme.Rem
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

@SuppressLint("MissingPermission")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onReliability: () -> Unit, onRoute: (String) -> Unit = {}) {
    val context = LocalContext.current
    val c = Rem.colors
    val scope = rememberCoroutineScope()
    val container = context.container
    val s by container.settings.state.collectAsState()
    val places by remember { container.database.places().observeAll() }.collectAsState(emptyList())
    var timeFor by remember { mutableStateOf<Pair<String, LocalTime>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var namingPlace by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    fun update(t: (AppSettings) -> AppSettings) {
        container.settings.update(t)
        scope.launch { app.rem.jobs.DailyJobs.schedule(context); app.rem.triggers.Triggers.sync(context) }
    }

    // --- launchers
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) update { it.copy(respectMeetings = true) } }
    var savingHome by remember { mutableStateOf(false) }
    fun captureLocation(home: Boolean) {
        LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) { message = "Couldn't get your location. Try again outdoors."; return@addOnSuccessListener }
                if (home) scope.launch {
                    val existing = places.firstOrNull { it.isHome }
                    container.database.places().upsert(PlaceEntity(existing?.id ?: 0, "Home", loc.latitude, loc.longitude, 150f, isHome = true))
                    app.rem.triggers.Triggers.sync(context)
                    message = "Home saved."
                } else namingPlace = loc.latitude to loc.longitude
            }
            .addOnFailureListener { message = "Location isn't available on this phone." }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) captureLocation(savingHome)
    }
    var soundFor by remember { mutableStateOf<Category?>(null) }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cat = soundFor ?: return@rememberLauncherForActivityResult
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            update { st -> st.copy(sounds = if (uri == null) st.sounds - cat else st.sounds + (cat to uri.toString())) }
        }
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = Backup.exportJson(context)
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            message = "Backup saved."
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                val json = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty() }
                "Restored ${Backup.importJson(context, json)} reminders."
            }.getOrElse { "That file couldn't be restored: ${it.message}" }
        }
    }

    NoirScreen("Settings", "Make it *yours.*", onBack) {
        item {
            Caption("Appearance")
            GroupedCard {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge, color = c.label)
                    Spacer(Modifier.height(10.dp))
                    app.rem.ui.components.SegmentedControl(
                        app.rem.data.ThemeMode.entries.map { it.label }, s.theme.ordinal,
                        { i -> update { it.copy(theme = app.rem.data.ThemeMode.entries[i]) } },
                    )
                }
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Accent", style = MaterialTheme.typography.bodyLarge, color = c.label, modifier = Modifier.weight(1f))
                    Accent.entries.forEach { a ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(a.argb))
                                .border(2.dp, if (s.accent == a) c.ivory else Color.Transparent, CircleShape)
                                .clickable { update { it.copy(accent = a) } },
                        )
                    }
                }
                RowDivider()
                SwitchRow("Focus mode", s.focusMode, { on -> update { it.copy(focusMode = on) } }, icon = Icons.Rounded.CenterFocusStrong, iconColor = c.secondaryLabel,
                    subtitle = "Home shows only the next three things")
                RowDivider(54.dp)
                SwitchRow("App lock", s.appLock, { on -> update { it.copy(appLock = on) } }, icon = Icons.Rounded.Fingerprint, iconColor = c.secondaryLabel,
                    subtitle = "Fingerprint or screen lock to open Rem; hides private reminders")
            }
        }
        item {
            Caption("Your day")
            GroupedCard {
                SettingRow("I usually wake up", icon = Icons.Rounded.WbSunny, iconColor = c.secondaryLabel, value = formatTime(s.wakeTime), valueColor = c.gold, onClick = { timeFor = "wake" to s.wakeTime })
                RowDivider(54.dp)
                SettingRow("I go to bed", icon = Icons.Rounded.Bedtime, iconColor = c.secondaryLabel, value = formatTime(s.bedtime), valueColor = c.gold, onClick = { timeFor = "bed" to s.bedtime })
                RowDivider(54.dp)
                SettingRow("“Tonight” starts", icon = Icons.Rounded.BatteryChargingFull, iconColor = c.secondaryLabel, value = formatTime(s.chargingWindowStart), valueColor = c.gold, onClick = { timeFor = "charge" to s.chargingWindowStart })
                RowDivider(54.dp)
                SwitchRow("Morning card", s.morningCardEnabled, { on -> update { it.copy(morningCardEnabled = on) } }, icon = Icons.Rounded.Event, iconColor = c.secondaryLabel,
                    subtitle = if (s.morningCardEnabled) "Every day at ${formatTime(s.morningCardTime)}" else null)
                if (s.morningCardEnabled) {
                    RowDivider(54.dp)
                    SettingRow("Card time", value = formatTime(s.morningCardTime), valueColor = c.gold, onClick = { timeFor = "card" to s.morningCardTime })
                }
                RowDivider(54.dp)
                SwitchRow("Bedtime summary", s.bedtimeSummary, { on -> update { it.copy(bedtimeSummary = on) } }, icon = Icons.Rounded.Bedtime, iconColor = c.secondaryLabel,
                    subtitle = "At ${formatTime(s.bedtime.minusMinutes(30))}: what's left today, with “Move to tomorrow”")
                RowDivider(54.dp)
                SwitchRow("Water reminders", s.waterEnabled, { on -> update { it.copy(waterEnabled = on) } }, icon = Icons.Rounded.WaterDrop, iconColor = c.secondaryLabel,
                    subtitle = if (s.waterEnabled) "${s.waterGlasses} glasses, spread between when you wake and sleep" else null)
                if (s.waterEnabled) {
                    RowDivider(54.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Glasses a day", style = MaterialTheme.typography.bodyLarge, color = c.label, modifier = Modifier.weight(1f))
                        listOf(6, 8, 10, 12).forEach { n ->
                            Box(
                                Modifier.padding(start = 6.dp).size(38.dp).clip(CircleShape).background(if (s.waterGlasses == n) c.gold else c.fill)
                                    .clickable { update { it.copy(waterGlasses = n) } },
                                contentAlignment = Alignment.Center,
                            ) { Text("$n", style = MaterialTheme.typography.labelLarge, color = if (s.waterGlasses == n) c.onIvory else c.label) }
                        }
                    }
                }
            }
        }
        item {
            Caption("Shortcuts")
            GroupedCard {
                SettingRow("From notifications", icon = Icons.Rounded.NotificationsActive, iconColor = c.secondaryLabel,
                    value = if (app.rem.capture.Captured.hasAccess(context)) "On" else "Off", subtitle = "Turn any notification into a reminder", onClick = { onRoute("captured") })
                RowDivider(54.dp)
                SettingRow("Bills from messages", icon = Icons.Rounded.Sms, iconColor = c.secondaryLabel,
                    value = if (s.smsBills) "On" else "Off", subtitle = "Offer a reminder when a bill arrives by SMS", onClick = { onRoute("bills") })
                RowDivider(54.dp)
                SettingRow("Templates", icon = Icons.Rounded.AutoAwesomeMotion, iconColor = c.secondaryLabel, subtitle = "Travel, monthly bills, moving and more", onClick = { onRoute("templates") })
                RowDivider(54.dp)
                SettingRow("Quick Settings tile", icon = Icons.Rounded.Tune, iconColor = c.secondaryLabel, showChevron = false,
                    subtitle = "Pull down your notification shade, tap the pencil, and drag “Add reminder” in")
            }
        }
        item {
            Caption("Don't interrupt me")
            GroupedCard {
                SwitchRow("Wait while I'm on a call", s.respectCalls, { on -> update { it.copy(respectCalls = on) } }, icon = Icons.Rounded.Phone, iconColor = c.secondaryLabel)
                RowDivider(54.dp)
                SwitchRow("Wait until meetings end", s.respectMeetings, { on ->
                    if (on) calendarPermission.launch(Manifest.permission.READ_CALENDAR) else update { it.copy(respectMeetings = false) }
                }, icon = Icons.Rounded.CalendarMonth, iconColor = c.secondaryLabel, subtitle = "Uses busy events in your calendar")
                RowDivider(54.dp)
                val now = java.time.Instant.now()
                if (s.panicActive(now)) {
                    SettingRow("Silenced until ${formatTime(java.time.LocalDateTime.ofInstant(s.panicUntil, java.time.ZoneId.systemDefault()).toLocalTime())}",
                        icon = Icons.Rounded.NotificationsOff, iconColor = c.gold, value = "Resume", valueColor = c.gold, showChevron = false,
                        onClick = { scope.launch { container.service.endPanic() } })
                } else {
                    SettingRow("Silence everything", icon = Icons.Rounded.NotificationsOff, iconColor = c.secondaryLabel, value = "1 or 2 hours", onClick = { sheet = "panic" })
                }
            }
        }
        item {
            Caption("Places")
            GroupedCard {
                val home = places.firstOrNull { it.isHome }
                SettingRow(if (home == null) "Set home to where I am now" else "Home", icon = Icons.Rounded.Home, iconColor = c.secondaryLabel,
                    value = if (home == null) null else "Update", valueColor = c.gold, onClick = {
                        savingHome = true
                        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    })
                places.filter { !it.isHome }.forEach { p ->
                    RowDivider(54.dp)
                    SettingRow(p.name, icon = Icons.Rounded.Place, iconColor = c.secondaryLabel, value = "Remove", onClick = { scope.launch { container.database.places().delete(p.id); app.rem.triggers.Triggers.sync(context) } }, showChevron = false)
                }
                RowDivider(54.dp)
                SettingRow("Add where I am now", icon = Icons.Rounded.Place, iconColor = c.gold, onClick = {
                    savingHome = false
                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                })
            }
            Text("For “when I leave home” and “when I arrive” reminders. Location stays on your phone.", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
        }
        item {
            Caption("Alarm sounds")
            GroupedCard {
                listOf(Category.MEDICINE, Category.TASK, Category.BILL, Category.HEALTH, Category.WORK).forEachIndexed { i, cat ->
                    if (i > 0) RowDivider(66.dp)
                    Row(Modifier.clickable {
                        soundFor = cat
                        ringtonePicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "${cat.label} alarm sound")
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, s.sounds[cat]?.let(Uri::parse)),
                        )
                    }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryGlyph(cat, size = 32.dp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(cat.label, style = MaterialTheme.typography.bodyLarge, color = c.label) }
                        Text(s.sounds[cat]?.let { u -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(u))?.getTitle(context) }.getOrNull() } ?: "Default", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
                        androidx.compose.material3.Icon(Icons.Rounded.MusicNote, null, tint = c.tertiaryLabel, modifier = Modifier.padding(start = 8.dp).size(16.dp))
                    }
                }
            }
        }
        item {
            Caption("Accountability buddy")
            GroupedCard {
                FieldRow("Name", s.accountabilityName, "e.g. Asha") { v -> update { it.copy(accountabilityName = v) } }
                RowDivider()
                FieldRow("Phone", s.accountabilityPhone, "For a gentle text", KeyboardType.Phone) { v -> update { it.copy(accountabilityPhone = v) } }
            }
            Text("If you skip a habit 3 times in a week, Rem offers to message them. Nothing is sent without you.", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
        }
        item {
            Caption("Backup & export")
            GroupedCard {
                SwitchRow("Back up every night", s.nightlyBackup, { on -> update { it.copy(nightlyBackup = on) } }, icon = Icons.Rounded.Backup, iconColor = c.secondaryLabel,
                    subtitle = Backup.latestNightly(context)?.name?.removePrefix("rem-")?.removeSuffix(".json")?.let { "Last: $it" })
                RowDivider(54.dp)
                SettingRow("Save a backup file", icon = Icons.Rounded.Download, iconColor = c.secondaryLabel, onClick = { createBackup.launch("rem-backup-${LocalDate.now()}.json") })
                RowDivider(54.dp)
                SettingRow("Restore from a file", icon = Icons.Rounded.Upload, iconColor = c.secondaryLabel, onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "*/*")) })
                RowDivider(54.dp)
                SettingRow("Export to calendar (.ics)", icon = Icons.Rounded.CalendarMonth, iconColor = c.secondaryLabel, onClick = {
                    scope.launch { shareFile(context, Backup.exportIcs(context), "text/calendar", title = "Export to calendar") }
                })
                RowDivider(54.dp)
                SettingRow("Export as PDF", icon = Icons.Rounded.PictureAsPdf, iconColor = c.secondaryLabel, onClick = {
                    scope.launch { shareFile(context, Backup.exportPdf(context), "application/pdf", title = "Export as PDF") }
                })
            }
        }
        item {
            Caption("Reliability")
            GroupedCard { SettingRow("Permissions & battery", icon = Icons.Rounded.Shield, iconColor = c.secondaryLabel, onClick = onReliability) }
            Spacer(Modifier.height(20.dp))
        }
    }

    timeFor?.let { (key, initial) ->
        val ts = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { timeFor = null },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(ts.hour, ts.minute)
                    update {
                        when (key) {
                            "wake" -> it.copy(wakeTime = t)
                            "bed" -> it.copy(bedtime = t)
                            "charge" -> it.copy(chargingWindowStart = t)
                            else -> it.copy(morningCardTime = t)
                        }
                    }
                    scope.launch { container.service.rescheduleAll() }
                    timeFor = null
                }) { Text("Done", color = c.gold) }
            },
            dismissButton = { TextButton(onClick = { timeFor = null }) { Text("Cancel") } },
            text = { TimePicker(ts) },
        )
    }
    if (sheet == "panic") {
        OptionSheet("Silence everything", listOf(1L to "For 1 hour", 2L to "For 2 hours", 4L to "For 4 hours"), { false }, { h ->
            scope.launch { container.service.panicSnooze(h) }
            sheet = null
        }, { sheet = null })
    }
    namingPlace?.let { (lat, lng) ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { namingPlace = null },
            title = { Text("Name this place") },
            text = { GroupedCard { FieldRow("Name", name, "e.g. Office, Gym") { name = it } } },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.database.places().upsert(PlaceEntity(0, name.ifBlank { "Saved place" }, lat, lng)); app.rem.triggers.Triggers.sync(context) }
                    namingPlace = null
                }) { Text("Save", color = c.gold) }
            },
            dismissButton = { TextButton(onClick = { namingPlace = null }) { Text("Cancel") } },
        )
    }
    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK", color = c.gold) } }, text = { Text(m) })
    }
}
