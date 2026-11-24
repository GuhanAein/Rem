package app.rem.core.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.LocalTime

enum class AlertStyle { NOTIFICATION, ALARM }

enum class Priority { NONE, LOW, MEDIUM, HIGH }

enum class Status { ACTIVE, DONE }

enum class Kind(val label: String) {
    STANDARD("Reminder"),
    SUBSCRIPTION("Subscription"),
    WARRANTY("Warranty"),
    CAR_SERVICE("Car service"),
    PLANT("Plant"),
    LEND("Lent"),
    BORROW("Borrowed"),
    RETURN("Return"),
    HABIT("Habit"),
    CHORE("Chore"),
    DOCUMENT("Document"),
    /** Internal: the reminder attached to a shopping list (at the shop, or on a day). */
    SHOPPING_LIST("Shopping list"),
}

/** Personal reminders and work tasks live apart; work respects work hours. */
enum class Space { PERSONAL, WORK }

/** Allowed days and hours for a reminder to fire. */
data class TimeWindow(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime) {
    init { require(days.isNotEmpty()) { "A window needs at least one day" } }

    fun contains(t: LocalDateTime): Boolean = t.dayOfWeek in days && !t.toLocalTime().isBefore(start) && !t.toLocalTime().isAfter(end)

    /** The earliest moment at or after [t] inside the window. */
    fun adjust(t: LocalDateTime): LocalDateTime {
        if (contains(t)) return t
        var d = t.toLocalDate()
        if (t.dayOfWeek in days && t.toLocalTime().isBefore(start)) return d.atTime(start)
        repeat(8) {
            d = d.plusDays(1)
            if (d.dayOfWeek in days) return d.atTime(start)
        }
        return t
    }

    fun encode(): String = days.sorted().joinToString(",") { it.value.toString() } + "|" + start + "|" + end

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        fun businessHours() = TimeWindow(WEEKDAYS, LocalTime.of(10, 0), LocalTime.of(16, 0))
        fun decode(s: String?): TimeWindow? = s?.split('|')?.takeIf { it.size == 3 }?.let { (d, a, b) ->
            runCatching { TimeWindow(d.split(',').map { DayOfWeek.of(it.toInt()) }.toSet(), LocalTime.parse(a), LocalTime.parse(b)) }.getOrNull()
        }
    }
}

/** "N times a day at random moments between start and end." */
data class RandomTimes(val perDay: Int, val start: LocalTime, val end: LocalTime) {
    init { require(perDay in 1..24 && end.isAfter(start)) }
    fun encode(): String = "$perDay|$start|$end"

    companion object {
        fun decode(s: String?): RandomTimes? = s?.split('|')?.takeIf { it.size == 3 }?.let { (n, a, b) ->
            runCatching { RandomTimes(n.toInt(), LocalTime.parse(a), LocalTime.parse(b)) }.getOrNull()
        }
    }
}

enum class Trigger(val label: String, val timeBased: Boolean = false) {
    CHARGING_TONIGHT("When I plug in tonight"),
    WAKE_UP("When I wake up", timeBased = true),
    BEDTIME("Before I sleep", timeBased = true),
    HEADPHONES("When headphones connect"),
    LEAVE_HOME("When I leave home"),
    ARRIVE_PLACE("When I arrive at a place"),
    LEAVE_PLACE("When I leave a place"),
}

/**
 * "Remind me of everything": each category carries smart defaults so a medicine
 * reminder behaves like an alarm and a bill warns you days ahead, with no setup.
 */
enum class Category(
    val label: String,
    val defaultStyle: AlertStyle,
    val defaultAlertOffsets: List<Int>,
    val defaultNagMinutes: Int?,
    val defaultRecurrence: String?,
) {
    TASK("Task", AlertStyle.NOTIFICATION, listOf(0), null, null),
    WORK("Work", AlertStyle.NOTIFICATION, listOf(15, 0), null, null),
    MEDICINE("Medicine", AlertStyle.ALARM, listOf(0), 5, null),
    HEALTH("Health", AlertStyle.NOTIFICATION, listOf(0), null, null),
    HABIT("Habit", AlertStyle.NOTIFICATION, listOf(0), null, null),
    BILL("Bill", AlertStyle.NOTIFICATION, listOf(3 * 1440, 1440, 0), null, null),
    BIRTHDAY("Birthday", AlertStyle.NOTIFICATION, listOf(1440, 0), null, "FREQ=YEARLY"),
    EVENT("Event", AlertStyle.NOTIFICATION, listOf(1440, 60), null, null),
    DOCUMENT("Document", AlertStyle.NOTIFICATION, listOf(30 * 1440, 7 * 1440, 1440), null, null),
    VEHICLE("Vehicle", AlertStyle.NOTIFICATION, listOf(7 * 1440, 1440), null, null),
    HOME("Home", AlertStyle.NOTIFICATION, listOf(0), null, null),
    PET("Pet", AlertStyle.NOTIFICATION, listOf(0), null, null),
    SHOPPING("Shopping", AlertStyle.NOTIFICATION, listOf(0), null, null),
}

data class Reminder(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val category: Category = Category.TASK,
    val priority: Priority = Priority.NONE,
    /** Wall-clock due time in the device's zone; null = no date ("someday"). */
    val dueAt: LocalDateTime? = null,
    /** RFC 5545 RRULE subset, e.g. "FREQ=WEEKLY;BYDAY=MO,WE,FR". */
    val recurrence: String? = null,
    val alertStyle: AlertStyle = AlertStyle.NOTIFICATION,
    /** Minutes before [dueAt] to alert; 0 = at the due time. */
    val alertOffsets: List<Int> = listOf(0),
    /** Repeat the alarm every N minutes until handled; null = off. */
    val nagMinutes: Int? = null,
    /** Optional money amount for bills/subscriptions, shown on the card. */
    val amount: String? = null,
    val status: Status = Status.ACTIVE,
    val snoozedUntil: Instant? = null,
    val occurrenceCount: Int = 0,
    val createdAt: Instant = Instant.EPOCH,
    val completedAt: Instant? = null,
    /** What kind of thing this is; drives special screens (subscriptions, warranties, lending...). */
    val kind: Kind = Kind.STANDARD,
    /** Only fire inside this window ("call the bank on weekdays 10-4"). */
    val window: TimeWindow? = null,
    /** Fire N times a day at random moments inside a window ("drink water"). */
    val randomTimes: RandomTimes? = null,
    /** Fire on an event instead of (or as well as) a clock time. */
    val trigger: Trigger? = null,
    /** When completed, ask for the follow-up ("Book doctor" -> "Doctor visit"). */
    val followUp: String? = null,
    /** A 2-minute first step for big tasks. */
    val firstStep: String? = null,
    /** Hidden behind the app lock. */
    val isPrivate: Boolean = false,
    /** Linked person (birthdays, lending, relationship nudges). */
    val personId: Long? = null,
    /** Kind-specific data: photo URI, odometer, trial end, item lent, ... */
    val extras: Map<String, String> = emptyMap(),
    val space: Space = Space.PERSONAL,
    val projectId: Long? = null,
    /** Work status name (TODO, DOING, WAITING, DONE). */
    val workStatus: String? = null,
    /** "Waiting on Ravi": who you're waiting for. */
    val waitingOn: String? = null,
) {
    val isRecurring: Boolean get() = !recurrence.isNullOrBlank()

    fun isOverdue(now: Instant, zone: ZoneId): Boolean =
        status == Status.ACTIVE && dueAt != null && dueAt.atZone(zone).toInstant().isBefore(now)
}
