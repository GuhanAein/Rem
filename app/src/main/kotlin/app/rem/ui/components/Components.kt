package app.rem.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.ui.motion.pressScale
import app.rem.ui.motion.sharedBounds
import app.rem.ui.theme.Rem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A solid colour circle with a white glyph: the only place category colour appears. */
@Composable
fun CategoryGlyph(category: Category, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(modifier.size(size).clip(CircleShape).background(category.color), contentAlignment = Alignment.Center) {
        Icon(category.icon, contentDescription = category.label, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/** Hollow ring that fills and draws a check when completed. */
@Composable
fun CheckCircle(color: Color, checked: Boolean, onCheck: () -> Unit, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val ring = Rem.colors.tertiaryLabel
    val fill by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "fill")
    val draw by animateFloatAsState(if (checked) 1f else 0f, tween(240, delayMillis = 100), label = "draw")
    Box(
        modifier
            .size(44.dp) // generous touch target
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClickLabel = "Mark done", onClick = onCheck),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = 1.6.dp.toPx()
            val r = this.size.minDimension / 2
            drawCircle(if (fill > 0f) color else ring, radius = r - stroke / 2, style = Stroke(stroke))
            if (fill > 0f) drawCircle(color, radius = (r - stroke / 2) * fill)
            if (draw > 0f) {
                val p1 = Offset(this.size.width * 0.29f, this.size.height * 0.52f)
                val p2 = Offset(this.size.width * 0.44f, this.size.height * 0.66f)
                val p3 = Offset(this.size.width * 0.72f, this.size.height * 0.37f)
                val a = (draw / 0.4f).coerceIn(0f, 1f)
                val b = ((draw - 0.4f) / 0.6f).coerceIn(0f, 1f)
                val w = 2.dp.toPx()
                drawLine(Color.White, p1, Offset(p1.x + (p2.x - p1.x) * a, p1.y + (p2.y - p1.y) * a), w, StrokeCap.Round)
                if (b > 0f) drawLine(Color.White, p2, Offset(p2.x + (p3.x - p2.x) * b, p2.y + (p3.y - p2.y) * b), w, StrokeCap.Round)
            }
        }
    }
}

/** White rounded card that holds a group of rows. */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Rem.colors.card)
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 500f)),
        content = content,
    )
}

@Composable
fun RowDivider(startIndent: Dp = 16.dp) {
    Box(Modifier.padding(start = startIndent).fillMaxWidth().height(0.5.dp).background(Rem.colors.separator))
}

/** Small grey caption above a group. */
@Composable
fun GroupCaption(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Rem.colors.label, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A settings-style row: [icon] Title ............ value  >  */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = Rem.colors.accent,
    value: String? = null,
    valueColor: Color = Rem.colors.secondaryLabel,
    subtitle: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(iconColor), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Rem.colors.label)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Rem.colors.accent) }
        }
        value?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
        }
        trailing?.invoke(this)
        if (onClick != null && showChevron && trailing == null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Rem.colors.tertiaryLabel, modifier = Modifier.padding(start = 4.dp).size(22.dp))
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, icon: ImageVector? = null, iconColor: Color = Rem.colors.accent, subtitle: String? = null) {
    SettingRow(title = title, icon = icon, iconColor = iconColor, subtitle = subtitle, onClick = { onChange(!checked) }) {
        RemSwitch(checked, onChange)
    }
}

@Composable
fun RemSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Rem.colors.success,
            checkedThumbColor = Color.White,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = Rem.colors.fill,
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Color.Transparent,
        ),
        thumbContent = null,
    )
}

