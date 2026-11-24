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
}
