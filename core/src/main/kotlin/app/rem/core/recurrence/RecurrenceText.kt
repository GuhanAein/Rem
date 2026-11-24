package app.rem.core.recurrence

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Human-friendly description of a rule: "Every Mon, Wed, Fri", "Monthly on the last day". */
fun RecurrenceRule.describe(): String {
    val every = if (interval == 1) "" else "$interval "
    val unit = when (freq) {
        Freq.DAILY -> if (interval == 1) "Daily" else "Every ${every}days"
        Freq.WEEKLY -> if (interval == 1) "Weekly" else "Every ${every}weeks"
        Freq.MONTHLY -> if (interval == 1) "Monthly" else "Every ${every}months"
        Freq.YEARLY -> if (interval == 1) "Yearly" else "Every ${every}years"
    }
    val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val detail = when {
        byDay.isNotEmpty() && byDay.all { it.ordinal == null } && byDay.map { it.day }.toSet() == weekdays -> return "Every weekday"
        byDay.isNotEmpty() && byDay.all { it.ordinal == null } ->
            byDay.joinToString(", ") { it.day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
                .let { return if (interval == 1) "Every $it" else "$unit on $it" }
        byDay.isNotEmpty() -> " on the " + byDay.joinToString(", ") {
            ordinalWord(it.ordinal!!) + " " + it.day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        }
        byMonthDay.isNotEmpty() -> " on the " + byMonthDay.joinToString(", ") { if (it == -1) "last day" else ordinalWord(it) }
        else -> ""
    }
    val end = when {
        count != null -> ", $count times"
        else -> ""
    }
    return unit + detail + end
}

private fun ordinalWord(n: Int): String = when {
    n == -1 -> "last"
    n < 0 -> "${-n}${suffix(-n)} last"
    else -> "$n${suffix(n)}"
}

private fun suffix(n: Int) = when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}
