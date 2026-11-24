package app.rem.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.core.recurrence.Freq
import app.rem.core.recurrence.describe
import app.rem.ui.components.CategoryGlyph
import app.rem.ui.components.color
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.OptionSheet
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SegmentedControl
import app.rem.ui.components.SettingRow
import app.rem.ui.components.SwitchRow
import app.rem.ui.format.describeOffset
import app.rem.ui.format.formatDay
import app.rem.ui.format.formatTime
import app.rem.ui.motion.sharedBounds
import app.rem.ui.theme.Rem
import app.rem.container
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun EditScreen(viewModel: EditViewModel, onBack: () -> Unit, onManagePlaces: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val crowd by viewModel.crowd.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val places by remember { context.container.database.places().observeAll() }.collectAsStateWithLifecycle(emptyList<app.rem.data.PlaceEntity>())
    EditContent(
        state = state,
        onBack = onBack,
        onChange = viewModel::update,
        onCategory = viewModel::selectCategory,
        onSave = { viewModel.save(onBack) },
        onDelete = { viewModel.delete(onBack) },
        onKind = viewModel::setKind,
        onExtra = viewModel::setExtra,
        onEscalate = viewModel::setEscalating,
        places = places,
        onManagePlaces = onManagePlaces,
        invite = viewModel::inviteFile,
    )
    if (crowd.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.keepAsIs(onBack) },
            title = { Text("That time is busy") },
            text = { Text("You have ${crowd.size} reminders within 15 minutes. Spread them out so each one gets your attention?") },
            confirmButton = { TextButton(onClick = { viewModel.spreadOut(onBack) }) { Text("Spread out", color = Rem.colors.gold) } },
            dismissButton = { TextButton(onClick = { viewModel.keepAsIs(onBack) }) { Text("Keep as is") } },
        )
    }
}

private val ALERT_CHOICES = listOf(0, 5, 15, 30, 60, 1440, 3 * 1440, 7 * 1440, 30 * 1440)
private val NAG_CHOICES = listOf<Int?>(null, 1, 5, 10, 15, 30)

private enum class RepeatPreset(val label: String) {
    NEVER("Never"), DAILY("Every Day"), WEEKDAYS("Weekdays"), WEEKLY("Every Week"), BIWEEKLY("Every 2 Weeks"),
    MONTHLY("Every Month"), MONTH_END("Last Day of Month"), YEARLY("Every Year"),
}

private fun RepeatForm.preset(): RepeatPreset? = when {
    freq == null -> RepeatPreset.NEVER
    freq == Freq.DAILY && interval == 1 -> RepeatPreset.DAILY
    freq == Freq.WEEKLY && interval == 1 && weekdays == setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> RepeatPreset.WEEKDAYS
    freq == Freq.WEEKLY && interval == 1 && weekdays.size <= 1 -> RepeatPreset.WEEKLY
    freq == Freq.WEEKLY && interval == 2 && weekdays.size <= 1 -> RepeatPreset.BIWEEKLY
    freq == Freq.MONTHLY && interval == 1 && lastDayOfMonth -> RepeatPreset.MONTH_END
    freq == Freq.MONTHLY && interval == 1 -> RepeatPreset.MONTHLY
    freq == Freq.YEARLY && interval == 1 -> RepeatPreset.YEARLY
    else -> null
}

