package app.rem.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' ORDER BY dueAt IS NULL, dueAt ASC, id ASC")
    fun observeActive(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE completedAt IS NOT NULL AND completedAt >= :sinceMillis ORDER BY completedAt DESC")
    fun observeCompletedSince(sinceMillis: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND (dueAt IS NOT NULL OR snoozedUntil IS NOT NULL)")
    suspend fun activeScheduled(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observe(id: Long): Flow<ReminderEntity?>

    @Upsert
    suspend fun upsert(entity: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}
