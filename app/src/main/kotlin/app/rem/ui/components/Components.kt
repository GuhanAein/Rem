package app.rem.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.ui.motion.pressScale
import app.rem.ui.motion.rememberHaptics
import app.rem.ui.motion.sharedBounds
import app.rem.ui.theme.Rem
import app.rem.ui.theme.RemPalette
import app.rem.ui.theme.Serif
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------

/** A charcoal surface lit by a fine highlight along its top edge. The basic building block. */
fun Modifier.noirSurface(shape: Shape = RoundedCornerShape(22.dp), elevated: Boolean = false): Modifier = this
    .clip(shape)
    .background(RemPalette.current.let { if (elevated) it.elevated else it.surface })
    .border(
        1.dp,
        if (RemPalette.current.isDark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.025f)))
        else Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.09f))),
        shape,
    )

/** Card that holds a group of rows. */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().noirSurface().animateContentSize(spring(dampingRatio = 0.9f, stiffness = 500f)),
        content = content,
    )
}

@Composable
fun RowDivider(startIndent: Dp = 18.dp) {
    Box(Modifier.padding(start = startIndent).fillMaxWidth().height(0.5.dp).background(Rem.colors.separator))
}

/** Small-caps caption: "OVERDUE · 2". */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, count: Int? = null, color: Color = Rem.colors.secondaryLabel) {
    Row(modifier.padding(start = 6.dp, top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
        if (count != null) {
            Text("  ·  $count", style = MaterialTheme.typography.labelSmall, color = Rem.colors.tertiaryLabel)
        }
    }
}

@Composable
fun GroupCaption(text: String, modifier: Modifier = Modifier) = Caption(text, modifier)

// ---------------------------------------------------------------------------------------------
// Category mark: a tinted line glyph on a dark tile (no loud colour blocks)
// ---------------------------------------------------------------------------------------------

@Composable
fun CategoryGlyph(category: Category, modifier: Modifier = Modifier, size: Dp = 34.dp, selected: Boolean = false) {
    val tint = category.color
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(if (selected) tint.copy(alpha = 0.22f) else Rem.colors.fill)
            .border(1.dp, if (selected) tint.copy(alpha = 0.6f) else Rem.colors.highlight.copy(alpha = 0.05f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = category.label, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

// ---------------------------------------------------------------------------------------------
// Check ring
// ---------------------------------------------------------------------------------------------

@Composable
fun CheckCircle(@Suppress("UNUSED_PARAMETER") color: Color, checked: Boolean, onCheck: () -> Unit, modifier: Modifier = Modifier, size: Dp = 24.dp, ring: Color = Rem.colors.tertiaryLabel) {
    val gold = Rem.colors.gold
    val fill by animateFloatAsState(if (checked) 1f else 0f, tween(220), label = "fill")
    val draw by animateFloatAsState(if (checked) 1f else 0f, tween(260, delayMillis = 120), label = "draw")
    val pop by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "pop")
    Box(
        modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClickLabel = "Mark done", onClick = onCheck),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size).graphicsLayer { val s = 1f + 0.12f * pop * (1f - pop); scaleX = s; scaleY = s }) {
            val stroke = 1.4.dp.toPx()
            val r = this.size.minDimension / 2 - stroke / 2
            drawCircle(if (fill > 0f) gold else ring, radius = r, style = Stroke(stroke))
            if (fill > 0f) drawCircle(gold, radius = r * fill)
            if (draw > 0f) {
                val w = this.size.width
                val h = this.size.height
                val p1 = Offset(w * 0.29f, h * 0.52f)
                val p2 = Offset(w * 0.44f, h * 0.66f)
                val p3 = Offset(w * 0.72f, h * 0.37f)
                val a = (draw / 0.4f).coerceIn(0f, 1f)
                val b = ((draw - 0.4f) / 0.6f).coerceIn(0f, 1f)
                val sw = 1.9.dp.toPx()
                val ink = if (RemPalette.current.isDark) Color(0xFF0B0B0D) else Color.White
                drawLine(ink, p1, Offset(p1.x + (p2.x - p1.x) * a, p1.y + (p2.y - p1.y) * a), sw, StrokeCap.Round)
                if (b > 0f) drawLine(ink, p2, Offset(p2.x + (p3.x - p2.x) * b, p2.y + (p3.y - p2.y) * b), sw, StrokeCap.Round)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/** Primary action: ivory capsule, black text. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val c = Rem.colors
    val bg by animateColorAsState(if (enabled) c.ivory else c.fill, label = "btn")
    val haptics = rememberHaptics()
    Row(
        modifier
            .pressScale(interaction, 0.97f)
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(bg)
            .clickable(interaction, ripple(color = RemPalette.current.onIvory), enabled = enabled, role = Role.Button) { haptics.tick(); onClick() }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) c.onIvory else c.tertiaryLabel)
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, null, tint = if (enabled) c.onIvory else c.tertiaryLabel, modifier = Modifier.size(18.dp))
        }
    }
}

/** Secondary action: hairline capsule. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val c = Rem.colors
    Row(
        modifier
            .pressScale(interaction, 0.97f)
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(27.dp))
            .border(1.dp, c.separator.copy(alpha = 1f), RoundedCornerShape(27.dp))
            .background(c.surface)
            .clickable(interaction, ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.label, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.label)
    }
}

// ---------------------------------------------------------------------------------------------
// Rows, switches, segmented
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = Rem.colors.gold,
    value: String? = null,
    valueColor: Color = Rem.colors.secondaryLabel,
    subtitle: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Rem.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.label)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.gold) }
        }
        value?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
        }
        trailing?.invoke(this)
        if (onClick != null && showChevron && trailing == null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tertiaryLabel, modifier = Modifier.padding(start = 6.dp).size(20.dp))
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, icon: ImageVector? = null, iconColor: Color = Rem.colors.gold, subtitle: String? = null) {
    SettingRow(title = title, icon = icon, iconColor = iconColor, subtitle = subtitle, onClick = { onChange(!checked) }) {
        RemSwitch(checked, onChange)
    }
}

/** A refined toggle: hairline track, ivory knob, gold when on. */
@Composable
fun RemSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Rem.colors
    val haptics = rememberHaptics()
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = 0.7f, stiffness = 700f), label = "knob")
    val track by animateColorAsState(if (checked) c.gold else c.fill, tween(180), label = "track")
    Box(
        Modifier.size(width = 48.dp, height = 28.dp).clip(RoundedCornerShape(14.dp)).background(track)
            .border(1.dp, c.highlight.copy(alpha = if (checked) 0f else 0.06f), RoundedCornerShape(14.dp))
            .clickable(role = Role.Switch) { haptics.tick(); onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size(22.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(if (checked) c.onIvory else c.ivory))
    }
}

/** Segmented control with a sliding indicator. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, counts: List<Int?>? = null) {
    val c = Rem.colors
    val haptics = rememberHaptics()
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp).noirSurface(RoundedCornerShape(22.dp)).padding(4.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(c.ivory))
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val fg by animateColorAsState(if (on) c.onIvory else c.secondaryLabel, tween(180), label = "fg")
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Tab) { if (!on) haptics.tick(); onSelect(i) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
                    counts?.getOrNull(i)?.let { n ->
                        Spacer(Modifier.width(4.dp))
                        Text("$n", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }
}

/** Hairline capsule showing something Rem understood. */
@Composable
fun Token(text: String, icon: ImageVector?, modifier: Modifier = Modifier, tint: Color = Rem.colors.secondaryLabel, onClick: (() -> Unit)? = null) {
    val c = Rem.colors
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, if (tint == c.secondaryLabel) c.separator else tint.copy(alpha = 0.35f), RoundedCornerShape(50))
            .background(if (tint == c.secondaryLabel) Color.Transparent else tint.copy(alpha = 0.08f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (tint == c.secondaryLabel) c.label else tint, maxLines = 1)
    }
}

/** Thin gold progress arc. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 22.dp, stroke: Dp = 2.dp, color: Color = Rem.colors.gold) {
    val animated = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(progress) { animated.animateTo(progress.coerceIn(0f, 1f), tween(900)) }
    val track = Rem.colors.separator
    Canvas(modifier.size(size)) {
        val s = stroke.toPx()
        val arc = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s)
        drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), arc, style = Stroke(s))
        drawArc(color, -90f, 360f * animated.value, false, Offset(s / 2, s / 2), arc, style = Stroke(s, cap = StrokeCap.Round))
    }
}

/** "Slide to complete": a deliberate, satisfying gesture for the alarm screen. */
@Composable
fun SlideToConfirm(text: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val c = Rem.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().height(66.dp).noirSurface(RoundedCornerShape(33.dp), elevated = true).padding(5.dp)) {
        val knob = 56.dp
        val maxPx = with(density) { (maxWidth - knob).toPx() }
        val offset = remember { Animatable(0f) }
        var done by remember { mutableStateOf(false) }
        val progress = (offset.value / maxPx).coerceIn(0f, 1f)
        Text(
            text, style = MaterialTheme.typography.labelLarge, color = c.secondaryLabel.copy(alpha = 1f - progress),
            modifier = Modifier.align(Alignment.Center).padding(start = 40.dp),
        )
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(knob)
                .clip(CircleShape)
                .background(c.ivory)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value > maxPx * 0.82f) {
                                    offset.animateTo(maxPx, tween(120)); done = true; haptics.confirm(); onConfirm()
                                } else offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f))
                            }
                        },
                    ) { _, drag -> scope.launch { offset.snapTo((offset.value + drag).coerceIn(0f, maxPx)) } }
                }
                .clickable(role = Role.Button, onClickLabel = text) { haptics.confirm(); onConfirm() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (done) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.onIvory, modifier = Modifier.size(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Reminder row
// ---------------------------------------------------------------------------------------------

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
    /** "8:30" and "AM" shown as an editorial time column; null for undated. */
    val time: String? = null,
    val meridiem: String? = null,
    /** "Yesterday", "Thu", "12 Dec": shown above the time when not today. */
    val dayLabel: String? = null,
    /** "Medicine · Daily · 1 h late" */
    val meta: String = "",
)

@Composable
fun ReminderRow(model: ReminderCardModel, onClick: () -> Unit, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    var checked by remember(model.id, model.dueText) { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val dim by animateFloatAsState(if (checked) 0.35f else 1f, tween(320), label = "dim")
    val strike by animateFloatAsState(if (checked) 1f else 0f, tween(320, delayMillis = 100), label = "strike")
    val c = Rem.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.surface)
            .sharedBounds("card-${model.id}")
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(
            model.category.color, checked,
            ring = if (model.isLate) c.danger.copy(alpha = 0.7f) else c.tertiaryLabel,
            onCheck = {
                if (!checked) {
                    checked = true
                    haptics.confirm()
                    scope.launch { delay(600); onComplete() }
                }
            },
        )
        Column(Modifier.weight(1f).padding(vertical = 8.dp).graphicsLayer { alpha = dim }) {
            val strikeColor = c.secondaryLabel
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(model.category.color))
                Spacer(Modifier.width(8.dp))
                Text(
                    model.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        if (strike > 0f) drawLine(strikeColor, Offset(0f, size.height / 2), Offset(size.width * strike, size.height / 2), 1.2.dp.toPx())
                    },
                )
            }
            if (model.meta.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    model.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (model.isLate) c.danger else c.secondaryLabel,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
            if (model.notes.isNotBlank()) {
                Text(
                    model.notes, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = c.tertiaryLabel,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp, top = 1.dp),
                )
            }
        }
        if (model.time != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp).graphicsLayer { alpha = dim }) {
                model.dayLabel?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = if (model.isLate) c.danger else c.secondaryLabel) }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(model.time, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Serif), color = if (model.isLate) c.danger else c.label)
                    Spacer(Modifier.width(2.dp))
                    Text(model.meridiem.orEmpty(), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel, modifier = Modifier.padding(bottom = 3.dp))
                }
                model.amount?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = c.gold) }
            }
        } else {
            model.amount?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = c.gold) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Option sheet
// ---------------------------------------------------------------------------------------------

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
    val c = Rem.colors
    val haptics = rememberHaptics()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = c.label, modifier = Modifier.padding(start = 6.dp, bottom = 18.dp))
            GroupedCard {
                options.forEachIndexed { i, (value, label) ->
                    if (i > 0) RowDivider(if (icons != null) 66.dp else 18.dp)
                    val selected = isSelected(value)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable { haptics.tick(); onSelect(value) }.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        icons?.invoke(value)?.let { it(); Spacer(Modifier.width(14.dp)) }
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) c.label else c.label.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                        if (selected) Icon(Icons.Rounded.Check, "Selected", tint = c.gold, modifier = Modifier.size(20.dp))
                    }
                }
            }
            footer?.invoke(this)
        }
    }
}

@Composable
fun SheetHandle() {
    Box(Modifier.padding(top = 10.dp, bottom = 14.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(RemPalette.current.highlight.copy(alpha = 0.18f)))
}
