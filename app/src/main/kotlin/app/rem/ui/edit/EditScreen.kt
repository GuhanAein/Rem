package app.rem.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.core.recurrence.Freq
import app.rem.core.recurrence.describe
import app.rem.ui.components.CategoryBubble
import app.rem.ui.components.GradientButton
import app.rem.ui.components.SectionCard
import app.rem.ui.components.SelectPill
import app.rem.ui.components.color
import app.rem.ui.components.icon
import app.rem.ui.components.label
import app.rem.ui.format.describeOffset
import app.rem.ui.format.formatDay
import app.rem.ui.format.formatTime
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun EditScreen(viewModel: EditViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EditContent(
        state = state,
        onBack = onBack,
        onChange = viewModel::update,
        onCategory = viewModel::selectCategory,
        onSave = { viewModel.save(onBack) },
        onDelete = { viewModel.delete(onBack) },
    )
}

private val ALERT_CHOICES = listOf(0, 5, 15, 30, 60, 1440, 3 * 1440, 7 * 1440, 30 * 1440)
private val NAG_CHOICES = listOf<Int?>(null, 1, 5, 10, 15, 30)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditContent(
    state: EditUiState,
    onBack: () -> Unit = {},
    onChange: ((EditUiState) -> EditUiState) -> Unit = {},
    onCategory: (Category) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val accent = state.category.color

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                    Spacer(Modifier.width(12.dp))
                    Text(if (state.isNew) "New reminder" else "Edit reminder", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (!state.isNew) RoundIcon(Icons.Rounded.DeleteOutline, "Delete") { confirmDelete = true }
                }
            }

            // Title + notes
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(18.dp)) {
                        CategoryBubble(state.category, size = 48.dp, selected = true)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Field(state.title, "What should I remind you?", MaterialTheme.typography.titleLarge) { v -> onChange { it.copy(title = v) } }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.AutoMirrored.Rounded.Notes, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                Spacer(Modifier.width(8.dp))
                                Field(state.notes, "Add notes", MaterialTheme.typography.bodyMedium) { v -> onChange { it.copy(notes = v) } }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard("Category", Icons.Rounded.Category) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.entries.forEach { cat ->
                            SelectPill(cat.label, cat == state.category, { onCategory(cat) }, icon = cat.icon, accent = cat.color)
                        }
                    }
                }
            }

            item {
                SectionCard("When", Icons.Rounded.Schedule) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile(Icons.Rounded.CalendarMonth, "Date", state.date?.let { formatDay(it, today) } ?: "No date", accent, Modifier.weight(1f)) { showDate = true }
                        Tile(Icons.Rounded.Schedule, "Time", formatTime(state.time), accent, Modifier.weight(1f), enabled = state.date != null) { showTime = true }
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectPill("Today", state.date == today, { onChange { it.copy(date = today) } }, accent = accent)
                        SelectPill("Tomorrow", state.date == today.plusDays(1), { onChange { it.copy(date = today.plusDays(1)) } }, accent = accent)
                        SelectPill("Next week", state.date == today.plusWeeks(1), { onChange { it.copy(date = today.plusWeeks(1)) } }, accent = accent)
                        SelectPill("No date", state.date == null, { onChange { it.copy(date = null, repeat = RepeatForm()) } }, accent = accent)
                    }
                }
            }

            item {
                SectionCard("Repeat", Icons.Rounded.Repeat) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectPill("Never", state.repeat.freq == null, { onChange { it.copy(repeat = RepeatForm()) } }, accent = accent)
                        listOf(Freq.DAILY to "Daily", Freq.WEEKLY to "Weekly", Freq.MONTHLY to "Monthly", Freq.YEARLY to "Yearly").forEach { (f, label) ->
                            SelectPill(label, state.repeat.freq == f, {
                                onChange { s -> s.copy(date = s.date ?: today, repeat = s.repeat.copy(freq = f, weekdays = if (f == Freq.WEEKLY) setOf((s.date ?: today).dayOfWeek) else emptySet(), lastDayOfMonth = false)) }
                            }, accent = accent)
                        }
                    }
                    AnimatedVisibility(state.repeat.freq != null) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Every", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.width(12.dp))
                                Stepper(state.repeat.interval, accent) { n -> onChange { it.copy(repeat = it.repeat.copy(interval = n)) } }
                                Spacer(Modifier.width(12.dp))
                                val unit = when (state.repeat.freq) { Freq.DAILY -> "day"; Freq.WEEKLY -> "week"; Freq.MONTHLY -> "month"; else -> "year" }
                                Text(if (state.repeat.interval == 1) unit else "${unit}s", style = MaterialTheme.typography.bodyLarge)
                            }
                            if (state.repeat.freq == Freq.WEEKLY) {
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    DayOfWeek.entries.forEach { d ->
                                        val on = d in state.repeat.weekdays
                                        val bg by animateColorAsState(if (on) accent else MaterialTheme.colorScheme.surfaceContainer, label = "dow")
                                        Box(
                                            Modifier.size(38.dp).clip(CircleShape).background(bg).clickable(role = Role.Checkbox) {
                                                onChange { s -> s.copy(repeat = s.repeat.copy(weekdays = if (on) s.repeat.weekdays - d else s.repeat.weekdays + d)) }
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(d.name.take(1), color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                            if (state.repeat.freq == Freq.MONTHLY) {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("On the last day of the month", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Switch(state.repeat.lastDayOfMonth, { v -> onChange { it.copy(repeat = it.repeat.copy(lastDayOfMonth = v)) } })
                                }
                            }
                            state.repeat.toRule(state.date)?.let {
                                Spacer(Modifier.height(10.dp))
                                Text(it.describe(), style = MaterialTheme.typography.labelLarge, color = accent)
                            }
                        }
                    }
                }
            }

            item {
                SectionCard("Alerts", Icons.Rounded.Notifications) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ALERT_CHOICES.forEach { m ->
                            val on = m in state.alertOffsets
                            SelectPill(describeOffset(m), on, {
                                onChange { s -> s.copy(alertOffsets = if (on && s.alertOffsets.size > 1) s.alertOffsets - m else s.alertOffsets + m) }
                            }, icon = if (on) Icons.Rounded.Check else null, accent = accent)
                        }
                    }
                }
            }

            item {
                SectionCard("How to remind you", Icons.Rounded.NotificationsActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StyleCard(Icons.Rounded.Notifications, "Notification", "A gentle ping", state.style == AlertStyle.NOTIFICATION, accent, Modifier.weight(1f)) {
                            onChange { it.copy(style = AlertStyle.NOTIFICATION) }
                        }
                        StyleCard(Icons.Rounded.Alarm, "Alarm", "Full screen, rings", state.style == AlertStyle.ALARM, accent, Modifier.weight(1f)) {
                            onChange { it.copy(style = AlertStyle.ALARM, nagMinutes = it.nagMinutes ?: 5) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Keep reminding me until it's done", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NAG_CHOICES.forEach { n ->
                            SelectPill(if (n == null) "Off" else "Every $n min", state.nagMinutes == n, { onChange { it.copy(nagMinutes = n) } }, accent = accent)
                        }
                    }
                }
            }

            item {
                SectionCard("Priority", Icons.Rounded.Flag) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Priority.entries.reversed().forEach { p ->
                            val c = p.color ?: MaterialTheme.colorScheme.onSurfaceVariant
                            SelectPill(p.label, state.priority == p, { onChange { it.copy(priority = p) } }, icon = Icons.Rounded.Flag, accent = c)
                        }
                    }
                }
            }

            if (state.category == Category.BILL || state.amount.isNotBlank()) {
                item {
                    SectionCard("Amount", Icons.Rounded.Payments) {
                        Field(state.amount, "e.g. ₹12,000", MaterialTheme.typography.titleLarge, KeyboardType.Text) { v -> onChange { it.copy(amount = v) } }
                    }
                }
            }
        }

        GradientButton(
            text = if (state.isNew) "Save reminder" else "Save changes",
            onClick = onSave,
            enabled = state.canSave,
            icon = Icons.Rounded.EventAvailable,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
        )
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (state.date ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange { it.copy(date = d) }
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(pickerState) }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(state.time.hour, state.time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange { it.copy(time = LocalTime.of(timeState.hour, timeState.minute)) }
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(timeState) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this reminder?") },
            text = { Text("“${state.title}” will be removed and its alarms cancelled.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = description) }
    }
}

@Composable
private fun Field(
    value: String,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValue: (String) -> Unit,
) {
    Box {
        if (value.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, value: String, accent: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = if (enabled) 0.10f else 0.04f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = accent.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

@Composable
private fun Stepper(value: Int, accent: Color, onValue: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onValue((value - 1).coerceAtLeast(1)) }, enabled = value > 1) { Icon(Icons.Rounded.Remove, "Fewer") }
        Text("$value", style = MaterialTheme.typography.titleMedium, color = accent, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(onClick = { onValue((value + 1).coerceAtMost(99)) }) { Icon(Icons.Rounded.Add, "More") }
    }
}

@Composable
private fun StyleCard(icon: ImageVector, title: String, subtitle: String, selected: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.outlineVariant, label = "styleBorder")
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(18.dp))
            .background(if (selected) accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
