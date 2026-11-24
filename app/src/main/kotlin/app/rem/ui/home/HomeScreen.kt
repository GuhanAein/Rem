package app.rem.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import app.rem.ui.components.CategoryBubble
import app.rem.ui.components.ProgressRing
import app.rem.ui.components.ReminderCard
import app.rem.ui.components.ReminderCardModel
import app.rem.ui.components.color
import app.rem.ui.components.icon
import app.rem.ui.motion.AnimatedCounter
import app.rem.ui.motion.AuroraBackground
import app.rem.ui.motion.ConfettiBurst
import app.rem.ui.motion.floating
import app.rem.ui.motion.pressScale
import app.rem.ui.motion.ringing
import app.rem.ui.motion.shimmer
import app.rem.ui.motion.staggeredEntrance
import app.rem.ui.theme.Rem
import app.rem.ui.theme.SectionLabel
import java.time.LocalDate

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    quickAddPrefill: String?,
    onQuickAddConsumed: () -> Unit,
    onOpen: (Long) -> Unit,
    onMoreOptions: (String) -> Unit,
    onSetup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var prefill by rememberSaveable { mutableStateOf("") }
    var snoozeTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(quickAddPrefill) {
        if (quickAddPrefill != null) {
            prefill = quickAddPrefill
            showQuickAdd = true
            onQuickAddConsumed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is HomeEvent.Completed -> {
                    val r = snackbar.showSnackbar("Done: ${e.before.title}", actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) viewModel.undo(e.before)
                }
                is HomeEvent.Snoozed -> snackbar.showSnackbar("Snoozed ${e.preset.label.lowercase()}: ${e.title}", duration = SnackbarDuration.Short)
                is HomeEvent.Saved -> snackbar.showSnackbar("Saved: ${e.title}", duration = SnackbarDuration.Short)
            }
        }
    }

    HomeContent(
        state = state,
        snackbar = snackbar,
        onAdd = { text -> prefill = text; showQuickAdd = true },
        onOpen = onOpen,
        onComplete = viewModel::complete,
        onSnooze = { snoozeTarget = it },
        onSelectDate = viewModel::selectDate,
        onSelectCategory = viewModel::selectCategory,
        onSetup = onSetup,
    )

    if (showQuickAdd) {
        QuickAddSheet(
            initialText = prefill,
            onDismiss = { showQuickAdd = false; prefill = "" },
            onSave = { reminder: Reminder ->
                viewModel.save(reminder)
                showQuickAdd = false
                prefill = ""
            },
            onMoreOptions = { text ->
                showQuickAdd = false
                prefill = ""
                onMoreOptions(text)
            },
        )
    }

    snoozeTarget?.let { id ->
        SnoozeSheet(onDismiss = { snoozeTarget = null }, onPick = { preset ->
            viewModel.snooze(id, preset)
            snoozeTarget = null
        })
    }
}

