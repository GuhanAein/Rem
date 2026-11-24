package app.rem.core.spaces

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SpacesTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun dt(s: String) = LocalDateTime.parse(s)

    // ---- Shopping

    @Test fun splitsAListWithQuantitiesAndUnits() {
        val items = ShoppingParser.split("milk, eggs, 2 bread, 1kg rice and onions; atta 5 kg, a dozen bananas")
        assertEquals(listOf("Milk", "Eggs", "Bread", "Rice", "Onions", "Atta", "Bananas"), items.map { it.first })
        assertEquals(2.0, items[2].second)
        assertEquals(1.0 to "kg", items[3].second to items[3].third)
        assertEquals(5.0 to "kg", items[5].second to items[5].third)
        assertEquals(12.0, items[6].second)
        assertNull(items[0].second)
    }

    @Test fun unitsAreNormalised() {
        assertEquals(Triple("Oil", 2.0, "L"), ShoppingParser.parseOne("2 litres oil"))
        assertEquals(Triple("Paneer", 200.0, "g"), ShoppingParser.parseOne("200 gms paneer"))
        assertEquals(Triple("Coke", 3.0, "can"), ShoppingParser.parseOne("coke x 3 cans"))
    }

    @Test fun aislesSortTheList() {
        assertEquals(Aisle.DAIRY, Aisles.of("Milk"))
        assertEquals(Aisle.PRODUCE, Aisles.of("Onions"))
        assertEquals(Aisle.STAPLES, Aisles.of("Basmati rice"))
        assertEquals(Aisle.HOUSEHOLD, Aisles.of("Surf excel detergent"))
        assertEquals(Aisle.PHARMACY, Aisles.of("Dolo 650"))
        assertEquals(Aisle.OTHER, Aisles.of("Birthday candles"))
        val list = listOf("Milk", "Onions", "Chips", "Bread", "Detergent").mapIndexed { i, n -> ShoppingItem(i.toLong(), 1, n) }
        assertEquals(listOf(Aisle.PRODUCE, Aisle.DAIRY, Aisle.BAKERY, Aisle.SNACKS, Aisle.HOUSEHOLD), Shopping.grouped(list).map { it.first })
    }

    @Test fun staplesBuyAgainTotalsAndText() {
        val milk = ShoppingItem(1, 1, "Milk", checked = true, everyDays = 3, lastBought = d("2026-11-21"), timesBought = 9)
        assertTrue(Shopping.stapleDue(milk, d("2026-11-24")))
        assertFalse(Shopping.stapleDue(milk, d("2026-11-22")))
        assertFalse(Shopping.stapleDue(milk.copy(checked = false), d("2026-11-30")))
        val items = listOf(
            milk,
            ShoppingItem(2, 1, "Bread", quantity = 2.0, price = 45.0),
            ShoppingItem(3, 1, "Rice", quantity = 1.0, unit = "kg", price = 90.0),
            ShoppingItem(4, 1, "Eggs", checked = true, timesBought = 3),
        )
        assertEquals(180.0, Shopping.estimatedTotal(items))
        assertEquals(listOf("Milk", "Eggs"), Shopping.buyAgain(items).map { it.name })
        assertEquals("Groceries\n• Bread (2)\n• Rice (1 kg)", Shopping.asText("Groceries", items))
        assertFalse(Shopping.isLong(items))
    }

    // ---- Wishlist

    @Test fun coolOffAndTargets() {
        val w = Wish(title = "Headphones", price = 24990.0, targetPrice = 22000.0, addedOn = d("2026-10-20"))
        assertTrue(Wishes.coolOffDue(w, d("2026-11-24")))
        assertFalse(Wishes.coolOffDue(w.copy(askOn = null), d("2026-11-24")))
        assertFalse(Wishes.atTarget(w))
        assertTrue(Wishes.atTarget(w.copy(price = 21999.0)))
    }

    @Test fun paydayAffordabilityAndSaving() {
        val wishes = listOf(
            Wish(id = 1, title = "Book", price = 499.0),
            Wish(id = 2, title = "Shoes", price = 4999.0, saved = 2000.0),
            Wish(id = 3, title = "Phone", price = 70000.0),
            Wish(id = 4, title = "Old", price = 100.0, status = WishStatus.BOUGHT),
        )
        assertEquals(listOf(1L, 2L), Wishes.affordable(wishes, 5000.0).map { it.id })
        assertTrue(Wishes.isPayday(d("2026-11-30"), 31)) // short month: last day counts
        assertFalse(Wishes.isPayday(d("2026-11-29"), 30))
        assertEquals(0.4f, Wishes.progress(wishes[1]), 0.01f)
        assertEquals(17500.0, Wishes.monthlySaving(wishes[2], d("2026-11-24"), d("2027-03-01")))
    }

    @Test fun giftsAreRemindedTwoWeeksBefore() {
        val gift = Wish(title = "Shawl", occasionDate = d("1951-12-03"))
        assertTrue(Wishes.giftDue(gift, d("2026-11-24")))
        assertFalse(Wishes.giftDue(gift, d("2026-11-01")))
    }

    @Test fun sharedProductLinks() {
        val a = Wishes.fromShared("Sony WH-1000XM5 Wireless Headphones ₹24,990 https://www.amazon.in/dp/B09XS7JWHH")
        assertEquals("Sony WH-1000XM5 Wireless Headphones", a.title)
        assertEquals("https://www.amazon.in/dp/B09XS7JWHH", a.url)
        assertEquals(24990.0, a.price)
        val b = Wishes.fromShared("https://www.flipkart.com/nike-air-zoom-pegasus-running-shoes/p/itm123")
        assertEquals("Nike air zoom pegasus running shoes", b.title)
    }

    // ---- Work

    @Test fun workHoursHoldAndResume() {
        val h = WorkHours.DEFAULT
        assertTrue(h.isOn(dt("2026-11-24T11:00")))
        assertFalse(h.isOn(dt("2026-11-24T20:00")))
        assertEquals(dt("2026-11-25T09:30"), h.resumesAt(dt("2026-11-24T20:00")))
        assertEquals(dt("2026-11-30T09:30"), h.resumesAt(dt("2026-11-28T10:00"))) // Saturday -> Monday
        assertEquals(h, WorkHours.decode(h.encode()))
    }

    @Test fun followUpsSkipWeekends() {
        val h = WorkHours.DEFAULT
        assertEquals(dt("2026-11-26T10:30"), Work.followUpAt(dt("2026-11-24T15:00"), h)) // Tue -> Thu
        assertEquals(dt("2026-12-01T10:30"), Work.followUpAt(dt("2026-11-27T15:00"), h)) // Fri -> Tue
        assertEquals(dt("2026-11-30T09:30"), Work.nextWorkMorning(d("2026-11-27"), h))
        assertEquals(LocalTime.of(9, 30), Work.nextWorkMorning(d("2026-11-24"), h).toLocalTime())
    }
}
