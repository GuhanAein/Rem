package app.rem.core.parser

import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.core.recurrence.Freq
import app.rem.core.recurrence.RecurrenceRule
import app.rem.core.recurrence.WeekdayNum
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class ParsedReminder(
    val title: String,
    val dueAt: LocalDateTime?,
    val recurrence: RecurrenceRule?,
    val priority: Priority,
    val category: Category,
    /** Character ranges of the input that were understood as date/time/priority (for highlighting). */
    val matchedSpans: List<IntRange>,
)

/**
 * Rule-based natural-language parser: "pay rent every 1st 9am !1", "call mom tomorrow 6pm".
 * No AI and no network: ordered regex matchers each consume a span of the text.
 */
class QuickAddParser(private val clock: Clock) {

    private val weekdayAlt = DateVocabulary.WEEKDAYS.keys.sortedByDescending { it.length }.joinToString("|")
    private val monthAlt = DateVocabulary.MONTHS.keys.sortedByDescending { it.length }.joinToString("|")

    private val reMeridiemTime = Regex("""\b(?:at\s+|@\s*)?(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)(?=\s|$|[,.!?])""")
    private val re24hTime = Regex("""\b(?:at\s+|@\s*)?([01]?\d|2[0-3]):([0-5]\d)\b""")
    private val reBareAt = Regex("""\bat\s+(\d{1,2})\b(?![:/])""")
    private val rePartOfDay = Regex("""\b(?:in\s+the\s+|this\s+)?(morning|afternoon|evening|tonight|night|noon|midnight)\b""")
    private val rePriority = Regex("""(?<=^|\s)(!!!|!!|!1|!2|!3|!4|p1|p2|p3|p4|!)(?=\s|$)""")
    private val reRelative = Regex("""\bin\s+(\d+|an?|one|two|three|half\s+an?)\s+(minutes?|mins?|hours?|hrs?|days?|weeks?|months?)\b""")
    private val reDayAfterTomorrow = Regex("""\bday\s+after\s+(?:tomorrow|tmrw|tmr)\b""")
    private val reRelDay = Regex("""\b(today|tomorrow|tmrw|tmr|tonight|next\s+week|next\s+month|this\s+weekend|weekend)\b""")
    private val reWeekday = Regex("""\b(?:(next|this|on|coming)\s+)?($weekdayAlt)\b""")
    private val reDayMonth = Regex("""\b(?:on\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($monthAlt)\b(?:,?\s+(\d{4}))?""")
    private val reMonthDay = Regex("""\b(?:on\s+)?($monthAlt)\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4}))?""")
    private val reNumericDate = Regex("""\b(?:on\s+)?(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val reOrdinalDay = Regex("""\bon\s+the\s+(\d{1,2})(?:st|nd|rd|th)\b""")

    private val reEvery = Regex(
        """\b(?:every|each)\s+(?:""" +
            """(other|\d+)\s+(days?|weeks?|months?|years?)""" + // 1,2
            """|(last\s+day)(?:\s+of\s+(?:the\s+)?month)?""" + // 3
            """|(day|week|month|year)""" + // 4
            """|(weekdays?|weekends?)""" + // 5
            """|((?:$weekdayAlt)(?:\s*(?:,|&|and|/)?\s*(?:$weekdayAlt))*)""" + // 6
            """|(\d{1,2})(?:st|nd|rd|th)(?:\s+of\s+(?:the\s+|every\s+)?month)?""" + // 7
            """|(morning|evening|night)""" + // 8
            """)\b""",
    )
    private val reAdverbRepeat = Regex("""\b(daily|weekly|monthly|yearly|annually)\b""")
    private val reRemindPrefix = Regex("""^\s*(?:remind\s+me\s+(?:to|about|that)\s+|remember\s+to\s+|don'?t\s+forget\s+to\s+)""")

    fun parse(text: String): ParsedReminder {
        val lower = text.lowercase()
        val consumed = BooleanArray(text.length)
        val spans = mutableListOf<IntRange>()
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()

        fun take(re: Regex): MatchResult? {
            val m = re.findAll(lower).firstOrNull { r -> r.range.none { consumed[it] } } ?: return null
            m.range.forEach { consumed[it] = true }
            spans += m.range
            return m
        }

        reRemindPrefix.find(lower)?.let { m -> m.range.forEach { consumed[it] = true } }

        // Priority
        var priority = Priority.NONE
        take(rePriority)?.let { m ->
            priority = when (m.groupValues[1]) {
                "!!!", "!1", "p1" -> Priority.HIGH
                "!!", "!2", "p2" -> Priority.MEDIUM
                "!", "!3", "p3" -> Priority.LOW
                else -> Priority.NONE
            }
        }

        // Recurrence
        var rule: RecurrenceRule? = null
        var recurrencePartOfDay: LocalTime? = null
        take(reEvery)?.let { m ->
            val g = m.groupValues
            rule = when {
                g[1].isNotEmpty() -> {
                    val n = if (g[1] == "other") 2 else g[1].toInt().coerceAtLeast(1)
                    RecurrenceRule(freqOf(g[2]), interval = n)
                }
                g[3].isNotEmpty() -> RecurrenceRule(Freq.MONTHLY, byMonthDay = listOf(-1))
                g[4].isNotEmpty() -> RecurrenceRule(freqOf(g[4]))
                g[5].startsWith("weekday") -> RecurrenceRule(Freq.WEEKLY, byDay = WORKDAYS.map { WeekdayNum(it) })
                g[5].startsWith("weekend") -> RecurrenceRule(Freq.WEEKLY, byDay = listOf(WeekdayNum(DayOfWeek.SATURDAY), WeekdayNum(DayOfWeek.SUNDAY)))
                g[6].isNotEmpty() -> {
                    val days = Regex(weekdayAlt).findAll(g[6]).map { DateVocabulary.WEEKDAYS.getValue(it.value) }
                        .distinct().sorted().map { WeekdayNum(it) }.toList()
                    RecurrenceRule(Freq.WEEKLY, byDay = days)
                }
                g[7].isNotEmpty() -> RecurrenceRule(Freq.MONTHLY, byMonthDay = listOf(g[7].toInt().coerceIn(1, 31)))
                g[8].isNotEmpty() -> {
                    recurrencePartOfDay = DateVocabulary.PARTS_OF_DAY[g[8]]
                    RecurrenceRule(Freq.DAILY)
                }
                else -> null
            }
        }
        if (rule == null) {
            take(reAdverbRepeat)?.let { m ->
                rule = RecurrenceRule(
                    when (m.groupValues[1]) {
                        "daily" -> Freq.DAILY
                        "weekly" -> Freq.WEEKLY
                        "monthly" -> Freq.MONTHLY
                        else -> Freq.YEARLY
                    },
                )
            }
        }

        // Relative offsets ("in 20 minutes") produce a complete date-time.
        var exact: LocalDateTime? = null
        take(reRelative)?.let { m ->
            val amountWord = m.groupValues[1]
            val unit = m.groupValues[2]
            val n = when {
                amountWord.startsWith("half") -> 0
                amountWord == "a" || amountWord == "an" || amountWord == "one" -> 1
                amountWord == "two" -> 2
                amountWord == "three" -> 3
                else -> amountWord.toInt()
            }
            exact = when {
                amountWord.startsWith("half") -> now.plusMinutes(30)
                unit.startsWith("min") -> now.plusMinutes(n.toLong())
                unit.startsWith("h") -> now.plusHours(n.toLong())
                unit.startsWith("day") -> today.plusDays(n.toLong()).atTime(DateVocabulary.DEFAULT_TIME)
                unit.startsWith("week") -> today.plusWeeks(n.toLong()).atTime(DateVocabulary.DEFAULT_TIME)
                else -> today.plusMonths(n.toLong()).atTime(DateVocabulary.DEFAULT_TIME)
            }.withSecond(0).withNano(0)
        }

        // Dates
        var date: LocalDate? = null
        var strictlyFuture = false
        var impliedTime: LocalTime? = null
        take(reDayAfterTomorrow)?.let { date = today.plusDays(2) }
        if (date == null) take(reRelDay)?.let { m ->
            when (val w = m.groupValues[1].replace(Regex("\\s+"), " ")) {
                "today" -> date = today
                "tonight" -> { date = today; impliedTime = DateVocabulary.PARTS_OF_DAY["tonight"] }
                "tomorrow", "tmrw", "tmr" -> date = today.plusDays(1)
                "next week" -> date = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                "next month" -> date = today.plusMonths(1).withDayOfMonth(1)
                else -> if (w.endsWith("weekend")) date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            }
        }
        if (date == null) take(reDayMonth)?.let { m ->
            date = buildDate(m.groupValues[1].toInt(), DateVocabulary.MONTHS.getValue(m.groupValues[2]), m.groupValues[3], today)
        }
        if (date == null) take(reMonthDay)?.let { m ->
            date = buildDate(m.groupValues[2].toInt(), DateVocabulary.MONTHS.getValue(m.groupValues[1]), m.groupValues[3], today)
        }
        if (date == null) take(reNumericDate)?.let { m ->
            // Day-first (dd/mm), as used in most of the world.
            date = buildDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], today)
        }
        if (date == null && rule == null) take(reOrdinalDay)?.let { m ->
            val d = m.groupValues[1].toInt()
            date = runCatching {
                val thisMonth = today.withDayOfMonth(d)
                if (thisMonth.isBefore(today)) today.plusMonths(1).withDayOfMonth(d) else thisMonth
            }.getOrNull()
        }
        if (date == null && rule == null) take(reWeekday)?.let { m ->
            val day = DateVocabulary.WEEKDAYS.getValue(m.groupValues[2])
            strictlyFuture = m.groupValues[1] == "next"
            date = if (strictlyFuture) today.with(TemporalAdjusters.next(day)) else today.with(TemporalAdjusters.nextOrSame(day))
        }

        // Times
        var time: LocalTime? = null
        take(reMeridiemTime)?.let { m ->
            var h = m.groupValues[1].toInt() % 12
            if (m.groupValues[3].startsWith("p")) h += 12
            time = LocalTime.of(h.coerceIn(0, 23), m.groupValues[2].ifEmpty { "0" }.toInt().coerceIn(0, 59))
        }
        if (time == null) take(re24hTime)?.let { m -> time = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        if (time == null) take(reBareAt)?.let { m ->
            val h = m.groupValues[1].toInt()
            // "at 5" -> 17:00, "at 9" -> 09:00, "at 12" -> noon.
            time = when (h) {
                in 1..6 -> LocalTime.of(h + 12, 0)
                in 7..12 -> LocalTime.of(h % 24, 0)
                in 13..23 -> LocalTime.of(h, 0)
                else -> LocalTime.MIDNIGHT
            }
        }
        if (time == null) take(rePartOfDay)?.let { m -> time = DateVocabulary.PARTS_OF_DAY[m.groupValues[1]] }
        if (time == null) time = impliedTime ?: recurrencePartOfDay

        // Combine
        val dueAt: LocalDateTime? = when {
            exact != null -> exact
            rule != null -> {
                val anchor = (date ?: today).atTime(time ?: DateVocabulary.DEFAULT_TIME)
                rule!!.firstOnOrAfter(anchor, maxOf(anchor, now)) ?: anchor
            }
            date != null -> date!!.atTime(time ?: DateVocabulary.DEFAULT_TIME)
            time != null -> today.atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) }
            else -> null
        }

