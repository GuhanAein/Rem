package app.rem.core.insights

import app.rem.core.model.Reminder
import app.rem.core.model.Status
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A record of what happened to a reminder occurrence. */
data class LogEvent(val reminderId: Long, val at: LocalDateTime, val type: Type, val minutes: Int = 0) {
    enum class Type { DONE, SNOOZED, SKIPPED, MISSED }
}

/** "You always push the 8:00 gym reminder to 8:30 — move it?" */
object SnoozeAdvisor {
    /** Returns the suggested new time of day, or null if there's no consistent pattern. */
    fun suggest(reminder: Reminder, events: List<LogEvent>, minRepeats: Int = 3): LocalTime? {
        val due = reminder.dueAt ?: return null
        if (reminder.recurrence.isNullOrBlank()) return null
        val snoozes = events.filter { it.reminderId == reminder.id && it.type == LogEvent.Type.SNOOZED }
            .sortedByDescending { it.at }.take(5)
        if (snoozes.size < minRepeats) return null
        val minutes = snoozes.map { it.minutes }.sorted()
        val median = minutes[minutes.size / 2]
        if (median < 10) return null
        val consistent = minutes.count { abs(it - median) <= 15 } >= minRepeats
        if (!consistent) return null
        val rounded = ((median + 7) / 15) * 15
        return due.toLocalTime().plusMinutes(rounded.toLong())
    }
}

/** Warns when too many reminders pile up around the same time, and offers to spread them. */
object ConflictDetector {
    fun crowdAt(all: List<Reminder>, at: LocalDateTime, windowMinutes: Long = 15): List<Reminder> =
        all.filter { it.status == Status.ACTIVE && it.dueAt != null && abs(ChronoUnit.MINUTES.between(it.dueAt, at)) <= windowMinutes }

    fun isCrowded(all: List<Reminder>, at: LocalDateTime, threshold: Int = 4): Boolean = crowdAt(all, at).size >= threshold

    /** Staggers the crowd by [stepMinutes], keeping the earliest in place. */
    fun spread(crowd: List<Reminder>, stepMinutes: Long = 15): List<Reminder> =
        crowd.sortedWith(compareBy({ it.dueAt }, { it.id })).mapIndexed { i, r -> r.copy(dueAt = r.dueAt!!.plusMinutes(i * stepMinutes)) }
}

/** Streaks with a forgiving "freeze": one missed day per week doesn't break it. */
object Streaks {
    data class Result(val current: Int, val best: Int, val freezesUsed: Int)

    fun compute(doneDays: Set<LocalDate>, today: LocalDate, freezePerWeek: Int = 1): Result {
        if (doneDays.isEmpty()) return Result(0, 0, 0)
        // Current streak: walk back from today (today may still be pending, so start at yesterday if needed).
        var day = if (today in doneDays) today else today.minusDays(1)
        var current = 0
        var freezes = 0
        val usedInWeek = mutableMapOf<Long, Int>()
        while (true) {
            if (day in doneDays) {
                current++
            } else {
                val week = day.toEpochDay() / 7
                val used = usedInWeek[week] ?: 0
                if (used < freezePerWeek && current > 0 && (day.minusDays(1) in doneDays)) {
                    usedInWeek[week] = used + 1
                    freezes++
                } else break
            }
            day = day.minusDays(1)
        }
        // Best streak without freezes, simple scan.
        val sorted = doneDays.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
        }
        return Result(current, maxOf(best, current), freezes)
    }
}

/** Parses money like "₹2,340", "$9.99", "Rs 499". */
object Money {
    fun parse(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val digits = Regex("""\d[\d,]*(?:\.\d+)?""").find(text)?.value ?: return null
        return digits.replace(",", "").toDoubleOrNull()
    }

    fun symbol(text: String?): String = text?.trim()?.takeWhile { !it.isDigit() }?.trim()?.ifBlank { null } ?: "₹"

    fun format(amount: Double, symbol: String = "₹"): String {
        val whole = amount.toLong()
        val withCommas = "%,d".format(whole)
        val frac = amount - whole
        return if (frac > 0.004) "$symbol$withCommas.${"%02d".format((frac * 100).toInt())}" else "$symbol$withCommas"
    }
}

