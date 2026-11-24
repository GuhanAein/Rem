package app.rem.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ReminderEntity::class, LogEntity::class, FiredEntity::class, PersonEntity::class, PlaceEntity::class,
        ShoppingListEntity::class, ShoppingItemEntity::class, WishEntity::class, ProjectEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class RemDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun log(): LogDao
    abstract fun fired(): FiredDao
    abstract fun people(): PersonDao
    abstract fun places(): PlaceDao
    abstract fun shopping(): ShoppingDao
    abstract fun wishes(): WishDao
    abstract fun projects(): ProjectDao

    companion object {
        /** v1 -> v2: smarter reminders, logs, people and places. Existing reminders are kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "ALTER TABLE reminders ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'STANDARD'",
                    "ALTER TABLE reminders ADD COLUMN `window` TEXT",
                    "ALTER TABLE reminders ADD COLUMN `randomTimes` TEXT",
                    "ALTER TABLE reminders ADD COLUMN `trigger` TEXT",
                    "ALTER TABLE reminders ADD COLUMN `followUp` TEXT",
                    "ALTER TABLE reminders ADD COLUMN `firstStep` TEXT",
                    "ALTER TABLE reminders ADD COLUMN `isPrivate` INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE reminders ADD COLUMN `personId` INTEGER",
                    "ALTER TABLE reminders ADD COLUMN `extras` TEXT NOT NULL DEFAULT '{}'",
                    "CREATE TABLE IF NOT EXISTS `log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reminderId` INTEGER NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `at` TEXT NOT NULL, `type` TEXT NOT NULL, `minutes` INTEGER NOT NULL)",
                    "CREATE INDEX IF NOT EXISTS `index_log_reminderId` ON `log` (`reminderId`)",
                    "CREATE INDEX IF NOT EXISTS `index_log_at` ON `log` (`at`)",
                    "CREATE TABLE IF NOT EXISTS `fired` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reminderId` INTEGER NOT NULL, `scheduledAt` INTEGER NOT NULL, `firedAt` INTEGER NOT NULL)",
                    "CREATE INDEX IF NOT EXISTS `index_fired_firedAt` ON `fired` (`firedAt`)",
                    "CREATE TABLE IF NOT EXISTS `people` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `cadenceDays` INTEGER, `lastContact` TEXT, `birthday` TEXT, `anniversary` TEXT, `giftIdeas` TEXT NOT NULL, `phone` TEXT)",
                    "CREATE TABLE IF NOT EXISTS `places` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `radiusMeters` REAL NOT NULL, `isHome` INTEGER NOT NULL)",
                ).forEach(db::execSQL)
            }
        }

        /** v2 -> v3: Shopping, Wishlist and Work spaces. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "ALTER TABLE reminders ADD COLUMN `space` TEXT NOT NULL DEFAULT 'PERSONAL'",
                    "ALTER TABLE reminders ADD COLUMN `projectId` INTEGER",
                    "ALTER TABLE reminders ADD COLUMN `workStatus` TEXT",
                    "ALTER TABLE reminders ADD COLUMN `waitingOn` TEXT",
                    "CREATE TABLE IF NOT EXISTS `shopping_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `remindWhenLong` INTEGER NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS `shopping_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `listId` INTEGER NOT NULL, `name` TEXT NOT NULL, `quantity` REAL, `unit` TEXT, `aisle` TEXT NOT NULL, `price` REAL, `note` TEXT NOT NULL, `checked` INTEGER NOT NULL, `everyDays` INTEGER, `lastBought` TEXT, `runningLow` INTEGER NOT NULL, `timesBought` INTEGER NOT NULL)",
                    "CREATE INDEX IF NOT EXISTS `index_shopping_items_listId` ON `shopping_items` (`listId`)",
                    "CREATE TABLE IF NOT EXISTS `wishes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT, `photo` TEXT, `price` REAL, `targetPrice` REAL, `saved` REAL NOT NULL, `priority` INTEGER NOT NULL, `notes` TEXT NOT NULL, `forPersonId` INTEGER, `occasionDate` TEXT, `status` TEXT NOT NULL, `addedOn` TEXT NOT NULL, `askOn` TEXT)",
                    "CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `archived` INTEGER NOT NULL)",
                ).forEach(db::execSQL)
            }
        }

        fun create(context: Context): RemDatabase =
            Room.databaseBuilder(context, RemDatabase::class.java, "rem.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        fun inMemory(context: Context): RemDatabase =
            Room.inMemoryDatabaseBuilder(context, RemDatabase::class.java).allowMainThreadQueries().build()
    }
}
