package dev.devicecontrolcenter

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.Locale

const val SNAPSHOT_HISTORY_LIMIT = 120

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
}

@Database(
    entities = [SnapshotHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SnapshotHistoryDatabase : RoomDatabase() {
    abstract fun snapshotHistoryDao(): SnapshotHistoryDao

    companion object {
        private const val DATABASE_NAME = "device_control_center_history.db"

        @Volatile
        private var instance: SnapshotHistoryDatabase? = null

        fun get(context: Context): SnapshotHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SnapshotHistoryDatabase::class.java,
                DATABASE_NAME,
            ).build().also { instance = it }
        }
    }
}

class SnapshotHistoryRepository(context: Context) {
    private val dao = SnapshotHistoryDatabase.get(context).snapshotHistoryDao()

    fun record(snapshot: DeviceSnapshot, capturedAtMillis: Long) {
        dao.insert(snapshot.toHistoryEntity(capturedAtMillis))
        dao.trimTo(SNAPSHOT_HISTORY_LIMIT)
    }

    fun recent(): List<SnapshotHistoryEntity> = dao.recent(SNAPSHOT_HISTORY_LIMIT)
}

fun DeviceSnapshot.toHistoryEntity(capturedAtMillis: Long): SnapshotHistoryEntity = SnapshotHistoryEntity(
    capturedAtMillis = capturedAtMillis,
    availableMemoryBytes = availableMemoryBytes,
    isLowMemory = isLowMemory,
    thermalStatus = thermalStatus,
    thermalHeadroom = thermalHeadroom,
    availableStorageBytes = availableStorageBytes,
    batteryLevelPercent = battery.levelPercent,
    batteryTemperatureCelsius = battery.temperatureCelsius,
    cpuActivityPercent = cpu.activityPercent,
)

data class HistoryUiState(
    val entries: List<SnapshotHistoryEntity> = emptyList(),
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
