package app.rem.ui

import app.rem.core.model.Category
import app.rem.core.model.Reminder
import app.rem.ui.home.SmartList
import app.rem.ui.home.buildHomeState
import app.rem.widget.widgetRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class HomeStateTest {
    private val now = LocalDateTime.parse("2026-11-24T10:00") // Tuesday

    private fun r(id: Long, due: String?, category: Category = Category.TASK) =
        Reminder(id = id, title = "r$id", dueAt = due?.let(LocalDateTime::parse), category = category)

    private val sample = listOf(
        r(1, "2026-11-23T09:00"), r(2, "2026-11-24T09:00"), r(3, "2026-11-24T18:00"),
        r(4, "2026-11-25T09:00"), r(5, "2026-11-28T09:00"), r(6, "2026-12-10T09:00"), r(7, null),
    )

    @Test fun todayShowsOverdueThenToday() {
        val s = buildHomeState(sample, emptyList(), now, SmartList.TODAY, null, false)
        assertEquals(listOf("Overdue", "Today"), s.sections.map { it.title })
        assertEquals(listOf(1L, 2L), s.sections[0].items.map { it.id })
        assertEquals(listOf(3L), s.sections[1].items.map { it.id })
        assertEquals(mapOf(SmartList.TODAY to 3, SmartList.SCHEDULED to 6, SmartList.OVERDUE to 2, SmartList.ALL to 7), s.counts)
    }

    @Test fun scheduledGroupsByDayThenLater() {
        val s = buildHomeState(sample, emptyList(), now, SmartList.SCHEDULED, null, false)
        assertEquals(listOf("Overdue", "Today", "Tomorrow", "Saturday, 28 November", "Later"), s.sections.map { it.title })
        assertEquals(listOf(6L), s.sections.last().items.map { it.id })
    }

    @Test fun allIncludesUndated() {
        val s = buildHomeState(sample, emptyList(), now, SmartList.ALL, null, false)
        assertEquals("No date", s.sections.last().title)
        assertEquals(7, s.sections.sumOf { it.items.size })
    }

    @Test fun progressCountsCompletionsToday() {
        val done = listOf(r(9, "2026-11-24T08:00").copy(completedAt = Instant.now()))
        val s = buildHomeState(listOf(r(3, "2026-11-24T18:00")), done, now, SmartList.TODAY, null, false)
        assertEquals(2, s.todayTotal)
        assertEquals(1, s.todayDone)
        assertEquals(0.5f, s.progress)
    }

    @Test fun categoryFilter() {
        val list = listOf(r(1, "2026-11-24T19:00", Category.BILL), r(2, "2026-11-24T20:00", Category.MEDICINE))
        val s = buildHomeState(list, emptyList(), now, SmartList.TODAY, Category.BILL, false)
        assertEquals(listOf(1L), s.sections.flatMap { it.items }.map { it.id })
    }

    @Test fun emptyState() {
        assertTrue(buildHomeState(emptyList(), emptyList(), now, SmartList.TODAY, null, false).isEmpty)
    }

    @Test fun widgetRowsOrderAndCount() {
        val (count, rows) = widgetRows(sample, now, max = 4)
        assertEquals(3, count) // overdue + today
        assertEquals(listOf(1L, 2L, 3L, 4L), rows.map { it.id })
        assertTrue(rows[0].late && rows[1].late && !rows[2].late)
        assertEquals("6:00 PM", rows[2].time)
    }
}
