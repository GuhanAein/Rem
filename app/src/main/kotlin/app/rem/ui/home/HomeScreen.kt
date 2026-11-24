package app.rem.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import app.rem.ui.components.SectionHeader
import app.rem.ui.components.color
import app.rem.ui.components.icon
import app.rem.ui.theme.Rem
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
    onAdd: (String) -> Unit = {},
    onOpen: (Long) -> Unit = {},
    onComplete: (Long) -> Unit = {},
    onSnooze: (Long) -> Unit = {},
    onSelectDate: (LocalDate) -> Unit = {},
    onSelectCategory: (Category?) -> Unit = {},
    onSetup: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = "hero") { Hero(state, onSetup, onSelectDate) }
            if (state.categories.size > 1) {
                item(key = "categories") { CategoryFilter(state, onSelectCategory) }
            }
            if (state.needsSetup) {
                item(key = "setup") { SetupBanner(onSetup) }
            }
            if (state.isEmpty) {
                item(key = "empty") { EmptyState(state.selectedCategory != null || state.sections.any { it.kind == SectionKind.DAY }, onAdd) }
            }
            state.sections.forEach { section ->
                if (section.items.isNotEmpty()) {
                    item(key = "h-${section.kind}-${section.title}") {
                        SectionHeader(section.title, section.items.size, section.kind.accent, Modifier.padding(horizontal = 20.dp))
                    }
                    items(section.items, key = { "r-${it.id}-${it.dueText}" }) { model ->
                        SwipeableCard(
                            model = model,
                            onOpen = { onOpen(model.id) },
                            onComplete = { onComplete(model.id) },
                            onSnooze = { onSnooze(model.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateItem(),
                        )
                    }
                }
            }
        }

        AddButton(
            onClick = { onAdd("") },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
    }
}

@Composable
private fun Hero(state: HomeUiState, onSetup: () -> Unit, onSelectDate: (LocalDate) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Rem.extras.heroGradient),
    ) {
        // Soft decorative glows.
        Box(Modifier.align(Alignment.TopEnd).offset(70.dp, (-90).dp).size(240.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
        Box(Modifier.align(Alignment.BottomStart).offset((-70).dp, 50.dp).size(170.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))

        Column(Modifier.statusBarsPadding().padding(top = 8.dp, bottom = 20.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(10.dp))
                Text("Rem", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(role = Role.Button, onClick = onSetup),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Tune, contentDescription = "Reliability settings", tint = Color.White) }
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.greeting, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(4.dp))
                    Text(state.headline, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
                ProgressRing(progress = state.progress) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.todayDone}/${state.todayTotal}", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
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
private fun StatPill(label: String, value: Int, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlight) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f))
    }
}

@Composable
private fun DayPill(day: DayChip, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.12f), label = "day")
    val fg = if (selected) MaterialTheme.colorScheme.primary else Color.White
    Column(
        Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (day.isToday) "Today" else day.weekday, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = if (selected) 1f else 0.8f))
        Spacer(Modifier.height(2.dp))
        Text("${day.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(5.dp).clip(CircleShape)
                .background(if (day.count > 0) (if (selected) MaterialTheme.colorScheme.primary else Color.White) else Color.Transparent),
        )
    }
}

@Composable
private fun CategoryFilter(state: HomeUiState, onSelect: (Category?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterPill("All", null, state.selectedCategory == null, MaterialTheme.colorScheme.primary) { onSelect(null) }
        }
        items(state.categories, key = { it.first.name }) { (cat, count) ->
            FilterPill(cat.label, cat.icon, state.selectedCategory == cat, cat.color, count) { onSelect(cat) }
        }
    }
}

@Composable
private fun FilterPill(text: String, icon: ImageVector?, selected: Boolean, accent: Color, count: Int? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.surface, label = "pill")
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
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
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = Rem.extras.warning.copy(alpha = 0.12f),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Rem.extras.warning), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size(20.dp))
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
private fun EmptyState(filtered: Boolean, onAdd: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).shadow(24.dp, CircleShape, ambientColor = Color(0xFF5B4BFF), spotColor = Color(0xFF5B4BFF))
                .clip(CircleShape).background(Rem.extras.heroGradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(
            if (filtered) "Nothing here" else "Your mind, clear",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (filtered) "No reminders for this filter." else "Tell Rem anything: tasks, medicine, bills, birthdays. Just type it the way you'd say it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!filtered) {
            Spacer(Modifier.height(20.dp))
            Text("TRY ONE", style = app.rem.ui.theme.SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            EXAMPLES.forEach { (cat, text) ->
                Surface(
                    onClick = { onAdd(text) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
            val toDone = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = if (toDone) Rem.extras.success else Rem.extras.warning
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(color).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDone) Arrangement.Start else Arrangement.End,
            ) {
                Icon(if (toDone) Icons.Rounded.Check else Icons.Rounded.Snooze, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if (toDone) "Done" else "Snooze", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        },
    ) {
        ReminderCard(model, onClick = onOpen, onComplete = onComplete)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(18.dp, RoundedCornerShape(50), ambientColor = Color(0xFF5B4BFF), spotColor = Color(0xFF5B4BFF))
            .clip(RoundedCornerShape(50))
            .background(Rem.extras.accentGradient)
            .clickable(role = Role.Button, onClickLabel = "Add reminder", onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text("Remind me", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeSheet(onDismiss: () -> Unit, onPick: (SnoozePreset) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
            options.forEach { (preset, icon, label) ->
                Surface(
                    onClick = { onPick(preset) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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

@Composable
fun AnimatedAppear(content: @Composable () -> Unit) {
    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 4 }) { content() }
}