/** iOS-style segmented control. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Rem.colors.fill).padding(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Rem.colors.card else Color.Transparent, tween(180), label = "seg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(bg)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (on) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium, color = Rem.colors.label, maxLines = 1)
            }
        }
    }
}

/** Solid accent button. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (enabled) Rem.colors.accent else Rem.colors.fill, label = "btn")
    Row(
        modifier
            .pressScale(interaction)
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interaction, ripple(color = Color.White), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color.White else Rem.colors.secondaryLabel, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) Color.White else Rem.colors.secondaryLabel)
    }
}

/** Quiet secondary button: tinted text on a light fill. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction)
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Rem.colors.fill)
            .clickable(interaction, ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Rem.colors.accent, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = Rem.colors.accent)
    }
}

/** Small grey token showing something Rem understood ("Tomorrow, 6:00 PM"). */
@Composable
fun Token(text: String, icon: ImageVector?, modifier: Modifier = Modifier, tint: Color = Rem.colors.secondaryLabel, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Rem.colors.fill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (tint == Rem.colors.secondaryLabel) Rem.colors.label else tint, maxLines = 1)
    }
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 22.dp, stroke: Dp = 3.dp, color: Color = Rem.colors.accent) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "ring")
    val track = Rem.colors.fill
    Canvas(modifier.size(size)) {
        val s = stroke.toPx()
        val arc = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s)
        drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), arc, style = Stroke(s))
        drawArc(color, -90f, 360f * animated, false, Offset(s / 2, s / 2), arc, style = Stroke(s, cap = StrokeCap.Round))
    }
}

data class ReminderCardModel(
    val id: Long,
    val title: String,
    val category: Category,
    val priority: Priority,
    val dueText: String,
    val isLate: Boolean,
    val repeatText: String?,
    val alertStyle: AlertStyle,
    val amount: String?,
    val notes: String,
    val nagging: Boolean,
)

/** One reminder in a list: ( ) Title / grey due line. Calm, scannable, nothing extra. */
@Composable
fun ReminderRow(model: ReminderCardModel, onClick: () -> Unit, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    var checked by remember(model.id, model.dueText) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dim by animateFloatAsState(if (checked) 0.4f else 1f, tween(300), label = "dim")
    val strike by animateFloatAsState(if (checked) 1f else 0f, tween(300, delayMillis = 80), label = "strike")
    val c = Rem.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.card)
            .sharedBounds("card-${model.id}")
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(model.category.color, checked, onCheck = {
            if (!checked) {
                checked = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch { delay(550); onComplete() }
            }
        })
        Column(Modifier.weight(1f).padding(vertical = 8.dp).graphicsLayer { alpha = dim }) {
            val strikeColor = c.secondaryLabel
            Text(
                model.title,
                style = MaterialTheme.typography.bodyLarge,
                color = c.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    if (strike > 0f) drawLine(strikeColor, Offset(0f, size.height / 2), Offset(size.width * strike, size.height / 2), 1.5.dp.toPx())
                },
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.dueText, style = MaterialTheme.typography.bodyMedium, color = if (model.isLate) c.danger else c.secondaryLabel, maxLines = 1)
                if (model.repeatText != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Repeat, "Repeats", tint = c.secondaryLabel, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(model.repeatText, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (model.alertStyle == AlertStyle.ALARM) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Alarm, "Rings like an alarm", tint = c.secondaryLabel, modifier = Modifier.size(14.dp))
                }
            }
            if (model.notes.isNotBlank()) {
                Text(model.notes, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.graphicsLayer { alpha = dim }) {
            if (model.priority == Priority.HIGH) Icon(Icons.Rounded.Flag, "High priority", tint = c.warning, modifier = Modifier.size(16.dp))
            model.amount?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel) }
        }
    }
}

/** A bottom sheet with a single- or multi-select list of options, each with a check mark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionSheet(
    title: String,
    options: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    icons: ((T) -> (@Composable () -> Unit)?)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Rem.colors.canvas,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Rem.colors.label, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp))
            GroupedCard {
                options.forEachIndexed { i, (value, label) ->
                    if (i > 0) RowDivider(if (icons != null) 56.dp else 16.dp)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelect(value) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        icons?.invoke(value)?.let { it(); Spacer(Modifier.width(12.dp)) }
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = Rem.colors.label, modifier = Modifier.weight(1f))
                        if (isSelected(value)) Icon(Icons.Rounded.Check, "Selected", tint = Rem.colors.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
            footer?.invoke(this)
        }
    }
}
