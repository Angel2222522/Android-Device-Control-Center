package dev.devicecontrolcenter

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale
import java.util.UUID

const val SNAPSHOT_HISTORY_LIMIT = 120
const val APP_USAGE_HISTORY_PER_PACKAGE_LIMIT = 120
const val APP_USAGE_HISTORY_LIMIT = 4_096

@Entity(tableName = "snapshot_history")
data class SnapshotHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val capturedAtMillis: Long,
    val availableMemoryBytes: Long,
    val isLowMemory: Boolean,
    val thermalStatus: Int,
    val thermalHeadroom: Float?,
    val availableStorageBytes: Long,
    val batteryLevelPercent: Int?,
    val batteryTemperatureCelsius: Double?,
    val cpuActivityPercent: Double?,
    val captureId: String? = null,
)

@Entity(tableName = "battery_samples")
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val capturedAtMillis: Long,
    val levelPercent: Int?,
    val status: Int?,
    val temperatureCelsius: Double?,
    val currentNowMicroamps: Int?,
    val chargeCounterMicroampHours: Int?,
    val plugged: Int? = null,
    val voltageMillivolts: Int? = null,
    val energyCounterNanowattHours: Long? = null,
    val captureId: String? = null,
)

@Entity(tableName = "network_samples")
data class NetworkSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val capturedAtMillis: Long,
    val periodStartMillis: Long,
    val wifiReceivedBytes: Long?,
    val wifiSentBytes: Long?,
    val mobileReceivedBytes: Long?,
    val mobileSentBytes: Long?,
    val source: String,
    val captureId: String? = null,
)

@Entity(tableName = "action_log")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val createdAtMillis: Long,
    val action: String,
    val result: String,
    val details: String?,
    val captureId: String? = null,
)

@Entity(tableName = "history_metadata")
data class HistoryMetadataEntity(
    @PrimaryKey
    val id: Int = 1,
    val lastTelemetryClearedAtMillis: Long = 0L,
)

/**
 * Local, bounded history for the fields already exposed by the App Center.
 * Null metric values are intentional: the corresponding Android API did not
 * provide a trustworthy value for that sample.
 */
@Entity(
    tableName = "app_usage_history",
    indices = [
        Index(value = ["packageName", "capturedAtMillis"]),
        Index(value = ["capturedAtMillis"]),
    ],
)
data class AppUsageHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val capturedAtMillis: Long,
    val lastUsedAtMillis: Long?,
    val foregroundMillis: Long?,
    val apkBytes: Long?,
    val dataBytes: Long?,
    val cacheBytes: Long?,
    val wifiBytes: Long?,
    val mobileBytes: Long?,
    val usageAvailability: String,
    val storageAvailability: String,
    val networkAvailability: String,
)

@Dao
interface SnapshotHistoryDao {
    @Insert
    fun insert(snapshot: SnapshotHistoryEntity)

    @Query(
        "SELECT * FROM snapshot_history " +
            "ORDER BY capturedAtMillis DESC, id DESC LIMIT :limit",
    )
    fun recent(limit: Int): List<SnapshotHistoryEntity>

    @Query(
        "DELETE FROM snapshot_history " +
            "WHERE id NOT IN (" +
            "SELECT id FROM snapshot_history " +
            "ORDER BY capturedAtMillis DESC, id DESC LIMIT :keep" +
            ")",
    )
    fun trimTo(keep: Int)

    @Query("DELETE FROM snapshot_history")
    fun deleteAll()
}

@Dao
interface BatterySampleDao {
    @Insert
    fun insert(sample: BatterySampleEntity)

    @Query("SELECT * FROM battery_samples ORDER BY capturedAtMillis DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): List<BatterySampleEntity>

    @Query(
        "DELETE FROM battery_samples WHERE id NOT IN " +
            "(SELECT id FROM battery_samples ORDER BY capturedAtMillis DESC, id DESC LIMIT :keep)",
    )
    fun trimTo(keep: Int)

    @Query("DELETE FROM battery_samples")
    fun deleteAll()
}

@Dao
interface NetworkSampleDao {
    @Insert
    fun insert(sample: NetworkSampleEntity)

    @Query("SELECT * FROM network_samples ORDER BY capturedAtMillis DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): List<NetworkSampleEntity>

    @Query(
        "DELETE FROM network_samples WHERE id NOT IN " +
            "(SELECT id FROM network_samples ORDER BY capturedAtMillis DESC, id DESC LIMIT :keep)",
    )
    fun trimTo(keep: Int)

    @Query("DELETE FROM network_samples")
    fun deleteAll()
}

@Dao
interface ActionLogDao {
    @Insert
    fun insert(entry: ActionLogEntity)

