package dev.devicecontrolcenter

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real on-device verification of the complete Room migration chain.
 *
 * This is intentionally an instrumentation gate: Room's migration helper and the
 * generated database implementation must run on Android SQLite. JVM tests cannot
 * prove that v1 data survives the real schema upgrade.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotHistoryMigrationTest {
    private val databaseName = "migration-${UUID.randomUUID()}.db"

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SnapshotHistoryDatabase::class.java,
    )

    @Before
    fun before() {
        context().deleteDatabase(databaseName)
    }

    @After
    fun after() {
        context().deleteDatabase(databaseName)
    }

    @Test
    fun migrateV1ToCurrentPreservesSnapshotAndCreatesEveryNewTable() {
        // The test applies the authoritative production Migration objects directly so it can
        // validate the complete chain and the retained v1 row on real Android SQLite. The
        // exported schema is still generated for Room tooling and future migration tests.
        migrationTestHelper.createDatabase(databaseName, 1).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS snapshot_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "capturedAtMillis INTEGER NOT NULL, " +
                    "availableMemoryBytes INTEGER NOT NULL, " +
                    "isLowMemory INTEGER NOT NULL, " +
                    "thermalStatus INTEGER NOT NULL, " +
                    "thermalHeadroom REAL, " +
                    "availableStorageBytes INTEGER NOT NULL, " +
                    "batteryLevelPercent INTEGER, " +
                    "batteryTemperatureCelsius REAL, " +
                    "cpuActivityPercent REAL" +
                    ")",
            )
            database.execSQL(
                "INSERT INTO snapshot_history (" +
                    "capturedAtMillis, availableMemoryBytes, isLowMemory, thermalStatus, " +
                    "thermalHeadroom, availableStorageBytes, batteryLevelPercent, " +
                    "batteryTemperatureCelsius, cpuActivityPercent" +
                    ") VALUES (1700000000000, 123456789, 0, 2, 0.75, 987654321, 73, 31.5, 12.5)",
            )
            var expectedStartVersion = 1
            productionMigrations().forEach { migration ->
                assertEquals(expectedStartVersion, migration.startVersion)
                migration.migrate(database)
                expectedStartVersion = migration.endVersion
            }
            assertEquals(SnapshotHistoryDatabase.CURRENT_SCHEMA_VERSION, expectedStartVersion)
            database.execSQL("PRAGMA user_version = ${SnapshotHistoryDatabase.CURRENT_SCHEMA_VERSION}")

            database.query(
                "SELECT capturedAtMillis, availableMemoryBytes, isLowMemory, " +
                    "batteryLevelPercent, captureId FROM snapshot_history",
            ).use { cursor ->
                assertTrue("The v1 snapshot must survive migration", cursor.moveToFirst())
                assertEquals(1700000000000L, cursor.getLong(0))
                assertEquals(123456789L, cursor.getLong(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(73, cursor.getInt(3))
                assertTrue("New v5/v6 field must remain nullable for v1 rows", cursor.isNull(4))
            }

            assertTableExists(database, "battery_samples")
            assertTableExists(database, "network_samples")
            assertTableExists(database, "action_log")
            assertTableExists(database, "history_metadata")
            assertTableExists(database, "app_usage_history")

            assertColumns(
                database,
                "battery_samples",
                "plugged",
                "voltageMillivolts",
                "energyCounterNanowattHours",
                "captureId",
            )
            assertColumns(database, "snapshot_history", "captureId")
            assertColumns(database, "network_samples", "captureId")
            assertColumns(database, "action_log", "captureId")
            assertColumns(
                database,
                "app_usage_history",
                "packageName",
                "usageAvailability",
                "storageAvailability",
                "networkAvailability",
            )

            database.query(
                "SELECT lastTelemetryClearedAtMillis FROM history_metadata WHERE id = 1",
            ).use { cursor ->
                assertTrue("Migration must seed history metadata", cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }
        }
    }

    /**
     * The production companion owns the authoritative migration objects. Keeping this access
     * test-only avoids duplicating SQL in the test and avoids changing production Kotlin solely
     * to expose a verification hook. A renamed/private migration collection must fail loudly.
     */
    @Suppress("UNCHECKED_CAST")
    private fun productionMigrations(): Array<Migration> {
        val field = SnapshotHistoryDatabase.Companion::class.java.getDeclaredField("MIGRATIONS")
        field.isAccessible = true
        return field.get(SnapshotHistoryDatabase.Companion) as? Array<Migration>
            ?: error("SnapshotHistoryDatabase.Companion.MIGRATIONS is not an Array<Migration>")
    }

    private fun assertTableExists(database: SupportSQLiteDatabase, tableName: String) {
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName),
        ).use { cursor ->
            assertTrue("Expected migrated table $tableName", cursor.moveToFirst())
        }
    }

    private fun assertColumns(
        database: SupportSQLiteDatabase,
        tableName: String,
        vararg expectedColumns: String,
    ) {
        val actualColumns = buildSet {
            database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                if (cursor.moveToFirst()) {
                    do add(cursor.getString(nameColumn)) while (cursor.moveToNext())
                }
            }
        }
        expectedColumns.forEach { expected ->
            assertTrue("Expected column $tableName.$expected", expected in actualColumns)
        }
    }

    private fun context() =
        InstrumentationRegistry.getInstrumentation().targetContext
}
