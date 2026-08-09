package dev.devicecontrolcenter

data class HistoryChartPoint(
    val capturedAtMillis: Long,
    val availableMemoryGib: Float,
    val availableStorageGib: Float,
    val batteryPercent: Float?,
)

object HistoryChartData {
    fun points(
        current: DeviceSnapshot,
        currentCapturedAtMillis: Long?,
        history: List<SnapshotHistoryEntity>,
    ): List<HistoryChartPoint> {
        val previous = history
            .asSequence()
            .filter { currentCapturedAtMillis == null || it.capturedAtMillis != currentCapturedAtMillis }
            .sortedBy { it.capturedAtMillis }
            .takeLast(23)
            .map {
                HistoryChartPoint(
                    capturedAtMillis = it.capturedAtMillis,
                    availableMemoryGib = it.availableMemoryBytes / 1_073_741_824f,
                    availableStorageGib = it.availableStorageBytes / 1_073_741_824f,
                    batteryPercent = it.batteryLevelPercent?.toFloat(),
                )
            }
        return (previous + HistoryChartPoint(
            capturedAtMillis = currentCapturedAtMillis ?: System.currentTimeMillis(),
            availableMemoryGib = current.availableMemoryBytes / 1_073_741_824f,
            availableStorageGib = current.availableStorageBytes / 1_073_741_824f,
            batteryPercent = current.battery.levelPercent?.toFloat(),
        )).toList()
    }

    fun limitation(): String =
        "Οι γραμμές δείχνουν μόνο επιτυχείς λήψεις και δεν αποτελούν συνεχή καταγραφή ή διάγνωση."
}
