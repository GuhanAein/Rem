package app.rem.ui.spaces

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.rem.container
import app.rem.core.model.Reminder
import app.rem.core.model.Space
import app.rem.core.model.TimeWindow
import app.rem.core.spaces.WorkHours
import app.rem.core.spaces.WorkStatus
import app.rem.data.ProjectEntity
import app.rem.ui.components.Caption
import app.rem.ui.components.CheckCircle
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.OptionSheet
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SegmentedControl
import app.rem.ui.components.SettingRow
import app.rem.ui.components.SwitchRow
import app.rem.ui.components.noirSurface
import app.rem.ui.edit.FieldRow
import app.rem.ui.format.formatDay
import app.rem.ui.format.formatTime
import app.rem.ui.motion.rememberHaptics
import app.rem.ui.onboarding.editorial
import app.rem.ui.theme.Rem
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val HOURS_PRESETS = listOf(
    WorkHours(TimeWindow.WEEKDAYS, LocalTime.of(9, 30), LocalTime.of(18, 30)) to "Mon–Fri, 9:30 AM – 6:30 PM",
    WorkHours(TimeWindow.WEEKDAYS, LocalTime.of(9, 0), LocalTime.of(17, 0)) to "Mon–Fri, 9 AM – 5 PM",
    WorkHours(TimeWindow.WEEKDAYS, LocalTime.of(10, 0), LocalTime.of(19, 0)) to "Mon–Fri, 10 AM – 7 PM",
    WorkHours(TimeWindow.WEEKDAYS + DayOfWeek.SATURDAY, LocalTime.of(10, 0), LocalTime.of(19, 0)) to "Mon–Sat, 10 AM – 7 PM",
)

