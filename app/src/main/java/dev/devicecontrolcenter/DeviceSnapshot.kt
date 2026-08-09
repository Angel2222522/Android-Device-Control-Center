package dev.devicecontrolcenter

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import java.util.Locale

data class DeviceSnapshot(
    val advertisedMemoryBytes: Long,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val lowMemoryThresholdBytes: Long,
    val isLowMemory: Boolean,
    val thermalStatus: Int,
    val thermalHeadroom: Float?,
    val totalStorageBytes: Long,
    val availableStorageBytes: Long,
    val hasUsageAccess: Boolean,
    val hasAllFilesAccess: Boolean,
    val battery: BatterySnapshot,
    val cpu: CpuSnapshot = CpuSnapshot.unavailable(),
)

object DeviceSnapshotReader {
    fun read(context: Context): DeviceSnapshot {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val storage = StatFs(Environment.getDataDirectory().absolutePath)
        val headroom = powerManager.getThermalHeadroom(0).takeUnless(Float::isNaN)

        return DeviceSnapshot(
            advertisedMemoryBytes = memory.advertisedMem,
            totalMemoryBytes = memory.totalMem,
            availableMemoryBytes = memory.availMem,
            lowMemoryThresholdBytes = memory.threshold,
            isLowMemory = memory.lowMemory,
            thermalStatus = powerManager.currentThermalStatus,
            thermalHeadroom = headroom,
            totalStorageBytes = storage.totalBytes,
            availableStorageBytes = storage.availableBytes,
            hasUsageAccess = hasUsageAccess(context),
            hasAllFilesAccess = Environment.isExternalStorageManager(),
            battery = BatterySnapshotReader.read(context),
            cpu = CpuSnapshotReader.read(),
        )
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }
}

object SnapshotPresentation {
    fun gib(bytes: Long): String = String.format(Locale.ROOT, "%.2f GiB", bytes / 1_073_741_824.0)

    fun decimalGb(bytes: Long): String = String.format(Locale.ROOT, "%.2f GB", bytes / 1_000_000_000.0)

    fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Χωρίς θερμικό περιορισμό"
        PowerManager.THERMAL_STATUS_LIGHT -> "Ελαφρύς θερμικός περιορισμός"
        PowerManager.THERMAL_STATUS_MODERATE -> "Μέτριος θερμικός περιορισμός"
        PowerManager.THERMAL_STATUS_SEVERE -> "Σοβαρός θερμικός περιορισμός"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Κρίσιμος θερμικός περιορισμός"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Θερμική κατάσταση έκτακτης ανάγκης"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Επικείμενος θερμικός τερματισμός"
        else -> "Άγνωστη θερμική κατάσταση"
    }

    fun thermalEnvelopeLabel(headroom: Float): String = String.format(
        Locale.ROOT,
        "Χρήση θερμικού ορίου: %.0f%% · Το 100%% είναι το κατώφλι σοβαρού περιορισμού",
        headroom * 100,
    )

    fun accessLabel(granted: Boolean): String = if (granted) "Ενεργή" else "Δεν έχει δοθεί"

    fun memoryDetail(advertisedBytes: Long, kernelBytes: Long, thresholdBytes: Long): String =
        "Εγκατεστημένη φυσική RAM ${decimalGb(advertisedBytes)} (${gib(advertisedBytes)}) · " +
            "Προσβάσιμη στον πυρήνα ${gib(kernelBytes)} · " +
            "Όριο χαμηλής μνήμης ${gib(thresholdBytes)}"
}
