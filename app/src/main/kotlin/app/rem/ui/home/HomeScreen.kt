package app.rem.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsOff
import app.rem.container
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rem.core.model.Category
import app.rem.core.model.Reminder
import app.rem.core.scheduling.SnoozePreset
import app.rem.ui.add.QuickAddSheet
import app.rem.ui.components.Caption
import app.rem.ui.components.CategoryGlyph
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.OptionSheet
import app.rem.ui.components.ProgressRing
import app.rem.ui.components.ReminderCardModel
import app.rem.ui.components.ReminderRow
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SegmentedControl
import app.rem.ui.components.SettingRow
import app.rem.ui.components.color
import app.rem.ui.components.noirSurface
import app.rem.ui.motion.AnimatedCounter
import app.rem.ui.motion.pressScale
import app.rem.ui.motion.rememberHaptics
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
    onSpace: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by context.container.settings.state.collectAsStateWithLifecycle()
    var followUp by remember { mutableStateOf<Reminder?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
                    if (!e.before.followUp.isNullOrBlank()) followUp = e.before
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
        onSpace = onSpace,
        focusMode = settings.focusMode,
        silencedUntil = settings.panicUntil?.takeIf { it.isAfter(java.time.Instant.now()) }?.let {
            app.rem.ui.format.formatTime(java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.systemDefault()).toLocalTime())
        },
        onEndSilence = { scope.launch { context.container.service.endPanic() } },
    )

    followUp?.let { done ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { followUp = null },
            title = { Text("Done. What\u2019s next?") },
            text = { Text("You planned a follow-up: \u201C${done.followUp}\u201D. Add it now and say when.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { prefill = done.followUp.orEmpty() + " "; showQuickAdd = true; followUp = null }) {
                    Text("Add it", color = Rem.colors.gold)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { followUp = null }) { Text("Not now") } },
        )
    }

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
        OptionSheet(
            title = "Snooze until",
            options = listOf(
                SnoozePreset.MIN_15 to "In 15 minutes",
                SnoozePreset.HOUR_1 to "In an hour",
                SnoozePreset.TONIGHT to "Tonight at 8",
                SnoozePreset.TOMORROW_MORNING to "Tomorrow at 9",
            ),
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
                        null, tint = Rem.colors.gold, modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
    }
}

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
    onSpace: (String) -> Unit = {},
    focusMode: Boolean = false,
    silencedUntil: String? = null,
    onEndSilence: () -> Unit = {},
) {
    val seen = remember { mutableSetOf<Any>() }
    var showAll by remember { mutableStateOf(false) }
    val c = Rem.colors
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 || (listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset > 0) } }
    val lists = SmartList.entries

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        // A faint warm glow at the top gives the canvas depth without decoration.
        Canvas(Modifier.fillMaxWidth().height(420.dp)) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFD9BC83).copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.85f, 0f), radius = size.width * 1.1f,
                ),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 132.dp + app.rem.ui.spaces.LocalBottomInset.current),
        ) {
            item(key = "header") { Header(state, onSetup, Modifier.staggeredEntrance("header", 0, seen)) }
            item(key = "seg") {
                SegmentedControl(
                    options = lists.map { it.title },
                    counts = lists.map { state.counts[it] },
                    selected = lists.indexOf(state.list),
                    onSelect = { onSelectList(lists[it]) },
                    modifier = Modifier.padding(top = 28.dp).staggeredEntrance("seg", 1, seen),
                )
            }
            item(key = "spaces") { Spaces(onSpace, Modifier.padding(top = 14.dp).staggeredEntrance("spaces", 2, seen)) }
            if (silencedUntil != null) {
                item(key = "silenced") {
                    GroupedCard(Modifier.padding(top = 14.dp).animateItem()) {
                        SettingRow("Silenced until $silencedUntil", icon = Icons.Rounded.NotificationsOff, iconColor = c.gold, value = "Resume", valueColor = c.gold, showChevron = false, onClick = onEndSilence)
                    }
                }
            }
            if (focusMode && !showAll && !state.isEmpty) {
                item(key = "focus") { FocusView(state, onOpen, onComplete, onShowAll = { showAll = true }) }
                return@LazyColumn
            }
            if (state.selectedCategory != null) {
                item(key = "filter") {
                    Row(
                        Modifier.padding(top = 14.dp).clip(RoundedCornerShape(50)).border(1.dp, c.separator, RoundedCornerShape(50))
                            .clickable { onSelectCategory(null) }.padding(horizontal = 12.dp, vertical = 7.dp).animateItem(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(state.selectedCategory.color))
                        Spacer(Modifier.width(8.dp))
                        Text(state.selectedCategory.label, style = MaterialTheme.typography.labelMedium, color = c.label)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Close, "Clear filter", tint = c.secondaryLabel, modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (state.needsSetup) {
                item(key = "setup") {
                    GroupedCard(Modifier.padding(top = 18.dp).animateItem()) {
                        SettingRow("Finish setup so Rem is never late", icon = Icons.Rounded.Shield, iconColor = c.warning, onClick = onSetup)
                    }
                }
            }
            if (state.isEmpty) item(key = "empty") { EmptyState(state, onVoice, Modifier.staggeredEntrance("empty", 2, seen)) }

            var order = 2
            state.sections.forEach { section ->
                item(key = "cap-${section.key}") {
                    Caption(section.title, Modifier.animateItem(), count = section.items.size, color = if (section.danger) c.danger else c.secondaryLabel)
                }
                itemsIndexed(section.items, key = { _, it -> "r-${it.id}" }) { i, model ->
                    val first = i == 0
                    val last = i == section.items.lastIndex
                    val shape = RoundedCornerShape(
                        topStart = if (first) 22.dp else 0.dp, topEnd = if (first) 22.dp else 0.dp,
                        bottomStart = if (last) 22.dp else 0.dp, bottomEnd = if (last) 22.dp else 0.dp,
                    )
                    Column(
                        Modifier
                            .animateItem(
                                fadeInSpec = tween(220),
                                placementSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                                fadeOutSpec = tween(200),
                            )
                            .staggeredEntrance("r-${model.id}", order + i, seen)
                            .clip(shape)
                            .background(c.surface),
                    ) {
                        if (first) Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.09f), Color.Transparent))))
                        SwipeRow(model, onOpen = { onOpen(model.id) }, onComplete = { onComplete(model.id) }, onSnooze = { onSnooze(model.id) })
                        if (!last) RowDivider(56.dp)
                    }
                }
                order += section.items.size
            }

            if (state.categories.size > 1 && state.selectedCategory == null) {
                item(key = "col-cap") { Caption("Collections", Modifier.padding(top = 8.dp).animateItem()) }
                item(key = "collections") { Collections(state, onSelectCategory, Modifier.animateItem()) }
            }
        }

        // Compact title once the hero scrolls away.
        AnimatedVisibility(scrolled, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
            Column(Modifier.fillMaxWidth().background(c.canvas.copy(alpha = 0.97f)).statusBarsPadding()) {
                Text(
                    state.selectedCategory?.label ?: state.list.title,
                    style = MaterialTheme.typography.headlineSmall, color = c.label, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
            }
        }

        Dock(onAdd, onVoice, Modifier.align(Alignment.BottomCenter))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp + app.rem.ui.spaces.LocalBottomInset.current)) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(18.dp),
                containerColor = c.ivory,
                contentColor = c.onIvory,
                actionColor = c.goldDeep,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }
}