@Composable
fun WorkScreen(initialBoard: Boolean = false, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val c = Rem.colors
    val scope = rememberCoroutineScope()
    val container = context.container
    val spaces = container.spaces
    val settings by container.settings.state.collectAsState()
    val all by remember { container.repository.observeActive() }.collectAsState(emptyList())
    val projects by remember { container.database.projects().observeAll() }.collectAsState(emptyList())
    var project by remember { mutableStateOf<Long?>(null) }
    var board by remember { mutableStateOf(initialBoard) }
    var input by remember { mutableStateOf("") }
    var acting by remember { mutableStateOf<Reminder?>(null) }
    var askWho by remember { mutableStateOf<Reminder?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var newProject by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val tasks = all.filter { it.space == Space.WORK && (project == null || it.projectId == project) }
    val weekAgo = remember { java.time.Instant.now().minus(java.time.Duration.ofDays(7)) }
    val completed by remember { container.repository.observeCompletedSince(weekAgo) }.collectAsState(emptyList())
    val doneRecent = completed.filter { it.space == Space.WORK && it.status == app.rem.core.model.Status.DONE }
    fun statusOf(r: Reminder) = r.workStatus?.let { runCatching { WorkStatus.valueOf(it) }.getOrNull() } ?: WorkStatus.TODO
    val now = LocalDateTime.now()
    val hours = settings.workHours
    val onClock = hours.isOn(now)

    LazyColumn(Modifier.fillMaxSize().background(c.canvas), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = LocalBottomInset.current + 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 20.dp)) {
                Text("WORK", style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, modifier = Modifier.padding(start = 2.dp))
                Spacer(Modifier.height(8.dp))
                val open = tasks.count { statusOf(it) != WorkStatus.DONE }
                Text(editorial(if (open == 0) "Inbox *zero.*" else "$open on your *plate.*"), style = MaterialTheme.typography.displaySmall, color = c.label)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.separator, RoundedCornerShape(50)).clickable { sheet = "hours" }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (onClock) c.success else c.tertiaryLabel))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (!settings.respectWorkHours) "Work hours off" else if (onClock) "On the clock until ${formatTime(hours.end)}"
                        else hours.resumesAt(now).let { "Off the clock · back ${formatDay(it.toLocalDate())} ${formatTime(it.toLocalTime())}" },
                        style = MaterialTheme.typography.labelMedium, color = c.label,
                    )
                }
            }
        }
        // Projects
        item {
            LazyRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ProjectChip("All", null, project == null) { project = null } }
                items(projects, key = { it.id }) { p -> ProjectChip(p.name, Color(p.color), project == p.id) { project = if (project == p.id) null else p.id } }
                item {
                    Box(Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.separator, RoundedCornerShape(50)).clickable { newProject = true }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Icon(Icons.Rounded.Add, "New project", tint = c.gold, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        // Add task
        item {
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().noirSurface(RoundedCornerShape(26.dp)).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (input.isEmpty()) Text("Send the deck to Ravi Thu 3pm", style = MaterialTheme.typography.bodyLarge, color = c.tertiaryLabel)
                    BasicTextField(
                        input, { input = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.label), cursorBrush = SolidColor(c.gold),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) scope.launch { spaces.addTask(input, project); input = "" } }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SendOrMic(input.isNotBlank(), onSend = { haptics.tick(); scope.launch { spaces.addTask(input, project); input = "" } }, onMic = { voice = true })
            }
        }
        item { SegmentedControl(listOf("List", "Board"), if (board) 1 else 0, { board = it == 1 }, Modifier.padding(top = 14.dp)) }

        val order = listOf(WorkStatus.DOING, WorkStatus.TODO, WorkStatus.WAITING)
        if (!board) {
            order.forEach { st ->
                val group = tasks.filter { statusOf(it) == st }.sortedWith(compareBy(nullsLast()) { it.dueAt })
                if (group.isNotEmpty()) item(key = "s-$st") {
                    Caption(st.label, Modifier.animateItem(), count = group.size, color = if (st == WorkStatus.DOING) c.gold else c.secondaryLabel)
                    GroupedCard(Modifier.animateItem()) {
                        group.forEachIndexed { i, t ->
                            if (i > 0) RowDivider(56.dp)
                            TaskRow(t, projects, onDone = { haptics.confirm(); scope.launch { spaces.setStatus(t, WorkStatus.DONE) } }, onClick = { acting = t })
                        }
                    }
                }
            }
            if (tasks.isEmpty()) item { Text("Add what's on your plate. Work reminders stay quiet outside work hours and come back the next working morning.", style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, modifier = Modifier.padding(top = 28.dp, start = 4.dp)) }
            val dueToday = tasks.count { t -> t.dueAt?.toLocalDate()?.let { !it.isAfter(LocalDate.now()) } == true && statusOf(t) != WorkStatus.WAITING }
            if (dueToday > 0) item {
                GroupedCard(Modifier.padding(top = 22.dp)) {
                    SettingRow("Wrap up the day", icon = Icons.Rounded.WbTwilight, iconColor = c.gold, value = "Move $dueToday to tomorrow", valueColor = c.gold, showChevron = false,
                        onClick = { scope.launch { spaces.wrapUp() } })
                }
            }
        } else {
            item {
                val board = BOARD_COLUMNS.associateWith { st ->
                    if (st == WorkStatus.DONE) doneRecent.filter { project == null || it.projectId == project }
                    else tasks.filter { statusOf(it) == st }.sortedWith(compareBy(nullsLast()) { it.dueAt })
                }
                KanbanBoard(
                    board, projects,
                    onMove = { t, to ->
                        scope.launch {
                            when {
                                t.status == app.rem.core.model.Status.DONE -> container.service.save(
                                    t.copy(status = app.rem.core.model.Status.ACTIVE, completedAt = null, workStatus = to.name),
                                ).also { if (to == WorkStatus.WAITING) askWho = container.repository.get(t.id) }
                                to == WorkStatus.WAITING -> askWho = t
                                else -> spaces.setStatus(t, to)
                            }
                        }
                    },
                    onTap = { t -> if (t.status == app.rem.core.model.Status.DONE) onOpen(t.id) else acting = t },
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text("Press and hold a card to drag it to another column.", style = MaterialTheme.typography.bodySmall, color = c.tertiaryLabel, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
            }
        }
    }

    if (voice) {
        app.rem.ui.voice.VoiceInputSheet(
            caption = "New work task", example = "send the deck to Ravi on Thursday at 3", actionLabel = "Add task",
            onConfirm = { text -> scope.launch { spaces.addTask(text, project) }; voice = false },
            onDismiss = { voice = false },
            preview = { text ->
                val parsed = remember(text) { app.rem.core.parser.QuickAddParser(java.time.Clock.systemDefaultZone()).parse(text) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.rem.ui.components.Token(parsed.title, Icons.Rounded.WorkOutline, tint = c.label)
                    parsed.dueAt?.let { app.rem.ui.components.Token(app.rem.ui.format.describeDue(it), Icons.Rounded.Schedule, tint = c.gold) }
                }
            },
        )
    }

    acting?.let { t ->
        OptionSheet(t.title, listOf("DOING" to "Start working on it", "TODO" to "Move back to To do", "WAITING" to "Waiting on someone…", "TOMORROW" to "Move to tomorrow morning", "DONE" to "Done", "EDIT" to "Open details"),
            { it == (t.workStatus ?: "TODO") }, { k ->
                scope.launch {
                    when (k) {
                        "WAITING" -> askWho = t
                        "TOMORROW" -> container.service.save(t.copy(dueAt = app.rem.core.spaces.Work.nextWorkMorning(LocalDate.now(), hours)))
                        "EDIT" -> onOpen(t.id)
                        else -> spaces.setStatus(t, WorkStatus.valueOf(k))
                    }
                }
                acting = null
            }, { acting = null })
    }
    askWho?.let { t ->
        var who by remember { mutableStateOf(t.waitingOn.orEmpty()) }
        AlertDialog(
            onDismissRequest = { askWho = null },
            title = { Text("Waiting on whom?") },
            text = { Column { GroupedCard { FieldRow("Person", who, "e.g. Ravi") { who = it } }; Text("Rem reminds you to follow up after two working days.", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, modifier = Modifier.padding(top = 8.dp)) } },
            confirmButton = { TextButton(onClick = { scope.launch { spaces.setStatus(t, WorkStatus.WAITING, who) }; askWho = null }) { Text("Save", color = c.gold) } },
            dismissButton = { TextButton(onClick = { askWho = null }) { Text("Cancel") } },
        )
    }
    if (sheet == "hours") {
        OptionSheet("Work hours", HOURS_PRESETS, { it == hours && settings.respectWorkHours }, { h ->
            container.settings.update { it.copy(workHours = h, respectWorkHours = true) }
            scope.launch { spaces.applyWorkHours(); app.rem.jobs.DailyJobs.schedule(context) }
            sheet = null
        }, { sheet = null }, icons = { { Icon(Icons.Rounded.Schedule, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp)) } }, footer = {
            Spacer(Modifier.height(12.dp))
            GroupedCard {
                SwitchRow("Hold work reminders outside these hours", settings.respectWorkHours, { on ->
                    container.settings.update { it.copy(respectWorkHours = on) }
                    scope.launch { spaces.applyWorkHours() }
                })
            }
        })
    }
    if (newProject) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newProject = false },
            title = { Text("New project") },
            text = { GroupedCard { FieldRow("Name", name, "e.g. Q1 launch") { name = it } } },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) scope.launch { project = spaces.addProject(name.trim()) }; newProject = false }) { Text("Create", color = c.gold) } },
            dismissButton = { TextButton(onClick = { newProject = false }) { Text("Cancel") } },
        )
    }
}

