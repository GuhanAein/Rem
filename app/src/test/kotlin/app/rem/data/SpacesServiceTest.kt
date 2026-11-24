package app.rem.data

import androidx.test.core.app.ApplicationProvider
import app.rem.RemApplication
import app.rem.core.model.Space
import app.rem.core.model.Status
import app.rem.core.spaces.Aisle
import app.rem.core.spaces.WishStatus
import app.rem.core.spaces.WorkStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpacesServiceTest {
    private val app = ApplicationProvider.getApplicationContext<RemApplication>()
    private val c get() = app.container
    private val s get() = c.spaces

    private suspend fun groceries(): Long { s.ensureDefaultLists(); return c.database.shopping().lists().first { it.name == "Groceries" }.id }

    @Test fun fastAddSortsAndDoesNotDuplicate() = runBlocking {
        val id = groceries()
        assertEquals(3, s.addItems(id, "milk, 2 bread, 1kg rice"))
        s.addItems(id, "Milk")
        val items = c.database.shopping().items(id).map { it.toModel() }
        assertEquals(3, items.size)
        assertEquals(Aisle.DAIRY, items.first { it.name == "Milk" }.aisle)
        assertEquals(1.0 to "kg", items.first { it.name == "Rice" }.let { it.quantity to it.unit })
    }

    @Test fun buyingCountsAndStaplesComeBack() = runBlocking {
        val id = groceries()
        s.addItems(id, "milk")
        val milk = c.database.shopping().items(id).single().toModel()
        s.toggle(milk)
        val bought = c.database.shopping().items(id).single().toModel()
        assertTrue(bought.checked)
        assertEquals(1, bought.timesBought)
        s.updateItem(bought.copy(everyDays = 3, lastBought = LocalDate.now().minusDays(4)))
        assertEquals(1, s.restockStaples())
        assertTrue(!c.database.shopping().items(id).single().checked)
    }

    @Test fun listReminderShowsTheItems() = runBlocking {
        val id = groceries()
        assertNull(s.listText(id)) // nothing to buy -> no reminder text
        s.addItems(id, "eggs, onions")
        s.setListReminder(id, null, java.time.LocalDateTime.now().plusDays(1), "FREQ=WEEKLY;BYDAY=SA")
        assertNotNull(s.listReminder(id))
        assertTrue(s.listText(id)!!.contains("Onions"))
        s.setListReminder(id, null, null, null)
        assertNull(s.listReminder(id))
    }

    @Test fun workTasksRespectWorkHoursAndFollowUps() = runBlocking {
        val pid = s.addProject("Launch")
        val id = s.addTask("Send the deck to Ravi tomorrow 9pm", pid)
        val task = c.repository.get(id)!!
        assertEquals(Space.WORK, task.space)
        assertEquals(c.settings.value.workHours.window, task.window) // 9 PM is outside work hours -> held
        s.setStatus(task, WorkStatus.WAITING, waitingOn = "Ravi")
        val waiting = c.repository.get(id)!!
        assertEquals("Ravi", waiting.waitingOn)
        assertTrue(waiting.dueAt!!.isAfter(java.time.LocalDateTime.now()))
        s.setStatus(waiting, WorkStatus.DONE)
        assertEquals(Status.DONE, c.repository.get(id)!!.status)
    }

    @Test fun wrapUpMovesOpenWorkToTomorrowMorning() = runBlocking {
        val id = s.addTask("Review PR today 11am", null)
        c.service.save(c.repository.get(id)!!.copy(dueAt = LocalDate.now().atTime(11, 0)))
        assertEquals(1, s.wrapUp())
        val moved = c.repository.get(id)!!.dueAt!!
        assertTrue(moved.toLocalDate().isAfter(LocalDate.now()))
        assertEquals(c.settings.value.workHours.start, moved.toLocalTime())
    }

    @Test fun wishlistShareKeepAndBuy() = runBlocking {
        val id = s.addShared("Sony WH-1000XM5 ₹24,990 https://www.amazon.in/dp/B09XS7JWHH")
        val w = c.database.wishes().get(id)!!.toModel()
        assertEquals(24990.0, w.price)
        assertEquals(LocalDate.now().plusDays(30), w.askOn)
        s.setWishStatus(w, WishStatus.BOUGHT)
        assertEquals(WishStatus.BOUGHT, c.database.wishes().get(id)!!.toModel().status)
    }
}