        val title = buildTitle(text, consumed)
        return ParsedReminder(
            title = title,
            dueAt = dueAt,
            recurrence = rule,
            priority = priority,
            category = detectCategory(title),
            matchedSpans = spans.sortedBy { it.first },
        )
    }

    private fun buildTitle(original: String, consumed: BooleanArray): String {
        val sb = StringBuilder()
        original.forEachIndexed { i, c -> sb.append(if (consumed[i]) ' ' else c) }
        var t = sb.toString().replace(Regex("\\s+"), " ").trim()
        // Drop dangling connector words left behind by removed date phrases.
        val connector = Regex("""(?i)(?:\s+|^)(at|on|by|every|in|for|from|the|,)$""")
        while (true) {
            val stripped = connector.replace(t, "").trim()
            if (stripped == t) break
            t = stripped
        }
        t = t.trim(',', ' ', '-')
        return t.ifEmpty { original.trim() }
    }

    private fun detectCategory(title: String): Category {
        val lower = " " + title.lowercase() + " "
        for ((category, words) in DateVocabulary.CATEGORY_KEYWORDS) {
            if (words.any { w -> Regex("""(?<![a-z])${Regex.escape(w)}(?![a-z])""").containsMatchIn(lower) }) return category
        }
        return Category.TASK
    }

    private fun buildDate(day: Int, month: Int, yearText: String, today: LocalDate): LocalDate? = runCatching {
        if (yearText.isNotEmpty()) {
            val y = yearText.toInt().let { if (it < 100) 2000 + it else it }
            LocalDate.of(y, month, day)
        } else {
            val candidate = LocalDate.of(today.year, month, day)
            if (candidate.isBefore(today)) LocalDate.of(today.year + 1, month, day) else candidate
        }
    }.getOrNull()

    private fun freqOf(word: String): Freq = when {
        word.startsWith("day") -> Freq.DAILY
        word.startsWith("week") -> Freq.WEEKLY
        word.startsWith("month") -> Freq.MONTHLY
        else -> Freq.YEARLY
    }

    private companion object {
        val WORKDAYS = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
}
