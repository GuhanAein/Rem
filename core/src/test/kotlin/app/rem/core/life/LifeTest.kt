package app.rem.core.life

import app.rem.core.model.Category
import app.rem.core.model.Kind
import app.rem.core.model.Reminder
import app.rem.core.model.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LifeTest {
    private val today = LocalDate.of(2026, 11, 24)

    @Test fun electricityBillWithNumericDate() {
        val b = BillSms.parse("VM-BESCOM", "Dear customer, your BESCOM bill of Rs.1,240.00 for Nov is due on 05-12-2026. Pay now to avoid disconnection.", today)
        assertNotNull(b)
        assertEquals("BESCOM", b!!.biller.uppercase())
        assertEquals(1240.0, b.amount)
        assertEquals(LocalDate.of(2026, 12, 5), b.dueOn)
    }

    @Test fun creditCardWithNamedMonth() {
        val b = BillSms.parse("AD-HDFCBK", "HDFC Bank Credit Card XX1234 statement: Total amt due INR 18,450.50, Min amt due INR 920. Pay by 12 Dec.", today)!!
        assertEquals("HDFC", b.biller)
        assertEquals(18450.50, b.amount)
        assertEquals(LocalDate.of(2026, 12, 12), b.dueOn)
    }

    @Test fun rechargeExpiringMonthFirst() {
        val b = BillSms.parse("JM-JIOINF", "Your Jio plan of ₹299 expires on Nov 30, 2026. Recharge now to continue.", today)!!
        assertEquals("Jio", b.biller)
        assertEquals(LocalDate.of(2026, 11, 30), b.dueOn)
    }

    @Test fun ignoresOtpsAndPayments() {
        assertNull(BillSms.parse("AD-HDFCBK", "123456 is your OTP for payment of Rs 500 due today 24-11-2026.", today))
        assertNull(BillSms.parse("AD-HDFCBK", "Thank you for your payment of Rs 18,450 received on 20-11-2026.", today))
        assertNull(BillSms.parse("AD-ZOMATO", "Your order is on the way! Arriving by 8:40 PM.", today))
    }

    @Test fun pastDatesAreIgnored() {
        assertNull(BillSms.parse("VM-AIRTEL", "Your Airtel bill was due on 01-10-2026.", today))
    }

    @Test fun billReminderDefaults() {
        val b = BillSms.parse("VM-AIRTEL", "Airtel postpaid bill Rs 599 due on 28-11-2026", today)!!
        val r = BillSms.toReminder(b, today.atTime(9, 0))
        assertEquals("Pay Airtel bill", r.title)
        assertEquals(Category.BILL, r.category)
        assertEquals(LocalDate.of(2026, 11, 28).atTime(10, 0), r.dueAt)
        assertEquals("₹599", r.amount)
    }

    @Test fun waterIsSpreadAcrossWakingHours() {
        val t = WaterPlan.times(LocalTime.of(7, 0), LocalTime.of(23, 0), 8)
        assertEquals(8, t.size)
        assertEquals(LocalTime.of(7, 30), t.first())
        assertEquals(LocalTime.of(22, 0), t.last())
        assertTrue(t.zipWithNext().all { (a, b) -> b.isAfter(a) })
    }

    @Test fun waterStopsWhenGoalMet() {
        assertNull(WaterPlan.next(LocalTime.of(12, 0), LocalTime.of(7, 0), LocalTime.of(23, 0), 8, 8))
        assertEquals(LocalTime.of(13, 45), WaterPlan.next(LocalTime.of(12, 0), LocalTime.of(7, 0), LocalTime.of(23, 0), 8, 2))
        assertEquals(3, WaterPlan.expectedBy(LocalTime.of(12, 0), LocalTime.of(7, 0), LocalTime.of(23, 0), 8))
    }

    @Test fun bedtimeSummaryListsMissedAndTomorrow() {
        val now = today.atTime(22, 0)
        val all = listOf(
            Reminder(id = 1, title = "Call bank", dueAt = today.atTime(11, 0)),
            Reminder(id = 2, title = "Gym", dueAt = today.atTime(23, 30)),
            Reminder(id = 3, title = "Pay rent", dueAt = today.plusDays(1).atTime(10, 0)),
            Reminder(id = 4, title = "Old thing", dueAt = today.minusDays(2).atTime(9, 0)),
            Reminder(id = 5, title = "Secret", dueAt = today.atTime(9, 0), isPrivate = true),
        )
        val s = Bedtime.summary(all, completedToday = 3, now = now)
        assertEquals(listOf("Old thing", "Call bank"), s.missed.map { it.title })
        assertEquals(listOf("Pay rent"), s.tomorrow.map { it.title })
        assertEquals("2 left from today", s.title)
        assertEquals("Old thing, Call bank · 3 done · 1 tomorrow", s.body)
        assertEquals(today.plusDays(1).atTime(11, 0), Bedtime.moveToTomorrow(all[0], now).dueAt)
    }

    @Test fun billsTotalAndRenewals() {
        val all = listOf(
            Reminder(title = "Electricity", category = Category.BILL, amount = "₹1,200", dueAt = today.plusDays(5).atTime(10, 0)),
            Reminder(title = "Netflix", kind = Kind.SUBSCRIPTION, category = Category.BILL, amount = "₹649", recurrence = "FREQ=MONTHLY", dueAt = today.plusDays(2).atTime(10, 0)),
            Reminder(title = "Done bill", category = Category.BILL, amount = "₹999", dueAt = today.atTime(10, 0), status = Status.DONE),
            Reminder(title = "Next month", category = Category.BILL, amount = "₹500", dueAt = today.plusMonths(1).atTime(10, 0)),
        )
        assertEquals(1849.0, Bills.monthTotal(all, today))
        assertEquals(listOf("Netflix"), Bills.renewingSoon(all, today).map { it.title })
    }

    @Test fun documentLeadTimes() {
        val r = Documents.reminder("Passport", "Me", LocalDate.of(2027, 6, 1), today.atTime(9, 0))
        assertEquals("Passport (Me) expires", r.title)
        assertEquals(Kind.DOCUMENT, r.kind)
        assertEquals(listOf(180 * 1440, 90 * 1440, 30 * 1440, 7 * 1440, 0), r.alertOffsets)
        assertEquals(Documents.State.EXPIRING, Documents.state(today.plusDays(10), today))
        assertEquals(Documents.State.EXPIRED, Documents.state(today.minusDays(1), today))
    }

    @Test fun templatesCreateDatedReminders() {
        val now = LocalDateTime.of(2026, 11, 24, 9, 30)
        val bills = Templates.reminders(Templates.byId("bills")!!, now)
        assertEquals(5, bills.size)
        assertEquals(LocalDate.of(2026, 12, 1).atTime(10, 0), bills.first { it.title == "Pay rent" }.dueAt)
        assertEquals(LocalDate.of(2026, 12, 10).atTime(10, 0), bills.first { it.title.contains("electricity") }.dueAt)
        val trip = Templates.reminders(Templates.byId("trip")!!, now)
        assertTrue(trip.all { it.dueAt!!.isAfter(now) })
        assertTrue(Templates.byId("packing")!!.asList)
    }
}
