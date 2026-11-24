package app.rem.core.parser

import app.rem.core.model.Category
import java.time.DayOfWeek
import java.time.LocalTime

/** English word tables. Other languages can be added as further vocabularies. */
internal object DateVocabulary {
    val WEEKDAYS: Map<String, DayOfWeek> = buildMap {
        fun put(day: DayOfWeek, vararg names: String) = names.forEach { put(it, day) }
        put(DayOfWeek.MONDAY, "monday", "mon")
        put(DayOfWeek.TUESDAY, "tuesday", "tues", "tue")
        put(DayOfWeek.WEDNESDAY, "wednesday", "wed")
        put(DayOfWeek.THURSDAY, "thursday", "thurs", "thur", "thu")
        put(DayOfWeek.FRIDAY, "friday", "fri")
        put(DayOfWeek.SATURDAY, "saturday", "sat")
        put(DayOfWeek.SUNDAY, "sunday", "sun")
    }

    val MONTHS: Map<String, Int> = buildMap {
        listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
        ).forEachIndexed { i, name ->
            put(name, i + 1)
            put(name.take(3), i + 1)
        }
        put("sept", 9)
    }

    val PARTS_OF_DAY: Map<String, LocalTime> = mapOf(
        "morning" to LocalTime.of(9, 0),
        "noon" to LocalTime.of(12, 0),
        "afternoon" to LocalTime.of(14, 0),
        "evening" to LocalTime.of(18, 0),
        "tonight" to LocalTime.of(20, 0),
        "night" to LocalTime.of(21, 0),
        "midnight" to LocalTime.of(0, 0),
    )

    val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    /** Keyword rules for "smart" category detection: no AI, just words. */
    val CATEGORY_KEYWORDS: List<Pair<Category, List<String>>> = listOf(
        Category.MEDICINE to listOf("medicine", "medication", "pill", "pills", "tablet", "tablets", "dose", "capsule", "insulin", "vitamin", "vitamins", "syrup", "inhaler"),
        Category.BIRTHDAY to listOf("birthday", "bday", "b'day", "anniversary"),
        Category.BILL to listOf("pay", "bill", "bills", "rent", "emi", "recharge", "electricity", "subscription", "invoice", "credit card", "loan", "premium", "fees", "netflix", "spotify"),
        Category.DOCUMENT to listOf("passport", "licence", "license", "visa", "insurance", "renew", "expiry", "expires", "warranty", "aadhaar", "pan card"),
        Category.VEHICLE to listOf("car", "bike", "scooter", "service", "oil change", "tyre", "tire", "puc", "fuel", "petrol"),
        Category.PET to listOf("dog", "cat", "pet", "vet", "puppy", "kitten", "walk the dog", "feed the"),
        Category.HOME to listOf("clean", "laundry", "plants", "water the plants", "trash", "garbage", "dishes", "filter", "gas cylinder", "cook"),
        Category.HEALTH to listOf("water", "drink", "workout", "gym", "exercise", "doctor", "dentist", "walk", "yoga", "stretch", "sleep", "run"),
        Category.HABIT to listOf("meditate", "read", "journal", "practice", "habit", "study", "learn"),
        Category.WORK to listOf("meeting", "email", "deadline", "report", "submit", "standup", "client", "presentation", "interview", "office"),
        Category.SHOPPING to listOf("buy", "groceries", "grocery", "shopping", "order", "pick up", "pickup"),
        Category.EVENT to listOf("party", "wedding", "trip", "flight", "concert", "exam", "movie", "dinner", "event"),
    )
}
