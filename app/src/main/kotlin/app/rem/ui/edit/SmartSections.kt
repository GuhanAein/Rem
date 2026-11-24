package app.rem.ui.edit

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.rem.core.insights.CarLog
import app.rem.core.insights.Rotation
import app.rem.core.insights.Subscriptions
import app.rem.core.model.Kind
import app.rem.core.model.RandomTimes
import app.rem.core.model.TimeWindow
import app.rem.core.model.Trigger
import app.rem.data.PlaceEntity
import app.rem.triggers.Triggers
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.OptionSheet
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SecondaryButton
import app.rem.ui.components.SettingRow
import app.rem.ui.components.SwitchRow
import app.rem.ui.format.formatDay
import app.rem.ui.theme.Rem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val WINDOWS: List<Pair<TimeWindow?, String>> = listOf(
    null to "Anytime",
    TimeWindow.businessHours() to "Weekdays, 10 AM – 4 PM",
    TimeWindow(TimeWindow.WEEKDAYS, LocalTime.of(9, 0), LocalTime.of(18, 0)) to "Weekdays, 9 AM – 6 PM",
    TimeWindow(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), LocalTime.of(10, 0), LocalTime.of(20, 0)) to "Weekends, 10 AM – 8 PM",
    TimeWindow(DayOfWeek.entries.toSet(), LocalTime.of(18, 0), LocalTime.of(22, 0)) to "Evenings, 6 – 10 PM",
)

private val TIMES_A_DAY: List<Pair<List<Int>, String>> = listOf(
    emptyList<Int>() to "Once",
    listOf(9, 21) to "Twice (9 AM, 9 PM)",
    listOf(8, 14, 20) to "3 times (8, 2, 8)",
    listOf(8, 12, 16, 20) to "4 times (8, 12, 4, 8)",
)

private val RANDOMS: List<Pair<RandomTimes?, String>> = listOf(
    null to "Off",
    RandomTimes(3, LocalTime.of(10, 0), LocalTime.of(18, 0)) to "3 times, 10 AM – 6 PM",
    RandomTimes(5, LocalTime.of(9, 0), LocalTime.of(19, 0)) to "5 times, 9 AM – 7 PM",
    RandomTimes(8, LocalTime.of(8, 0), LocalTime.of(20, 0)) to "8 times, 8 AM – 8 PM",
)

