package app.rem.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rem.core.model.Category
import app.rem.core.model.Reminder
import app.rem.core.scheduling.SnoozePreset
import app.rem.ui.add.QuickAddSheet
import app.rem.ui.components.CategoryGlyph
import app.rem.ui.components.GroupCaption
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.OptionSheet
import app.rem.ui.components.ProgressRing
import app.rem.ui.components.ReminderCardModel
import app.rem.ui.components.ReminderRow
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SettingRow
import app.rem.ui.components.color
import app.rem.ui.motion.AnimatedCounter
import app.rem.ui.motion.pressScale
import app.rem.ui.motion.staggeredEntrance
import app.rem.ui.theme.Rem
import app.rem.ui.voice.VoiceSheet

/** What the home screen should open on arrival (from a widget, shortcut or share). */
sealed interface LaunchAction {
    data class QuickAdd(val text: String = "") : LaunchAction
    data object Voice : LaunchAction
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    launchAction: LaunchAction?,
    onLaunchConsumed: () -> Unit,
    onOpen: (Long) -> Unit,
    onMoreOptions: (String) -> Unit,
    onSetup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var showVoice by rememberSaveable { mutableStateOf(false) }
    var prefill by rememberSaveable { mutableStateOf("") }
    var snoozeTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(launchAction) {
        when (launchAction) {
            is LaunchAction.QuickAdd -> { prefill = launchAction.text; showQuickAdd = true }
            LaunchAction.Voice -> showVoice = true
            null -> return@LaunchedEffect
        }
        onLaunchConsumed()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is HomeEvent.Completed -> {
                    val r = snackbar.showSnackbar("Completed “${e.before.title}”", actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) viewModel.undo(e.before)
                }
                is HomeEvent.Snoozed -> snackbar.showSnackbar("Snoozed ${e.preset.label.lowercase()}", duration = SnackbarDuration.Short)
                is HomeEvent.Saved -> snackbar.showSnackbar("Added “${e.title}”", duration = SnackbarDuration.Short)
            }
        }
    }

    HomeContent(
        state = state,
        snackbar = snackbar,
        onAdd = { prefill = ""; showQuickAdd = true },
        onVoice = { showVoice = true },
        onOpen = onOpen,
        onComplete = viewModel::complete,
        onSnooze = { snoozeTarget = it },
        onSelectList = viewModel::selectList,
        onSelectCategory = viewModel::selectCategory,
        onSetup = onSetup,
    )

    if (showQuickAdd) {
        QuickAddSheet(
            initialText = prefill,
            onDismiss = { showQuickAdd = false; prefill = "" },
            onSave = { reminder: Reminder -> viewModel.save(reminder); showQuickAdd = false; prefill = "" },
            onMoreOptions = { text -> showQuickAdd = false; prefill = ""; onMoreOptions(text) },
            onVoice = { showQuickAdd = false; showVoice = true },
        )
    }

    if (showVoice) {
        VoiceSheet(
            onDismiss = { showVoice = false },
            onSave = { reminder -> viewModel.save(reminder); showVoice = false },
            onEdit = { text -> showVoice = false; onMoreOptions(text) },
        )
    }

    snoozeTarget?.let { id ->
        val options = listOf(
            SnoozePreset.MIN_15 to "In 15 minutes",
            SnoozePreset.HOUR_1 to "In 1 hour",
            SnoozePreset.TONIGHT to "Tonight, 8:00 PM",
            SnoozePreset.TOMORROW_MORNING to "Tomorrow, 9:00 AM",
        )
        OptionSheet(
            title = "Snooze",
            options = options,
            isSelected = { false },
            onSelect = { preset -> viewModel.snooze(id, preset); snoozeTarget = null },
            onDismiss = { snoozeTarget = null },
            icons = { preset ->
                {
                    Icon(
                        when (preset) {
                            SnoozePreset.TONIGHT -> Icons.Rounded.Bedtime
                            SnoozePreset.TOMORROW_MORNING -> Icons.Rounded.WbSunny
                            else -> Icons.Rounded.Timer
                        },
                        null, tint = Rem.colors.accent, modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
    }
}

private data class TileStyle(val icon: ImageVector, val color: @Composable () -> Color)

private val TILE_STYLES = mapOf(
    SmartList.TODAY to TileStyle(Icons.Rounded.CalendarToday) { Rem.colors.accent },
    SmartList.SCHEDULED to TileStyle(Icons.Rounded.CalendarMonth) { Rem.colors.danger },
    SmartList.OVERDUE to TileStyle(Icons.Rounded.ErrorOutline) { Rem.colors.warning },
    SmartList.ALL to TileStyle(Icons.Rounded.Inbox) { Color(0xFF6B6B72) },
)

@Composable
fun HomeContent(
    state: HomeUiState,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    onAdd: () -> Unit = {},
    onVoice: () -> Unit = {},
    onOpen: (Long) -> Unit = {},
    onComplete: (Long) -> Unit = {},
    onSnooze: (Long) -> Unit = {},
    onSelectList: (SmartList) -> Unit = {},
    onSelectCategory: (Category?) -> Unit = {},
    onSetup: () -> Unit = {},
) {
    val seen = remember { mutableSetOf<Any>() }
    val c = Rem.colors
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
        ) {
            item(key = "header") { Header(state, onSetup) }
            item(key = "tiles") { Tiles(state, onSelectList, Modifier.staggeredEntrance("tiles", 1, seen)) }
            item(key = "title") {
                ListTitle(state, onClearCategory = { onSelectCategory(null) })
            }
            if (state.needsSetup) {
                item(key = "setup") {
                    GroupedCard(Modifier.padding(top = 8.dp).animateItem()) {
                        SettingRow(
                            "Finish setup so reminders are never late",
                            icon = Icons.Rounded.Shield, iconColor = c.warning, onClick = onSetup,
                        )
                    }
                }
            }
            if (state.isEmpty) item(key = "empty") { EmptyState(state, onAdd, onVoice) }

            state.sections.forEach { section ->
                item(key = "cap-${section.key}") {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (section.danger) c.danger else c.label,
                        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp).animateItem(),
                    )
                }
                itemsIndexed(section.items, key = { _, it -> "r-${it.id}" }) { i, model ->
                    val first = i == 0
                    val last = i == section.items.lastIndex
                    val shape = RoundedCornerShape(
                        topStart = if (first) 14.dp else 0.dp, topEnd = if (first) 14.dp else 0.dp,
                        bottomStart = if (last) 14.dp else 0.dp, bottomEnd = if (last) 14.dp else 0.dp,
                    )
                    Column(
                        Modifier
                            .animateItem(
                                fadeInSpec = tween(200),
                                placementSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                                fadeOutSpec = tween(180),
                            )
                            .staggeredEntrance("r-${model.id}", 2 + i, seen)
                            .clip(shape),
                    ) {
                        SwipeRow(model, onOpen = { onOpen(model.id) }, onComplete = { onComplete(model.id) }, onSnooze = { onSnooze(model.id) })
                        if (!last) Box(Modifier.background(c.card)) { RowDivider(48.dp) }
                    }
                }
            }

            if (state.categories.size > 1) {
                item(key = "cat-cap") { GroupCaption("Categories", Modifier.padding(start = 0.dp).animateItem()) }
                item(key = "cats") {
                    GroupedCard(Modifier.animateItem()) {
                        state.categories.forEachIndexed { i, (cat, count) ->
                            if (i > 0) RowDivider(56.dp)
                            CategoryRow(cat, count, selected = cat == state.selectedCategory) { onSelectCategory(cat) }
                        }
                    }
                }
            }
        }

        // Compact title that appears once the large title scrolls away.
        AnimatedVisibility(scrolled, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            Column(Modifier.fillMaxWidth().background(c.canvas.copy(alpha = 0.96f)).statusBarsPadding()) {
                Text(
                    state.selectedCategory?.label ?: state.list.title,
                    style = MaterialTheme.typography.titleMedium, color = c.label, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
            }
        }

        BottomBar(onAdd, onVoice, Modifier.align(Alignment.BottomCenter))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp))
    }
}