/** Subscriptions: normalised monthly cost and renewal/trial warnings. */
object Subscriptions {
    enum class Cycle(val monthsFactor: Double, val label: String) {
        WEEKLY(52.0 / 12.0, "week"), MONTHLY(1.0, "month"), QUARTERLY(1.0 / 3.0, "quarter"), YEARLY(1.0 / 12.0, "year")
    }

    fun cycleOf(rrule: String?): Cycle = when {
        rrule == null -> Cycle.MONTHLY
        "FREQ=WEEKLY" in rrule -> Cycle.WEEKLY
        "FREQ=YEARLY" in rrule -> Cycle.YEARLY
        "FREQ=MONTHLY" in rrule && "INTERVAL=3" in rrule -> Cycle.QUARTERLY
        else -> Cycle.MONTHLY
    }

    fun monthlyCost(r: Reminder): Double = (Money.parse(r.amount) ?: 0.0) * cycleOf(r.recurrence).monthsFactor

    fun monthlyTotal(subs: List<Reminder>): Double = subs.filter { it.status == Status.ACTIVE }.sumOf { monthlyCost(it) }

    /** True when a free trial ends within [days] days: time to cancel if you don't want it. */
    fun trialEndingSoon(r: Reminder, today: LocalDate, days: Long = 3): Boolean {
        val end = r.extras[TRIAL_END]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        return !end.isBefore(today) && ChronoUnit.DAYS.between(today, end) <= days
    }

    const val TRIAL_END = "trialEnd"
    const val CANCEL_MODE = "cancelBeforeRenewal"
}

/** Birthdays and anniversaries: "turns 30", "10th anniversary". */
object Anniversaries {
    fun ordinal(n: Int): String = n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    /** [origin] is the original date (birth/wedding) with a real year; [on] is the occurrence date. */
    fun describe(origin: LocalDate, on: LocalDate, isBirthday: Boolean): String? {
        val years = on.year - origin.year
        if (years <= 0) return null
        return if (isBirthday) "turns $years" else "${ordinal(years)} anniversary"
    }

    fun isMilestone(years: Int): Boolean = years in setOf(1, 5, 10, 18, 21, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100)

    const val ORIGIN = "originDate"
}

/** Relationship nudges: "You haven't called Grandma in 2 weeks." */
data class Person(
    val id: Long = 0,
    val name: String,
    /** How often you'd like to be in touch, in days; null = no nudges. */
    val cadenceDays: Int? = null,
    val lastContact: LocalDate? = null,
    val birthday: LocalDate? = null,
    val anniversary: LocalDate? = null,
    val giftIdeas: List<String> = emptyList(),
    val phone: String? = null,
)

object Nudges {
    fun isDue(p: Person, today: LocalDate): Boolean {
        val cadence = p.cadenceDays ?: return false
        val last = p.lastContact ?: return true
        return ChronoUnit.DAYS.between(last, today) >= cadence
    }

    fun message(p: Person, today: LocalDate): String {
        val last = p.lastContact ?: return "Say hello to ${p.name}"
        val days = ChronoUnit.DAYS.between(last, today)
        val gap = when {
            days >= 60 -> "${days / 30} months"
            days >= 14 -> "${days / 7} weeks"
            else -> "$days days"
        }
        return "You haven't been in touch with ${p.name} in $gap"
    }

    /** Gift ideas surface this many days before a birthday/anniversary. */
    fun giftsDue(p: Person, today: LocalDate, leadDays: Long = 14): Boolean {
        if (p.giftIdeas.isEmpty()) return false
        return listOfNotNull(p.birthday, p.anniversary).any { d ->
            var next = d.withYear(today.year)
            if (next.isBefore(today)) next = next.plusYears(1)
            ChronoUnit.DAYS.between(today, next) in 0..leadDays
        }
    }
}

/** Family chores that rotate between people automatically. */
object Rotation {
    fun assignee(members: List<String>, start: LocalDate, on: LocalDate, periodDays: Int = 7): String? {
        if (members.isEmpty()) return null
        val periods = Math.floorDiv(ChronoUnit.DAYS.between(start, on), periodDays.toLong())
        return members[Math.floorMod(periods, members.size.toLong()).toInt()]
    }
}

