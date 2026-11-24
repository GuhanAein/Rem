package app.rem.core.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class AlertStyle { NOTIFICATION, ALARM }

enum class Priority { NONE, LOW, MEDIUM, HIGH }

enum class Status { ACTIVE, DONE }

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
    MEDICINE("Medicine", AlertStyle.ALARM, listOf(0), 5, "FREQ=DAILY"),
    HEALTH("Health", AlertStyle.NOTIFICATION, listOf(0), null, "FREQ=DAILY"),
    HABIT("Habit", AlertStyle.NOTIFICATION, listOf(0), null, "FREQ=DAILY"),
    BILL("Bill", AlertStyle.NOTIFICATION, listOf(3 * 1440, 1440, 0), null, "FREQ=MONTHLY"),
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
) {
    val isRecurring: Boolean get() = !recurrence.isNullOrBlank()

    fun isOverdue(now: Instant, zone: ZoneId): Boolean =
        status == Status.ACTIVE && dueAt != null && dueAt.atZone(zone).toInstant().isBefore(now)
}