@Composable
private fun Header(state: HomeUiState, onSetup: () -> Unit, modifier: Modifier = Modifier) {
    val c = Rem.colors
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.dateLabel, style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, modifier = Modifier.weight(1f).padding(start = 2.dp))
            Box(
                Modifier.pressScale(interaction, 0.9f).size(40.dp).clip(CircleShape).border(1.dp, c.separator, CircleShape)
                    .clickable(interaction, ripple(), role = Role.Button, onClickLabel = "Settings", onClick = onSetup),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Tune, "Settings", tint = c.label, modifier = Modifier.size(19.dp)) }
        }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("${state.greeting},", style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic), color = c.secondaryLabel)
                Spacer(Modifier.height(2.dp))
                val left = state.remainingToday
                AnimatedContent(
                    targetState = when {
                        state.loading -> ""
                        left == 0 && state.todayDone > 0 -> "All clear\nfor today."
                        left == 0 -> "A clear\nday ahead."
                        left == 1 -> "One thing\nfor today."
                        else -> "$left things\nfor today."
                    },
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 }) togetherWith (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 6 })
                    },
                    label = "headline",
                ) { Text(it, style = MaterialTheme.typography.displayMedium, color = c.label) }
            }
            if (state.todayTotal > 0) {
                Box(Modifier.padding(bottom = 6.dp).size(72.dp), contentAlignment = Alignment.Center) {
                    ProgressRing(state.progress, size = 72.dp, stroke = 2.dp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedCounter(state.todayDone, MaterialTheme.typography.headlineMedium, c.label)
                        Text("/${state.todayTotal}", style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Spaces(onSpace: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Rem.colors
    val spaces = listOf(
        Triple("life", "Life", Icons.Rounded.Spa),
        Triple("people", "People", Icons.Rounded.Group),
        Triple("insights", "Insights", Icons.Rounded.Insights),
        Triple("morning", "Today\u2019s card", Icons.Rounded.WbSunny),
    )
    androidx.compose.foundation.lazy.LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(spaces.size) { i ->
            val (route, label, icon) = spaces[i]
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier.pressScale(interaction, 0.95f).noirSurface(RoundedCornerShape(50))
                    .clickable(interaction, ripple()) { onSpace(route) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = c.gold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.label)
            }
        }
    }
}

/** Focus mode: just the next three things, large and calm. */
@Composable
private fun FocusView(state: HomeUiState, onOpen: (Long) -> Unit, onComplete: (Long) -> Unit, onShowAll: () -> Unit) {
    val c = Rem.colors
    val next = state.sections.flatMap { it.items }.take(3)
    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FOCUS", style = MaterialTheme.typography.labelSmall, color = c.gold, modifier = Modifier.padding(start = 6.dp))
        next.forEach { m ->
            Row(
                Modifier.fillMaxWidth().noirSurface().clickable { onOpen(m.id) }.padding(start = 6.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                app.rem.ui.components.CheckCircle(m.category.color, false, onCheck = { onComplete(m.id) }, size = 28.dp)
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.headlineMedium, color = c.label)
                    Text(m.meta, style = MaterialTheme.typography.bodySmall, color = if (m.isLate) c.danger else c.secondaryLabel)
                }
                m.time?.let { Text(it, style = MaterialTheme.typography.headlineMedium, color = if (m.isLate) c.danger else c.gold) }
            }
        }
        androidx.compose.material3.TextButton(onClick = onShowAll, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Show everything", color = c.secondaryLabel)
        }
    }
}