@Composable
fun HomeContent(
    state: HomeUiState,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    onAdd: (String) -> Unit = {},
    onOpen: (Long) -> Unit = {},
    onComplete: (Long) -> Unit = {},
    onSnooze: (Long) -> Unit = {},
    onSelectDate: (LocalDate) -> Unit = {},
    onSelectCategory: (Category?) -> Unit = {},
    onSetup: () -> Unit = {},
) {
    val seen = remember { mutableSetOf<Any>() }
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 420 } }

    // FAB hides while scrolling down, returns when scrolling up.
    var lastIndex by remember { mutableIntStateOf(0) }
    var lastOffset by remember { mutableIntStateOf(0) }
    var fabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val i = listState.firstVisibleItemIndex
        val o = listState.firstVisibleItemScrollOffset
        val down = i > lastIndex || (i == lastIndex && o > lastOffset + 8)
        val up = i < lastIndex || (i == lastIndex && o < lastOffset - 8)
        if (down) fabVisible = false else if (up) fabVisible = true
        lastIndex = i
        lastOffset = o
    }

    // Celebrate when the last thing for today gets done.
    var celebrate by remember { mutableStateOf<Int?>(null) }
    var previousRemaining by remember { mutableStateOf<Int?>(null) }
    val remaining = state.todayTotal - state.todayDone
    LaunchedEffect(remaining, state.loading) {
        val prev = previousRemaining
        if (!state.loading && prev != null && prev > 0 && remaining == 0 && state.todayDone > 0) celebrate = (celebrate ?: 0) + 1
        if (!state.loading) previousRemaining = remaining
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = "hero") { Hero(state, listState, onSetup, onSelectDate) }
            if (state.categories.size > 1) {
                item(key = "categories") {
                    CategoryFilter(state, onSelectCategory, Modifier.staggeredEntrance("categories", 1, seen))
                }
            }
            if (state.needsSetup) {
                item(key = "setup") { SetupBanner(onSetup, Modifier.staggeredEntrance("setup", 2, seen).animateItem()) }
            }
            if (state.isEmpty) {
                item(key = "empty") { EmptyState(state.selectedCategory != null || state.sections.any { it.kind == SectionKind.DAY }, onAdd, seen) }
            }
            var index = 2
            state.sections.forEach { section ->
                if (section.items.isNotEmpty()) {
                    val headerIndex = index++
                    item(key = "h-${section.kind}-${section.title}") {
                        AnimatedSectionHeader(
                            section.title, section.items.size, section.kind.accent,
                            Modifier.padding(horizontal = 20.dp).staggeredEntrance("h-${section.kind}", headerIndex, seen).animateItem(),
                        )
                    }
                    itemsIndexed(section.items, key = { _, it -> "r-${it.id}" }) { i, model ->
                        val order = index + i
                        SwipeableCard(
                            model = model,
                            onOpen = { onOpen(model.id) },
                            onComplete = { onComplete(model.id) },
                            onSnooze = { onSnooze(model.id) },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .staggeredEntrance("r-${model.id}", order, seen)
                                .animateItem(
                                    fadeInSpec = tween(250),
                                    placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                                    fadeOutSpec = tween(220),
                                ),
                        )
                    }
                    index += section.items.size
                }
            }
        }

        CollapsedBar(visible = collapsed, state = state, onSetup = onSetup)

        AnimatedVisibility(
            visible = fabVisible,
            enter = slideInVertically(spring(dampingRatio = 0.7f)) { it * 2 } + fadeIn(),
            exit = slideOutVertically { it * 2 } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        ) {
            AddButton(onClick = { onAdd("") })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
        ConfettiBurst(celebrate, origin = Offset(0.5f, 0.18f), count = 110, durationMs = 1800)
    }
}

@Composable
private fun Hero(state: HomeUiState, listState: LazyListState, onSetup: () -> Unit, onSelectDate: (LocalDate) -> Unit) {
    // Parallax: the hero drifts slower than the list and fades as it scrolls away.
    val scroll by remember { derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f } }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)),
    ) {
        AuroraBackground(
            base = Rem.extras.heroGradient,
            blobs = listOf(Color(0xFFFF7AC6), Color(0xFF38BDF8), Color(0xFF4F46E5), Color(0xFFC084FC)),
            modifier = Modifier.matchParentSize().graphicsLayer { translationY = scroll * 0.45f },
        )
        Column(
            Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 20.dp)
                .graphicsLayer {
                    translationY = scroll * 0.25f
                    alpha = (1f - scroll / 700f).coerceIn(0f, 1f)
                },
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(19.dp).ringing()) }
                Spacer(Modifier.width(10.dp))
                Text("Rem", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Rounded.Tune, "Reliability settings", onSetup)
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.greeting, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = state.headline,
                        transitionSpec = {
                            (slideInVertically(spring(dampingRatio = 0.75f)) { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "headline",
                    ) { text -> Text(text, style = MaterialTheme.typography.headlineMedium, color = Color.White) }
                }
                ProgressRing(progress = state.progress) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedCounter(state.todayDone, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), Color.White)
                            Text("/", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
                            AnimatedCounter(state.todayTotal, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), Color.White)
                        }
                        Text("done", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("Overdue", state.overdueCount, Modifier.weight(1f), highlight = state.overdueCount > 0)
                StatPill("Later today", state.laterTodayCount, Modifier.weight(1f))
                StatPill("Upcoming", state.upcomingCount, Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.week, key = { it.date.toString() }) { day ->
                    DayPill(day, selected = day.date == state.selectedDate, onClick = { onSelectDate(day.date) })
                }
            }
        }
    }
}