    @Query("SELECT * FROM action_log ORDER BY createdAtMillis DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): List<ActionLogEntity>

    @Query(
        "DELETE FROM action_log WHERE id NOT IN " +
            "(SELECT id FROM action_log ORDER BY createdAtMillis DESC, id DESC LIMIT :keep)",
    )
    fun trimTo(keep: Int)

    @Query("DELETE FROM action_log")
    fun deleteAll()
}

@Dao
interface HistoryMetadataDao {
    @Query("SELECT * FROM history_metadata WHERE id = 1 LIMIT 1")
    fun get(): HistoryMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(metadata: HistoryMetadataEntity)
}

@Dao
interface AppUsageHistoryDao {
    @Insert
    fun insert(sample: AppUsageHistoryEntity): Long

    @Insert
    fun insertAll(samples: List<AppUsageHistoryEntity>)

    @Query(
        "SELECT * FROM app_usage_history " +
            "WHERE packageName = :packageName " +
            "ORDER BY capturedAtMillis DESC, id DESC LIMIT :limit",
    )
    fun recentForPackage(packageName: String, limit: Int): List<AppUsageHistoryEntity>

    @Query(
        "SELECT * FROM app_usage_history " +
            "ORDER BY capturedAtMillis DESC, id DESC LIMIT :limit",
    )
    fun recent(limit: Int): List<AppUsageHistoryEntity>

    @Query(
        "DELETE FROM app_usage_history WHERE id NOT IN " +
            "(SELECT id FROM app_usage_history " +
            "ORDER BY capturedAtMillis DESC, id DESC LIMIT :keep)",
    )
    fun trimTo(keep: Int)

    @Query(
        "DELETE FROM app_usage_history WHERE packageName = :packageName AND id NOT IN " +
            "(SELECT id FROM app_usage_history WHERE packageName = :packageName " +
            "ORDER BY capturedAtMillis DESC, id DESC LIMIT :keep)",
    )
    fun trimPackageTo(packageName: String, keep: Int)

    @Query("DELETE FROM app_usage_history")
    fun deleteAll()
}

@Database(
    entities = [
        SnapshotHistoryEntity::class,
        BatterySampleEntity::class,
        NetworkSampleEntity::class,
        ActionLogEntity::class,
        HistoryMetadataEntity::class,
        AppUsageHistoryEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class SnapshotHistoryDatabase : RoomDatabase() {
    abstract fun snapshotHistoryDao(): SnapshotHistoryDao
    abstract fun batterySampleDao(): BatterySampleDao
    abstract fun networkSampleDao(): NetworkSampleDao
    abstract fun actionLogDao(): ActionLogDao
    abstract fun historyMetadataDao(): HistoryMetadataDao
    abstract fun appUsageHistoryDao(): AppUsageHistoryDao

    companion object {
        private const val DATABASE_NAME = "device_control_center_history.db"
        internal const val CURRENT_SCHEMA_VERSION = 8
        internal val MIGRATION_VERSION_CHAIN = listOf(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8)

        @Volatile
        private var instance: SnapshotHistoryDatabase? = null

        fun get(context: Context): SnapshotHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SnapshotHistoryDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(*MIGRATIONS.toTypedArray())
                .build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS battery_samples (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "capturedAtMillis INTEGER NOT NULL, " +
                        "levelPercent INTEGER, status INTEGER, temperatureCelsius REAL, " +
                        "currentNowMicroamps INTEGER, chargeCounterMicroampHours INTEGER)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS network_samples (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "capturedAtMillis INTEGER NOT NULL, periodStartMillis INTEGER NOT NULL, " +
                        "wifiReceivedBytes INTEGER, wifiSentBytes INTEGER, " +
                        "mobileReceivedBytes INTEGER, mobileSentBytes INTEGER, source TEXT NOT NULL)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS action_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "createdAtMillis INTEGER NOT NULL, action TEXT NOT NULL, " +
                        "result TEXT NOT NULL, details TEXT)",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE battery_samples ADD COLUMN plugged INTEGER")
                database.execSQL("ALTER TABLE battery_samples ADD COLUMN voltageMillivolts INTEGER")
                database.execSQL("ALTER TABLE battery_samples ADD COLUMN energyCounterNanowattHours INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE snapshot_history ADD COLUMN captureId TEXT")
                database.execSQL("ALTER TABLE battery_samples ADD COLUMN captureId TEXT")
                database.execSQL("ALTER TABLE network_samples ADD COLUMN captureId TEXT")
                database.execSQL("ALTER TABLE action_log ADD COLUMN captureId TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS history_metadata (" +
                        "id INTEGER NOT NULL, " +
                        "lastTelemetryClearedAtMillis INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))",
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO history_metadata (id, lastTelemetryClearedAtMillis) VALUES (1, 0)",
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS app_usage_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "packageName TEXT NOT NULL, " +
                        "capturedAtMillis INTEGER NOT NULL, " +
                        "lastUsedAtMillis INTEGER, foregroundMillis INTEGER, " +
                        "apkBytes INTEGER, dataBytes INTEGER, cacheBytes INTEGER, " +
                        "wifiBytes INTEGER, mobileBytes INTEGER, " +
                        "usageAvailability TEXT NOT NULL, " +
                        "storageAvailability TEXT NOT NULL, " +
                        "networkAvailability TEXT NOT NULL)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_app_usage_history_packageName_capturedAtMillis " +
                        "ON app_usage_history(packageName, capturedAtMillis)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_app_usage_history_capturedAtMillis " +
                        "ON app_usage_history(capturedAtMillis)",
                )
            }
        }

        private val MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
        )
    }
}

enum class HistoryWriteStatus {
    RECORDED,
    SKIPPED_BEFORE_CLEAR,
}

data class HistoryWriteResult(
    val status: HistoryWriteStatus,
    val captureId: String?,
    val actionRecorded: Boolean,
)

enum class HistoryClearScope {
    TELEMETRY_ONLY,
    ALL_LOCAL_HISTORY,
}

data class HistoryClearResult(
    val scope: HistoryClearScope,
    val clearedAtMillis: Long,
    val actionLogRetained: Boolean,
)

class SnapshotHistoryRepository(context: Context) {
    private val database = SnapshotHistoryDatabase.get(context)
    private val dao = database.snapshotHistoryDao()
    private val batteryDao = database.batterySampleDao()
    private val networkDao = database.networkSampleDao()
    private val actionDao = database.actionLogDao()
    private val metadataDao = database.historyMetadataDao()
    private val appUsageHistoryDao = database.appUsageHistoryDao()