@Composable
private fun Header(state: HomeUiState, onSetup: () -> Unit) {
    val c = Rem.colors
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(state.dateLabel, style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(c.fill).clickable(role = Role.Button, onClickLabel = "Settings", onClick = onSetup),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MoreHoriz, "Settings", tint = c.accent, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun Tiles(state: HomeUiState, onSelect: (SmartList) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(SmartList.TODAY to SmartList.SCHEDULED, SmartList.OVERDUE to SmartList.ALL).forEach { (a, b) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(a, state.counts[a] ?: 0, state.list == a, Modifier.weight(1f)) { onSelect(a) }
                Tile(b, state.counts[b] ?: 0, state.list == b, Modifier.weight(1f)) { onSelect(b) }
            }
        }
    }
}

@Composable
private fun Tile(list: SmartList, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val style = TILE_STYLES.getValue(list)
    val tint = style.color()
    val c = Rem.colors
    val bg by animateColorAsState(if (selected) tint else c.card, tween(200), label = "tile")
    val fg = if (selected) Color.White else c.label
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interaction, ripple(), role = Role.Tab, onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(if (selected) Color.White else tint),
                contentAlignment = Alignment.Center,
            ) { Icon(style.icon, null, tint = if (selected) tint else Color.White, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.weight(1f))
            AnimatedCounter(count, MaterialTheme.typography.headlineMedium, fg)
        }
        Spacer(Modifier.height(6.dp))
        Text(list.title, style = MaterialTheme.typography.titleSmall, color = if (selected) Color.White else c.secondaryLabel)
    }
}