@Composable
private fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(40.dp).pressScale(interaction, 0.88f).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
            .clickable(interaction, ripple(color = Color.White), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Color.White) }
}

/** Slim bar that slides down once the hero has scrolled away. */
@Composable
private fun CollapsedBar(visible: Boolean, state: HomeUiState, onSetup: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(dampingRatio = 0.8f)) { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Box(Modifier.fillMaxWidth().shadow(12.dp).clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))) {
            AuroraBackground(Rem.extras.heroGradient, listOf(Color(0xFFFF7AC6), Color(0xFF38BDF8)), Modifier.matchParentSize())
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Rem", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                val left = (state.todayTotal - state.todayDone).coerceAtLeast(0)
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedCounter(left, MaterialTheme.typography.labelLarge, Color.White)
                    Text(" to go today", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
                Spacer(Modifier.weight(1f))
                ProgressRing(state.progress, size = 30.dp, stroke = 4.dp)
                Spacer(Modifier.width(10.dp))
                GlassIconButton(Icons.Rounded.Tune, "Reliability settings", onSetup)
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: Int, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val bg by animateColorAsState(if (highlight) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.14f), label = "stat")
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        AnimatedCounter(value, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), Color.White)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f), maxLines = 1)
    }
}

@Composable
private fun DayPill(day: DayChip, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.12f), label = "day")
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "dayScale")
    val dot by animateFloatAsState(if (day.count > 0) 1f else 0f, spring(dampingRatio = 0.4f), label = "dot")
    val interaction = remember { MutableInteractionSource() }
    val fg = if (selected) MaterialTheme.colorScheme.primary else Color.White
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pressScale(interaction, 0.9f)
            .width(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(interaction, ripple(), role = Role.Tab, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (day.isToday) "Today" else day.weekday, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = if (selected) 1f else 0.8f))
        Spacer(Modifier.height(2.dp))
        Text("${day.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(5.dp).graphicsLayer { scaleX = dot; scaleY = dot }.clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.White),
        )
    }
}

@Composable
private fun CategoryFilter(state: HomeUiState, onSelect: (Category?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterPill("All", null, state.selectedCategory == null, MaterialTheme.colorScheme.primary) { onSelect(null) }
        }
        items(state.categories, key = { it.first.name }) { (cat, count) ->
            FilterPill(cat.label, cat.icon, state.selectedCategory == cat, cat.color, count, Modifier.animateItem()) { onSelect(cat) }
        }
    }
}

@Composable
private fun FilterPill(text: String, icon: ImageVector?, selected: Boolean, accent: Color, count: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.surface, label = "pill")
    val pop by animateFloatAsState(if (selected) 1.05f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "pillPop")
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .pressScale(interaction, 0.92f)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(interaction, ripple(), role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Color.White else accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            AnimatedCounter(
                count,
                MaterialTheme.typography.labelMedium,
                if (selected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnimatedSectionHeader(title: String, count: Int, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).floating(1.5f, 1800).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
        ) { AnimatedCounter(count, MaterialTheme.typography.labelMedium, accent) }
    }
}

@Composable
private fun SetupBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = Rem.extras.warning.copy(alpha = 0.12f),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Rem.extras.warning), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size(20.dp).ringing(3000, 10f))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Make reminders unmissable", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("A permission is missing, so reminders may be late. Tap to fix.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val EXAMPLES = listOf(
    Category.MEDICINE to "Take vitamins every day 8am",
    Category.BILL to "Pay rent every 1st 9am",
    Category.BIRTHDAY to "Mom's birthday 12 March every year",
    Category.HEALTH to "Gym every mon wed fri 7am",
    Category.DOCUMENT to "Renew passport on 5 Dec",
)

