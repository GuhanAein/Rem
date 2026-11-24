package app.rem.backup

import android.content.Context
import app.rem.container
import app.rem.core.export.IcsWriter
import app.rem.core.export.PdfWriter
import app.rem.core.model.Reminder
import app.rem.data.PersonEntity
import app.rem.data.PlaceEntity
import app.rem.data.ReminderEntity
import app.rem.data.toModel
import app.rem.ui.format.describeDue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Local, account-free backup and export: JSON (full restore), .ics (calendars) and PDF (print). */
object Backup {
    private const val VERSION = 2

    suspend fun exportJson(context: Context): String {
        val db = context.container.database
        val root = JSONObject()
        root.put("app", "Rem")
        root.put("version", VERSION)
        root.put("exportedAt", java.time.Instant.now().toString())
        root.put("reminders", JSONArray(db.reminders().all().map { it.toJson() }))
        root.put("people", JSONArray(db.people().all().map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("cadenceDays", p.cadenceDays ?: JSONObject.NULL)
                .put("lastContact", p.lastContact ?: JSONObject.NULL).put("birthday", p.birthday ?: JSONObject.NULL)
                .put("anniversary", p.anniversary ?: JSONObject.NULL).put("giftIdeas", p.giftIdeas).put("phone", p.phone ?: JSONObject.NULL)
        }))
        root.put("places", JSONArray(db.places().all().map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("latitude", p.latitude).put("longitude", p.longitude)
                .put("radiusMeters", p.radiusMeters.toDouble()).put("isHome", p.isHome)
        }))
        root.put("log", JSONArray(db.log().all().take(5000).map { l ->
            JSONObject().put("reminderId", l.reminderId).put("title", l.title).put("category", l.category).put("at", l.at).put("type", l.type).put("minutes", l.minutes)
        }))
        return root.toString(2)
    }

    /** Restores a JSON backup, replacing nothing: items are added alongside existing ones. Returns the number of reminders restored. */
    suspend fun importJson(context: Context, json: String): Int {
        val c = context.container
        val root = JSONObject(json)
        require(root.optString("app") == "Rem") { "This isn't a Rem backup" }
        val reminders = root.optJSONArray("reminders") ?: JSONArray()
        var restored = 0
        for (i in 0 until reminders.length()) {
            val e = reminders.getJSONObject(i).toReminderEntity().copy(id = 0)
            c.service.save(e.toModel())
            restored++
        }
        root.optJSONArray("people")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                c.database.people().upsert(
                    PersonEntity(0, o.getString("name"), o.optIntOrNull("cadenceDays"), o.optStringOrNull("lastContact"), o.optStringOrNull("birthday"),
                        o.optStringOrNull("anniversary"), o.optString("giftIdeas"), o.optStringOrNull("phone")),
                )
            }
        }
        root.optJSONArray("places")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                c.database.places().upsert(PlaceEntity(0, o.getString("name"), o.getDouble("latitude"), o.getDouble("longitude"), o.getDouble("radiusMeters").toFloat(), o.getBoolean("isHome")))
            }
        }
        return restored
    }

    /** Nightly automatic backup into private storage; keeps the last 7. */
    suspend fun nightly(context: Context): File {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "rem-${LocalDate.now()}.json")
        file.writeText(exportJson(context))
        dir.listFiles()?.sortedByDescending { it.name }?.drop(7)?.forEach { it.delete() }
        return file
    }

    fun latestNightly(context: Context): File? = File(context.filesDir, "backups").listFiles()?.maxByOrNull { it.name }

    suspend fun exportIcs(context: Context): File {
        val reminders = context.container.repository.allActive().filter { !it.isPrivate }
        return File(shareDir(context), "rem-reminders.ics").apply { writeText(IcsWriter.write(reminders, ZoneId.systemDefault())) }
    }

    /** A clean printable list of every active reminder. */
    suspend fun exportPdf(context: Context): File {
        val reminders = context.container.repository.allActive().filter { !it.isPrivate }.sortedWith(compareBy(nullsLast()) { it.dueAt })
        val lines = buildList {
            add(PdfWriter.Line("Rem", 28f, bold = true))
            add(PdfWriter.Line("REMINDERS  \u00B7  ${LocalDate.now()}", 9f, grey = true, gapBefore = 2f))
            reminders.forEach { r ->
                add(PdfWriter.Line(r.title, 12f, bold = true, gapBefore = 12f))
                add(PdfWriter.Line(listOfNotNull(r.category.label, describeDue(r.dueAt), r.amount).joinToString("  \u00B7  "), 10f, grey = true))
                if (r.notes.isNotBlank()) add(PdfWriter.Line(r.notes.take(120), 10f, grey = true))
            }
        }
        return File(shareDir(context), "rem-reminders.pdf").apply { writeBytes(PdfWriter.write(lines)) }
    }

    /** "Remind them": a calendar invite anyone can open, with an alert built in. */
    fun remindThemIcs(context: Context, reminder: Reminder): File =
        File(shareDir(context), "reminder-${reminder.id}.ics").apply { writeText(IcsWriter.write(listOf(reminder), ZoneId.systemDefault(), calendarName = reminder.title)) }

    fun shareDir(context: Context): File = File(context.cacheDir, "share").apply { mkdirs() }

    private fun ReminderEntity.toJson(): JSONObject = JSONObject()
        .put("title", title).put("notes", notes).put("category", category).put("priority", priority)
        .put("dueAt", dueAt ?: JSONObject.NULL).put("recurrence", recurrence ?: JSONObject.NULL).put("alertStyle", alertStyle)
        .put("alertOffsets", alertOffsets).put("nagMinutes", nagMinutes ?: JSONObject.NULL).put("amount", amount ?: JSONObject.NULL)
        .put("status", status).put("occurrenceCount", occurrenceCount).put("createdAt", createdAt)
        .put("kind", kind).put("window", window ?: JSONObject.NULL).put("randomTimes", randomTimes ?: JSONObject.NULL)
        .put("trigger", trigger ?: JSONObject.NULL).put("followUp", followUp ?: JSONObject.NULL).put("firstStep", firstStep ?: JSONObject.NULL)
        .put("isPrivate", isPrivate).put("extras", extras)

    private fun JSONObject.toReminderEntity() = ReminderEntity(
        id = 0, title = getString("title"), notes = optString("notes"), category = optString("category", "TASK"),
        priority = optString("priority", "NONE"), dueAt = optStringOrNull("dueAt"), recurrence = optStringOrNull("recurrence"),
        alertStyle = optString("alertStyle", "NOTIFICATION"), alertOffsets = optString("alertOffsets", "0"),
        nagMinutes = optIntOrNull("nagMinutes"), amount = optStringOrNull("amount"), status = optString("status", "ACTIVE"),
        snoozedUntil = null, occurrenceCount = optInt("occurrenceCount"), createdAt = optLong("createdAt"), completedAt = null,
        kind = optString("kind", "STANDARD"), window = optStringOrNull("window"), randomTimes = optStringOrNull("randomTimes"),
        trigger = optStringOrNull("trigger"), followUp = optStringOrNull("followUp"), firstStep = optStringOrNull("firstStep"),
        isPrivate = optBoolean("isPrivate"), personId = null, extras = optString("extras", "{}"),
    )

    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k) || !has(k)) null else optString(k)
    private fun JSONObject.optIntOrNull(k: String): Int? = if (isNull(k) || !has(k)) null else optInt(k)
}
