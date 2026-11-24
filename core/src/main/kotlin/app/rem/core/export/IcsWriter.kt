package app.rem.core.export

import app.rem.core.model.Reminder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Writes reminders as an iCalendar (.ics) file: importable into Google Calendar, Apple Calendar
 * and Outlook. Also used for "Remind them": the recipient opens the file and gets a calendar
 * alert, even without Rem installed.
 */
object IcsWriter {
    private val UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun write(reminders: List<Reminder>, zone: ZoneId, now: Instant = Instant.now(), calendarName: String = "Rem"): String {
        val sb = StringBuilder()
        sb.line("BEGIN:VCALENDAR")
        sb.line("VERSION:2.0")
        sb.line("PRODID:-//Rem//Reminders//EN")
        sb.line("CALSCALE:GREGORIAN")
        sb.line("X-WR-CALNAME:${escape(calendarName)}")
        reminders.filter { it.dueAt != null }.forEach { r ->
            val start = r.dueAt!!.atZone(zone).toInstant()
            sb.line("BEGIN:VEVENT")
            sb.line("UID:rem-${r.id}-${start.epochSecond}@rem.app")
            sb.line("DTSTAMP:${UTC.format(now)}")
            sb.line("DTSTART:${UTC.format(start)}")
            sb.line("DTEND:${UTC.format(start.plusSeconds(15 * 60))}")
            sb.line("SUMMARY:${escape(r.title)}")
            val description = listOfNotNull(r.notes.takeIf { it.isNotBlank() }, r.amount).joinToString("\\n")
            if (description.isNotBlank()) sb.line("DESCRIPTION:${escape(description)}")
            r.recurrence?.takeIf { it.isNotBlank() }?.let { sb.line("RRULE:${toUtcUntil(it, zone)}") }
            r.alertOffsets.ifEmpty { listOf(0) }.forEach { offset ->
                sb.line("BEGIN:VALARM")
                sb.line("ACTION:DISPLAY")
                sb.line("DESCRIPTION:${escape(r.title)}")
                sb.line("TRIGGER:-PT${offset}M")
                sb.line("END:VALARM")
            }
            sb.line("END:VEVENT")
        }
        sb.line("END:VCALENDAR")
        return sb.toString()
    }

    private fun toUtcUntil(rrule: String, zone: ZoneId): String = rrule.replace(Regex("UNTIL=(\\d{8}T\\d{6})(?!Z)")) { m ->
        val local = LocalDateTime.parse(m.groupValues[1], DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        "UNTIL=" + UTC.format(local.atZone(zone).toInstant())
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    /** RFC 5545 lines end in CRLF and are folded at 75 octets. */
    private fun StringBuilder.line(text: String) {
        var rest = text
        var first = true
        while (rest.toByteArray().size > (if (first) 75 else 74)) {
            val limit = if (first) 75 else 74
            var cut = minOf(limit, rest.length)
            while (rest.substring(0, cut).toByteArray().size > limit) cut--
            append(if (first) "" else " ").append(rest.substring(0, cut)).append("\r\n")
            rest = rest.substring(cut)
            first = false
        }
        append(if (first) "" else " ").append(rest).append("\r\n")
    }
}
