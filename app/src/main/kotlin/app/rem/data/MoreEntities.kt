package app.rem.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.rem.core.insights.FiredRecord
import app.rem.core.insights.LogEvent
import app.rem.core.insights.Person
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** What happened to each reminder occurrence: powers streaks, done list, snooze learning, recap. */
@Entity(tableName = "log", indices = [Index("reminderId"), Index("at")])
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val title: String,
    val category: String,
    /** "yyyy-MM-dd'T'HH:mm:ss" local time. */
    val at: String,
    val type: String,
    val minutes: Int = 0,
) {
    fun toEvent() = LogEvent(reminderId, LocalDateTime.parse(at), LogEvent.Type.valueOf(type), minutes)
}

/** When an alarm was meant to fire vs when it did: powers the reliability report. */
@Entity(tableName = "fired", indices = [Index("firedAt")])
data class FiredEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val scheduledAt: Long,
    val firedAt: Long,
) {
    fun toRecord() = FiredRecord(reminderId, Instant.ofEpochMilli(scheduledAt), Instant.ofEpochMilli(firedAt))
}

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cadenceDays: Int?,
    val lastContact: String?,
    val birthday: String?,
    val anniversary: String?,
    /** One idea per line. */
    val giftIdeas: String,
    val phone: String?,
) {
    fun toModel() = Person(
        id, name, cadenceDays, lastContact?.let(LocalDate::parse), birthday?.let(LocalDate::parse),
        anniversary?.let(LocalDate::parse), giftIdeas.lines().filter { it.isNotBlank() }, phone,
    )

    companion object {
        fun from(p: Person) = PersonEntity(
            p.id, p.name, p.cadenceDays, p.lastContact?.toString(), p.birthday?.toString(), p.anniversary?.toString(),
            p.giftIdeas.joinToString("\n"), p.phone,
        )
    }
}

/** A saved place for location triggers ("Home", "Office", "Supermarket"). */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val isHome: Boolean = false,
)

@Dao
interface LogDao {
    @Insert suspend fun insert(e: LogEntity)
    @Query("SELECT * FROM log WHERE at >= :from ORDER BY at DESC") fun observeSince(from: String): Flow<List<LogEntity>>
    @Query("SELECT * FROM log WHERE at >= :from ORDER BY at DESC") suspend fun since(from: String): List<LogEntity>
    @Query("SELECT * FROM log WHERE reminderId = :id ORDER BY at DESC LIMIT :limit") suspend fun forReminder(id: Long, limit: Int = 60): List<LogEntity>
    @Query("SELECT * FROM log ORDER BY at DESC") suspend fun all(): List<LogEntity>
}

@Dao
interface FiredDao {
    @Insert suspend fun insert(e: FiredEntity)
    @Query("SELECT * FROM fired WHERE firedAt >= :from ORDER BY firedAt DESC") suspend fun since(from: Long): List<FiredEntity>
    @Query("DELETE FROM fired WHERE firedAt < :before") suspend fun prune(before: Long)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name") fun observeAll(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM people ORDER BY name") suspend fun all(): List<PersonEntity>
    @Query("SELECT * FROM people WHERE id = :id") suspend fun get(id: Long): PersonEntity?
    @Upsert suspend fun upsert(p: PersonEntity): Long
    @Query("DELETE FROM people WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY isHome DESC, name") fun observeAll(): Flow<List<PlaceEntity>>
    @Query("SELECT * FROM places") suspend fun all(): List<PlaceEntity>
    @Query("SELECT * FROM places WHERE id = :id") suspend fun get(id: Long): PlaceEntity?
    @Upsert suspend fun upsert(p: PlaceEntity): Long
    @Query("DELETE FROM places WHERE id = :id") suspend fun delete(id: Long)
}
