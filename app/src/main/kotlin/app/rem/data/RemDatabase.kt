package app.rem.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ReminderEntity::class], version = 1, exportSchema = true)
abstract class RemDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao

    companion object {
        fun create(context: Context): RemDatabase =
            Room.databaseBuilder(context, RemDatabase::class.java, "rem.db").build()

        fun inMemory(context: Context): RemDatabase =
            Room.inMemoryDatabaseBuilder(context, RemDatabase::class.java).allowMainThreadQueries().build()
    }
}
