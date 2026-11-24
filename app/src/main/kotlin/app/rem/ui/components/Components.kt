package app.rem.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.ui.theme.Rem
import kotlinx.coroutines.delay
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import app.rem.ui.motion.ConfettiBurst
import app.rem.ui.motion.LocalAmbientMotion
import app.rem.ui.motion.pressScale
import app.rem.ui.motion.shimmer
import app.rem.ui.motion.ringing
import app.rem.ui.motion.sharedBounds
import app.rem.ui.motion.sharedElement
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Rounded square with a soft tint of the category colour. */
@Composable
fun CategoryBubble(category: Category, modifier: Modifier = Modifier, size: Dp = 44.dp, selected: Boolean = false) {
    val c = category.color
    val pop by animateFloatAsState(if (selected) 1.08f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow), label = "bubblePop")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(if (selected) Brush.linearGradient(listOf(c, c.copy(alpha = 0.75f))) else Brush.linearGradient(listOf(c.copy(alpha = 0.16f), c.copy(alpha = 0.10f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = category.label, tint = if (selected) Color.White else c, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "alpha")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction, 0.95f)
            .height(height)
            .shadow(if (enabled) 12.dp else 0.dp, RoundedCornerShape(height / 2), ambientColor = Color(0xFF5B4BFF), spotColor = Color(0xFF5B4BFF))
            .clip(RoundedCornerShape(height / 2))
            .background(Rem.extras.accentGradient, alpha = alpha)
            .shimmer(enabled = enabled)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A small rounded label with an optional icon. */
@Composable
fun MetaChip(text: String, icon: ImageVector?, tint: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) tint.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = if (filled) 6.dp else 0.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    stroke: Dp = 8.dp,
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    progressColor: Color = Color.White,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(900), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            drawArc(progressColor, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** The round "complete" button: fills green, then the check mark draws itself. */
@Composable
fun CompleteCircle(priority: Priority, checked: Boolean, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    val ring = priority.color ?: MaterialTheme.colorScheme.outline
    val success = Rem.extras.success
    val fill by animateFloatAsState(if (checked) 1f else 0f, tween(220), label = "fill")
    val draw by animateFloatAsState(if (checked) 1f else 0f, tween(320, delayMillis = 140), label = "draw")
    val scale by animateFloatAsState(if (checked) 1.18f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "scale")
    Box(
        modifier = modifier
            .size(30.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClickLabel = "Mark done", onClick = onCheck),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val stroke = 2.dp.toPx()
            val r = size.minDimension / 2
            // Ring morphs into a filled disc.
            drawCircle(if (fill > 0f) success else ring, radius = r - stroke / 2, style = Stroke(stroke))
            if (fill > 0f) drawCircle(success, radius = (r - stroke / 2) * fill)
            if (draw > 0f) {
                val p1 = Offset(size.width * 0.28f, size.height * 0.52f)
                val p2 = Offset(size.width * 0.44f, size.height * 0.67f)
                val p3 = Offset(size.width * 0.73f, size.height * 0.36f)
                val first = (draw / 0.4f).coerceIn(0f, 1f)
                val second = ((draw - 0.4f) / 0.6f).coerceIn(0f, 1f)
                val w = 2.6.dp.toPx()
                drawLine(Color.White, p1, Offset(p1.x + (p2.x - p1.x) * first, p1.y + (p2.y - p1.y) * first), w, StrokeCap.Round)
                if (second > 0f) drawLine(Color.White, p2, Offset(p2.x + (p3.x - p2.x) * second, p2.y + (p3.y - p2.y) * second), w, StrokeCap.Round)
            }
        }
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

@Composable
fun ReminderCard(model: ReminderCardModel, onClick: () -> Unit, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    var checked by remember(model.id, model.dueText) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    val strike by animateFloatAsState(if (checked) 1f else 0f, tween(380, delayMillis = 120), label = "strike")
    val dim by animateFloatAsState(if (checked) 0.5f else 1f, tween(400), label = "dim")
    val lateGlow = if (model.isLate && !checked && LocalAmbientMotion.current) {
        val t by rememberInfiniteTransition(label = "late").animateFloat(0.10f, 0.22f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "lateA")
        t
    } else 0.12f

    Box(modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interaction, 0.97f)
                .sharedBounds("card-${model.id}"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            shadowElevation = 1.dp,
            onClick = onClick,
            interactionSource = interaction,
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryBubble(model.category, Modifier.sharedElement("bubble-${model.id}").graphicsLayer { alpha = dim })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).graphicsLayer { alpha = dim }) {
                    val strikeColor = MaterialTheme.colorScheme.onSurface
                    Text(
                        model.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .sharedElement("title-${model.id}")
                            .drawWithContent {
                                drawContent()
                                if (strike > 0f) {
                                    val y = size.height / 2
                                    drawLine(strikeColor, Offset(0f, y), Offset(size.width * strike, y), 2.dp.toPx(), StrokeCap.Round)
                                }
                            },
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (model.isLate) Rem.extras.danger.copy(alpha = lateGlow) else Color.Transparent)
                                .padding(horizontal = if (model.isLate) 6.dp else 0.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val tint = if (model.isLate) Rem.extras.danger else muted
                            Icon(Icons.Rounded.AccessTime, null, tint = tint, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(model.dueText, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
                        }
                        model.repeatText?.let { MetaChip(it, Icons.Rounded.Repeat, muted, modifier = Modifier.weight(1f, fill = false)) }
                        if (model.alertStyle == AlertStyle.ALARM) {
                            Icon(Icons.Rounded.Alarm, contentDescription = "Rings like an alarm", tint = model.category.color, modifier = Modifier.size(15.dp).ringing(periodMs = 3600, maxDegrees = 14f))
                        }
                        if (model.nagging) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = "Keeps reminding", tint = muted, modifier = Modifier.size(15.dp))
                        }
                    }
                    model.amount?.let {
                        Spacer(Modifier.height(6.dp))
                        MetaChip(it, null, model.category.color, filled = true)
                    }
                }
                Spacer(Modifier.width(10.dp))
                CompleteCircle(model.priority, checked, onCheck = {
                    if (!checked) {
                        checked = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            delay(720)
                            onComplete()
                        }
                    }
                })
            }
        }
        ConfettiBurst(
            trigger = if (checked) model.id else null,
            origin = Offset(0.92f, 0.5f),
            count = 34,
            durationMs = 900,
        )
    }
}

@Composable
fun SectionHeader(title: String, count: Int, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** A selectable pill used across the app (filters, options). */
@Composable
fun SelectPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val bg by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.surface, label = "bg")
    val fg by animateColorAsState(if (selected) Color.White else MaterialTheme.colorScheme.onSurface, label = "fg")
    val pop by animateFloatAsState(if (selected) 1f else 0.98f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "pop")
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .pressScale(interaction, 0.93f)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Section container card used on the edit screen. */
@Composable
fun SectionCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
