package app.rem.core.spaces

import app.rem.core.insights.Money
import app.rem.core.model.TimeWindow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

// =============================================================================================
// Shopping
// =============================================================================================

enum class Aisle(val label: String) {
    PRODUCE("Fruit & vegetables"),
    DAIRY("Dairy & eggs"),
    BAKERY("Bakery"),
    MEAT("Meat & fish"),
    STAPLES("Rice, atta & dal"),
    SPICES("Masala & condiments"),
    SNACKS("Snacks"),
    BEVERAGES("Drinks"),
    FROZEN("Frozen"),
    HOUSEHOLD("Household"),
    PERSONAL("Personal care"),
    PHARMACY("Pharmacy"),
    BABY("Baby"),
    PET("Pet"),
    OTHER("Other"),
}

data class ShoppingItem(
    val id: Long = 0,
    val listId: Long,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val aisle: Aisle = Aisles.of(name),
    val price: Double? = null,
    val note: String = "",
    val checked: Boolean = false,
    /** Staple: re-add to the list every N days after it's bought. */
    val everyDays: Int? = null,
    val lastBought: LocalDate? = null,
    val runningLow: Boolean = false,
    val timesBought: Int = 0,
) {
    val quantityText: String?
        get() = quantity?.let { q ->
            val n = if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()
            if (unit.isNullOrBlank()) n else "$n $unit"
        }
}