private fun RepeatPreset.toForm(anchor: LocalDate): RepeatForm = when (this) {
    RepeatPreset.NEVER -> RepeatForm()
    RepeatPreset.DAILY -> RepeatForm(Freq.DAILY)
    RepeatPreset.WEEKDAYS -> RepeatForm(Freq.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
    RepeatPreset.WEEKLY -> RepeatForm(Freq.WEEKLY, weekdays = setOf(anchor.dayOfWeek))
    RepeatPreset.BIWEEKLY -> RepeatForm(Freq.WEEKLY, 2, setOf(anchor.dayOfWeek))
    RepeatPreset.MONTHLY -> RepeatForm(Freq.MONTHLY)
    RepeatPreset.MONTH_END -> RepeatForm(Freq.MONTHLY, lastDayOfMonth = true)
    RepeatPreset.YEARLY -> RepeatForm(Freq.YEARLY)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContent(
    state: EditUiState,
    onBack: () -> Unit = {},
    onChange: ((EditUiState) -> EditUiState) -> Unit = {},
    onCategory: (Category) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
    onKind: (app.rem.core.model.Kind) -> Unit = {},
    onExtra: (String, String?) -> Unit = { _, _ -> },
    onEscalate: (Boolean) -> Unit = {},
    places: List<app.rem.data.PlaceEntity> = emptyList(),
    onManagePlaces: () -> Unit = {},
    invite: () -> java.io.File = { java.io.File("") },
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val c = Rem.colors
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAmount by remember { mutableStateOf(false) }
    val sharedId = state.original?.id

    Column(
        Modifier.fillMaxSize()
            .then(if (sharedId != null) Modifier.sharedBounds("card-$sharedId") else Modifier)
            .background(c.canvas),
    ) {
        // Navigation bar: Cancel · Details · Done
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Cancel", style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel) }
            Text((if (state.isNew) "New reminder" else "Details").uppercase(), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            TextButton(onClick = onSave, enabled = state.canSave) {
                Text(if (state.isNew) "Add" else "Save", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = if (state.canSave) c.gold else c.tertiaryLabel)
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            item {
                Row(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryGlyph(state.category, size = 30.dp, selected = true)
                    Spacer(Modifier.width(10.dp))
                    Text(state.category.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = state.category.color)
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    if (state.title.isEmpty()) Text("Title", style = MaterialTheme.typography.displaySmall, color = c.tertiaryLabel)
                    BasicTextField(
                        value = state.title,
                        onValueChange = { v -> onChange { it.copy(title = v) } },
                        textStyle = MaterialTheme.typography.displaySmall.copy(color = c.label),
                        cursorBrush = SolidColor(c.gold),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                GroupedCard {
                    Field(state.notes, "Notes", MaterialTheme.typography.bodyLarge, minHeight = 64) { v -> onChange { it.copy(notes = v) } }
                    if (state.category == Category.BILL || state.amount.isNotBlank() || showAmount) {
                        RowDivider()
                        Field(state.amount, "Amount", MaterialTheme.typography.bodyLarge) { v -> onChange { it.copy(amount = v) } }
                    }
                }
            }

            item {
                GroupedCard {
                    SwitchRow(
                        "Date", state.date != null,
                        onChange = { on -> onChange { it.copy(date = if (on) today else null, repeat = if (on) it.repeat else RepeatForm()) } },
                        icon = Icons.Rounded.Event, iconColor = c.secondaryLabel,
                        subtitle = state.date?.let { "${formatDay(it, today)}, ${formatTime(state.time)}" },
                    )
                    AnimatedVisibility(state.date != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            RowDivider(54.dp)
                            SettingRow("Day", icon = Icons.Rounded.CalendarMonth, iconColor = c.secondaryLabel, value = state.date?.let { formatDay(it, today) } ?: "", onClick = { showDate = true }, valueColor = c.gold, showChevron = false)
                            RowDivider(54.dp)
                            SettingRow("Time", icon = Icons.Rounded.Schedule, iconColor = c.secondaryLabel, value = formatTime(state.time), valueColor = c.gold, onClick = { showTime = true }, showChevron = false)
                        }
                    }
                }
            }

            item {
                GroupedCard {
                    val preset = state.repeat.preset()
                    SettingRow(
                        "Repeat", icon = Icons.Rounded.Repeat, iconColor = c.secondaryLabel,
                        value = preset?.label ?: state.repeat.toRule(state.date)?.describe() ?: "Never",
                        onClick = { sheet = "repeat" },
                    )
                    RowDivider(54.dp)
                    SettingRow(
                        "Alerts", icon = Icons.Rounded.Notifications, iconColor = c.secondaryLabel,
                        value = state.alertOffsets.sorted().joinToString(", ") { describeOffset(it) },
                        onClick = { sheet = "alerts" },
                    )
                    RowDivider(54.dp)
                    SwitchRow(
                        "Ring Like an Alarm", state.style == AlertStyle.ALARM,
                        onChange = { on -> onChange { it.copy(style = if (on) AlertStyle.ALARM else AlertStyle.NOTIFICATION, nagMinutes = if (on) (it.nagMinutes ?: 5) else it.nagMinutes) } },
                        icon = Icons.Rounded.Alarm, iconColor = c.secondaryLabel,
                    )
                    RowDivider(54.dp)
                    SettingRow(
                        "Keep Reminding", icon = Icons.Rounded.NotificationsActive, iconColor = c.secondaryLabel,
                        value = state.nagMinutes?.let { "Every $it min" } ?: "Off",
                        onClick = { sheet = "nag" },
                    )
                }
            }

            item { TimingSection(state, onChange, onEscalate) }
            item { TriggerSection(state, places, onChange, onExtra, onManagePlaces) }
            item { KindSection(state, onKind, onExtra, onChange) }
            item {
                GroupedCard {
                    SettingRow(
                        "Category", value = state.category.label, onClick = { sheet = "category" },
                        trailing = null,
                        icon = null,
                    )
                    RowDivider()
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(Icons.Rounded.Flag, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Priority", style = MaterialTheme.typography.bodyLarge, color = c.label)
                        }
                        Spacer(Modifier.height(12.dp))
                        val order = listOf(Priority.NONE, Priority.LOW, Priority.MEDIUM, Priority.HIGH)
                        SegmentedControl(listOf("None", "Low", "Medium", "High"), order.indexOf(state.priority), { i -> onChange { it.copy(priority = order[i]) } })
                    }
                }
            }

            if (state.category != Category.BILL && state.amount.isBlank() && !showAmount) {
                item {
                    GroupedCard {
                        SettingRow("Add Amount", icon = Icons.Rounded.Payments, iconColor = c.secondaryLabel, onClick = { showAmount = true })
                    }
                }
            }

            item { MoreSection(state, onChange, invite) }

            if (!state.isNew) {
                item {
                    GroupedCard {
                        Text(
                            "Delete Reminder",
                            style = MaterialTheme.typography.bodyLarge, color = c.danger, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { confirmDelete = true }.padding(vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }

    when (sheet) {
        "repeat" -> {
            val current = state.repeat.preset()
            OptionSheet(
                title = "Repeat",
                options = RepeatPreset.entries.map { it to it.label },
                isSelected = { it == current },
                onSelect = { p -> onChange { s -> s.copy(date = s.date ?: today, repeat = p.toForm(s.date ?: today)) }; sheet = null },
                onDismiss = { sheet = null },
            )
        }
        "alerts" -> OptionSheet(
            title = "Alerts",
            options = ALERT_CHOICES.map { it to describeOffset(it) },
            isSelected = { it in state.alertOffsets },
            onSelect = { m -> onChange { s -> s.copy(alertOffsets = if (m in s.alertOffsets && s.alertOffsets.size > 1) s.alertOffsets - m else s.alertOffsets + m) } },
            onDismiss = { sheet = null },
        )
        "nag" -> OptionSheet(
            title = "Keep Reminding",
            options = NAG_CHOICES.map { it to (it?.let { n -> "Every $n minutes" } ?: "Off") },
            isSelected = { it == state.nagMinutes },
            onSelect = { n -> onChange { it.copy(nagMinutes = n) }; sheet = null },
            onDismiss = { sheet = null },
        )
        "category" -> OptionSheet(
            title = "Category",
            options = Category.entries.map { it to it.label },
            isSelected = { it == state.category },
            onSelect = { cat -> onCategory(cat); sheet = null },
            onDismiss = { sheet = null },
            icons = { cat -> { CategoryGlyph(cat) } },
        )
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = (state.date ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> onChange { it.copy(date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) } }
                    showDate = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(pickerState) }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(state.time.hour, state.time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = { onChange { it.copy(time = LocalTime.of(timeState.hour, timeState.minute)) }; showTime = false }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(timeState) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Reminder?") },
            text = { Text("“${state.title}” and its alerts will be removed.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Field(value: String, placeholder: String, style: TextStyle, minHeight: Int = 0, onValue: (String) -> Unit) {
    val c = Rem.colors
    Box(Modifier.fillMaxWidth().heightIn(min = maxOf(48, minHeight).dp).padding(horizontal = 16.dp, vertical = 13.dp)) {
        if (value.isBlank()) Text(placeholder, style = style, color = c.tertiaryLabel)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = style.copy(color = c.label),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
