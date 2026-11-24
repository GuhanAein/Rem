package app.rem.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.core.model.Reminder
import app.rem.core.parser.ParsedReminder
import app.rem.core.parser.QuickAddParser
import app.rem.core.recurrence.RecurrenceRule
import app.rem.core.recurrence.describe
import app.rem.ui.components.CategoryBubble
import app.rem.ui.components.GradientButton
import app.rem.ui.components.color
import app.rem.ui.components.icon
import app.rem.ui.components.label
import app.rem.ui.format.describeDue
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Turns what the user typed (plus their overrides) into a reminder with smart category defaults. */
fun buildQuickReminder(
    parsed: ParsedReminder,
    category: Category,
    dueAt: LocalDateTime?,
    alarm: Boolean,
    now: Instant,
): Reminder {
    val recurrence = parsed.recurrence?.toRRule() ?: category.defaultRecurrence?.takeIf { dueAt != null }
    return Reminder(
        title = parsed.title,
        category = category,
        priority = parsed.priority,
        dueAt = dueAt,
        recurrence = recurrence,
        alertStyle = if (alarm) AlertStyle.ALARM else AlertStyle.NOTIFICATION,
        alertOffsets = (category.defaultAlertOffsets + 0).distinct(),
        nagMinutes = if (alarm) (category.defaultNagMinutes ?: 5) else null,
        createdAt = now,
    )
}

private enum class QuickPick(val label: String) { HOUR("In 1 hour"), EVENING("This evening"), TOMORROW("Tomorrow 9 AM"), WEEKEND("Weekend"), NEXT_WEEK("Next week") }

private fun QuickPick.resolve(now: LocalDateTime): LocalDateTime = when (this) {
    QuickPick.HOUR -> now.plusHours(1).withSecond(0).withNano(0)
    QuickPick.EVENING -> now.toLocalDate().atTime(18, 0).let { if (it.isAfter(now)) it else it.plusDays(1) }
    QuickPick.TOMORROW -> now.toLocalDate().plusDays(1).atTime(9, 0)
    QuickPick.WEEKEND -> now.toLocalDate().with(TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY)).atTime(10, 0)
    QuickPick.NEXT_WEEK -> now.toLocalDate().with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atTime(9, 0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onMoreOptions: (String) -> Unit,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val parser = remember(clock) { QuickAddParser(clock) }
    var text by remember { mutableStateOf(initialText) }
    var categoryOverride by remember { mutableStateOf<Category?>(null) }
    var dueOverride by remember { mutableStateOf<LocalDateTime?>(null) }
    var alarmOverride by remember { mutableStateOf<Boolean?>(null) }

    val parsed = remember(text) { parser.parse(text) }
    val category = categoryOverride ?: parsed.category
    val due = dueOverride ?: parsed.dueAt
    val alarm = alarmOverride ?: (category.defaultStyle == AlertStyle.ALARM)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        QuickAddContent(
            text = text,
            onTextChange = { text = it; dueOverride = null },
            parsed = parsed,
            category = category,
            due = due,
            alarm = alarm,
            onCategory = { categoryOverride = it; alarmOverride = null },
            onQuickPick = { dueOverride = it },
            onAlarm = { alarmOverride = it },
            onSave = {
                if (text.isNotBlank()) onSave(buildQuickReminder(parsed, category, due, alarm, clock.instant()))
            },
            onMoreOptions = { onMoreOptions(text) },
            now = LocalDateTime.now(clock),
            autoFocus = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddContent(
    text: String,
    onTextChange: (String) -> Unit,
    parsed: ParsedReminder,
    category: Category,
    due: LocalDateTime?,
    alarm: Boolean,
    onCategory: (Category) -> Unit,
    onQuickPick: (LocalDateTime) -> Unit,
    onAlarm: (Boolean) -> Unit,
    onSave: () -> Unit,
    onMoreOptions: () -> Unit,
    now: LocalDateTime,
    autoFocus: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val hasText = text.isNotBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 16.dp)
            .animateContentSize(),
    ) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Type it the way you'd say it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.padding(horizontal = 24.dp)) {
            if (text.isEmpty()) {
                Text(
                    "Call mom tomorrow at 6pm",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }

        Spacer(Modifier.height(16.dp))
        // Live understanding of the sentence.
        FlowRow(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreviewChip(Icons.Rounded.CalendarMonth, describeDue(due, now.toLocalDate()), MaterialTheme.colorScheme.primary, active = due != null)
            val repeat = parsed.recurrence ?: RecurrenceRule.parseOrNull(category.defaultRecurrence?.takeIf { due != null })
            repeat?.let { PreviewChip(Icons.Rounded.Repeat, it.describe(), Color(0xFF06B6D4), active = true) }
            if (parsed.priority != Priority.NONE) {
                PreviewChip(Icons.Rounded.Flag, "${parsed.priority.label} priority", Color(0xFFEF4444), active = true)
            }
            if (alarm) PreviewChip(Icons.Rounded.Alarm, "Rings until done", category.color, active = true)
        }

        AnimatedVisibility(visible = due == null && hasText, enter = fadeIn(), exit = fadeOut()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QuickPick.entries) { pick ->
                    Text(
                        pick.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(role = Role.Button) { onQuickPick(pick.resolve(now)) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "CATEGORY",
            style = app.rem.ui.theme.SectionLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Category.entries) { cat ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.RadioButton) { onCategory(cat) }.padding(4.dp),
                ) {
                    CategoryBubble(cat, size = 48.dp, selected = cat == category)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        cat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (cat == category) cat.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Alarm, null, tint = if (alarm) category.color else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Ring like an alarm", style = MaterialTheme.typography.titleSmall)
                Text("Full screen, keeps reminding until you act", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = alarm,
                onCheckedChange = onAlarm,
                colors = SwitchDefaults.colors(checkedTrackColor = category.color),
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMoreOptions) {
                Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("More options")
            }
            Spacer(Modifier.weight(1f))
            GradientButton("Save", onClick = onSave, enabled = hasText, icon = Icons.AutoMirrored.Rounded.Send, height = 52.dp)
        }
    }
}

@Composable
private fun PreviewChip(icon: ImageVector, text: String, tint: Color, active: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) tint.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (active) tint.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (active) tint else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (active) tint else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
