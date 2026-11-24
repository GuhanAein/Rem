package app.rem.ui

import app.rem.core.model.Category
import app.rem.core.model.Reminder
import app.rem.ui.home.SectionKind
import app.rem.ui.home.buildHomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class HomeStateTest {
    private val now = LocalDateTime.parse("2026-11-24T10:00") // Tuesday

    private fun r(id: Long, due: String?, category: Category = Category.TASK) =
        Reminder(id = id, title = "r$id", dueAt = due?.let(LocalDateTime::parse), category = category)

    @Test fun groupsIntoSections() {
        val state = buildHomeState(
            listOf(
                r(1, "2026-11-23T09:00"), r(2, "2026-11-24T09:00"), r(3, "2026-11-24T18:00"),
                r(4, "2026-11-25T09:00"), r(5, "2026-11-28T09:00"), r(6, "2026-12-10T09:00"), r(7, null),
            ),
            emptyList(), now, null, null, false,
        )
        val bySection = state.sections.associate { it.kind to it.items.map { m -> m.id } }
        assertEquals(listOf(1L, 2L), bySection[SectionKind.OVERDUE])
        assertEquals(listOf(3L), bySection[SectionKind.TODAY])
        assertEquals(listOf(4L), bySection[SectionKind.TOMORROW])
        assertEquals(listOf(5L), bySection[SectionKind.WEEK])
        assertEquals(listOf(6L), bySection[SectionKind.LATER])
        assertEquals(listOf(7L), bySection[SectionKind.SOMEDAY])
        assertEquals(2, state.overdueCount)
        assertEquals("3 things to\nremember today", state.headline)
    }

    @Test fun progressCountsCompletionsToday() {
        val done = listOf(r(9, "2026-11-24T08:00").copy(completedAt = Instant.now()))
        val state = buildHomeState(listOf(r(3, "2026-11-24T18:00")), done, now, null, null, false)
        assertEquals(2, state.todayTotal)
        assertEquals(1, state.todayDone)
        assertEquals(0.5f, state.progress)
    }

    @Test fun categoryFilterAndDaySelection() {
        val list = listOf(r(1, "2026-11-26T09:00", Category.BILL), r(2, "2026-11-26T10:00", Category.MEDICINE), r(3, "2026-11-27T10:00", Category.BILL))
        val filtered = buildHomeState(list, emptyList(), now, null, Category.BILL, false)
        assertEquals(listOf(1L, 3L), filtered.sections.flatMap { it.items }.map { it.id })
        val day = buildHomeState(list, emptyList(), now, now.toLocalDate().plusDays(2), null, false)
        assertEquals(SectionKind.DAY, day.sections.single().kind)
        assertEquals(listOf(1L, 2L), day.sections.single().items.map { it.id })
    }

    @Test fun emptyStateWhenNothing() {
        val state = buildHomeState(emptyList(), emptyList(), now, null, null, false)
        assertTrue(state.isEmpty)
    }
}
