package app.rem.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import app.rem.core.model.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RemDatabase::class.java)

    @Test fun v1RemindersSurviveUpgradeToV2() {
        helper.createDatabase("migrate.db", 1).apply {
            execSQL(
                "INSERT INTO reminders (id, title, notes, category, priority, dueAt, recurrence, alertStyle, alertOffsets, nagMinutes, amount, status, snoozedUntil, occurrenceCount, createdAt, completedAt) " +
                    "VALUES (1, 'Pay rent', '', 'BILL', 'HIGH', '2026-12-01T09:00:00', 'FREQ=MONTHLY;BYMONTHDAY=1', 'NOTIFICATION', '1440,0', NULL, '₹25,000', 'ACTIVE', NULL, 0, 0, NULL)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migrate.db", 2, true, RemDatabase.MIGRATION_1_2)
        db.query("SELECT title, kind, extras, isPrivate FROM reminders WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Pay rent", c.getString(0))
            assertEquals(Kind.STANDARD.name, c.getString(1))
            assertEquals("{}", c.getString(2))
            assertEquals(0, c.getInt(3))
        }
    }

    @Test fun v2UpgradesToV3WithSpaces() {
        helper.createDatabase("migrate3.db", 2).apply {
            execSQL(
                "INSERT INTO reminders (id, title, notes, category, priority, dueAt, recurrence, alertStyle, alertOffsets, nagMinutes, amount, status, snoozedUntil, occurrenceCount, createdAt, completedAt, kind, isPrivate, extras) " +
                    "VALUES (1, 'Standup', '', 'WORK', 'NONE', '2026-12-01T10:00:00', NULL, 'NOTIFICATION', '0', NULL, NULL, 'ACTIVE', NULL, 0, 0, NULL, 'STANDARD', 0, '{}')",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migrate3.db", 3, true, RemDatabase.MIGRATION_2_3)
        db.query("SELECT title, space FROM reminders WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Standup", c.getString(0))
            assertEquals("PERSONAL", c.getString(1))
        }
        db.execSQL("INSERT INTO shopping_lists (name, sortOrder, remindWhenLong) VALUES ('Groceries', 0, 1)")
        db.execSQL("INSERT INTO wishes (title, saved, priority, notes, status, addedOn) VALUES ('Headphones', 0, 0, '', 'WANTED', '2026-11-24')")
    }
}