/** Kind picker plus the fields that kind needs (subscription, warranty, car, plant, lend, return, chore). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KindSection(state: EditUiState, onKind: (Kind) -> Unit, onExtra: (String, String?) -> Unit, onChange: ((EditUiState) -> EditUiState) -> Unit) {
    val c = Rem.colors
    var pick by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf<String?>(null) }
    GroupedCard {
        SettingRow("Type", icon = Icons.Rounded.Category, iconColor = c.secondaryLabel, value = state.kind.label, onClick = { pick = true })
        when (state.kind) {
            Kind.SUBSCRIPTION -> {
                RowDivider(54.dp)
                FieldRow("Price", state.amount, "₹649", KeyboardType.Text) { v -> onChange { it.copy(amount = v) } }
                RowDivider(54.dp)
                SettingRow("Free trial ends", icon = Icons.Rounded.EventBusy, iconColor = c.secondaryLabel,
                    value = state.extras[Subscriptions.TRIAL_END]?.let { formatDay(LocalDate.parse(it)) } ?: "None", onClick = { pickDate = Subscriptions.TRIAL_END })
                RowDivider(54.dp)
                SwitchRow("Remind me to cancel before it renews", state.extras[Subscriptions.CANCEL_MODE] == "true", { onExtra(Subscriptions.CANCEL_MODE, if (it) "true" else null) })
            }
            Kind.WARRANTY, Kind.PLANT -> {
                RowDivider(54.dp)
                PhotoRow(if (state.kind == Kind.WARRANTY) "Receipt photo" else "Plant photo", state.extras[PHOTO]) { onExtra(PHOTO, it) }
                if (state.kind == Kind.WARRANTY) {
                    RowDivider(54.dp)
                    FieldRow("Product", state.extras["product"].orEmpty(), "e.g. Washing machine") { onExtra("product", it) }
                }
            }
            Kind.CAR_SERVICE -> {
                RowDivider(54.dp)
                FieldRow("Service every (km)", state.extras[CarLog.INTERVAL_KM].orEmpty(), "5000", KeyboardType.Number) { onExtra(CarLog.INTERVAL_KM, it) }
                RowDivider(54.dp)
                FieldRow("Last service at (km)", state.extras[CarLog.LAST_SERVICE_KM].orEmpty(), "20000", KeyboardType.Number) { onExtra(CarLog.LAST_SERVICE_KM, it) }
                RowDivider(54.dp)
                FieldRow("Odometer now (km)", state.extras[CarLog.ODOMETER].orEmpty(), "21500", KeyboardType.Number) { v ->
                    val km = v.toIntOrNull()
                    if (km == null) onExtra(CarLog.ODOMETER, v) else onChange { s ->
                        val r = CarLog.withOdometer(app.rem.core.model.Reminder(title = "", extras = s.extras), km, LocalDate.now())
                        s.copy(extras = r.extras)
                    }
                }
                val r = app.rem.core.model.Reminder(title = "", extras = state.extras)
                CarLog.kmLeft(r)?.let { left ->
                    val est = CarLog.estimateDate(r)?.let { " · around ${formatDay(it)}" }.orEmpty()
                    Text(
                        if (left > 0) "%,d km to the next service$est".format(left) else "Service is due now",
                        style = MaterialTheme.typography.bodySmall, color = if (left > 0) c.gold else c.danger,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                    )
                }
            }
            Kind.LEND, Kind.BORROW -> {
                RowDivider(54.dp)
                FieldRow(if (state.kind == Kind.LEND) "Lent to" else "Borrowed from", state.extras["person"].orEmpty(), "Name") { onExtra("person", it) }
                RowDivider(54.dp)
                FieldRow("Item", state.extras["item"].orEmpty(), "e.g. Drill") { onExtra("item", it) }
            }
            Kind.RETURN -> {
                RowDivider(54.dp)
                FieldRow("Store", state.extras["store"].orEmpty(), "e.g. Amazon") { onExtra("store", it) }
                Text("Set the return-by date above. Rem reminds you two days before.", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp))
            }
            Kind.CHORE -> {
                RowDivider(54.dp)
                FieldRow("Takes turns", state.extras[MEMBERS].orEmpty(), "Asha, Ravi, Meera") { v ->
                    onExtra(MEMBERS, v)
                    if (state.extras[ROTATION_START] == null) onExtra(ROTATION_START, LocalDate.now().toString())
                }
                val members = state.extras[MEMBERS].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val start = state.extras[ROTATION_START]?.let(LocalDate::parse) ?: LocalDate.now()
                Rotation.assignee(members, start, state.date ?: LocalDate.now())?.let {
                    Text("Next up: $it, then it rotates every week.", style = MaterialTheme.typography.bodySmall, color = c.gold, modifier = Modifier.padding(start = 18.dp, bottom = 14.dp))
                }
            }
            else -> Unit
        }
    }
    if (pick) {
        OptionSheet("Type", Kind.entries.map { it to it.label }, { it == state.kind }, { onKind(it); pick = false }, { pick = false })
    }
    pickDate?.let { key ->
        val ps = rememberDatePickerState(initialSelectedDateMillis = LocalDate.now().plusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton(onClick = {
                    ps.selectedDateMillis?.let { onExtra(key, Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    pickDate = null
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { onExtra(key, null); pickDate = null }) { Text("Clear") } },
        ) { DatePicker(ps) }
    }
}

/** Smarter timing: several times a day, random moments, allowed hours, an end date, deadline mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimingSection(state: EditUiState, onChange: ((EditUiState) -> EditUiState) -> Unit, onEscalate: (Boolean) -> Unit) {
    val c = Rem.colors
    var sheet by remember { mutableStateOf<String?>(null) }
    var pickUntil by remember { mutableStateOf(false) }
    GroupedCard {
        SettingRow("Times a day", icon = Icons.Rounded.AccessTime, iconColor = c.secondaryLabel,
            value = TIMES_A_DAY.firstOrNull { it.first == state.repeat.hours }?.second ?: "${state.repeat.hours.size} times", onClick = { sheet = "times" })
        RowDivider(54.dp)
        SettingRow("Random moments", icon = Icons.Rounded.Shuffle, iconColor = c.secondaryLabel,
            value = RANDOMS.firstOrNull { it.first == state.randomTimes }?.second ?: "${state.randomTimes?.perDay} times", onClick = { sheet = "random" })
        RowDivider(54.dp)
        SettingRow("Only between", icon = Icons.Rounded.Timelapse, iconColor = c.secondaryLabel,
            value = WINDOWS.firstOrNull { it.first == state.window }?.second ?: "Custom", onClick = { sheet = "window" })
        if (state.repeat.freq != null || state.randomTimes != null) {
            RowDivider(54.dp)
            SettingRow("Repeat until", icon = Icons.Rounded.EventBusy, iconColor = c.secondaryLabel,
                value = state.repeat.until?.let { formatDay(it) } ?: "Forever", onClick = { pickUntil = true })
        }
        RowDivider(54.dp)
        SwitchRow("Deadline mode", state.escalating, onEscalate, icon = Icons.Rounded.TrendingUp, iconColor = c.secondaryLabel,
            subtitle = if (state.escalating) "Gentle 7 and 3 days before, firm on the day" else null)
    }
    when (sheet) {
        "times" -> OptionSheet("Times a day", TIMES_A_DAY, { it == state.repeat.hours }, { hrs ->
            onChange { s ->
                s.copy(
                    repeat = s.repeat.copy(freq = if (hrs.isEmpty()) s.repeat.freq else (s.repeat.freq ?: app.rem.core.recurrence.Freq.DAILY), hours = hrs),
                    date = s.date ?: LocalDate.now(),
                    time = if (hrs.isEmpty()) s.time else LocalTime.of(hrs.first(), 0),
                    randomTimes = if (hrs.isEmpty()) s.randomTimes else null,
                )
            }
            sheet = null
        }, { sheet = null })
        "random" -> OptionSheet("Random moments", RANDOMS, { it == state.randomTimes }, { rt ->
            onChange { s -> s.copy(randomTimes = rt, date = s.date ?: LocalDate.now(), repeat = if (rt != null) s.repeat.copy(hours = emptyList()) else s.repeat) }
            sheet = null
        }, { sheet = null })
        "window" -> OptionSheet("Only fire between", WINDOWS, { it == state.window }, { w -> onChange { it.copy(window = w) }; sheet = null }, { sheet = null })
    }
    if (pickUntil) {
        val ps = rememberDatePickerState(initialSelectedDateMillis = LocalDate.now().plusDays(5).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickUntil = false },
            confirmButton = {
                TextButton(onClick = {
                    ps.selectedDateMillis?.let { ms -> onChange { s -> s.copy(repeat = s.repeat.copy(until = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())) } }
                    pickUntil = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { onChange { s -> s.copy(repeat = s.repeat.copy(until = null)) }; pickUntil = false }) { Text("Forever") } },
        ) { DatePicker(ps) }
    }
}

/** "Fire when…": event triggers, with the permission each one needs asked at the moment it's chosen. */
@Composable
fun TriggerSection(state: EditUiState, places: List<PlaceEntity>, onChange: ((EditUiState) -> EditUiState) -> Unit, onExtra: (String, String?) -> Unit, onManagePlaces: () -> Unit) {
    val c = Rem.colors
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<String?>(null) }
    val fine = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val bt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askFor(t: Trigger?) {
        when (t) {
            Trigger.LEAVE_HOME, Trigger.ARRIVE_PLACE, Trigger.LEAVE_PLACE -> {
                if (!Triggers.hasLocationPermission(context)) {
                    fine.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    if (Build.VERSION.SDK_INT >= 29) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            Trigger.HEADPHONES -> if (Build.VERSION.SDK_INT >= 31) bt.launch(Manifest.permission.BLUETOOTH_CONNECT)
            else -> Unit
        }
    }
    GroupedCard {
        SettingRow("Fire when", icon = Icons.Rounded.Sensors, iconColor = c.secondaryLabel, value = state.trigger?.label ?: "At the time", onClick = { sheet = "trigger" })
        if (state.trigger == Trigger.ARRIVE_PLACE || state.trigger == Trigger.LEAVE_PLACE) {
            RowDivider(54.dp)
            val place = places.firstOrNull { it.id.toString() == state.extras[Triggers.PLACE_ID] }
            SettingRow("Place", value = place?.name ?: "Choose", onClick = { sheet = "place" })
        }
        if (state.trigger == Trigger.LEAVE_HOME && places.none { it.isHome }) {
            RowDivider(54.dp)
            SettingRow("Set your home location", valueColor = c.gold, onClick = onManagePlaces)
        }
        if (state.trigger == Trigger.HEADPHONES) {
            Text("Works with Bluetooth headphones anytime; wired ones while Rem is open.", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp))
        }
    }
    when (sheet) {
        "trigger" -> OptionSheet("Fire when", listOf<Pair<Trigger?, String>>(null to "At the time") + Trigger.entries.map { it to it.label },
            { it == state.trigger }, { t -> onChange { it.copy(trigger = t) }; askFor(t); sheet = null }, { sheet = null })
        "place" -> OptionSheet("Place", places.map { it.id to it.name } + (-1L to "Manage places…"), { it.toString() == state.extras[Triggers.PLACE_ID] }, { id ->
            if (id == -1L) onManagePlaces() else onExtra(Triggers.PLACE_ID, id.toString())
            sheet = null
        }, { sheet = null })
    }
}

