package app.rem.core.parser

/**
 * Rewrites spoken-style phrases into the compact forms the parser understands, e.g.
 * "half past six" -> "6:30", "in ten minutes" -> "in 10 minutes", "the first of every month" ->
 * "every 1st", "six o'clock" -> "at 6". Only number words in time/date context are rewritten,
 * so titles like "buy two apples" are left alone.
 */
internal object SpokenNormalizer {
    private val NUMBERS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "forty five" to 45, "forty-five" to 45, "forty five" to 45, "sixty" to 60, "ninety" to 90,
    )
    private val HOURS = NUMBERS.filterValues { it in 1..12 }
    private val ORDINALS = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7,
        "eighth" to 8, "ninth" to 9, "tenth" to 10, "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13,
        "fourteenth" to 14, "fifteenth" to 15, "sixteenth" to 16, "seventeenth" to 17, "eighteenth" to 18,
        "nineteenth" to 19, "twentieth" to 20, "twenty first" to 21, "twenty-first" to 21, "twenty second" to 22,
        "twenty-second" to 22, "twenty third" to 23, "twenty-third" to 23, "twenty fourth" to 24, "twenty-fourth" to 24,
        "twenty fifth" to 25, "twenty-fifth" to 25, "twenty sixth" to 26, "twenty-sixth" to 26, "twenty seventh" to 27,
        "twenty-seventh" to 27, "twenty eighth" to 28, "twenty-eighth" to 28, "twenty ninth" to 29, "twenty-ninth" to 29,
        "thirtieth" to 30, "thirty first" to 31, "thirty-first" to 31,
    )

    private fun alt(words: Collection<String>) = words.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private val hourAlt = alt(HOURS.keys) + "|\\d{1,2}"
    private val numAlt = alt(NUMBERS.keys) + "|\\d+"
    private val ordAlt = alt(ORDINALS.keys)

    private fun hour(word: String): Int = HOURS[word.lowercase()] ?: word.toInt()
    private fun num(word: String): Int = NUMBERS[word.lowercase()] ?: word.toInt()

    private val rules: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        // Ordinal words -> 1st, 2nd... ("the first of every month", "March twelfth")
        Regex("""\b($ordAlt)\b""", RegexOption.IGNORE_CASE) to { m -> ordinal(ORDINALS.getValue(m.value.lowercase())) },
        // "on the 1st of every month" / "the 15th of each month" -> "every 15th"
        Regex("""\b(?:on\s+)?the\s+(\d{1,2})(?:st|nd|rd|th)\s+of\s+(?:every|each)\s+month\b""", RegexOption.IGNORE_CASE) to { m -> "every " + ordinal(m.groupValues[1].toInt()) },
        Regex("""\b(?:on\s+)?the\s+last\s+day\s+of\s+(?:every|each|the)\s+month\b""", RegexOption.IGNORE_CASE) to { _ -> "every last day of the month" },
        // half past six / quarter past 6 / quarter to seven
        Regex("""\bhalf\s+past\s+($hourAlt)\b""", RegexOption.IGNORE_CASE) to { m -> "${hour(m.groupValues[1])}:30" },
        Regex("""\b(?:a\s+)?quarter\s+past\s+($hourAlt)\b""", RegexOption.IGNORE_CASE) to { m -> "${hour(m.groupValues[1])}:15" },
        Regex("""\b(?:a\s+)?quarter\s+(?:to|till)\s+($hourAlt)\b""", RegexOption.IGNORE_CASE) to { m -> "${(hour(m.groupValues[1]) + 10) % 12 + 1}:45" },
        // six thirty / 6 fifteen / seven forty five
        Regex("""\b($hourAlt)\s+(fifteen|thirty|forty[\s-]five|o\s?five|zero\s+five)\b""", RegexOption.IGNORE_CASE) to { m ->
            val mins = when (m.groupValues[2].lowercase().replace('-', ' ')) { "fifteen" -> 15; "thirty" -> 30; "forty five" -> 45; else -> 5 }
            "${hour(m.groupValues[1])}:%02d".format(mins)
        },
        // "six o'clock" -> "at 6" (ambiguous hour, resolved later by am/pm heuristics)
        Regex("""\b(?:at\s+)?($hourAlt)\s+o'?\s?clock\b""", RegexOption.IGNORE_CASE) to { m -> "at ${hour(m.groupValues[1])}" },
        // number words directly before am/pm or a colon: "six pm"
        Regex("""\b(${alt(HOURS.keys)})(?=\s*(?:a\.?m\.?|p\.?m\.?)(?:\s|$|[,.!?]))""", RegexOption.IGNORE_CASE) to { m -> "${hour(m.value)}" },
        // "at six" -> "at 6"
        Regex("""\bat\s+(${alt(HOURS.keys)})\b""", RegexOption.IGNORE_CASE) to { m -> "at ${hour(m.groupValues[1])}" },
        // in ten minutes / in a couple of hours / in a few days
        Regex("""\bin\s+(a\s+couple\s+of|a\s+few|$numAlt)\s+(minutes?|mins?|hours?|days?|weeks?|months?)\b""", RegexOption.IGNORE_CASE) to { m ->
            val n = when (val w = m.groupValues[1].lowercase().replace(Regex("\\s+"), " ")) {
                "a couple of" -> 2
                "a few" -> 3
                else -> num(w)
            }
            "in $n ${m.groupValues[2]}"
        },
        // every two weeks / every three days
        Regex("""\bevery\s+(${alt(NUMBERS.keys)})\s+(days?|weeks?|months?|years?)\b""", RegexOption.IGNORE_CASE) to { m -> "every ${num(m.groupValues[1])} ${m.groupValues[2]}" },
        // "p.m." / "P.M." -> "pm"
        Regex("""\b([ap])\.\s?m\.?(?=\s|$|[,!?])""", RegexOption.IGNORE_CASE) to { m -> m.groupValues[1].lowercase() + "m" },
    )

    fun normalize(text: String): String {
        var t = text
        for ((re, f) in rules) t = re.replace(t) { f(it) }
        return t
    }

    private fun ordinal(n: Int): String = n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
}
