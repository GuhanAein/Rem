package app.rem.core.recurrence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class RecurrenceRuleTest {
    private fun dt(s: String) = LocalDateTime.parse(s)

    @Test fun parseRoundTrip() {
        val s = "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR"
        assertEquals(s, RecurrenceRule.parse(s).toRRule())
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR", RecurrenceRule.parse("FREQ=MONTHLY;BYDAY=-1FR").toRRule())
    }

    @Test fun dailyInterval3() {
        val start = dt("2026-11-24T07:00")
        assertEquals(dt("2026-11-27T07:00"), RecurrenceRule(Freq.DAILY, interval = 3).next(start, start))
    }

    @Test fun weeklyMonWedFri() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=MO,WE,FR")
        val start = dt("2026-11-23T07:00") // Monday
        assertEquals(dt("2026-11-25T07:00"), rule.next(start, start))
        assertEquals(dt("2026-11-30T07:00"), rule.next(start, dt("2026-11-27T07:00")))
    }

    @Test fun everyTwoWeeksKeepsGrid() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2")
        val start = dt("2026-11-23T07:00")
        assertEquals(dt("2026-12-07T07:00"), rule.next(start, start))
    }

    @Test fun monthDay31SkipsShortMonths() {
        val rule = RecurrenceRule.parse("FREQ=MONTHLY;BYMONTHDAY=31")
        assertEquals(dt("2026-03-31T09:00"), rule.next(dt("2026-01-31T09:00"), dt("2026-01-31T09:00")))
    }

    @Test fun lastDayOfMonthHitsFebruary() {
        val rule = RecurrenceRule.parse("FREQ=MONTHLY;BYMONTHDAY=-1")
        assertEquals(dt("2026-02-28T09:00"), rule.next(dt("2026-01-31T09:00"), dt("2026-01-31T09:00")))
        assertEquals(dt("2028-02-29T09:00"), rule.next(dt("2028-01-31T09:00"), dt("2028-01-31T09:00")))
    }

    @Test fun lastFridayOfMonth() {
        val rule = RecurrenceRule.parse("FREQ=MONTHLY;BYDAY=-1FR")
        assertEquals(dt("2026-11-27T09:00"), rule.next(dt("2026-11-01T09:00"), dt("2026-11-01T09:00")))
    }

    @Test fun yearlyBirthday() {
        val rule = RecurrenceRule(Freq.YEARLY)
        assertEquals(dt("2027-03-15T09:00"), rule.next(dt("2000-03-15T09:00"), dt("2026-03-15T09:00")))
    }

    @Test fun countStopsSeries() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;COUNT=3")
        assertNull(rule.next(dt("2026-11-24T09:00"), dt("2026-11-26T09:00"), occurrencesSoFar = 3))
    }

    @Test fun untilStopsSeries() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;UNTIL=20261201T000000")
        assertNull(rule.next(dt("2026-11-24T09:00"), dt("2026-11-30T09:00")))
    }

    @Test fun parseRejectsGarbage() {
        assertThrows<IllegalArgumentException> { RecurrenceRule.parse("FREQ=HOURLY") }
        assertThrows<IllegalArgumentException> { RecurrenceRule.parse("nonsense") }
    }

    @Test fun describesRules() {
        assertEquals("Every Mon, Wed, Fri", RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=MO,WE,FR").describe())
        assertEquals("Every weekday", RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR").describe())
        assertEquals("Monthly on the last day", RecurrenceRule.parse("FREQ=MONTHLY;BYMONTHDAY=-1").describe())
        assertEquals("Every 2 weeks", RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2").describe())
        assertEquals("Monthly on the 1st", RecurrenceRule.parse("FREQ=MONTHLY;BYMONTHDAY=1").describe())
    }

    @Test fun firstOccurrenceSnapsToTheRuleDays() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=SA")
        // Thursday 10:00 with an every-Saturday rule -> Saturday 10:00
        assertEquals(dt("2026-11-28T10:00"), rule.firstOnOrAfter(dt("2026-11-26T10:00"), dt("2026-11-26T10:00")))
        // Already on a matching day: unchanged
        assertEquals(dt("2026-11-28T10:00"), rule.firstOnOrAfter(dt("2026-11-28T10:00"), dt("2026-11-28T10:00")))
    }
}