    /**
     * Stores telemetry as one linked capture. For a capture that also needs an audit entry,
     * callers should use [recordWithAction] so both writes share one transaction.
     */
    fun record(snapshot: DeviceSnapshot, capturedAtMillis: Long): HistoryWriteResult =
        database.runInTransaction {
            writeTelemetry(snapshot, capturedAtMillis, UUID.randomUUID().toString())
        }

    fun recent(): List<SnapshotHistoryEntity> = dao.recent(SNAPSHOT_HISTORY_LIMIT)

    fun recentBattery(): List<BatterySampleEntity> = batteryDao.recent(SNAPSHOT_HISTORY_LIMIT)

    fun recentNetwork(): List<NetworkSampleEntity> = networkDao.recent(SNAPSHOT_HISTORY_LIMIT)

    fun recentActions(): List<ActionLogEntity> = actionDao.recent(SNAPSHOT_HISTORY_LIMIT)

    /** Records one App Center sample and keeps the global history bounded. */
    fun recordAppUsage(sample: AppUsageHistorySample): AppUsageHistoryEntity? {
        var recordedEntity: AppUsageHistoryEntity? = null
        database.runInTransaction(Runnable {
            val entity = sample.toEntity()
            if (entity.capturedAtMillis >= (metadataDao.get()?.lastTelemetryClearedAtMillis ?: 0L)) {
                val insertedId = appUsageHistoryDao.insert(entity)
                appUsageHistoryDao.trimPackageTo(
                    packageName = entity.packageName,
                    keep = APP_USAGE_HISTORY_PER_PACKAGE_LIMIT,
                )
                appUsageHistoryDao.trimTo(APP_USAGE_HISTORY_LIMIT)
                recordedEntity = entity.copy(id = insertedId)
            }
        })
        return recordedEntity
    }

    /** Convenience API for a single App Center detail page. */
    fun recordAppUsage(
        app: AppRecord,
        capturedAtMillis: Long,
        hasUsageAccess: Boolean,
    ): AppUsageHistoryEntity? = recordAppUsage(
        app.toAppUsageHistorySample(capturedAtMillis, hasUsageAccess),
    )

    /** Convenience API for a completed App Center inventory refresh. */
    fun recordAppUsage(
        catalog: AppCatalogResult,
        capturedAtMillis: Long? = null,
    ): Int = recordAppUsage(catalog.toAppUsageHistorySamples(capturedAtMillis))

    /** Records one sample per visible package in one transaction. */
    fun recordAppUsage(samples: List<AppUsageHistorySample>): Int =
        database.runInTransaction {
            val lastClearedAtMillis = metadataDao.get()?.lastTelemetryClearedAtMillis ?: 0L
            val entities = samples
                .asSequence()
                .filter { it.packageName.isNotBlank() }
                .map(AppUsageHistorySample::toEntity)
                .filter { it.capturedAtMillis >= lastClearedAtMillis }
                .toList()
            if (entities.isNotEmpty()) {
                appUsageHistoryDao.insertAll(entities)
                entities.asSequence()
                    .map(AppUsageHistoryEntity::packageName)
                    .distinct()
                    .forEach { packageName ->
                        appUsageHistoryDao.trimPackageTo(
                            packageName = packageName,
                            keep = APP_USAGE_HISTORY_PER_PACKAGE_LIMIT,
                        )
                    }
                appUsageHistoryDao.trimTo(APP_USAGE_HISTORY_LIMIT)
            }
            entities.size
        }