/** "milk, eggs, 2 bread, 1kg rice and onions" -> five items with quantities and units. */
object ShoppingParser {
    private val UNITS = listOf("kg", "kgs", "g", "gm", "gms", "grams", "l", "ltr", "litre", "litres", "liter", "ml", "pcs", "pc", "packs", "pack", "packet", "packets", "dozen", "bottles", "bottle", "boxes", "box", "loaves", "loaf", "cans", "can", "bunch", "bunches")
    private val unitAlt = UNITS.sortedByDescending { it.length }.joinToString("|")
    private val leading = Regex("""^(\d+(?:\.\d+)?)\s*($unitAlt)?\b\s*(?:of\s+)?(.+)$""", RegexOption.IGNORE_CASE)
    private val trailing = Regex("""^(.+?)\s+(?:x\s*)?(\d+(?:\.\d+)?)\s*($unitAlt)?$""", RegexOption.IGNORE_CASE)
    private val words = mapOf("a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0, "half" to 0.5, "a dozen" to 12.0)

    fun split(text: String): List<Triple<String, Double?, String?>> =
        text.split(Regex("""\s*(?:,|;|\n|\band\b|\+)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotEmpty() }
            .map(::parseOne)

    fun parseOne(raw: String): Triple<String, Double?, String?> {
        var t = raw.trim()
        words.entries.sortedByDescending { it.key.length }.firstOrNull { t.lowercase().startsWith(it.key + " ") }?.let { (w, n) ->
            t = "${if (n == n.toLong().toDouble()) n.toLong() else n} " + t.substring(w.length).trim()
        }
        leading.matchEntire(t)?.let { m ->
            return Triple(m.groupValues[3].trim().cap(), m.groupValues[1].toDouble(), m.groupValues[2].normUnit())
        }
        trailing.matchEntire(t)?.let { m ->
            return Triple(m.groupValues[1].trim().cap(), m.groupValues[2].toDouble(), m.groupValues[3].normUnit())
        }
        return Triple(t.cap(), null, null)
    }

    private fun String.normUnit(): String? = when (lowercase()) {
        "" -> null
        "kgs" -> "kg"
        "gm", "gms", "grams" -> "g"
        "ltr", "litre", "litres", "liter", "l" -> "L"
        "pc" -> "pcs"
        "packs", "packet", "packets" -> "pack"
        "bottles" -> "bottle"
        "boxes" -> "box"
        "loaves" -> "loaf"
        "cans" -> "can"
        "bunches" -> "bunch"
        else -> lowercase()
    }

    private fun String.cap() = replaceFirstChar { it.uppercase() }
}

/** Keyword rules that sort an item into the aisle you'd find it in. */
object Aisles {
    private val rules: List<Pair<Aisle, List<String>>> = listOf(
        Aisle.PHARMACY to listOf("paracetamol", "crocin", "dolo", "tablet", "medicine", "syrup", "bandage", "ointment", "vitamin", "ors", "antiseptic", "dettol liquid"),
        Aisle.BABY to listOf("diaper", "diapers", "baby", "wipes", "formula"),
        Aisle.PET to listOf("dog food", "cat food", "pedigree", "whiskas", "litter"),
        Aisle.DAIRY to listOf("milk", "curd", "dahi", "paneer", "cheese", "butter", "ghee", "yogurt", "yoghurt", "cream", "egg", "eggs"),
        Aisle.BAKERY to listOf("bread", "pav", "bun", "buns", "cake", "rusk", "croissant"),
        Aisle.MEAT to listOf("chicken", "mutton", "fish", "prawn", "prawns", "meat", "salmon", "sausage"),
        Aisle.FROZEN to listOf("frozen", "ice cream", "peas frozen"),
        Aisle.BEVERAGES to listOf("tea", "coffee", "juice", "water", "soda", "cola", "coke", "pepsi", "beer", "wine"),
        Aisle.SNACKS to listOf("chips", "biscuit", "biscuits", "namkeen", "chocolate", "cookies", "nuts", "popcorn", "maggi", "noodles"),
        Aisle.STAPLES to listOf("rice", "atta", "flour", "dal", "lentil", "lentils", "sugar", "salt", "oil", "poha", "rava", "sooji", "besan", "oats", "pasta"),
        Aisle.SPICES to listOf("masala", "turmeric", "haldi", "chilli", "chili", "jeera", "cumin", "pepper", "ketchup", "sauce", "pickle", "vinegar", "mustard"),
        Aisle.PRODUCE to listOf("onion", "onions", "potato", "potatoes", "tomato", "tomatoes", "banana", "bananas", "apple", "apples", "mango", "lemon", "ginger", "garlic", "spinach", "palak", "carrot", "carrots", "coriander", "dhaniya", "chillies", "cucumber", "grapes", "orange", "oranges", "vegetable", "vegetables", "fruit", "fruits", "capsicum", "cabbage", "cauliflower", "beans", "mushroom", "mushrooms"),
        Aisle.HOUSEHOLD to listOf("detergent", "surf", "soap bar", "dishwash", "vim", "phenyl", "harpic", "lizol", "tissue", "tissues", "garbage bag", "garbage bags", "bulb", "battery", "batteries", "foil", "cling", "broom", "mop", "sponge"),
        Aisle.PERSONAL to listOf("shampoo", "soap", "toothpaste", "toothbrush", "deodorant", "razor", "lotion", "conditioner", "face wash", "sanitary", "pads", "sunscreen"),
    )

    fun of(name: String): Aisle {
        val n = " " + name.lowercase() + " "
        return rules.firstOrNull { (_, words) -> words.any { w -> Regex("""(?<![a-z])${Regex.escape(w)}(?![a-z])""").containsMatchIn(n) } }?.first ?: Aisle.OTHER
    }
}

object Shopping {
    /** Unchecked first, grouped by aisle in walking order; checked items last. */
    fun grouped(items: List<ShoppingItem>): List<Pair<Aisle, List<ShoppingItem>>> =
        items.filter { !it.checked }.groupBy { it.aisle }.toSortedMap(compareBy { it.ordinal }).map { it.key to it.value.sortedBy { i -> i.name.lowercase() } }

    fun estimatedTotal(items: List<ShoppingItem>): Double = items.filter { !it.checked }.sumOf { (it.price ?: 0.0) * (it.quantity?.takeIf { q -> it.unit == null || it.unit == "pcs" } ?: 1.0) }

    /** Staples bought long enough ago come back onto the list. */
    fun stapleDue(item: ShoppingItem, today: LocalDate): Boolean {
        val every = item.everyDays ?: return false
        if (!item.checked) return false
        val last = item.lastBought ?: return true
        return ChronoUnit.DAYS.between(last, today) >= every
    }

    /** "Buy again": your most-bought items that aren't on the list right now. */
    fun buyAgain(items: List<ShoppingItem>, limit: Int = 8): List<ShoppingItem> =
        items.filter { it.checked && it.timesBought > 0 }.sortedByDescending { it.timesBought }.take(limit)

    fun isLong(items: List<ShoppingItem>, threshold: Int = 5) = items.count { !it.checked } >= threshold

    /** Text for sharing or for the "you're at the shop" notification. */
    fun asText(listName: String, items: List<ShoppingItem>): String = buildString {
        append(listName).append('\n')
        grouped(items).forEach { (_, list) -> list.forEach { i -> append("• ").append(i.name); i.quantityText?.let { append(" (").append(it).append(')') }; append('\n') } }
    }.trimEnd()
}

// =============================================================================================
// Wishlist
// =============================================================================================

enum class WishStatus { WANTED, BOUGHT, RECEIVED, LET_GO }

data class Wish(
    val id: Long = 0,
    val title: String,
    val url: String? = null,
    val photo: String? = null,
    val price: Double? = null,
    val targetPrice: Double? = null,
    val saved: Double = 0.0,
    val priority: Int = 0,
    val notes: String = "",
    val forPersonId: Long? = null,
    val occasionDate: LocalDate? = null,
    val status: WishStatus = WishStatus.WANTED,
    val addedOn: LocalDate = LocalDate.now(),
    /** Cool-off: when to ask "still want it?"; null once answered. */
    val askOn: LocalDate? = addedOn.plusDays(Wishes.COOL_OFF_DAYS),
)

object Wishes {
    const val COOL_OFF_DAYS = 30L

    fun coolOffDue(w: Wish, today: LocalDate) = w.status == WishStatus.WANTED && w.askOn != null && !today.isBefore(w.askOn)

    fun atTarget(w: Wish) = w.status == WishStatus.WANTED && w.price != null && w.targetPrice != null && w.price <= w.targetPrice

    /** Wanted items you could buy with [budget], cheapest first. */
    fun affordable(all: List<Wish>, budget: Double): List<Wish> =
        all.filter { it.status == WishStatus.WANTED && it.price != null && it.price - it.saved <= budget }.sortedBy { it.price!! - it.saved }

    fun progress(w: Wish): Float = w.price?.takeIf { it > 0 }?.let { (w.saved / it).toFloat().coerceIn(0f, 1f) } ?: 0f

    /** How much to set aside each month to afford it by [by]. */
    fun monthlySaving(w: Wish, today: LocalDate, by: LocalDate): Double? {
        val price = w.price ?: return null
        val months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), by.withDayOfMonth(1)).coerceAtLeast(1)
        return ((price - w.saved).coerceAtLeast(0.0) / months)
    }

    /** Gifts: start reminding 14 days before the occasion. */
    fun giftDue(w: Wish, today: LocalDate, leadDays: Long = 14): Boolean {
        val d = w.occasionDate ?: return false
        if (w.status != WishStatus.WANTED) return false
        var next = d.withYear(today.year)
        if (next.isBefore(today)) next = next.plusYears(1)
        return ChronoUnit.DAYS.between(today, next) in 0..leadDays
    }

    fun isPayday(today: LocalDate, payday: Int?): Boolean {
        if (payday == null) return false
        val day = payday.coerceAtMost(today.lengthOfMonth())
        return today.dayOfMonth == day
    }

    /** What a shopping app shares: usually "Title https://link" or the link alone. */
    fun fromShared(text: String): Wish {
        val url = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')')
        val price = Regex("""(?:₹|Rs\.?|INR|\$)\s?([\d,]+(?:\.\d+)?)""").find(text)?.let { Money.parse(it.value) }
        var title = text.replace(url ?: "\u0000", "").replace(Regex("""(?i)check out this (product|item)( on \w+)?:?"""), "")
            .replace(Regex("""(?:₹|Rs\.?|INR|\$)\s?[\d,]+(?:\.\d+)?"""), "").trim().trim('-', ':', '|', ' ')
        if (title.isBlank() && url != null) {
            // Use the product slug from the link, e.g. /sony-wh-1000xm5-headphones/dp/...
            title = Regex("""/([a-zA-Z0-9]+(?:-[a-zA-Z0-9]+){1,})/""").find(url)?.groupValues?.get(1)
                ?.replace('-', ' ')?.split(' ')?.take(6)?.joinToString(" ")?.replaceFirstChar { it.uppercase() } ?: "Saved item"
        }
        return Wish(title = title.take(120), url = url, price = price)
    }
}

// =============================================================================================
// Work
// =============================================================================================

enum class WorkStatus(val label: String) { TODO("To do"), DOING("Doing"), WAITING("Waiting"), DONE("Done") }

data class WorkHours(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime) {
    val window: TimeWindow get() = TimeWindow(days, start, end)

    fun isOn(t: LocalDateTime) = window.contains(t)

    /** When work reminders resume, if we're off the clock now. */
    fun resumesAt(t: LocalDateTime): LocalDateTime = window.adjust(t)

    fun encode() = window.encode()

    companion object {
        val DEFAULT = WorkHours(TimeWindow.WEEKDAYS, LocalTime.of(9, 30), LocalTime.of(18, 30))
        fun decode(s: String?): WorkHours? = TimeWindow.decode(s)?.let { WorkHours(it.days, it.start, it.end) }
    }
}

object Work {
    const val DEFAULT_FOLLOW_UP_DAYS = 2L

    /** "Waiting on Ravi" turns into a follow-up nudge after a couple of working days. */
    fun followUpAt(since: LocalDateTime, hours: WorkHours, days: Long = DEFAULT_FOLLOW_UP_DAYS): LocalDateTime {
        var d = since.toLocalDate()
        var added = 0
        while (added < days) {
            d = d.plusDays(1)
            if (d.dayOfWeek in hours.days) added++
        }
        return d.atTime(hours.start.plusHours(1))
    }

    /** Evening wrap-up: everything still open today moves to the next working morning. */
    fun nextWorkMorning(today: LocalDate, hours: WorkHours): LocalDateTime {
        var d = today.plusDays(1)
        while (d.dayOfWeek !in hours.days) d = d.plusDays(1)
        return d.atTime(hours.start)
    }
}
