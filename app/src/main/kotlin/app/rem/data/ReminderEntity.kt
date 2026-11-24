package app.rem.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.rem.core.model.AlertStyle
import app.rem.core.model.Category
import app.rem.core.model.Priority
import app.rem.core.model.Reminder
import app.rem.core.model.Status
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Entity(tableName = "reminders", indices = [Index("status"), Index("dueAt")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String,
    val category: String,
    val priority: String,
    /** Fixed-width "yyyy-MM-dd'T'HH:mm:ss" so it sorts lexicographically. */
    val dueAt: String?,
    val recurrence: String?,
    val alertStyle: String,
    /** Comma-separated minutes-before offsets. */
    val alertOffsets: String,
    val nagMinutes: Int?,
    val amount: String?,
    val status: String,
    val snoozedUntil: Long?,
    val occurrenceCount: Int,
    val createdAt: Long,
    val completedAt: Long?,
)

private val DUE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

fun LocalDateTime.toDbString(): String = format(DUE_FORMAT)

fun ReminderEntity.toModel() = Reminder(
    id = id,
    title = title,
    notes = notes,
    category = runCatching { Category.valueOf(category) }.getOrDefault(Category.TASK),
    priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.NONE),
    dueAt = dueAt?.let { LocalDateTime.parse(it, DUE_FORMAT) },
    recurrence = recurrence,
    alertStyle = runCatching { AlertStyle.valueOf(alertStyle) }.getOrDefault(AlertStyle.NOTIFICATION),
    alertOffsets = alertOffsets.split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(0) },
    nagMinutes = nagMinutes,
    amount = amount,
    status = runCatching { Status.valueOf(status) }.getOrDefault(Status.ACTIVE),
    snoozedUntil = snoozedUntil?.let(Instant::ofEpochMilli),
    occurrenceCount = occurrenceCount,
    createdAt = Instant.ofEpochMilli(createdAt),
    completedAt = completedAt?.let(Instant::ofEpochMilli),
)

fun Reminder.toEntity() = ReminderEntity(
    id = id,
    title = title,
    notes = notes,
    category = category.name,
    priority = priority.name,
    dueAt = dueAt?.toDbString(),
    recurrence = recurrence?.takeIf { it.isNotBlank() },
    alertStyle = alertStyle.name,
    alertOffsets = alertOffsets.distinct().sortedDescending().joinToString(","),
    nagMinutes = nagMinutes,
    amount = amount?.takeIf { it.isNotBlank() },
    status = status.name,
    snoozedUntil = snoozedUntil?.toEpochMilli(),
    occurrenceCount = occurrenceCount,
    createdAt = createdAt.toEpochMilli(),
    completedAt = completedAt?.toEpochMilli(),
)
