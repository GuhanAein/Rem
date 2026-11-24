package app.rem.ui.components

import androidx.compose.animation.animateColorAsState
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Rounded square with a soft tint of the category colour. */
@Composable
fun CategoryBubble(category: Category, modifier: Modifier = Modifier, size: Dp = 44.dp, selected: Boolean = false) {
    val c = category.color
    Box(
        modifier = modifier
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
    Box(
        modifier = modifier
            .height(height)
            .shadow(if (enabled) 12.dp else 0.dp, RoundedCornerShape(height / 2), ambientColor = Color(0xFF5B4BFF), spotColor = Color(0xFF5B4BFF))
            .clip(RoundedCornerShape(height / 2))
            .background(Rem.extras.accentGradient, alpha = alpha)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
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

/** The round "complete" button on each card: fills with a check when tapped. */
@Composable
fun CompleteCircle(priority: Priority, checked: Boolean, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    val ring = priority.color ?: MaterialTheme.colorScheme.outline
    val fill by animateColorAsState(if (checked) Rem.extras.success else Color.Transparent, label = "fill")
    val scale by animateFloatAsState(if (checked) 1.12f else 1f, animationSpec = spring(Spring.DampingRatioMediumBouncy), label = "scale")
    Box(
        modifier = modifier
            .size(30.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, if (checked) Rem.extras.success else ring, CircleShape)
            .clickable(role = Role.Checkbox, onClickLabel = "Mark done", onClick = onCheck),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        shadowElevation = 1.dp,
        onClick = onClick,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBubble(model.category)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaChip(
                        model.dueText,
                        Icons.Rounded.AccessTime,
                        if (model.isLate) Rem.extras.danger else muted,
                        filled = model.isLate,
                    )
                    model.repeatText?.let { MetaChip(it, Icons.Rounded.Repeat, muted, modifier = Modifier.weight(1f, fill = false)) }
                    if (model.alertStyle == AlertStyle.ALARM) {
                        Icon(Icons.Rounded.Alarm, contentDescription = "Rings like an alarm", tint = model.category.color, modifier = Modifier.size(15.dp))
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
                        delay(380)
                        onComplete()
                    }
                }
            })
        }
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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
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
        Column(Modifier.padding(18.dp)) {
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
