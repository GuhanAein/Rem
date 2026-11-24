package app.rem.core.export

import app.rem.core.model.Reminder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class IcsWriterTest {
    @Test fun writesValidCalendar() {
        val ics = IcsWriter.write(
            listOf(
                Reminder(id = 7, title = "Pay rent, on time", dueAt = LocalDateTime.parse("2026-12-01T09:00"), recurrence = "FREQ=MONTHLY;BYMONTHDAY=1", alertOffsets = listOf(1440, 0), amount = "₹25,000"),
                Reminder(id = 8, title = "No date"),
            ),
            ZoneId.of("Asia/Kolkata"),
            now = Instant.parse("2026-11-24T00:00:00Z"),
        )
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.contains("DTSTART:20261201T033000Z\r\n"))
        assertTrue(ics.contains("SUMMARY:Pay rent\\, on time\r\n"))
        assertTrue(ics.contains("RRULE:FREQ=MONTHLY;BYMONTHDAY=1\r\n"))
        assertTrue(ics.contains("TRIGGER:-PT1440M\r\n"))
        assertTrue(ics.split("BEGIN:VEVENT").size == 2) // undated reminders are skipped
        assertTrue(ics.lines().all { it.toByteArray().size <= 76 })
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
    }
}
