package app.rem.core.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** A BYDAY entry: a weekday, optionally with an ordinal within the month (1..5 or -1..-5). */
data class WeekdayNum(val day: DayOfWeek, val ordinal: Int? = null)

/**
 * A practical subset of RFC 5545 RRULE: FREQ, INTERVAL, BYDAY, BYMONTHDAY, BYMONTH, COUNT, UNTIL.
 * Occurrences keep the time-of-day of the series anchor.
 */
data class RecurrenceRule(
    val freq: Freq,
    val interval: Int = 1,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val count: Int? = null,
    val until: LocalDateTime? = null,
) {
    init {
        require(interval >= 1) { "INTERVAL must be >= 1" }
    }

    fun toRRule(): String = buildList {
        add("FREQ=${freq.name}")
        if (interval != 1) add("INTERVAL=$interval")
        if (byDay.isNotEmpty()) add("BYDAY=" + byDay.joinToString(",") { (it.ordinal?.toString() ?: "") + DAY_CODES.getValue(it.day) })
        if (byMonthDay.isNotEmpty()) add("BYMONTHDAY=" + byMonthDay.joinToString(","))
        if (byMonth.isNotEmpty()) add("BYMONTH=" + byMonth.joinToString(","))
        count?.let { add("COUNT=$it") }
        until?.let { add("UNTIL=" + it.format(UNTIL_FORMAT)) }
    }.joinToString(";")

    /**
     * The next occurrence strictly after [after], for the series anchored at [start].
     * Returns null when the series has ended (COUNT reached, past UNTIL) or nothing matches.
     */
    fun next(start: LocalDateTime, after: LocalDateTime, occurrencesSoFar: Int = 0): LocalDateTime? {
        if (count != null && occurrencesSoFar >= count) return null
        val time = start.toLocalTime()
        var date = maxOf(start.toLocalDate(), after.toLocalDate())
        repeat(MAX_DAYS_SEARCHED) {
            if (matches(start.toLocalDate(), date)) {
                val candidate = date.atTime(time)
                if (candidate.isAfter(after) && !candidate.isBefore(start)) {
                    return if (until != null && candidate.isAfter(until)) null else candidate
                }
            }
            date = date.plusDays(1)
            if (until != null && date.atStartOfDay().isAfter(until)) return null
        }
        return null
    }

    /** First occurrence at or after [from] (inclusive), anchored at [start]. */
    fun firstOnOrAfter(start: LocalDateTime, from: LocalDateTime): LocalDateTime? =
        next(start, from.minusNanos(1))

    private fun matches(anchor: LocalDate, date: LocalDate): Boolean = when (freq) {
        Freq.DAILY ->
            ChronoUnit.DAYS.between(anchor, date) % interval == 0L && monthOk(date) && weekdayOk(date)

        Freq.WEEKLY -> {
            val anchorWeek = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val dateWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeks = ChronoUnit.WEEKS.between(anchorWeek, dateWeek)
            val days = if (byDay.isEmpty()) setOf(anchor.dayOfWeek) else byDay.map { it.day }.toSet()
            weeks % interval == 0L && date.dayOfWeek in days && monthOk(date)
        }

        Freq.MONTHLY -> {
            val months = ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), date.withDayOfMonth(1))
            months % interval == 0L && monthOk(date) && dayInMonthOk(anchor, date)
        }

        Freq.YEARLY -> {
            val years = (date.year - anchor.year).toLong()
            val monthMatches = if (byMonth.isEmpty()) date.monthValue == anchor.monthValue else date.monthValue in byMonth
            years % interval == 0L && monthMatches && dayInMonthOk(anchor, date)
        }
    }

    private fun monthOk(date: LocalDate) = byMonth.isEmpty() || date.monthValue in byMonth

    private fun weekdayOk(date: LocalDate) = byDay.isEmpty() || byDay.any { it.day == date.dayOfWeek }

    private fun dayInMonthOk(anchor: LocalDate, date: LocalDate): Boolean {
        if (byMonthDay.isEmpty() && byDay.isEmpty()) return date.dayOfMonth == anchor.dayOfMonth
        val length = date.lengthOfMonth()
        val monthDayOk = byMonthDay.isEmpty() || byMonthDay.any { d ->
            if (d > 0) date.dayOfMonth == d else date.dayOfMonth == length + d + 1
        }
        val byDayOk = byDay.isEmpty() || byDay.any { wd ->
            if (wd.day != date.dayOfWeek) return@any false
            val ord = wd.ordinal ?: return@any true
            if (ord > 0) (date.dayOfMonth - 1) / 7 + 1 == ord
            else (length - date.dayOfMonth) / 7 + 1 == -ord
        }
        return monthDayOk && byDayOk
    }

    companion object {
        private const val MAX_DAYS_SEARCHED = 366 * 12
        private val UNTIL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

        val DAY_CODES: Map<DayOfWeek, String> = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )
        private val CODE_DAYS = DAY_CODES.entries.associate { (k, v) -> v to k }

        fun parse(rrule: String): RecurrenceRule {
            val parts = rrule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.associate { part ->
                val eq = part.indexOf('=')
                require(eq > 0) { "Malformed RRULE part: $part" }
                part.substring(0, eq).uppercase() to part.substring(eq + 1)
            }
            val freq = parts["FREQ"]?.let { f -> Freq.entries.firstOrNull { it.name == f.uppercase() } }
                ?: throw IllegalArgumentException("Unsupported or missing FREQ in: $rrule")
            return RecurrenceRule(
                freq = freq,
                interval = parts["INTERVAL"]?.toIntOrNull() ?: 1,
                byDay = parts["BYDAY"]?.split(',')?.map { token ->
                    val code = token.takeLast(2).uppercase()
                    val day = CODE_DAYS[code] ?: throw IllegalArgumentException("Bad BYDAY: $token")
                    val ord = token.dropLast(2).takeIf { it.isNotEmpty() }?.toInt()
                    WeekdayNum(day, ord)
                } ?: emptyList(),
                byMonthDay = parts["BYMONTHDAY"]?.split(',')?.map { it.toInt() } ?: emptyList(),
                byMonth = parts["BYMONTH"]?.split(',')?.map { it.toInt() } ?: emptyList(),
                count = parts["COUNT"]?.toInt(),
                until = parts["UNTIL"]?.let { LocalDateTime.parse(it.removeSuffix("Z"), UNTIL_FORMAT) },
            )
        }

        fun parseOrNull(rrule: String?): RecurrenceRule? =
            rrule?.takeIf { it.isNotBlank() }?.let { runCatching { parse(it) }.getOrNull() }
    }
}
