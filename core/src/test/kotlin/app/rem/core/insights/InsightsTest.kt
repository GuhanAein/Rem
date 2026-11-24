package app.rem.core.insights

import app.rem.core.model.RandomTimes
import app.rem.core.model.Reminder
import app.rem.core.model.TimeWindow
import app.rem.core.recurrence.RecurrenceRule
import app.rem.core.recurrence.describe
import app.rem.core.scheduling.ReminderPlanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class InsightsTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun dt(s: String) = LocalDateTime.parse(s)
    private fun d(s: String) = LocalDate.parse(s)
    private fun clockAt(s: String) = Clock.fixed(dt(s).atZone(ist).toInstant(), ist)

    // ---- Smarter scheduling ----

    @Test fun windowMovesToBusinessHours() {
        val w = TimeWindow.businessHours()
        assertEquals(dt("2026-11-24T10:00"), w.adjust(dt("2026-11-24T08:00"))) // Tue early -> 10:00
        assertEquals(dt("2026-11-25T10:00"), w.adjust(dt("2026-11-24T18:00"))) // Tue late -> Wed
        assertEquals(dt("2026-11-30T10:00"), w.adjust(dt("2026-11-28T11:00"))) // Sat -> Mon
        assertEquals(dt("2026-11-24T12:00"), w.adjust(dt("2026-11-24T12:00")))
        assertEquals(w, TimeWindow.decode(w.encode()))
    }

    @Test fun plannerHonoursWindow() {
        val p = ReminderPlanner(clockAt("2026-11-28T09:00")) // Saturday
        val r = Reminder(id = 1, title = "Call bank", dueAt = dt("2026-11-28T11:00"), window = TimeWindow.businessHours())
        assertEquals(dt("2026-11-30T10:00").atZone(ist).toInstant(), p.nextAlarm(r, ist)?.fireAt)
    }

    @Test fun preAlertsAreGentleOnlyDueAlertRings() {
        val p = ReminderPlanner(clockAt("2026-11-24T10:00"))
        val r = Reminder(id = 1, title = "Tax filing", dueAt = dt("2026-12-01T09:00"), alertOffsets = listOf(7 * 1440, 3 * 1440, 0))
        val first = p.nextAlarm(r, ist)!!
        assertEquals(dt("2026-11-28T09:00").atZone(ist).toInstant(), first.fireAt)
        assertFalse(first.isDue)
        val late = ReminderPlanner(clockAt("2026-11-30T10:00")).nextAlarm(r, ist)!!
        assertTrue(late.isDue)
    }

    @Test fun severalTimesADayRecurrence() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;BYHOUR=8,14,20;BYMINUTE=0;UNTIL=20261128T235959")
        val start = dt("2026-11-24T08:00")
        assertEquals(dt("2026-11-24T14:00"), rule.next(start, dt("2026-11-24T08:00")))
        assertEquals(dt("2026-11-25T08:00"), rule.next(start, dt("2026-11-24T20:00")))
        assertNull(rule.next(start, dt("2026-11-28T20:00")))
        assertEquals("Daily · 3× a day until 28 Nov", rule.describe())
    }

    @Test fun randomTimesAreStableSpreadAndInsideWindow() {
        val rt = RandomTimes(4, LocalTime.of(10, 0), LocalTime.of(18, 0))
        val a = ReminderPlanner.randomSlots(9, rt, d("2026-11-24"))
        assertEquals(a, ReminderPlanner.randomSlots(9, rt, d("2026-11-24")))
        assertTrue(a != ReminderPlanner.randomSlots(9, rt, d("2026-11-25")).map { it.minusDays(1) })
        assertEquals(4, a.size)
        assertTrue(a.all { !it.toLocalTime().isBefore(LocalTime.of(10, 0)) && it.toLocalTime().isBefore(LocalTime.of(18, 0)) })
        assertEquals(a.sorted(), a)
        val p = ReminderPlanner(clockAt("2026-11-24T12:00"))
        val r = Reminder(id = 9, title = "Water", dueAt = a.first(), randomTimes = rt)
        val next = p.complete(r, ist)
        assertTrue(next.dueAt!!.isAfter(dt("2026-11-24T12:00")))
    }

    @Test fun eventTriggeredRemindersHaveNoClockAlarm() {
        val p = ReminderPlanner(clockAt("2026-11-24T10:00"))
        val r = Reminder(id = 1, title = "Set out clothes", dueAt = dt("2026-11-24T20:00"), trigger = app.rem.core.model.Trigger.CHARGING_TONIGHT)
        assertNull(p.nextAlarm(r, ist))
    }

    @Test fun snoozeAdvisorFindsPattern() {
        val r = Reminder(id = 3, title = "Gym", dueAt = dt("2026-11-24T08:00"), recurrence = "FREQ=DAILY")
        val events = (1..4).map { LogEvent(3, dt("2026-11-2${it}T08:00"), LogEvent.Type.SNOOZED, minutes = listOf(30, 25, 30, 35)[it - 1]) }
        assertEquals(LocalTime.of(8, 30), SnoozeAdvisor.suggest(r, events))
        assertNull(SnoozeAdvisor.suggest(r, events.take(2)))
        assertNull(SnoozeAdvisor.suggest(r.copy(recurrence = null), events))
    }

    @Test fun conflictsAndSpread() {
        val at = dt("2026-11-24T18:00")
        val crowd = (1L..5L).map { Reminder(id = it, title = "r$it", dueAt = at.plusMinutes(it)) }
        assertTrue(ConflictDetector.isCrowded(crowd, at))
        val spread = ConflictDetector.spread(crowd)
        assertEquals(listOf(18 * 60 + 1, 18 * 60 + 17, 18 * 60 + 33, 18 * 60 + 49, 19 * 60 + 5), spread.map { it.dueAt!!.hour * 60 + it.dueAt!!.minute })
    }

    // ---- Motivation ----

    @Test fun streakSurvivesOneMissedDayWithFreeze() {
        val today = d("2026-11-24")
        val done = setOf("2026-11-24", "2026-11-23", "2026-11-21", "2026-11-20", "2026-11-19").map(::d).toSet()
        val s = Streaks.compute(done, today)
        assertEquals(5, s.current)
        assertEquals(1, s.freezesUsed)
        val noFreeze = Streaks.compute(done, today, freezePerWeek = 0)
        assertEquals(2, noFreeze.current)
        assertEquals(3, noFreeze.best)
    }

    @Test fun streakCountsFromYesterdayIfTodayPending() {
        val s = Streaks.compute(setOf(d("2026-11-23"), d("2026-11-22")), d("2026-11-24"))
        assertEquals(2, s.current)
    }

    // ---- Life admin ----

    @Test fun moneyParsing() {
        assertEquals(2340.0, Money.parse("₹2,340"))
        assertEquals(9.99, Money.parse("$9.99"))
        assertEquals(499.0, Money.parse("Rs 499"))
        assertNull(Money.parse("free"))
        assertEquals("₹1,649", Money.format(1649.0))
    }

    @Test fun subscriptionsMonthlyTotalAndTrial() {
        val subs = listOf(
            Reminder(id = 1, title = "Netflix", amount = "₹649", recurrence = "FREQ=MONTHLY"),
            Reminder(id = 2, title = "Prime", amount = "₹1,499", recurrence = "FREQ=YEARLY"),
            Reminder(id = 3, title = "Gym", amount = "₹600", recurrence = "FREQ=WEEKLY"),
        )
        assertEquals(649 + 1499 / 12.0 + 600 * 52 / 12.0, Subscriptions.monthlyTotal(subs), 0.01)
        val trial = subs[0].copy(extras = mapOf(Subscriptions.TRIAL_END to "2026-11-26"))
        assertTrue(Subscriptions.trialEndingSoon(trial, d("2026-11-24")))
        assertFalse(Subscriptions.trialEndingSoon(trial, d("2026-11-20")))
    }

    @Test fun anniversaries() {
        assertEquals("10th anniversary", Anniversaries.describe(d("2016-12-24"), d("2026-12-24"), isBirthday = false))
        assertEquals("turns 30", Anniversaries.describe(d("1996-03-12"), d("2026-03-12"), isBirthday = true))
        assertEquals("21st", Anniversaries.ordinal(21))
        assertTrue(Anniversaries.isMilestone(25))
    }

    @Test fun carServiceByDistance() {
        var r = Reminder(id = 1, title = "Service", extras = mapOf(CarLog.INTERVAL_KM to "5000", CarLog.LAST_SERVICE_KM to "20000"))
        r = CarLog.withOdometer(r, 21000, d("2026-11-01"))
        r = CarLog.withOdometer(r, 22000, d("2026-11-11")) // 100 km/day
        assertEquals(25000, CarLog.nextServiceKm(r))
        assertEquals(3000, CarLog.kmLeft(r))
        assertEquals(d("2026-12-11"), CarLog.estimateDate(r))
    }

    // ---- People ----

    @Test fun nudgesAndGifts() {
        val today = d("2026-11-24")
        val grandma = Person(name = "Grandma", cadenceDays = 14, lastContact = d("2026-11-08"), birthday = d("1950-12-01"), giftIdeas = listOf("Shawl"))
        assertTrue(Nudges.isDue(grandma, today))
        assertEquals("You haven't been in touch with Grandma in 2 weeks", Nudges.message(grandma, today))
        assertFalse(Nudges.isDue(grandma.copy(lastContact = d("2026-11-20")), today))
        assertTrue(Nudges.giftsDue(grandma, today))
        assertFalse(Nudges.giftsDue(grandma.copy(giftIdeas = emptyList()), today))
    }

    @Test fun choreRotation() {
        val m = listOf("Asha", "Ravi", "Meera")
        val start = d("2026-11-23")
        assertEquals("Asha", Rotation.assignee(m, start, d("2026-11-29")))
        assertEquals("Ravi", Rotation.assignee(m, start, d("2026-11-30")))
        assertEquals("Asha", Rotation.assignee(m, start, d("2026-12-14")))
        assertEquals("Meera", Rotation.assignee(m, start, d("2026-11-16")))
    }

    // ---- Trust ----

    @Test fun reliabilityReport() {
        val t = Instant.parse("2026-11-24T10:00:00Z")
        val r = Reliability.report(
            listOf(FiredRecord(1, t, t.plusSeconds(10)), FiredRecord(2, t, t.plusSeconds(600)), FiredRecord(3, t, t.plusSeconds(60))),
        )
        assertEquals(3, r.total)
        assertEquals(1, r.late)
        assertEquals(10, r.worstMinutes)
    }

    @Test fun yearRecap() {
        val events = listOf("2026-03-02T08:00", "2026-03-03T08:30", "2026-03-04T08:10", "2026-07-05T21:00").map { LogEvent(1, dt(it), LogEvent.Type.DONE) }
        val r = YearInReview.recap(2026, events)
        assertEquals(4, r.completed)
        assertEquals(java.time.Month.MARCH, r.busiestMonth)
        assertEquals(8, r.favouriteHour)
        assertEquals(3, r.longestStreak)
        assertEquals(DayOfWeek.MONDAY, YearInReview.recap(2026, events.take(1)).busiestWeekday)
    }
}
