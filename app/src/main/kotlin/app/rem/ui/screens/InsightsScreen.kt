package app.rem.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.rem.container
import app.rem.core.insights.LogEvent
import app.rem.core.insights.Reliability
import app.rem.core.insights.SnoozeAdvisor
import app.rem.core.insights.Streaks
import app.rem.core.insights.YearInReview
import app.rem.core.model.Reminder
import app.rem.ui.components.Caption
import app.rem.ui.components.GroupedCard
import app.rem.ui.components.RowDivider
import app.rem.ui.components.SettingRow
import app.rem.ui.components.noirSurface
import app.rem.ui.format.formatTime
import app.rem.ui.theme.Rem
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(onBack: () -> Unit, onFixReliability: () -> Unit, onYear: () -> Unit) {
    val context = LocalContext.current
    val c = Rem.colors
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val active = remember { context.container.repository.observeActive() }.collectAsState(emptyList()).value
    val log by remember { context.container.database.log().observeSince(today.minusDays(120).atStartOfDay().toString()) }.collectAsState(emptyList())
    val events = log.map { it.toEvent() }
    val report by produceState<Reliability.Report?>(null) {
        value = Reliability.report(context.container.database.fired().since(System.currentTimeMillis() - 7L * 24 * 3600 * 1000).map { it.toRecord() })
    }

    NoirScreen("Insights", "Your *rhythm.*", onBack) {
        // ---- Done today
        val doneToday = log.filter { it.type == LogEvent.Type.DONE.name && it.at.startsWith(today.toString()) }
        item {
            Caption("Done today", count = doneToday.size)
            Column(Modifier.fillMaxWidth().noirSurface().padding(18.dp)) {
                Text(if (doneToday.isEmpty()) "Nothing yet." else "${doneToday.size} done.", style = MaterialTheme.typography.displaySmall, color = c.label)
                doneToday.take(12).forEach { e ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, null, tint = c.gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(e.title, style = MaterialTheme.typography.bodyLarge, color = c.label, modifier = Modifier.weight(1f))
                        Text(formatTime(java.time.LocalDateTime.parse(e.at).toLocalTime()), style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel)
                    }
                }
            }
        }

        // ---- Streaks (repeating reminders and habits)
        val streaks = active.filter { it.isRecurring }.map { r ->
            r to Streaks.compute(events.filter { it.reminderId == r.id && it.type == LogEvent.Type.DONE }.map { it.at.toLocalDate() }.toSet(), today)
        }.filter { it.second.best > 0 }.sortedByDescending { it.second.current }
        if (streaks.isNotEmpty()) {
            item { Caption("Streaks") }
            item {
                GroupedCard {
                    streaks.take(8).forEachIndexed { i, (r, s) ->
                        if (i > 0) RowDivider(54.dp)
                        StreakRow(r, s, events, today)
                    }
                }
                Text("A missed day is forgiven once a week, so one off day never breaks a streak.", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            }
        }

        // ---- Snooze learning
        val suggestions = active.mapNotNull { r -> SnoozeAdvisor.suggest(r, events)?.let { r to it } }
        if (suggestions.isNotEmpty()) {
            item { Caption("Rem noticed") }
            item {
                GroupedCard {
                    suggestions.forEachIndexed { i, (r, t) ->
                        if (i > 0) RowDivider()
                        SettingRow(
                            "You usually push “${r.title}” to ${formatTime(t)}",
                            value = "Move it", valueColor = c.gold, showChevron = false,
                            onClick = { scope.launch { context.container.service.save(r.copy(dueAt = r.dueAt!!.with(t))) } },
                        )
                    }
                }
            }
        }

        // ---- Reliability
        item {
            Caption("Reliability, last 7 days")
            GroupedCard {
                val r = report
                SettingRow(
                    when {
                        r == null -> "Checking…"
                        r.total == 0 -> "No alarms fired yet"
                        r.healthy -> "Every alarm was on time"
                        else -> "${r.late} of ${r.total} alarms were late (up to ${r.worstMinutes} min)"
                    },
                    icon = Icons.Rounded.Shield, iconColor = if (r?.healthy != false) c.success else c.danger,
                    value = if (r != null && !r.healthy) "Fix" else null, valueColor = c.gold,
                    onClick = if (r != null && !r.healthy) onFixReliability else null,
                )
            }
        }

        // ---- Year in Rem
        item {
            Caption("Year in Rem")
            val recap = YearInReview.recap(today.year, events)
            Column(Modifier.fillMaxWidth().noirSurface().clickable(onClick = onYear).padding(18.dp)) {
                Text("${recap.year}", style = MaterialTheme.typography.labelSmall, color = c.gold)
                Text("${recap.completed} things done.", style = MaterialTheme.typography.headlineLarge, color = c.label)
                Text("See your year →", style = MaterialTheme.typography.labelLarge, color = c.secondaryLabel)
            }
        }
    }
}

@Composable
private fun StreakRow(r: Reminder, s: Streaks.Result, events: List<LogEvent>, today: LocalDate) {
    val c = Rem.colors
    val done = events.filter { it.reminderId == r.id && it.type == LogEvent.Type.DONE }.map { it.at.toLocalDate() }.toSet()
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.LocalFireDepartment, null, tint = if (s.current > 0) c.gold else c.tertiaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, color = c.label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Best ${s.best}", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
                if (s.freezesUsed > 0) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.AcUnit, "Streak freeze used", tint = c.secondaryLabel, modifier = Modifier.size(12.dp))
                    Text(" freeze used", style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
                }
            }
            Spacer(Modifier.height(6.dp))
            // Last 14 days heat strip.
            val gold = c.gold
            val empty = c.fill
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val n = 14
                val gap = 3.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val d = today.minusDays((n - 1 - i).toLong())
                    drawRoundRect(if (d in done) gold else empty, Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(2.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${s.current}", style = MaterialTheme.typography.titleLarge, color = c.label)
            Text("DAYS", style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
        }
    }
}

/** A full-screen annual recap. */
@Composable
fun YearScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = Rem.colors
    val year = LocalDate.now().year
    val events by produceState(emptyList<LogEvent>()) { value = context.container.database.log().all().map { it.toEvent() } }
    val recap = YearInReview.recap(year, events)
    NoirScreen("Year in Rem", "$year, *remembered.*", onBack) {
        item { Spacer(Modifier.height(12.dp)) }
        item { BigStat("${recap.completed}", "things done") }
        item { BigStat("${recap.longestStreak}", "days, your longest streak") }
        recap.busiestMonth?.let { m -> item { BigStat(m.getDisplayName(TextStyle.FULL, Locale.ENGLISH), "your busiest month") } }
        recap.busiestWeekday?.let { d -> item { BigStat(d.getDisplayName(TextStyle.FULL, Locale.ENGLISH), "the day you got the most done") } }
        recap.favouriteHour?.let { h -> item { BigStat(formatTime(java.time.LocalTime.of(h, 0)), "your most productive hour") } }
        if (recap.completed == 0) item { Text("Your recap fills in as you complete reminders.", style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel) }
    }
}

@Composable
private fun BigStat(value: String, label: String) {
    val c = Rem.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.displayLarge, color = c.label)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.gold)
    }
}