/** Car service by distance: "every 5,000 km". */
object CarLog {
    const val INTERVAL_KM = "intervalKm"
    const val LAST_SERVICE_KM = "lastServiceKm"
    const val ODOMETER = "odometerKm"
    const val ODOMETER_DATE = "odometerDate"
    const val FIRST_ODOMETER = "firstOdometerKm"
    const val FIRST_ODOMETER_DATE = "firstOdometerDate"

    fun nextServiceKm(r: Reminder): Int? {
        val interval = r.extras[INTERVAL_KM]?.toIntOrNull() ?: return null
        val last = r.extras[LAST_SERVICE_KM]?.toIntOrNull() ?: 0
        return last + interval
    }

    fun kmLeft(r: Reminder): Int? {
        val next = nextServiceKm(r) ?: return null
        val odo = r.extras[ODOMETER]?.toIntOrNull() ?: return null
        return next - odo
    }

    /** Estimates the service date from your average daily distance between two odometer readings. */
    fun estimateDate(r: Reminder): LocalDate? {
        val left = kmLeft(r) ?: return null
        val a = r.extras[FIRST_ODOMETER]?.toIntOrNull() ?: return null
        val ad = r.extras[FIRST_ODOMETER_DATE]?.let { LocalDate.parse(it) } ?: return null
        val b = r.extras[ODOMETER]?.toIntOrNull() ?: return null
        val bd = r.extras[ODOMETER_DATE]?.let { LocalDate.parse(it) } ?: return null
        val days = ChronoUnit.DAYS.between(ad, bd)
        if (days <= 0 || b <= a) return null
        val perDay = (b - a).toDouble() / days
        return bd.plusDays(maxOf(0L, (left / perDay).toLong()))
    }

    /** Records a reading and keeps the very first one for the average. */
    fun withOdometer(r: Reminder, km: Int, on: LocalDate): Reminder {
        val ex = r.extras.toMutableMap()
        if (ex[FIRST_ODOMETER] == null) {
            ex[FIRST_ODOMETER] = (ex[ODOMETER] ?: km.toString())
            ex[FIRST_ODOMETER_DATE] = (ex[ODOMETER_DATE] ?: on.toString())
        }
        ex[ODOMETER] = km.toString()
        ex[ODOMETER_DATE] = on.toString()
        return r.copy(extras = ex)
    }
}

/** Reliability report: alarms that fired late. */
data class FiredRecord(val reminderId: Long, val scheduledAt: Instant, val firedAt: Instant)

object Reliability {
    data class Report(val total: Int, val late: Int, val worstMinutes: Long) {
        val healthy: Boolean get() = late == 0
    }

    fun report(records: List<FiredRecord>, lateAfter: Duration = Duration.ofMinutes(2)): Report {
        val delays = records.map { Duration.between(it.scheduledAt, it.firedAt) }
        val late = delays.filter { it > lateAfter }
        return Report(records.size, late.size, late.maxOfOrNull { it.toMinutes() } ?: 0)
    }
}

/** "Year in Rem": an annual recap. */
object YearInReview {
    data class Recap(
        val year: Int,
        val completed: Int,
        val busiestMonth: java.time.Month?,
        val busiestWeekday: java.time.DayOfWeek?,
        val favouriteHour: Int?,
        val longestStreak: Int,
    )

    fun recap(year: Int, events: List<LogEvent>): Recap {
        val done = events.filter { it.type == LogEvent.Type.DONE && it.at.year == year }
        val days = done.map { it.at.toLocalDate() }.toSet()
        return Recap(
            year = year,
            completed = done.size,
            busiestMonth = done.groupingBy { it.at.month }.eachCount().maxByOrNull { it.value }?.key,
            busiestWeekday = done.groupingBy { it.at.dayOfWeek }.eachCount().maxByOrNull { it.value }?.key,
            favouriteHour = done.groupingBy { it.at.hour }.eachCount().maxByOrNull { it.value }?.key,
            longestStreak = if (days.isEmpty()) 0 else Streaks.compute(days, days.max(), freezePerWeek = 0).best,
        )
    }
}
