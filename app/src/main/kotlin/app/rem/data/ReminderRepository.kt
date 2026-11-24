package app.rem.data

import app.rem.core.model.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class ReminderRepository(private val dao: ReminderDao) {
    fun observeActive(): Flow<List<Reminder>> = dao.observeActive().map { list -> list.map { it.toModel() } }

    fun observeCompletedSince(since: Instant): Flow<List<Reminder>> =
        dao.observeCompletedSince(since.toEpochMilli()).map { list -> list.map { it.toModel() } }

    fun observe(id: Long): Flow<Reminder?> = dao.observe(id).map { it?.toModel() }

    suspend fun get(id: Long): Reminder? = dao.get(id)?.toModel()

    suspend fun activeScheduled(): List<Reminder> = dao.activeScheduled().map { it.toModel() }

    /** Inserts or updates; returns the reminder's id. */
    suspend fun upsert(reminder: Reminder): Long {
        val rowId = dao.upsert(reminder.toEntity())
        return if (reminder.id != 0L) reminder.id else rowId
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
