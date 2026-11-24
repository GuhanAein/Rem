package app.rem.ui.spaces

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.rem.core.model.Reminder
import app.rem.core.spaces.WorkStatus
import app.rem.data.ProjectEntity
import app.rem.ui.components.noirSurface
import app.rem.ui.format.formatDay
import app.rem.ui.format.formatTime
import app.rem.ui.motion.rememberHaptics
import app.rem.ui.theme.Rem
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Columns, left to right. */
val BOARD_COLUMNS = listOf(WorkStatus.TODO, WorkStatus.DOING, WorkStatus.WAITING, WorkStatus.DONE)

/** Which column an x position (in root coordinates) falls in; columns are matched on x only, so a card can be dropped anywhere down a column. */
internal fun columnAt(x: Float, bounds: Map<WorkStatus, ClosedFloatingPointRange<Float>>): WorkStatus? =
    bounds.entries.firstOrNull { x in it.value }?.key

/**
 * A kanban board for work tasks. Tap a card for its options; press and hold to pick it up,
 * then drag it to another column. The board scrolls on its own when you drag near an edge.
 */
@Composable
fun KanbanBoard(
    columns: Map<WorkStatus, List<Reminder>>,
    projects: List<ProjectEntity>,
    onMove: (Reminder, WorkStatus) -> Unit,
    onTap: (Reminder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Rem.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }

    val colBounds = remember { mutableStateMapOf<WorkStatus, ClosedFloatingPointRange<Float>>() }
    var boardX by remember { mutableStateOf(0f..0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf<Reminder?>(null) }
    var from by remember { mutableStateOf<WorkStatus?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    val hover = dragging?.let { columnAt(pointer.x, colBounds) }

    Box(modifier.onGloballyPositioned { origin = it.positionInRoot(); boardX = it.boundsInRoot().let { b -> b.left..b.right } }) {
        Row(
            Modifier.horizontalScroll(scroll, enabled = dragging == null).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BOARD_COLUMNS.forEach { st ->
                val cards = columns[st].orEmpty()
                val target = hover == st && from != st
                val border by animateColorAsState(if (target) c.gold else Color.Transparent, label = "drop")
                Column(
                    Modifier.width(250.dp).heightIn(min = 280.dp)
                        .onGloballyPositioned { colBounds[st] = it.boundsInRoot().let { b -> b.left..b.right } }
                        .noirSurface()
                        .border(1.5.dp, border, RoundedCornerShape(22.dp))
                        .padding(12.dp),
                ) {
                    Row(Modifier.padding(start = 4.dp, bottom = 10.dp)) {
                        Box(Modifier.padding(top = 4.dp).size(7.dp).clip(CircleShape).background(dot(st)))
                        Spacer(Modifier.width(8.dp))
                        Text("${st.label.uppercase()}  ·  ${cards.size}", style = MaterialTheme.typography.labelSmall, color = if (st == WorkStatus.DOING) c.gold else c.secondaryLabel)
                    }
                    cards.forEach { t ->
                        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                        val lifted = dragging?.id == t.id
                        val alpha by animateFloatAsState(if (lifted) 0.25f else 1f, label = "lift")
                        Card(
                            t, projects, done = st == WorkStatus.DONE,
                            Modifier.padding(bottom = 8.dp).graphicsLayer { this.alpha = alpha }
                                .onGloballyPositioned { coords = it }
                                .pointerInput(t.id, st) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { at ->
                                            val pos = coords?.positionInRoot() ?: Offset.Zero
                                            grab = at
                                            pointer = pos + at
                                            dragging = t
                                            from = st
                                            haptics.confirm()
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            pointer += amount
                                            when {
                                                pointer.x > boardX.endInclusive - edge -> scope.launch { scroll.scrollBy(18f) }
                                                pointer.x < boardX.start + edge -> scope.launch { scroll.scrollBy(-18f) }
                                            }
                                        },
                                        onDragEnd = {
                                            val to = columnAt(pointer.x, colBounds)
                                            val moved = dragging
                                            if (moved != null && to != null && to != from) {
                                                haptics.tick()
                                                onMove(moved, to)
                                            }
                                            dragging = null
                                            from = null
                                        },
                                        onDragCancel = { dragging = null; from = null },
                                    )
                                },
                            onClick = { onTap(t) },
                        )
                    }
                    if (cards.isEmpty()) {
                        Text(
                            if (dragging != null) "Drop here" else emptyText(st),
                            style = MaterialTheme.typography.bodySmall, color = c.tertiaryLabel, modifier = Modifier.padding(4.dp),
                        )
                    }
                }
            }
        }
        // The card under your finger while dragging.
        dragging?.let { t ->
            val p = pointer - origin - grab
            Card(
                t, projects, done = from == WorkStatus.DONE,
                Modifier.width(226.dp).offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                    .graphicsLayer { rotationZ = -2.5f; scaleX = 1.04f; scaleY = 1.04f }
                    .shadow(18.dp, RoundedCornerShape(14.dp)),
                onClick = {},
            )
        }
    }
}

@Composable
private fun dot(st: WorkStatus): Color {
    val c = Rem.colors
    return when (st) {
        WorkStatus.TODO -> c.tertiaryLabel
        WorkStatus.DOING -> c.gold
        WorkStatus.WAITING -> c.warning
        WorkStatus.DONE -> c.success
    }
}

private fun emptyText(st: WorkStatus) = when (st) {
    WorkStatus.TODO -> "Nothing to do"
    WorkStatus.DOING -> "Hold a card and drag it here to start"
    WorkStatus.WAITING -> "Nothing waiting on others"
    WorkStatus.DONE -> "Finished tasks from this week"
}

@Composable
private fun Card(t: Reminder, projects: List<ProjectEntity>, done: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Rem.colors
    val project = projects.firstOrNull { it.id == t.projectId }
    val late = !done && t.dueAt?.isBefore(LocalDateTime.now()) == true
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.elevated).clickable(onClick = onClick).padding(12.dp)) {
        if (project != null) {
            Row {
                Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(Color(project.color)))
                Spacer(Modifier.width(6.dp))
                Text(project.name, style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
        }
        Text(
            t.title, style = MaterialTheme.typography.titleSmall, color = if (done) c.secondaryLabel else c.label, maxLines = 3,
            textDecoration = if (done) TextDecoration.LineThrough else null,
        )
        val meta = listOfNotNull(
            t.waitingOn?.takeIf { t.workStatus == WorkStatus.WAITING.name }?.let { "Waiting on $it" },
            t.dueAt?.takeIf { !done }?.let { "${formatDay(it.toLocalDate())}, ${formatTime(it.toLocalTime())}" },
        ).joinToString("  ·  ")
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(meta, style = MaterialTheme.typography.bodySmall, color = if (late) c.danger else c.secondaryLabel)
        }
    }
}