    fun recentAppUsage(
        packageName: String,
        limit: Int = APP_USAGE_HISTORY_PER_PACKAGE_LIMIT,
    ): List<AppUsageHistorySample> =
        appUsageHistoryDao.recentForPackage(packageName, limit.boundedAppUsagePackageLimit())
            .map(AppUsageHistoryEntity::toDomain)

    fun recentAppUsage(limit: Int = APP_USAGE_HISTORY_LIMIT): List<AppUsageHistorySample> =
        appUsageHistoryDao.recent(limit.boundedAppUsageLimit())
            .map(AppUsageHistoryEntity::toDomain)

    fun trimAppUsageTo(keep: Int = APP_USAGE_HISTORY_LIMIT) {
        appUsageHistoryDao.trimTo(keep.boundedAppUsageLimit())
    }

    fun trimAppUsageForPackageTo(
        packageName: String,
        keep: Int = APP_USAGE_HISTORY_PER_PACKAGE_LIMIT,
    ) {
        appUsageHistoryDao.trimPackageTo(packageName, keep.boundedAppUsagePackageLimit())
    }

    /** Reads all history tables at one SQLite transaction boundary. */
    fun readHistory(): HistoryReadResult = database.runInTransaction {
        HistoryReadResult(
            snapshots = dao.recent(SNAPSHOT_HISTORY_LIMIT),
            battery = batteryDao.recent(SNAPSHOT_HISTORY_LIMIT),
            network = networkDao.recent(SNAPSHOT_HISTORY_LIMIT),
            actions = actionDao.recent(SNAPSHOT_HISTORY_LIMIT),
        )
    }

    /**
     * Atomically stores telemetry and its audit entry. If the capture predates a clear that
     * already committed, neither telemetry nor a success action is written.
     */
    fun recordWithAction(
        snapshot: DeviceSnapshot,
        capturedAtMillis: Long,
        action: String,
        result: String,
        details: String? = null,
        createdAtMillis: Long = capturedAtMillis,
    ): HistoryWriteResult = database.runInTransaction {
        val captureId = UUID.randomUUID().toString()
        val writeResult = writeTelemetry(snapshot, capturedAtMillis, captureId)
        if (writeResult.status == HistoryWriteStatus.RECORDED) {
            actionDao.insert(
                ActionLogEntity(
                    createdAtMillis = createdAtMillis,
                    action = action,
                    result = result,
                    details = details,
                    captureId = captureId,
                ),
            )
            actionDao.trimTo(SNAPSHOT_HISTORY_LIMIT)
            writeResult.copy(actionRecorded = true)
        } else {
            writeResult
        }
    }

    fun recordAction(
        action: String,
        result: String,
        details: String? = null,
        createdAtMillis: Long = System.currentTimeMillis(),
        captureId: String? = null,
    ) {
        database.runInTransaction {
            actionDao.insert(
                ActionLogEntity(
                    createdAtMillis = createdAtMillis,
                    action = action,
                    result = result,
                    details = details,
                    captureId = captureId,
                ),
            )
            actionDao.trimTo(SNAPSHOT_HISTORY_LIMIT)
        }
    }

    /**
     * Deletes measurements only. The audit log is intentionally retained and is reported by
     * the return value; this is not a "delete all local history" operation.
     */
    fun clearTelemetryHistory(clearedAtMillis: Long = System.currentTimeMillis()): HistoryClearResult =
        database.runInTransaction {
            dao.deleteAll()
            batteryDao.deleteAll()
            networkDao.deleteAll()
            appUsageHistoryDao.deleteAll()
            val effectiveClearTime = maxOf(clearedAtMillis, metadataDao.get()?.lastTelemetryClearedAtMillis ?: 0L)
            metadataDao.upsert(HistoryMetadataEntity(lastTelemetryClearedAtMillis = effectiveClearTime))
            HistoryClearResult(
                scope = HistoryClearScope.TELEMETRY_ONLY,
                clearedAtMillis = effectiveClearTime,
                actionLogRetained = true,
            )
        }