/** Follow-up, first step, privacy, and sending it to someone else. */
@Composable
fun MoreSection(state: EditUiState, onChange: ((EditUiState) -> EditUiState) -> Unit, invite: () -> java.io.File) {
    val c = Rem.colors
    val context = LocalContext.current
    GroupedCard {
        FieldRow("Then ask about", state.followUp, "e.g. Doctor visit", icon = Icons.Rounded.SubdirectoryArrowRight) { v -> onChange { it.copy(followUp = v) } }
        RowDivider(54.dp)
        FieldRow("Tiny first step", state.firstStep, "e.g. Open the tax portal", icon = Icons.Rounded.PlayArrow) { v -> onChange { it.copy(firstStep = v) } }
        RowDivider(54.dp)
        SwitchRow("Private", state.isPrivate, { on -> onChange { it.copy(isPrivate = on) } }, icon = Icons.Rounded.Lock, iconColor = c.secondaryLabel,
            subtitle = if (state.isPrivate) "Hidden behind app lock; notifications say “Private reminder”" else null)
    }
    Spacer(Modifier.height(12.dp))
    SecondaryButton("Remind someone else", onClick = {
        val file = invite()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/calendar").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "Reminder: ${state.title}. Open the attachment to add it to your calendar.")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Remind someone"))
    }, modifier = Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.Send)
    Text(
        "Sends a calendar invite with an alert built in. It works on any phone, even without Rem.",
        style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
fun FieldRow(
    label: String,
    value: String,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onValue: (String) -> Unit,
) {
    val c = Rem.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            androidx.compose.material3.Icon(icon, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.label)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = c.tertiaryLabel, textAlign = TextAlign.End)
            BasicTextField(
                value = value, onValueChange = onValue,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.gold, textAlign = TextAlign.End),
                cursorBrush = SolidColor(c.gold), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PhotoRow(label: String, uri: String?, onPicked: (String?) -> Unit) {
    val c = Rem.colors
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onPicked(picked.toString())
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(Icons.Rounded.AddAPhoto, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.label, modifier = Modifier.weight(1f))
        val bmp = rememberUriBitmap(uri)
        if (bmp != null) {
            Image(bmp, label, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Text(if (uri == null) "Add" else "Change", style = MaterialTheme.typography.bodyMedium, color = c.gold)
        }
    }
}

/** Loads a downscaled image from a content URI (no image library needed). */
@Composable
fun rememberUriBitmap(uri: String?, maxPx: Int = 512): ImageBitmap? {
    val context = LocalContext.current
    val state by produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val u = Uri.parse(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
                context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    return state
}

const val PHOTO = "photo"
const val MEMBERS = "members"
const val ROTATION_START = "rotationStart"
