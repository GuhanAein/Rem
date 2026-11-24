package app.rem.core.parser

import app.rem.core.model.Category
import app.rem.core.model.Priority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

class QuickAddParserTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    // Tuesday 2026-11-24 10:00 IST
    private val clock = Clock.fixed(LocalDateTime.parse("2026-11-24T10:00").atZone(zone).toInstant(), zone)
    private val parser = QuickAddParser(clock)
    private fun dt(s: String) = LocalDateTime.parse(s)

    @Test fun tomorrowAt6pm() {
        val r = parser.parse("call mom tomorrow 6pm")
        assertEquals("call mom", r.title)
        assertEquals(dt("2026-11-25T18:00"), r.dueAt)
    }

    @Test fun pastTimeWithoutDateRollsToTomorrow() {
        assertEquals(dt("2026-11-25T09:00"), parser.parse("standup at 9am").dueAt)
        assertEquals(dt("2026-11-24T13:00"), parser.parse("lunch at 1pm").dueAt)
    }

    @Test fun weekdayName() {
        assertEquals(dt("2026-11-27T16:30"), parser.parse("dentist friday 4:30pm").dueAt)
        val r = parser.parse("review next monday")
        assertEquals(dt("2026-11-30T09:00"), r.dueAt)
        assertEquals("review", r.title)
    }

    @Test fun relativeIn() {
        assertEquals(dt("2026-11-24T10:20"), parser.parse("check oven in 20 minutes").dueAt)
        assertEquals(dt("2026-11-24T12:00"), parser.parse("in 2 hours stretch").dueAt)
        assertEquals(dt("2026-11-27T09:00"), parser.parse("follow up in 3 days").dueAt)
    }

    @Test fun absoluteDate() {
        assertEquals(dt("2026-12-05T09:00"), parser.parse("renew passport on 5 dec").dueAt)
        val r = parser.parse("renew passport dec 5")
        assertEquals(dt("2026-12-05T09:00"), r.dueAt)
        assertEquals("renew passport", r.title)
        assertEquals(dt("2027-01-03T09:00"), parser.parse("party jan 3").dueAt)
        assertEquals(dt("2026-12-25T09:00"), parser.parse("gift 25/12").dueAt)
    }

    @Test fun twentyFourHour() {
        assertEquals(dt("2026-11-24T18:30"), parser.parse("deploy 18:30").dueAt)
    }

    @Test fun everyMonWedFri() {
        val r = parser.parse("gym every mon wed fri 7am")
        assertEquals("gym", r.title)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", r.recurrence?.toRRule())
        assertEquals(dt("2026-11-25T07:00"), r.dueAt)
        assertEquals(Category.HEALTH, r.category)
    }

    @Test fun everyFirstOfMonth() {
        val r = parser.parse("pay rent every 1st at 9am")
        assertEquals("pay rent", r.title)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=1", r.recurrence?.toRRule())
        assertEquals(dt("2026-12-01T09:00"), r.dueAt)
        assertEquals(Category.BILL, r.category)
    }

    @Test fun everyNWeeks() {
        val r = parser.parse("water plants every 2 weeks")
        assertEquals("FREQ=WEEKLY;INTERVAL=2", r.recurrence?.toRRule())
        assertEquals("water plants", r.title)
        assertEquals(Category.HOME, r.category)
    }

    @Test fun everyDayAndWeekdays() {
        val daily = parser.parse("vitamins every day 8am")
        assertEquals("FREQ=DAILY", daily.recurrence?.toRRule())
        assertEquals(dt("2026-11-25T08:00"), daily.dueAt)
        assertEquals(Category.MEDICINE, daily.category)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", parser.parse("every weekday 8am standup").recurrence?.toRRule())
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=-1", parser.parse("invoice every last day of the month").recurrence?.toRRule())
    }

    @Test fun priorityTokens() {
        val r = parser.parse("submit report !1 tomorrow")
        assertEquals(Priority.HIGH, r.priority)
        assertEquals("submit report", r.title)
        assertEquals(Priority.MEDIUM, parser.parse("email p2").priority)
    }

    @Test fun noDateKeepsTitle() {
        val r = parser.parse("buy milk")
        assertEquals("buy milk", r.title)
        assertNull(r.dueAt)
        assertNull(r.recurrence)
        assertEquals(Priority.NONE, r.priority)
        assertEquals(Category.SHOPPING, r.category)
    }

    @Test fun onlyDateWordsKeepsOriginalTitle() {
        val r = parser.parse("tomorrow")
        assertEquals("tomorrow", r.title)
        assertEquals(dt("2026-11-25T09:00"), r.dueAt)
    }

    @Test fun caseAndExtraSpaces() {
        val r = parser.parse("  Call  MOM   Tomorrow 6PM ")
        assertEquals("Call MOM", r.title)
        assertEquals(dt("2026-11-25T18:00"), r.dueAt)
    }

    @Test fun remindMePrefixIsStripped() {
        val r = parser.parse("remind me to take medicine tonight")
        assertEquals("take medicine", r.title)
        assertEquals(dt("2026-11-24T20:00"), r.dueAt)
        assertEquals(Category.MEDICINE, r.category)
    }

    @Test fun yearlyBirthday() {
        val r = parser.parse("Ravi's birthday 12 march every year")
        assertEquals("FREQ=YEARLY", r.recurrence?.toRRule())
        assertEquals(dt("2027-03-12T09:00"), r.dueAt)
        assertEquals(Category.BIRTHDAY, r.category)
    }
}