    /** Deletes telemetry and audit entries together; no audit row is written for this operation. */
    fun clearAllHistory(clearedAtMillis: Long = System.currentTimeMillis()): HistoryClearResult =
        database.runInTransaction {
            dao.deleteAll()
            batteryDao.deleteAll()
            networkDao.deleteAll()
            appUsageHistoryDao.deleteAll()
            actionDao.deleteAll()
            val effectiveClearTime = maxOf(clearedAtMillis, metadataDao.get()?.lastTelemetryClearedAtMillis ?: 0L)
            metadataDao.upsert(HistoryMetadataEntity(lastTelemetryClearedAtMillis = effectiveClearTime))
            HistoryClearResult(
                scope = HistoryClearScope.ALL_LOCAL_HISTORY,
                clearedAtMillis = effectiveClearTime,
                actionLogRetained = false,
            )
        }

    /** Atomically clears telemetry and records the corresponding audit entry. */
    fun clearTelemetryHistoryWithAction(
        clearedAtMillis: Long = System.currentTimeMillis(),
        details: String? = null,
    ): HistoryClearResult = database.runInTransaction {
        val result = clearTelemetryHistoryInternal(clearedAtMillis)
        actionDao.insert(
            ActionLogEntity(
                createdAtMillis = clearedAtMillis,
                action = "history_clear",
                result = "success",
                details = details,
            ),
        )
        actionDao.trimTo(SNAPSHOT_HISTORY_LIMIT)
        result
    }

    private fun writeTelemetry(
        snapshot: DeviceSnapshot,
        capturedAtMillis: Long,
        captureId: String,
    ): HistoryWriteResult {
        val lastClearedAtMillis = metadataDao.get()?.lastTelemetryClearedAtMillis ?: 0L
        if (capturedAtMillis < lastClearedAtMillis) {
            return HistoryWriteResult(
                status = HistoryWriteStatus.SKIPPED_BEFORE_CLEAR,
                captureId = null,
                actionRecorded = false,
            )
        }
        dao.insert(snapshot.toHistoryEntity(capturedAtMillis, captureId))
        batteryDao.insert(snapshot.toBatterySample(capturedAtMillis, captureId))
        networkDao.insert(snapshot.toNetworkSample(capturedAtMillis, captureId))
        dao.trimTo(SNAPSHOT_HISTORY_LIMIT)
        batteryDao.trimTo(SNAPSHOT_HISTORY_LIMIT)
        networkDao.trimTo(SNAPSHOT_HISTORY_LIMIT)
        return HistoryWriteResult(
            status = HistoryWriteStatus.RECORDED,
            captureId = captureId,
            actionRecorded = false,
        )
    }

    private fun clearTelemetryHistoryInternal(clearedAtMillis: Long): HistoryClearResult {
        dao.deleteAll()
        batteryDao.deleteAll()
        networkDao.deleteAll()
        appUsageHistoryDao.deleteAll()
        val effectiveClearTime = maxOf(clearedAtMillis, metadataDao.get()?.lastTelemetryClearedAtMillis ?: 0L)
        metadataDao.upsert(HistoryMetadataEntity(lastTelemetryClearedAtMillis = effectiveClearTime))
        return HistoryClearResult(
            scope = HistoryClearScope.TELEMETRY_ONLY,
            clearedAtMillis = effectiveClearTime,
            actionLogRetained = true,
        )
    }
}

data class HistoryReadResult(
    val snapshots: List<SnapshotHistoryEntity>,
    val battery: List<BatterySampleEntity>,
    val network: List<NetworkSampleEntity>,
    val actions: List<ActionLogEntity>,
)

enum class AppUsageMetricAvailability {
    AVAILABLE,
    PARTIAL,
    UNAVAILABLE_USAGE_ACCESS,
    UNAVAILABLE_SHARED_UID,
    UNAVAILABLE_API,
    NOT_COLLECTED,
}

data class AppUsageHistorySample(
    val packageName: String,
    val capturedAtMillis: Long,
    val lastUsedAtMillis: Long?,
    val foregroundMillis: Long?,
    val apkBytes: Long?,
    val dataBytes: Long?,
    val cacheBytes: Long?,
    val wifiBytes: Long?,
    val mobileBytes: Long?,
    val usageAvailability: AppUsageMetricAvailability,
    val storageAvailability: AppUsageMetricAvailability,
    val networkAvailability: AppUsageMetricAvailability,
)

fun AppUsageHistorySample.toEntity(): AppUsageHistoryEntity = AppUsageHistoryEntity(
    packageName = packageName,
    capturedAtMillis = capturedAtMillis,
    lastUsedAtMillis = lastUsedAtMillis,
    foregroundMillis = foregroundMillis,
    apkBytes = apkBytes,
    dataBytes = dataBytes,
    cacheBytes = cacheBytes,
    wifiBytes = wifiBytes,
    mobileBytes = mobileBytes,
    usageAvailability = usageAvailability.name,
    storageAvailability = storageAvailability.name,
    networkAvailability = networkAvailability.name,
)

fun AppUsageHistoryEntity.toDomain(): AppUsageHistorySample = AppUsageHistorySample(
    packageName = packageName,
    capturedAtMillis = capturedAtMillis,
    lastUsedAtMillis = lastUsedAtMillis,
    foregroundMillis = foregroundMillis,
    apkBytes = apkBytes,
    dataBytes = dataBytes,
    cacheBytes = cacheBytes,
    wifiBytes = wifiBytes,
    mobileBytes = mobileBytes,
    usageAvailability = availabilityOrNotCollected(usageAvailability),
    storageAvailability = availabilityOrNotCollected(storageAvailability),
    networkAvailability = availabilityOrNotCollected(networkAvailability),
)

private fun availabilityOrNotCollected(value: String): AppUsageMetricAvailability =
    runCatching { AppUsageMetricAvailability.valueOf(value) }
        .getOrDefault(AppUsageMetricAvailability.NOT_COLLECTED)

internal fun Int.boundedAppUsageLimit(): Int = coerceIn(1, APP_USAGE_HISTORY_LIMIT)

internal fun Int.boundedAppUsagePackageLimit(): Int =
    coerceIn(1, APP_USAGE_HISTORY_PER_PACKAGE_LIMIT)

/** Pure retention policy used by the repository and directly covered by JVM tests. */
internal object AppUsageHistoryRetention {
    fun packageNames(samples: List<AppUsageHistorySample>): List<String> =
        samples.asSequence()
            .map(AppUsageHistorySample::packageName)
            .filter(String::isNotBlank)
            .distinct()
            .toList()

