package app.rem.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.core.model.Reminder
import app.rem.core.parser.ParsedReminder
import app.rem.core.parser.QuickAddParser
import app.rem.core.recurrence.RecurrenceRule
import app.rem.core.recurrence.describe
import app.rem.ui.components.CategoryGlyph
import app.rem.ui.components.OptionSheet
import app.rem.ui.components.Token
import app.rem.ui.components.label
import app.rem.ui.format.describeDue
import app.rem.ui.theme.Rem
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Turns what the user typed or said (plus overrides) into a reminder with smart category defaults. */
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

enum class QuickPick(val label: String) { HOUR("In 1 hour"), EVENING("This evening"), TOMORROW("Tomorrow"), NEXT_WEEK("Next week") }

fun QuickPick.resolve(now: LocalDateTime): LocalDateTime = when (this) {
    QuickPick.HOUR -> now.plusHours(1).withSecond(0).withNano(0)
    QuickPick.EVENING -> now.toLocalDate().atTime(18, 0).let { if (it.isAfter(now)) it else it.plusDays(1) }
    QuickPick.TOMORROW -> now.toLocalDate().plusDays(1).atTime(9, 0)
    QuickPick.NEXT_WEEK -> now.toLocalDate().with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atTime(9, 0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onMoreOptions: (String) -> Unit,
    onVoice: () -> Unit = {},
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
        containerColor = Rem.colors.card,
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
            onSave = { if (text.isNotBlank()) onSave(buildQuickReminder(parsed, category, due, alarm, clock.instant())) },
            onMoreOptions = { onMoreOptions(text) },
            onVoice = onVoice,
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
    onVoice: () -> Unit = {},
    autoFocus: Boolean = false,
) {
    val c = Rem.colors
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val hasText = text.isNotBlank()
    var pickCategory by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 12.dp).animateContentSize()) {
        Row(verticalAlignment = Alignment.Top) {
            CategoryGlyph(category, Modifier.padding(top = 2.dp).clip(CircleShape).clickable { pickCategory = true }, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).padding(top = 2.dp)) {
                if (text.isEmpty()) Text("New Reminder", style = MaterialTheme.typography.titleLarge, color = c.tertiaryLabel)
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = c.label),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        if (text.isEmpty()) {
            Text(
                "Try “Pay rent every 1st at 9am”",
                style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel,
                modifier = Modifier.padding(start = 40.dp, top = 4.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        FlowRow(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Fade(due != null) { Token(describeDue(due, now.toLocalDate()), Icons.Rounded.CalendarMonth, tint = c.accent) }
            val repeat = parsed.recurrence ?: RecurrenceRule.parseOrNull(category.defaultRecurrence?.takeIf { due != null })
            Fade(repeat != null) { Token(repeat?.describe().orEmpty(), Icons.Rounded.Repeat) }
            Fade(parsed.priority == Priority.HIGH) { Token(parsed.priority.label, Icons.Rounded.Flag, tint = c.warning) }
            Fade(hasText) { Token(category.label, null, onClick = { pickCategory = true }) }
            Fade(hasText) { Token(if (alarm) "Alarm" else "Notification", Icons.Rounded.Alarm, tint = if (alarm) c.accent else c.secondaryLabel, onClick = { onAlarm(!alarm) }) }
        }
        AnimatedVisibility(due == null && hasText, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            Row(Modifier.padding(start = 40.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickPick.entries.forEach { pick ->
                    Text(
                        pick.label,
                        style = MaterialTheme.typography.labelMedium, color = c.accent,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(c.fill).clickable(role = Role.Button) { onQuickPick(pick.resolve(now)) }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.Rounded.Mic, "Speak instead", onVoice)
            IconAction(Icons.Rounded.Tune, "Details", onMoreOptions)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (hasText) c.accent else c.fill)
                    .clickable(enabled = hasText, role = Role.Button, onClickLabel = "Add", onClick = onSave),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ArrowUpward, "Add", tint = if (hasText) Color.White else c.tertiaryLabel, modifier = Modifier.size(20.dp)) }
        }
    }

    if (pickCategory) {
        OptionSheet(
            title = "Category",
            options = Category.entries.map { it to it.label },
            isSelected = { it == category },
            onSelect = { onCategory(it); pickCategory = false },
            onDismiss = { pickCategory = false },
            icons = { cat -> { CategoryGlyph(cat) } },
        )
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Rem.colors.accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Fade(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f), exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f)) { content() }
}