@Composable
private fun EmptyState(filtered: Boolean, onAdd: (String) -> Unit, seen: MutableSet<Any>) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(150.dp).staggeredEntrance("empty-art", 1, seen), contentAlignment = Alignment.Center) {
            // Orbiting satellites around a floating badge.
            listOf(Category.MEDICINE, Category.BILL, Category.BIRTHDAY).forEachIndexed { i, cat ->
                Box(
                    Modifier
                        .offset(x = listOf((-62).dp, 60.dp, 8.dp)[i], y = listOf((-28).dp, (-40).dp, 62.dp)[i])
                        .floating(5f, 2600 + i * 500, i * 0.3f),
                ) { CategoryBubble(cat, size = 30.dp) }
            }
            Box(
                Modifier.size(96.dp).floating(6f).shadow(24.dp, CircleShape, ambientColor = Color(0xFF5B4BFF), spotColor = Color(0xFF5B4BFF))
                    .clip(CircleShape).background(Rem.extras.heroGradient).shimmer(3200),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (filtered) "Nothing here" else "Your mind, clear",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.staggeredEntrance("empty-title", 2, seen),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (filtered) "No reminders for this filter." else "Tell Rem anything: tasks, medicine, bills, birthdays. Just type it the way you'd say it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggeredEntrance("empty-body", 3, seen),
        )
        if (!filtered) {
            Spacer(Modifier.height(20.dp))
            Text("TRY ONE", style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.staggeredEntrance("empty-try", 4, seen))
            Spacer(Modifier.height(10.dp))
            EXAMPLES.forEachIndexed { i, (cat, text) ->
                val interaction = remember { MutableInteractionSource() }
                Surface(
                    onClick = { onAdd(text) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).staggeredEntrance("ex-$i", 5 + i, seen).pressScale(interaction),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    interactionSource = interaction,
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBubble(cat, size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("“$text”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableCard(model: ReminderCardModel, onOpen: () -> Unit, onComplete: () -> Unit, onSnooze: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onComplete(); true }
                SwipeToDismissBoxValue.EndToStart -> { onSnooze(); false }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val dir = state.dismissDirection
            val toDone = dir == SwipeToDismissBoxValue.StartToEnd
            val progress = if (dir == SwipeToDismissBoxValue.Settled) 0f else state.progress.coerceIn(0f, 1f)
            val armed = progress > 0.35f
            val color = if (toDone) Rem.extras.success else Rem.extras.warning
            val iconScale by animateFloatAsState(if (armed) 1.35f else 0.85f + progress, spring(dampingRatio = 0.4f), label = "swipeIcon")
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
                    .background(color.copy(alpha = (0.35f + progress).coerceAtMost(1f)))
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDone) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    if (toDone) Icons.Rounded.Check else Icons.Rounded.Snooze, null, tint = Color.White,
                    modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale; rotationZ = if (toDone) 0f else (1f - progress) * -30f },
                )
                Spacer(Modifier.width(8.dp))
                AnimatedVisibility(armed, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Text(if (toDone) "Done!" else "Snooze", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
        },
    ) {
        ReminderCard(model, onClick = onOpen, onComplete = onComplete)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.pressScale(interaction, 0.92f)) {
        // Breathing glow.
        Box(
            Modifier.matchParentSize().floating(2f, 2400)
                .shadow(22.dp, RoundedCornerShape(50), ambientColor = Color(0xFF7C3AED), spotColor = Color(0xFF7C3AED)),
        )
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Rem.extras.accentGradient)
                .shimmer(3400)
                .clickable(interaction, ripple(color = Color.White), role = Role.Button, onClickLabel = "Add reminder", onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Remind me", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeSheet(onDismiss: () -> Unit, onPick: (SnoozePreset) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val seen = remember { mutableSetOf<Any>() }
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Snooze until…", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            val options = listOf(
                Triple(SnoozePreset.MIN_5, Icons.Rounded.Timer, "In 5 minutes"),
                Triple(SnoozePreset.MIN_15, Icons.Rounded.Timer, "In 15 minutes"),
                Triple(SnoozePreset.HOUR_1, Icons.Rounded.Snooze, "In 1 hour"),
                Triple(SnoozePreset.TONIGHT, Icons.Rounded.Bedtime, "Tonight, 8:00 PM"),
                Triple(SnoozePreset.TOMORROW_MORNING, Icons.Rounded.WbSunny, "Tomorrow, 9:00 AM"),
            )
            options.forEachIndexed { i, (preset, icon, label) ->
                val interaction = remember { MutableInteractionSource() }
                Surface(
                    onClick = { onPick(preset) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).staggeredEntrance("s$i", i, seen).pressScale(interaction),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