    fun latestPerPackage(
        samples: List<AppUsageHistorySample>,
        keep: Int = APP_USAGE_HISTORY_PER_PACKAGE_LIMIT,
    ): List<AppUsageHistorySample> = samples
        .groupBy(AppUsageHistorySample::packageName)
        .values
        .flatMap { packageSamples ->
            packageSamples.sortedByDescending(AppUsageHistorySample::capturedAtMillis)
                .take(keep.boundedAppUsagePackageLimit())
        }
}

fun DeviceSnapshot.toHistoryEntity(capturedAtMillis: Long, captureId: String? = null): SnapshotHistoryEntity = SnapshotHistoryEntity(
    capturedAtMillis = capturedAtMillis,
    availableMemoryBytes = availableMemoryBytes,
    isLowMemory = isLowMemory,
    thermalStatus = thermalStatus,
    thermalHeadroom = thermalHeadroom,
    availableStorageBytes = availableStorageBytes,
    batteryLevelPercent = battery.levelPercent,
    batteryTemperatureCelsius = battery.temperatureCelsius,
    cpuActivityPercent = cpu.activityPercent,
    captureId = captureId,
)

fun DeviceSnapshot.toBatterySample(capturedAtMillis: Long, captureId: String? = null): BatterySampleEntity = BatterySampleEntity(
    capturedAtMillis = capturedAtMillis,
    levelPercent = battery.levelPercent,
    status = battery.status,
    temperatureCelsius = battery.temperatureCelsius,
    currentNowMicroamps = battery.currentNowMicroamps,
    chargeCounterMicroampHours = battery.chargeCounterMicroampHours,
    plugged = battery.plugged,
    voltageMillivolts = battery.voltageMillivolts,
    energyCounterNanowattHours = battery.energyCounterNanowattHours,
    captureId = captureId,
)

fun DeviceSnapshot.toNetworkSample(capturedAtMillis: Long, captureId: String? = null): NetworkSampleEntity = NetworkSampleEntity(
    capturedAtMillis = capturedAtMillis,
    periodStartMillis = network.periodStartMillis,
    wifiReceivedBytes = network.wifiReceivedBytes,
    wifiSentBytes = network.wifiSentBytes,
    mobileReceivedBytes = network.mobileReceivedBytes,
    mobileSentBytes = network.mobileSentBytes,
    source = network.source.name,
    captureId = captureId,
)

data class HistoryUiState(
    val entries: List<SnapshotHistoryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class BatteryHistoryUiState(
    val entries: List<BatterySampleEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class NetworkHistoryUiState(
    val entries: List<NetworkSampleEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class ActionLogUiState(
    val entries: List<ActionLogEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

object SnapshotHistoryPresentation {
    fun summary(entries: List<SnapshotHistoryEntity>): String = when (entries.size) {
        0 -> "Δεν έχει καταγραφεί ακόμη τοπικό ιστορικό."
        1 -> "1 στιγμιότυπο αποθηκευμένο μόνο σε αυτή τη συσκευή."
        else -> "${entries.size} στιγμιότυπα αποθηκευμένα μόνο σε αυτή τη συσκευή."
    }

    fun memoryLabel(entry: SnapshotHistoryEntity): String =
        SnapshotPresentation.gib(entry.availableMemoryBytes)

    fun batteryLabel(entry: SnapshotHistoryEntity): String =
        entry.batteryLevelPercent?.let { "$it%" } ?: "Μη διαθέσιμη"

    fun thermalLabel(entry: SnapshotHistoryEntity): String =
        OverviewPresentation.thermalShortLabel(entry.thermalStatus)

    fun cpuLabel(entry: SnapshotHistoryEntity): String = entry.cpuActivityPercent?.let {
        String.format(Locale.ROOT, "%.1f%%", it)
    } ?: "Μη διαθέσιμη"
}

object BatteryHistoryPresentation {
    fun summary(entries: List<BatterySampleEntity>): String = when (entries.size) {
        0 -> "Δεν υπάρχει ακόμη ιστορικό φόρτισης και χρήσης."
        1 -> "1 τοπικό δείγμα · χρειάζονται περισσότερα για τάση."
        else -> "${entries.size} τοπικά δείγματα · η τάση είναι ενδεικτική, όχι διάγνωση υγείας."
    }

    fun temperature(entry: BatterySampleEntity): String = entry.temperatureCelsius?.let {
        String.format(Locale.ROOT, "%.1f °C", it)
    } ?: "Μη διαθέσιμη θερμοκρασία"

    fun current(entry: BatterySampleEntity): String = entry.currentNowMicroamps?.let {
        String.format(Locale.ROOT, "%d μA", it)
    } ?: "Μη διαθέσιμο ρεύμα"

    fun direction(entries: List<BatterySampleEntity>): String {
        val values = entries.asReversed().mapNotNull { it.levelPercent }
        if (values.size < 2) return "Ανεπαρκή δεδομένα για τάση"
        val delta = values.last() - values.first()
        return when {
            delta > 1 -> "Η στάθμη αυξήθηκε στα καταγεγραμμένα δείγματα"
            delta < -1 -> "Η στάθμη μειώθηκε στα καταγεγραμμένα δείγματα"
            else -> "Δεν διακρίνεται ουσιαστική μεταβολή"
        }
    }
}

data class BatteryHistoryAnalytics(
    val observedChargingMillis: Long = 0L,
    val chargingSamples: Int = 0,
    val equivalentFullCycles: Double? = null,
    val estimatedCapacityMah: Int? = null,
    val alerts: List<String> = emptyList(),
) {
    val observedChargingLabel: String
        get() {
            val minutes = observedChargingMillis / 60_000L
            return when {
                minutes < 1L -> "Κάτω από 1 λεπτό παρατήρησης"
                minutes < 60L -> "$minutes λεπτά παρατηρούμενης φόρτισης"
                else -> "${minutes / 60L} ώρ. ${minutes % 60L} λ. παρατηρούμενης φόρτισης"
            }
        }

    val cycleLabel: String
        get() = equivalentFullCycles?.let { String.format(Locale.ROOT, "≈ %.2f ισοδύναμοι κύκλοι", it) }
            ?: "Ανεπαρκή δεδομένα για κύκλους"

    val capacityLabel: String
        get() = estimatedCapacityMah?.let { "≈ $it mAh από fuel-gauge counter" }
            ?: "Δεν είναι διαθέσιμος αξιόπιστος counter χωρητικότητας"

    val wearLabel: String
        get() = "Φθορά: δεν υπολογίζεται χωρίς αξιόπιστη εργοστασιακή design capacity"
}

object BatteryHistoryAnalyticsCalculator {
    private const val MAX_INTERVAL_MILLIS = 6L * 60L * 60L * 1_000L

    fun calculate(entries: List<BatterySampleEntity>): BatteryHistoryAnalytics {
        if (entries.isEmpty()) return BatteryHistoryAnalytics()
        val chronological = entries.sortedBy(BatterySampleEntity::capturedAtMillis)
        var chargingMillis = 0L
        var chargingSamples = 0
        var levelDelta = 0.0
        chronological.zipWithNext().forEach { (previous, current) ->
            val interval = (current.capturedAtMillis - previous.capturedAtMillis)
                .coerceIn(0L, MAX_INTERVAL_MILLIS)
            if (isCharging(previous)) {
                chargingMillis += interval
                chargingSamples++
            }
            val previousLevel = previous.levelPercent
            val currentLevel = current.levelPercent
            if (previousLevel != null && currentLevel != null) {
                levelDelta += kotlin.math.abs(currentLevel - previousLevel).toDouble()
            }
        }
        val latest = chronological.last()
        val estimatedCapacityMah = latest.chargeCounterMicroampHours
            ?.takeIf { it > 0 && latest.levelPercent != null && latest.levelPercent > 0 }
            ?.let { counter -> (counter / 1_000.0 * 100.0 / latest.levelPercent!!).toInt() }
            ?.takeIf { it in 500..20_000 }
        val alerts = chronological
            .filter { (it.temperatureCelsius ?: Double.NEGATIVE_INFINITY) >= 45.0 }
            .takeLast(3)
            .map { "Υψηλή θερμοκρασία δείγματος: ${String.format(Locale.ROOT, "%.1f °C", it.temperatureCelsius)}" }
        return BatteryHistoryAnalytics(
            observedChargingMillis = chargingMillis,
            chargingSamples = chargingSamples,
            equivalentFullCycles = if (levelDelta > 0.0) levelDelta / 200.0 else null,
            estimatedCapacityMah = estimatedCapacityMah,
            alerts = alerts,
        )
    }

    private fun isCharging(entry: BatterySampleEntity): Boolean =
        entry.status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            entry.status == android.os.BatteryManager.BATTERY_STATUS_FULL ||
            (entry.plugged ?: 0) != 0
}

object NetworkHistoryPresentation {
    fun summary(entries: List<NetworkSampleEntity>): String = when (entries.size) {
        0 -> "Δεν υπάρχει ακόμη τοπικό ιστορικό κίνησης."
        1 -> "1 καταγραφή τελευταίου 24ώρου."
        else -> "${entries.size} καταγραφές τελευταίου 24ώρου."
    }

    fun total(entry: NetworkSampleEntity): String {
        val values = listOfNotNull(
            entry.wifiReceivedBytes,
            entry.wifiSentBytes,
            entry.mobileReceivedBytes,
            entry.mobileSentBytes,
        )
        if (values.isEmpty()) return "Μη διαθέσιμη"
        val total = values.fold(0L) { sum, value -> sum.saturatingAdd(value) }
        return StorageIntelligencePresentation.storageSize(total)
    }

    fun comparison(entries: List<NetworkSampleEntity>): String {
        val chronological = entries.sortedBy(NetworkSampleEntity::capturedAtMillis)
        if (chronological.size < 2) return "Χρειάζονται δύο 24ωρα δείγματα για ημερήσια σύγκριση."
        val latest = totalBytes(chronological.last())
        val previous = totalBytes(chronological[chronological.lastIndex - 1])
        if (latest == null || previous == null) return "Η σύγκριση δεν είναι διαθέσιμη όταν λείπει μέτρηση."
        val daily = signedDeltaLabel(latest, previous)
        val weeklySamples = chronological.dropLast(1).takeLast(7).mapNotNull(::totalBytes)
        val weekly = if (weeklySamples.isEmpty()) {
            "χωρίς εβδομαδιαίο baseline"
        } else {
            signedDeltaLabel(latest, weeklySamples.average().toLong()) + " έναντι μέσου όρου ${weeklySamples.size} προηγούμενων δειγμάτων"
        }
        return "Ημερήσια μεταβολή: $daily · Εβδομαδιαία ένδειξη: $weekly"
    }

    private fun totalBytes(entry: NetworkSampleEntity): Long? {
        val values = listOfNotNull(
            entry.wifiReceivedBytes,
            entry.wifiSentBytes,
            entry.mobileReceivedBytes,
            entry.mobileSentBytes,
        )
        return values.takeIf { it.isNotEmpty() }?.fold(0L) { sum, value -> sum.saturatingAdd(value) }
    }

    private fun signedDeltaLabel(latest: Long, baseline: Long): String {
        if (baseline <= 0L) return "δεν υπολογίζεται"
        val percent = ((latest - baseline).toDouble() / baseline.toDouble()) * 100.0
        return String.format(Locale.ROOT, "%+.1f%%", percent)
    }

    private fun Long.saturatingAdd(value: Long): Long = when {
        value <= 0L -> this
        Long.MAX_VALUE - this < value -> Long.MAX_VALUE
        else -> this + value
    }
}

object ActionLogPresentation {
    fun actionLabel(entry: ActionLogEntity): String = when (entry.action) {
        "manual_refresh" -> "Ανανέωση στιγμιότυπου"
        "scheduled_snapshot" -> "Προγραμματισμένο στιγμιότυπο"
        "storage_scan" -> "Σάρωση αποθήκευσης"
        "duplicate_scan" -> "Έλεγχος διπλοτύπων"
        "move_to_trash" -> "Μετακίνηση στον κάδο"
        "restore_from_trash" -> "Αναίρεση μετακίνησης"
        "export_report" -> "Εξαγωγή αναφοράς"
        "export_encrypted_report" -> "Εξαγωγή κρυπτογραφημένης αναφοράς"
        "history_clear" -> "Διαγραφή ιστορικού"
        "automation_toggle" -> "Αυτοματισμός"
        else -> entry.action
    }

    fun resultLabel(entry: ActionLogEntity): String = when (entry.result) {
        "success" -> "Ολοκληρώθηκε"
        "failure" -> "Απέτυχε"
        else -> entry.result
    }
}