@Composable
private fun ListTitle(state: HomeUiState, onClearCategory: () -> Unit) {
    val c = Rem.colors
    Column(Modifier.padding(start = 4.dp, top = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.selectedCategory?.label ?: state.list.title,
                style = MaterialTheme.typography.headlineLarge,
                color = if (state.selectedCategory != null) state.selectedCategory.color else c.label,
                modifier = Modifier.weight(1f),
            )
            if (state.list == SmartList.TODAY && state.todayTotal > 0) {
                ProgressRing(state.progress, size = 26.dp, stroke = 3.5.dp)
            }
        }
        val subtitle = when {
            state.selectedCategory != null -> null
            state.list == SmartList.TODAY && state.todayTotal > 0 ->
                "${state.remainingToday} remaining · ${state.todayDone} done"
            else -> null
        }
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel) }
        if (state.selectedCategory != null) {
            Row(
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClearCategory).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Close, null, tint = c.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Show all categories", style = MaterialTheme.typography.bodyMedium, color = c.accent)
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, count: Int, selected: Boolean, onClick: () -> Unit) {
    val c = Rem.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryGlyph(category)
        Spacer(Modifier.width(12.dp))
        Text(category.label, style = MaterialTheme.typography.bodyLarge, color = c.label, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(18.dp).padding(end = 2.dp))
        Text("$count", style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel)
    }
}

private val EXAMPLES = listOf(
    "Take vitamins every day at 8am",
    "Pay rent on the 1st of every month",
    "Call mom tomorrow at 6pm",
)

@Composable
private fun EmptyState(state: HomeUiState, onAdd: () -> Unit, onVoice: () -> Unit) {
    val c = Rem.colors
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (state.list == SmartList.TODAY && state.todayDone > 0) "All done for today" else "No reminders",
            style = MaterialTheme.typography.titleLarge, color = c.secondaryLabel,
        )
        if (state.counts[SmartList.ALL] == 0) {
            Spacer(Modifier.height(6.dp))
            Text("Tap the microphone and just say it:", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
            Spacer(Modifier.height(18.dp))
            GroupedCard {
                EXAMPLES.forEachIndexed { i, text ->
                    if (i > 0) RowDivider(52.dp)
                    SettingRow("“$text”", icon = Icons.Rounded.Mic, iconColor = c.accent, onClick = onVoice, showChevron = false)
                }
            }
        }
    }
}

@Composable
private fun SwipeRow(model: ReminderCardModel, onOpen: () -> Unit, onComplete: () -> Unit, onSnooze: () -> Unit) {
    val c = Rem.colors
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onComplete(); true }
                SwipeToDismissBoxValue.EndToStart -> { onSnooze(); false }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.4f },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toDone = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().background(if (toDone) c.success else c.warning).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDone) Arrangement.Start else Arrangement.End,
            ) {
                Icon(if (toDone) Icons.Rounded.Check else Icons.Rounded.Snooze, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (toDone) "Done" else "Snooze", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        ReminderRow(model, onClick = onOpen, onComplete = onComplete)
    }
}

@Composable
private fun BottomBar(onAdd: () -> Unit, onVoice: () -> Unit, modifier: Modifier = Modifier) {
    val c = Rem.colors
    val add = remember { MutableInteractionSource() }
    val mic = remember { MutableInteractionSource() }
    Box(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(c.canvas.copy(alpha = 0f), c.canvas, c.canvas))),
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.pressScale(add).clip(RoundedCornerShape(12.dp))
                    .clickable(add, ripple(), role = Role.Button, onClick = onAdd).padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AddCircle, null, tint = c.accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text("New Reminder", style = MaterialTheme.typography.titleMedium, color = c.accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.pressScale(mic, 0.9f).size(46.dp).clip(CircleShape).background(c.accent)
                    .clickable(mic, ripple(color = Color.White), role = Role.Button, onClickLabel = "Add by voice", onClick = onVoice),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Mic, "Add by voice", tint = Color.White, modifier = Modifier.size(24.dp)) }
        }
    }
}
