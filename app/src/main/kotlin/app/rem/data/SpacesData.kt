package app.rem.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.rem.core.spaces.Aisle
import app.rem.core.spaces.ShoppingItem
import app.rem.core.spaces.Wish
import app.rem.core.spaces.WishStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    /** Nudge when the list has 5+ items. */
    val remindWhenLong: Boolean = true,
)

@Entity(tableName = "shopping_items", indices = [Index("listId")])
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val aisle: String,
    val price: Double?,
    val note: String,
    val checked: Boolean,
    val everyDays: Int?,
    val lastBought: String?,
    val runningLow: Boolean,
    val timesBought: Int,
) {
    fun toModel() = ShoppingItem(
        id, listId, name, quantity, unit, runCatching { Aisle.valueOf(aisle) }.getOrDefault(Aisle.OTHER), price, note, checked,
        everyDays, lastBought?.let(LocalDate::parse), runningLow, timesBought,
    )

    companion object {
        fun from(i: ShoppingItem) = ShoppingItemEntity(
            i.id, i.listId, i.name, i.quantity, i.unit, i.aisle.name, i.price, i.note, i.checked, i.everyDays,
            i.lastBought?.toString(), i.runningLow, i.timesBought,
        )
    }
}

@Entity(tableName = "wishes")
data class WishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String?,
    val photo: String?,
    val price: Double?,
    val targetPrice: Double?,
    val saved: Double,
    val priority: Int,
    val notes: String,
    val forPersonId: Long?,
    val occasionDate: String?,
    val status: String,
    val addedOn: String,
    val askOn: String?,
) {
    fun toModel() = Wish(
        id, title, url, photo, price, targetPrice, saved, priority, notes, forPersonId, occasionDate?.let(LocalDate::parse),
        runCatching { WishStatus.valueOf(status) }.getOrDefault(WishStatus.WANTED), LocalDate.parse(addedOn), askOn?.let(LocalDate::parse),
    )

    companion object {
        fun from(w: Wish) = WishEntity(
            w.id, w.title, w.url, w.photo, w.price, w.targetPrice, w.saved, w.priority, w.notes, w.forPersonId,
            w.occasionDate?.toString(), w.status.name, w.addedOn.toString(), w.askOn?.toString(),
        )
    }
}

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val archived: Boolean = false,
)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_lists ORDER BY sortOrder, id") fun observeLists(): Flow<List<ShoppingListEntity>>
    @Query("SELECT * FROM shopping_lists ORDER BY sortOrder, id") suspend fun lists(): List<ShoppingListEntity>
    @Query("SELECT * FROM shopping_lists WHERE id = :id") suspend fun list(id: Long): ShoppingListEntity?
    @Upsert suspend fun upsertList(l: ShoppingListEntity): Long
    @Query("DELETE FROM shopping_lists WHERE id = :id") suspend fun deleteList(id: Long)

    @Query("SELECT * FROM shopping_items WHERE listId = :listId") fun observeItems(listId: Long): Flow<List<ShoppingItemEntity>>
    @Query("SELECT * FROM shopping_items WHERE listId = :listId") suspend fun items(listId: Long): List<ShoppingItemEntity>
    @Query("SELECT * FROM shopping_items") suspend fun allItems(): List<ShoppingItemEntity>
    @Query("SELECT * FROM shopping_items WHERE id = :id") suspend fun item(id: Long): ShoppingItemEntity?
    @Upsert suspend fun upsertItem(i: ShoppingItemEntity): Long
    @Query("DELETE FROM shopping_items WHERE id = :id") suspend fun deleteItem(id: Long)
    @Query("DELETE FROM shopping_items WHERE listId = :listId AND checked = 1 AND everyDays IS NULL AND timesBought = 0") suspend fun clearChecked(listId: Long)
    @Query("DELETE FROM shopping_items WHERE listId = :listId") suspend fun deleteItemsOf(listId: Long)
}

@Dao
interface WishDao {
    @Query("SELECT * FROM wishes ORDER BY status, priority DESC, id DESC") fun observeAll(): Flow<List<WishEntity>>
    @Query("SELECT * FROM wishes") suspend fun all(): List<WishEntity>
    @Query("SELECT * FROM wishes WHERE id = :id") suspend fun get(id: Long): WishEntity?
    @Upsert suspend fun upsert(w: WishEntity): Long
    @Query("DELETE FROM wishes WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY id") fun observeAll(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects") suspend fun all(): List<ProjectEntity>
    @Upsert suspend fun upsert(p: ProjectEntity): Long
    @Query("DELETE FROM projects WHERE id = :id") suspend fun delete(id: Long)
}
