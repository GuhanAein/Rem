package app.rem.ui.format

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

fun formatTime(time: LocalTime): String = time.format(TIME)

fun formatLongDate(date: LocalDate): String = date.format(LONG_DATE)

fun formatDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    date == today -> "Today"
    date == today.plusDays(1) -> "Tomorrow"
    date == today.minusDays(1) -> "Yesterday"
    date.isAfter(today) && date.isBefore(today.plusDays(7)) -> date.format(WEEKDAY)
    date.year == today.year -> date.format(DAY_MONTH)
    else -> date.format(DAY_MONTH_YEAR)
}

/** "Today · 6:00 PM", "Fri · 4:30 PM", "3 Jan 2027 · 9:00 AM", "No date". */
fun describeDue(due: LocalDateTime?, today: LocalDate = LocalDate.now()): String =
    if (due == null) "No date" else "${formatDay(due.toLocalDate(), today)} · ${formatTime(due.toLocalTime())}"

/** "5 min late", "2 h late", "3 days late". */
fun describeLateness(due: LocalDateTime, now: LocalDateTime): String {
    val d = Duration.between(due, now)
    return when {
        d.toMinutes() < 60 -> "${d.toMinutes().coerceAtLeast(1)} min late"
        d.toHours() < 24 -> "${d.toHours()} h late"
        else -> "${d.toDays()} day${if (d.toDays() == 1L) "" else "s"} late"
    }
}

fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

fun describeOffset(minutes: Int): String = when {
    minutes == 0 -> "At time"
    minutes < 60 -> "$minutes min before"
    minutes < 1440 -> "${minutes / 60} h before"
    minutes % 10080 == 0 -> "${minutes / 10080} wk before"
    else -> "${minutes / 1440} day${if (minutes / 1440 == 1) "" else "s"} before"
}
