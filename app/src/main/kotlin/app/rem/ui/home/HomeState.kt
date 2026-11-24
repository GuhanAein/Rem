package app.rem.ui.home

import androidx.compose.ui.graphics.Color
import app.rem.core.model.Category
import app.rem.core.model.Reminder
import app.rem.core.recurrence.RecurrenceRule
import app.rem.core.recurrence.describe
import app.rem.ui.components.ReminderCardModel
import app.rem.ui.format.describeLateness
import app.rem.ui.format.formatDay
import app.rem.ui.format.formatLongDate
import app.rem.ui.format.formatTime
import app.rem.ui.format.greetingFor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

enum class SectionKind(val title: String, val accent: Color) {
    OVERDUE("Overdue", Color(0xFFEF4444)),
    TODAY("Today", Color(0xFF5B4BFF)),
    TOMORROW("Tomorrow", Color(0xFFA855F7)),
    WEEK("This week", Color(0xFF06B6D4)),
    LATER("Later", Color(0xFF10B981)),
    SOMEDAY("Someday", Color(0xFF94A3B8)),
    DAY("Scheduled", Color(0xFF5B4BFF)),
}

data class HomeSection(val kind: SectionKind, val title: String, val items: List<ReminderCardModel>)

data class DayChip(val date: LocalDate, val weekday: String, val dayOfMonth: Int, val count: Int, val isToday: Boolean)

data class HomeUiState(
    val greeting: String = "",
    val headline: String = "",
    val todayTotal: Int = 0,
    val todayDone: Int = 0,
    val laterTodayCount: Int = 0,
    val overdueCount: Int = 0,
    val upcomingCount: Int = 0,
    val week: List<DayChip> = emptyList(),
    val selectedDate: LocalDate? = null,
    val categories: List<Pair<Category, Int>> = emptyList(),
    val selectedCategory: Category? = null,
    val sections: List<HomeSection> = emptyList(),
    val needsSetup: Boolean = false,
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && sections.all { it.items.isEmpty() }
    val progress: Float get() = if (todayTotal == 0) 0f else todayDone.toFloat() / todayTotal
}

/** Pure function: active + completed reminders and the current filters -> everything the home screen shows. */
fun buildHomeState(
    active: List<Reminder>,
    completedToday: List<Reminder>,
    now: LocalDateTime,
    selectedDate: LocalDate?,
    selectedCategory: Category?,
    needsSetup: Boolean,
): HomeUiState {
    val today = now.toLocalDate()
    // Everything still needing attention today = overdue + due later today; these always add up.
    val overdue = active.count { it.dueAt != null && it.dueAt!!.isBefore(now) }
    val laterToday = active.count { it.dueAt?.toLocalDate() == today && !it.dueAt!!.isBefore(now) }
    val upcoming = active.count { it.dueAt != null && it.dueAt!!.toLocalDate().isAfter(today) }
    val doneToday = completedToday.size
    val remainingToday = overdue + laterToday
    val todayTotal = doneToday + remainingToday

    val headline = when {
        active.isEmpty() && doneToday == 0 -> "What should I\nremember for you?"
        remainingToday == 0 -> "You're all\ncaught up today"
        remainingToday == 1 -> "1 thing to\nremember today"
        else -> "$remainingToday things to\nremember today"
    }

    val categories = active.groupingBy { it.category }.eachCount().toList().sortedByDescending { it.second }
    val filtered = active.filter { selectedCategory == null || it.category == selectedCategory }

    val week = (0 until 14).map { offset ->
        val d = today.plusDays(offset.toLong())
        DayChip(
            date = d,
            weekday = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            dayOfMonth = d.dayOfMonth,
            count = active.count { it.dueAt?.toLocalDate() == d },
            isToday = offset == 0,
        )
    }

    fun card(r: Reminder): ReminderCardModel {
        val due = r.dueAt
        val late = due != null && due.isBefore(now)
        val dueText = when {
            due == null -> "Anytime"
            late && due.toLocalDate() == today -> "${formatTime(due.toLocalTime())} · ${describeLateness(due, now)}"
            late -> describeLateness(due, now)
            due.toLocalDate() == today || due.toLocalDate() == selectedDate -> formatTime(due.toLocalTime())
            due.toLocalDate() == today.plusDays(1) -> "Tomorrow · ${formatTime(due.toLocalTime())}"
            else -> "${formatDay(due.toLocalDate(), today)} · ${formatTime(due.toLocalTime())}"
        }
        return ReminderCardModel(
            id = r.id,
            title = r.title,
            category = r.category,
            priority = r.priority,
            dueText = dueText,
            isLate = late,
            repeatText = RecurrenceRule.parseOrNull(r.recurrence)?.describe(),
            alertStyle = r.alertStyle,
            amount = r.amount,
            notes = r.notes,
            nagging = r.nagMinutes != null,
        )
    }

    val sections = if (selectedDate != null && selectedDate != today) {
        listOf(
            HomeSection(
                SectionKind.DAY,
                formatLongDate(selectedDate),
                filtered.filter { it.dueAt?.toLocalDate() == selectedDate }.map(::card),
            ),
        )
    } else {
        val endOfWeek = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val groups = filtered.groupBy { r ->
            val due = r.dueAt
            when {
                due == null -> SectionKind.SOMEDAY
                due.isBefore(now) -> SectionKind.OVERDUE
                due.toLocalDate() == today -> SectionKind.TODAY
                due.toLocalDate() == today.plusDays(1) -> SectionKind.TOMORROW
                !due.toLocalDate().isAfter(endOfWeek) -> SectionKind.WEEK
                else -> SectionKind.LATER
            }
        }
        SectionKind.entries.filter { it != SectionKind.DAY }.mapNotNull { kind ->
            groups[kind]?.let { HomeSection(kind, kind.title, it.map(::card)) }
        }
    }

    return HomeUiState(
        greeting = greetingFor(now.toLocalTime()),
        headline = headline,
        todayTotal = todayTotal,
        todayDone = doneToday,
        laterTodayCount = laterToday,
        overdueCount = overdue,
        upcomingCount = upcoming,
        week = week,
        selectedDate = selectedDate ?: today,
        categories = categories,
        selectedCategory = selectedCategory,
        sections = sections,
        needsSetup = needsSetup,
        loading = false,
    )
}