@Composable
private fun Collections(state: HomeUiState, onSelect: (Category) -> Unit, modifier: Modifier = Modifier) {
    val c = Rem.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.categories.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (cat, count) ->
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        Modifier.weight(1f).pressScale(interaction).noirSurface(RoundedCornerShape(20.dp))
                            .clickable(interaction, ripple()) { onSelect(cat) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryGlyph(cat, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(cat.label, style = MaterialTheme.typography.titleSmall, color = c.label, modifier = Modifier.weight(1f))
                        Text("$count", style = MaterialTheme.typography.titleMedium, color = c.secondaryLabel)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val EXAMPLES = listOf(
    "Take vitamins every day at 8am",
    "Pay rent on the 1st of every month",
    "Call mom tomorrow at 6pm",
)

@Composable
private fun EmptyState(state: HomeUiState, onVoice: () -> Unit, modifier: Modifier = Modifier) {
    val c = Rem.colors
    Column(modifier.fillMaxWidth().padding(top = 36.dp)) {
        if (state.counts[SmartList.ALL] == 0) {
            Caption("Just say it")
            GroupedCard {
                EXAMPLES.forEachIndexed { i, text ->
                    if (i > 0) RowDivider(56.dp)
                    SettingRow("“$text”", icon = Icons.Rounded.Mic, iconColor = c.gold, onClick = onVoice, showChevron = false)
                }
            }
        } else {
            Text(
                "Nothing here.", style = MaterialTheme.typography.headlineMedium.copy(fontStyle = FontStyle.Italic),
                color = c.tertiaryLabel, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SwipeRow(model: ReminderCardModel, onOpen: () -> Unit, onComplete: () -> Unit, onSnooze: () -> Unit) {
    val c = Rem.colors
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { haptics.confirm(); onComplete(); true }
                SwipeToDismissBoxValue.EndToStart -> { haptics.tick(); onSnooze(); false }
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
                Modifier.fillMaxSize().background(if (toDone) c.gold else c.elevated).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDone) Arrangement.Start else Arrangement.End,
            ) {
                val fg = if (toDone) c.onIvory else c.label
                Icon(if (toDone) Icons.Rounded.Check else Icons.Rounded.Snooze, null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (toDone) "Complete" else "Snooze", color = fg, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { ReminderRow(model, onClick = onOpen, onComplete = onComplete) }
}

/** Floating dock: the two ways to add. */
@Composable
private fun Dock(onAdd: () -> Unit, onVoice: () -> Unit, modifier: Modifier = Modifier) {
    val c = Rem.colors
    val haptics = rememberHaptics()
    val add = remember { MutableInteractionSource() }
    val mic = remember { MutableInteractionSource() }
    Box(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, c.canvas.copy(alpha = 0.92f), c.canvas)))) {
        Row(
            Modifier.align(Alignment.Center)
                .then(if (app.rem.ui.spaces.LocalBottomInset.current > 0.dp) Modifier.padding(bottom = app.rem.ui.spaces.LocalBottomInset.current) else Modifier.navigationBarsPadding())
                .padding(top = 22.dp, bottom = 14.dp)
                .shadow(24.dp, RoundedCornerShape(36.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .noirSurface(RoundedCornerShape(36.dp), elevated = true).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.pressScale(add, 0.96f).height(54.dp).clip(RoundedCornerShape(27.dp)).background(c.ivory)
                    .clickable(add, ripple(color = Color.Black), role = Role.Button) { haptics.tick(); onAdd() }
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = c.onIvory, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("New reminder", style = MaterialTheme.typography.labelLarge, color = c.onIvory)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.pressScale(mic, 0.9f).size(54.dp).clip(CircleShape).background(c.goldSheen)
                    .clickable(mic, ripple(color = Color.Black), role = Role.Button, onClickLabel = "Add by voice") { haptics.tick(); onVoice() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Mic, "Add by voice", tint = c.onIvory, modifier = Modifier.size(22.dp)) }
        }
    }
}
