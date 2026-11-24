package app.rem.core.life

import app.rem.core.insights.Money
import app.rem.core.insights.Subscriptions
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Kind
import app.rem.core.model.Reminder
import app.rem.core.model.Status
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

// ===================================================================== Bills SMS

/** A bill found in a text message: who it's from, how much, and when it's due. */
data class DetectedBill(val biller: String, val amount: Double?, val dueOn: LocalDate?, val source: String) {
    /** Stable key so the same SMS is not offered twice. */
    val key: String get() = "${biller.lowercase()}|${amount ?: ""}|${dueOn ?: ""}"
}

/**
 * Finds bills in SMS text with plain pattern rules: no AI, nothing leaves the phone.
 * It looks for a due-date phrase ("due on 28-11-2026", "pay by 5 Dec") plus bill words,
 * and ignores OTPs, credits and plain transaction alerts.
 */
object BillSms {
    private val billWords = Regex("""\b(bill|due|statement|payable|pay by|recharge|premium|emi|outstanding|amount due|min(imum)? amt|renewal|expires?|expiring)\b""", RegexOption.IGNORE_CASE)
    private val noise = Regex("""\b(otp|one time password|verification code|credited|has been paid|payment (of .* )?(is )?(received|successful)|thank you for (your )?payment|txn successful|cashback|refund)\b""", RegexOption.IGNORE_CASE)
    private val amountRx = Regex("""(?:rs\.?|inr|₹|\$|usd)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    private val months = Month.entries.associateBy { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase() } +
        Month.entries.associateBy { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase() } + mapOf("sept" to Month.SEPTEMBER)

    // 28-11-2026, 28/11/26, 28.11.2026
    private val numericDate = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b""")
    // 28 Nov 2026, 28-Nov-26, 5th December, 28 NOV
    private val dayMonth = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s-]+([A-Za-z]{3,9})\.?(?:[\s,-]+(\d{2,4}))?\b""")
    // Nov 28, 2026 / December 5
    private val monthDay = Regex("""\b([A-Za-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""")
    private val dueCue = Regex("""(due( date)?|pay(able)? by|before|by|on or before|last date|expires? on|expiring on|valid till|renew(s|al)? on)\s*:?\s*""", RegexOption.IGNORE_CASE)

    fun parse(sender: String, body: String, received: LocalDate): DetectedBill? {
        if (noise.containsMatchIn(body) || !billWords.containsMatchIn(body)) return null
        val due = findDate(body, received) ?: return null
        if (due.isBefore(received.minusDays(1))) return null
        val amount = amountRx.findAll(body).map { it.groupValues[1].replace(",", "").toDoubleOrNull() }.filterNotNull().firstOrNull()
        return DetectedBill(billerName(sender, body), amount, due, body)
    }

    /** Prefer a date right after a due cue; otherwise the first future-looking date. */
    fun findDate(body: String, received: LocalDate): LocalDate? {
        val cued = dueCue.findAll(body).mapNotNull { m -> dateAt(body.substring(m.range.last + 1).take(24), received) }.firstOrNull()
        return cued ?: dateAt(body, received, anywhere = true)
    }

    private fun dateAt(text: String, received: LocalDate, anywhere: Boolean = false): LocalDate? {
        val candidates = buildList {
            numericDate.find(text)?.let { m -> if (anywhere || m.range.first <= 2) add(m.range.first to numeric(m, received)) }
            dayMonth.find(text)?.let { m -> if (anywhere || m.range.first <= 2) add(m.range.first to named(m.groupValues[1], m.groupValues[2], m.groupValues[3], received)) }
            monthDay.find(text)?.let { m -> if (anywhere || m.range.first <= 2) add(m.range.first to named(m.groupValues[2], m.groupValues[1], m.groupValues[3], received)) }
        }
        return candidates.filter { it.second != null }.minByOrNull { it.first }?.second
    }

    private fun numeric(m: MatchResult, received: LocalDate): LocalDate? {
        val (d, mo, y) = m.destructured
        val year = y.toInt().let { if (it < 100) 2000 + it else it }
        return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
    }

    private fun named(day: String, month: String, year: String, received: LocalDate): LocalDate? {
        val mo = months[month.lowercase()] ?: return null
        val d = day.toIntOrNull() ?: return null
        val y = year.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
        val date = runCatching { LocalDate.of(y ?: received.year, mo, d) }.getOrNull() ?: return null
        return if (y == null && date.isBefore(received.minusDays(1))) date.plusYears(1) else date
    }

    private val known = listOf(
        "airtel", "jio", "vodafone", "vi", "bsnl", "act fibernet", "hathway", "tata play", "tata sky", "dish tv",
        "hdfc", "icici", "sbi", "axis", "kotak", "amex", "citi", "idfc", "yes bank", "indusind", "au bank", "rbl",
        "lic", "bescom", "tneb", "tangedco", "msedcl", "adani", "bses", "tata power", "mahanagar gas", "indane", "bharat gas", "hp gas",
        "netflix", "spotify", "prime", "hotstar", "youtube", "apple", "google",
    )

    fun billerName(sender: String, body: String): String {
        val text = body.lowercase()
        known.firstOrNull { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(text) }?.let { return pretty(it) }
        // Sender IDs look like "AD-HDFCBK" or "VM-AIRTEL"; keep the meaningful part.
        val id = sender.substringAfter('-').filter { it.isLetter() }
        return if (id.length >= 3 && !sender.any { it.isDigit() && sender.count { c -> c.isDigit() } > 6 }) pretty(id.lowercase().removeSuffix("bk").removeSuffix("in")) else "Bill"
    }

    private fun pretty(s: String) = when (s) {
        "sbi", "hdfc", "icici", "lic", "bsnl", "rbl", "idfc", "bses", "tneb", "act fibernet" -> s.uppercase().replace("ACT FIBERNET", "ACT Fibernet")
        "vi" -> "Vi"
        else -> s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    /** The reminder Rem creates for a detected bill: three days, one day, and on the day. */
    fun toReminder(b: DetectedBill, now: LocalDateTime): Reminder = Reminder(
        title = "Pay ${b.biller} bill",
        category = Category.BILL,
        dueAt = (b.dueOn ?: now.toLocalDate().plusDays(3)).atTime(10, 0),
        amount = b.amount?.let { Money.format(it) },
        alertOffsets = Category.BILL.defaultAlertOffsets,
        notes = b.source.take(300),
        createdAt = now.atZone(java.time.ZoneId.systemDefault()).toInstant(),
    )
}

// ===================================================================== Bills overview

object Bills {
    fun isBill(r: Reminder) = r.status == Status.ACTIVE && (r.category == Category.BILL || r.kind == Kind.SUBSCRIPTION)

    /** Bills (not subscriptions) due in the calendar month of [month]. */
    fun dueInMonth(all: List<Reminder>, month: LocalDate): List<Reminder> = all.filter {
        isBill(it) && it.kind != Kind.SUBSCRIPTION && it.dueAt?.let { d -> d.year == month.year && d.month == month.month } == true
    }.sortedBy { it.dueAt }

    /** Expected spend this month: bills due this month plus subscriptions at their monthly rate. */
    fun monthTotal(all: List<Reminder>, month: LocalDate): Double =
        dueInMonth(all, month).sumOf { Money.parse(it.amount) ?: 0.0 } + Subscriptions.monthlyTotal(all.filter { it.kind == Kind.SUBSCRIPTION })

    /** Subscriptions renewing in the next [days] days: the "cancel before it renews" warning. */
    fun renewingSoon(all: List<Reminder>, today: LocalDate, days: Long = 3): List<Reminder> = all.filter {
        it.status == Status.ACTIVE && it.kind == Kind.SUBSCRIPTION &&
            it.dueAt?.toLocalDate()?.let { d -> !d.isBefore(today) && !d.isAfter(today.plusDays(days)) } == true
    }
}

// ===================================================================== Documents

object Documents {
    const val PHOTO_BACK = "photoBack"
    const val NUMBER = "docNumber"

    val TYPES = listOf("Passport", "Driving licence", "Vehicle insurance", "Health insurance", "PUC certificate", "Vehicle RC", "Visa", "ID card", "Other")

    /** Renewal reminders start earlier for documents that take longer to renew. */
    fun leadDays(type: String): List<Int> = when (type) {
        "Passport", "Visa" -> listOf(180, 90, 30, 7)
        "Driving licence", "Vehicle RC" -> listOf(60, 30, 7)
        "Vehicle insurance", "Health insurance" -> listOf(30, 7, 1)
        "PUC certificate" -> listOf(7, 1)
        else -> listOf(30, 7, 1)
    }

    enum class State { VALID, EXPIRING, EXPIRED }

    fun state(expiry: LocalDate?, today: LocalDate, soonDays: Long = 30): State = when {
        expiry == null -> State.VALID
        expiry.isBefore(today) -> State.EXPIRED
        !expiry.isAfter(today.plusDays(soonDays)) -> State.EXPIRING
        else -> State.VALID
    }

    fun reminder(type: String, holder: String, expiry: LocalDate, now: LocalDateTime): Reminder = Reminder(
        title = listOf(type, holder.takeIf { it.isNotBlank() }?.let { "($it)" }).filterNotNull().joinToString(" ") + " expires",
        category = Category.DOCUMENT,
        kind = Kind.DOCUMENT,
        dueAt = expiry.atTime(10, 0),
        alertOffsets = leadDays(type).map { it * 1440 } + 0,
        extras = mapOf("docType" to type, "holder" to holder),
        createdAt = now.atZone(java.time.ZoneId.systemDefault()).toInstant(),
    )
}

// ===================================================================== Water

/**
 * Spreads a daily water goal evenly across your waking hours, starting 30 minutes after you
 * wake and finishing an hour before bed, so it adapts when your sleep times change.
 */
object WaterPlan {
    fun times(wake: LocalTime, bed: LocalTime, glasses: Int): List<LocalTime> {
        require(glasses in 1..20)
        val start = wake.plusMinutes(30).toSecondOfDay()
        var end = bed.minusMinutes(60).toSecondOfDay()
        if (end <= start) end += 24 * 3600 // bedtime after midnight
        if (glasses == 1) return listOf(LocalTime.ofSecondOfDay(start.toLong()))
        val step = (end - start) / (glasses - 1)
        return (0 until glasses).map { i ->
            // Round to the nearest five minutes so times read naturally (9:35, not 9:33).
            val s = ((start + i * step + 150) / 300 * 300) % (24 * 3600)
            LocalTime.ofSecondOfDay(s.toLong())
        }
    }

    /** The next reminder time after [now], or null when today's plan is done or the goal is met. */
    fun next(now: LocalTime, wake: LocalTime, bed: LocalTime, glasses: Int, drunk: Int): LocalTime? {
        if (drunk >= glasses) return null
        return times(wake, bed, glasses).firstOrNull { it.isAfter(now) }
    }

    /** How many glasses you should have had by now. */
    fun expectedBy(now: LocalTime, wake: LocalTime, bed: LocalTime, glasses: Int): Int =
        times(wake, bed, glasses).count { !it.isAfter(now) }
}

// ===================================================================== Bedtime summary

object Bedtime {
    data class Summary(val missed: List<Reminder>, val doneToday: Int, val tomorrow: List<Reminder>) {
        val isEmpty get() = missed.isEmpty() && tomorrow.isEmpty() && doneToday == 0
        val title: String get() = when {
            missed.isEmpty() && doneToday > 0 -> "All done today"
            missed.isEmpty() -> "Nothing left today"
            else -> "${missed.size} left from today"
        }
        val body: String get() = buildList {
            if (missed.isNotEmpty()) add(missed.take(3).joinToString(", ") { it.title } + if (missed.size > 3) " +${missed.size - 3}" else "")
            if (doneToday > 0) add("$doneToday done")
            if (tomorrow.isNotEmpty()) add("${tomorrow.size} tomorrow")
        }.joinToString(" · ")
    }

    /** Personal, non-repeating-alarm reminders due today or earlier that are still open. */
    fun summary(active: List<Reminder>, completedToday: Int, now: LocalDateTime): Summary {
        val today = now.toLocalDate()
        val missed = active.filter {
            it.status == Status.ACTIVE && !it.isPrivate && it.kind != Kind.SHOPPING_LIST &&
                it.dueAt?.let { d -> !d.toLocalDate().isAfter(today) && d.isBefore(now) } == true
        }.sortedBy { it.dueAt }
        val tomorrow = active.filter { it.status == Status.ACTIVE && it.dueAt?.toLocalDate() == today.plusDays(1) }
        return Summary(missed, completedToday, tomorrow)
    }

    /** Moving to tomorrow keeps each reminder's time of day; anything without one goes to 9am. */
    fun moveToTomorrow(r: Reminder, now: LocalDateTime): Reminder {
        val time = r.dueAt?.toLocalTime() ?: LocalTime.of(9, 0)
        return r.copy(dueAt = now.toLocalDate().plusDays(1).atTime(time), snoozedUntil = null)
    }
}

// ===================================================================== Templates

data class TemplateItem(val title: String, val category: Category = Category.TASK, val dayOffset: Long? = 0, val time: LocalTime = LocalTime.of(9, 0), val rrule: String? = null)

data class Template(val id: String, val name: String, val blurb: String, val items: List<TemplateItem>, val asList: Boolean = false)

object Templates {
    val ALL = listOf(
        Template(
            "packing", "Travel packing", "A checklist in Shopping you tick off as you pack.",
            listOf("Phone charger", "Power bank", "ID and tickets", "Toothbrush and paste", "Medicines", "Clothes for each day", "Sleepwear", "Towel", "Earphones", "Wallet and cards", "House keys", "Water bottle").map { TemplateItem(it, dayOffset = null) },
            asList = true,
        ),
        Template(
            "trip", "Before a trip", "The things to sort out in the days before you leave.",
            listOf(
                TemplateItem("Check passport and visa dates", Category.DOCUMENT, 0),
                TemplateItem("Web check-in", Category.EVENT, 1, LocalTime.of(10, 0)),
                TemplateItem("Pay bills due while away", Category.BILL, 1),
                TemplateItem("Ask someone to water the plants", Category.HOME, 2),
                TemplateItem("Switch off geyser and gas", Category.HOME, 3, LocalTime.of(7, 0)),
            ),
        ),
        Template(
            "bills", "Monthly bills", "Repeating reminders for the usual monthly payments.",
            listOf(
                TemplateItem("Pay rent", Category.BILL, null, LocalTime.of(10, 0), "FREQ=MONTHLY;BYMONTHDAY=1"),
                TemplateItem("Pay electricity bill", Category.BILL, null, LocalTime.of(10, 0), "FREQ=MONTHLY;BYMONTHDAY=10"),
                TemplateItem("Pay internet bill", Category.BILL, null, LocalTime.of(10, 0), "FREQ=MONTHLY;BYMONTHDAY=5"),
                TemplateItem("Pay phone bill", Category.BILL, null, LocalTime.of(10, 0), "FREQ=MONTHLY;BYMONTHDAY=15"),
                TemplateItem("Pay credit card bill", Category.BILL, null, LocalTime.of(10, 0), "FREQ=MONTHLY;BYMONTHDAY=20"),
            ),
        ),
        Template(
            "month", "New month checklist", "A fresh-start routine on the first of every month.",
            listOf(
                TemplateItem("Review last month's spending", Category.TASK, null, LocalTime.of(19, 0), "FREQ=MONTHLY;BYMONTHDAY=1"),
                TemplateItem("Set this month's budget", Category.TASK, null, LocalTime.of(19, 30), "FREQ=MONTHLY;BYMONTHDAY=1"),
                TemplateItem("Back up phone photos", Category.TASK, null, LocalTime.of(20, 0), "FREQ=MONTHLY;BYMONTHDAY=1"),
                TemplateItem("Clean out the fridge", Category.HOME, null, LocalTime.of(10, 0), "FREQ=MONTHLY;BYMONTHDAY=2"),
            ),
        ),
        Template(
            "moving", "Moving house", "Everything people forget when they move.",
            listOf(
                TemplateItem("Give notice to landlord", Category.TASK, 0),
                TemplateItem("Book packers and movers", Category.TASK, 1),
                TemplateItem("Update address with bank", Category.DOCUMENT, 7),
                TemplateItem("Transfer internet connection", Category.HOME, 7),
                TemplateItem("Update address on Aadhaar and licence", Category.DOCUMENT, 14),
                TemplateItem("Collect security deposit", Category.BILL, 30),
            ),
        ),
        Template(
            "exams", "Exam season", "A steady study plan for the weeks before exams.",
            listOf(
                TemplateItem("Make a study timetable", Category.TASK, 0, LocalTime.of(18, 0)),
                TemplateItem("Revise for 1 hour", Category.HABIT, 1, LocalTime.of(18, 0), "FREQ=DAILY"),
                TemplateItem("Solve a past paper", Category.TASK, null, LocalTime.of(10, 0), "FREQ=WEEKLY;BYDAY=SA"),
                TemplateItem("Pack hall ticket and stationery", Category.TASK, 13, LocalTime.of(20, 0)),
            ),
        ),
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id }

    /** Turns a template into reminders; repeating items start on their next occurrence. */
    fun reminders(t: Template, now: LocalDateTime): List<Reminder> = t.items.map { i ->
        val due = when {
            i.rrule != null -> app.rem.core.recurrence.RecurrenceRule.parseOrNull(i.rrule)
                ?.firstOnOrAfter(now.toLocalDate().atTime(i.time), now) ?: now.toLocalDate().plusDays(1).atTime(i.time)
            i.dayOffset != null -> now.toLocalDate().plusDays(i.dayOffset).atTime(i.time).let { if (it.isBefore(now)) it.plusDays(1) else it }
            else -> null
        }
        Reminder(
            title = i.title, category = i.category, recurrence = i.rrule, dueAt = due,
            alertStyle = AlertStyle.NOTIFICATION, alertOffsets = i.category.defaultAlertOffsets,
            createdAt = now.atZone(java.time.ZoneId.systemDefault()).toInstant(),
        )
    }
}
