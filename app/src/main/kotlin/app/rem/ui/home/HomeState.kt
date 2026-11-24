package app.rem.ui.home

import app.rem.core.model.Category
import app.rem.core.model.Reminder
import app.rem.core.recurrence.RecurrenceRule
import app.rem.core.recurrence.describe
import app.rem.ui.components.ReminderCardModel
import app.rem.ui.format.describeLateness
import app.rem.ui.format.formatDay
import app.rem.ui.format.formatLongDate
import app.rem.ui.format.formatTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The four smart lists at the top of the home screen. */
enum class SmartList(val title: String) { TODAY("Today"), SCHEDULED("Scheduled"), OVERDUE("Overdue"), ALL("All") }

data class HomeSection(val key: String, val title: String, val items: List<ReminderCardModel>, val danger: Boolean = false)

data class HomeUiState(
    val dateLabel: String = "",
    val greeting: String = "",
    val list: SmartList = SmartList.TODAY,
    val counts: Map<SmartList, Int> = emptyMap(),
    val categories: List<Pair<Category, Int>> = emptyList(),
    val selectedCategory: Category? = null,
    val sections: List<HomeSection> = emptyList(),
    val todayTotal: Int = 0,
    val todayDone: Int = 0,
    val needsSetup: Boolean = false,
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && sections.all { it.items.isEmpty() }
    val progress: Float get() = if (todayTotal == 0) 0f else todayDone.toFloat() / todayTotal
    val remainingToday: Int get() = (todayTotal - todayDone).coerceAtLeast(0)
}

private val DATE_LABEL = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

fun buildHomeState(
    active: List<Reminder>,
    completedToday: List<Reminder>,
    now: LocalDateTime,
    list: SmartList,
    selectedCategory: Category?,
    needsSetup: Boolean,
): HomeUiState {
    val today = now.toLocalDate()
    fun isOverdue(r: Reminder) = r.dueAt != null && r.dueAt!!.isBefore(now)
    fun isToday(r: Reminder) = r.dueAt?.toLocalDate() == today

    val counts = mapOf(
        SmartList.TODAY to active.count { isToday(it) || isOverdue(it) },
        SmartList.SCHEDULED to active.count { it.dueAt != null },
        SmartList.OVERDUE to active.count(::isOverdue),
        SmartList.ALL to active.size,
    )
    val remaining = counts.getValue(SmartList.TODAY)
    val done = completedToday.size

    fun card(r: Reminder): ReminderCardModel {
        val due = r.dueAt
        val late = due != null && due.isBefore(now)
        val text = when {
            due == null -> r.category.label
            late && due.toLocalDate() == today -> "${formatTime(due.toLocalTime())} \u00B7 ${describeLateness(due, now)}"
            late -> "${formatDay(due.toLocalDate(), today)}, ${formatTime(due.toLocalTime())}"
            due.toLocalDate() == today -> formatTime(due.toLocalTime())
            else -> "${formatDay(due.toLocalDate(), today)}, ${formatTime(due.toLocalTime())}"
        }
        val repeat = RecurrenceRule.parseOrNull(r.recurrence)?.describe()
        val clock = due?.let { formatTime(it.toLocalTime()) }
        val meta = listOfNotNull(
            r.category.label,
            repeat,
            if (late && due != null) describeLateness(due, now) else null,
        ).joinToString("  \u00B7  ")
        return ReminderCardModel(
            id = r.id, title = r.title, category = r.category, priority = r.priority, dueText = text, isLate = late,
            repeatText = repeat, alertStyle = r.alertStyle,
            amount = r.amount, notes = r.notes, nagging = r.nagMinutes != null,
            time = clock?.substringBefore(' '),
            meridiem = clock?.substringAfter(' ', ""),
            dayLabel = due?.toLocalDate()?.takeIf { it != today }?.let { formatDay(it, today) },
            meta = meta,
        )
    }

    val pool = active.filter { selectedCategory == null || it.category == selectedCategory }
    val sections: List<HomeSection> = when (list) {
        SmartList.TODAY -> listOf(
            HomeSection("overdue", "Overdue", pool.filter { isOverdue(it) }.map(::card), danger = true),
            HomeSection("today", "Today", pool.filter { isToday(it) && !isOverdue(it) }.map(::card)),
        )
        SmartList.OVERDUE -> listOf(HomeSection("overdue", "Overdue", pool.filter(::isOverdue).map(::card), danger = true))
        SmartList.SCHEDULED -> scheduledSections(pool.filter { it.dueAt != null }, today, now, ::card)
        SmartList.ALL -> scheduledSections(pool.filter { it.dueAt != null }, today, now, ::card) +
            HomeSection("none", "No date", pool.filter { it.dueAt == null }.map(::card))
    }.filter { it.items.isNotEmpty() }

    return HomeUiState(
        dateLabel = now.format(DATE_LABEL).uppercase(Locale.ENGLISH),
        greeting = app.rem.ui.format.greetingFor(now.toLocalTime()),
        list = list,
        counts = counts,
        categories = active.groupingBy { it.category }.eachCount().toList().sortedBy { it.first.ordinal },
        selectedCategory = selectedCategory,
        sections = sections,
        todayTotal = remaining + done,
        todayDone = done,
        needsSetup = needsSetup,
        loading = false,
    )
}

/** Overdue, then one section per day for the next week, then "Later". */
private fun scheduledSections(
    items: List<Reminder>,
    today: LocalDate,
    now: LocalDateTime,
    card: (Reminder) -> ReminderCardModel,
): List<HomeSection> {
    val overdue = items.filter { it.dueAt!!.isBefore(now) }
    val upcoming = items - overdue.toSet()
    val byDay = upcoming.groupBy { it.dueAt!!.toLocalDate() }
    val days = (0L..6L).map { today.plusDays(it) }
    val later = upcoming.filter { it.dueAt!!.toLocalDate().isAfter(today.plusDays(6)) }
    return buildList {
        add(HomeSection("overdue", "Overdue", overdue.map(card), danger = true))
        days.forEach { d ->
            val title = when (d) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> formatLongDate(d)
            }
            add(HomeSection("d-$d", title, byDay[d].orEmpty().map(card)))
        }
        add(HomeSection("later", "Later", later.map(card)))
    }
}