private fun taskMeta(t: Reminder, projects: List<ProjectEntity>): String = listOfNotNull(
    projects.firstOrNull { it.id == t.projectId }?.name,
    t.waitingOn?.takeIf { t.workStatus == WorkStatus.WAITING.name }?.let { "Waiting on $it" },
    t.dueAt?.let { "${formatDay(it.toLocalDate())}, ${formatTime(it.toLocalTime())}" },
).joinToString("  ·  ")

@Composable
private fun TaskRow(t: Reminder, projects: List<ProjectEntity>, onDone: () -> Unit, onClick: () -> Unit) {
    val c = Rem.colors
    val late = t.dueAt?.isBefore(LocalDateTime.now()) == true
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(start = 6.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckCircle(c.gold, false, onDone)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                projects.firstOrNull { it.id == t.projectId }?.let { Box(Modifier.size(6.dp).clip(CircleShape).background(Color(it.color))); Spacer(Modifier.width(8.dp)) }
                Text(t.title, style = MaterialTheme.typography.titleMedium, color = c.label)
            }
            val meta = taskMeta(t, projects)
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = if (late) c.danger else c.secondaryLabel)
        }
    }
}

@Composable
private fun ProjectChip(name: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    val c = Rem.colors
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) c.ivory else c.surface).border(1.dp, if (selected) c.ivory else c.separator, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        color?.let { Box(Modifier.size(7.dp).clip(CircleShape).background(it)); Spacer(Modifier.width(8.dp)) }
        Text(name, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onIvory else c.label)
    }
}
