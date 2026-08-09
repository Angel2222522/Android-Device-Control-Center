package dev.devicecontrolcenter

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTrendsTest {
    @Test
    fun trendUsesPreviousSamplesAndReportsBatteryStorageAndThermalContext() {
        val result = HistoryTrends.evaluate(
            current = sampleSnapshot(
                batteryLevel = 48,
                availableStorageBytes = 28L * 1_073_741_824L,
                thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            ),
            currentCapturedAtMillis = 100L,
            history = listOf(
                historyEntry(1L, batteryLevel = 45, storageBytes = 30L * 1_073_741_824L, thermalStatus = PowerManager.THERMAL_STATUS_SEVERE),
                historyEntry(2L, batteryLevel = 46, storageBytes = 29L * 1_073_741_824L, thermalStatus = PowerManager.THERMAL_STATUS_NONE),
                historyEntry(3L, batteryLevel = 47, storageBytes = 29L * 1_073_741_824L, thermalStatus = PowerManager.THERMAL_STATUS_LIGHT),
                historyEntry(4L, batteryLevel = 47, storageBytes = 28L * 1_073_741_824L, thermalStatus = PowerManager.THERMAL_STATUS_NONE),
                historyEntry(5L, batteryLevel = 48, storageBytes = 28L * 1_073_741_824L, thermalStatus = PowerManager.THERMAL_STATUS_NONE),
            ),
        )

        assertEquals(HistoryTrendState.READY, result.state)
        assertEquals(HistoryTrendDirection.INCREASED, checkNotNull(result.battery).direction)
        assertEquals(HistoryTrendDirection.DECREASED, checkNotNull(result.availableStorage).direction)
        assertEquals(2, result.previousThermalRestrictionCount)
        assertEquals(
            "Από 45% σε 48% · Αυξήθηκε κατά 3 μονάδες",
            HistoryTrendPresentation.batteryEvidence(checkNotNull(result.battery)),
        )
    }

    @Test
    fun trendRequiresPreviousSamples() {
        val result = HistoryTrends.evaluate(
            current = sampleSnapshot(),
            currentCapturedAtMillis = 100L,
            history = (1L..4L).map { historyEntry(it) },
        )

        assertEquals(HistoryTrendState.INSUFFICIENT_DATA, result.state)
        assertEquals(4, result.referenceSampleCount)
    }

    private fun sampleSnapshot(
        batteryLevel: Int? = 48,
        availableStorageBytes: Long = 28L * 1_073_741_824L,
        thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    ): DeviceSnapshot = DeviceSnapshot(
        advertisedMemoryBytes = 4_000_000_000L,
        totalMemoryBytes = 3_700_000_000L,
        availableMemoryBytes = 1_000_000_000L,
        lowMemoryThresholdBytes = 400_000_000L,
        isLowMemory = false,
        thermalStatus = thermalStatus,
        thermalHeadroom = null,
        totalStorageBytes = 100_000_000_000L,
        availableStorageBytes = availableStorageBytes,
        hasUsageAccess = false,
        hasAllFilesAccess = false,
        battery = BatterySnapshot(
            levelPercent = batteryLevel,
            status = null,
            plugged = null,
            temperatureCelsius = null,
            voltageMillivolts = null,
            voltageSource = BatteryVoltageSource.UNAVAILABLE_OR_REJECTED,
            currentNowMicroamps = null,
            currentAverageMicroamps = null,
            chargeCounterMicroampHours = null,
            energyCounterNanowattHours = null,
        ),
        cpu = CpuSnapshot.unavailable(),
    )

    private fun historyEntry(
        capturedAtMillis: Long,
        batteryLevel: Int? = 45,
        storageBytes: Long = 30L * 1_073_741_824L,
        thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    ): SnapshotHistoryEntity = SnapshotHistoryEntity(
        capturedAtMillis = capturedAtMillis,
        availableMemoryBytes = 1_000_000_000L,
        isLowMemory = false,
        thermalStatus = thermalStatus,
        thermalHeadroom = null,
        availableStorageBytes = storageBytes,
        batteryLevelPercent = batteryLevel,
        batteryTemperatureCelsius = null,
        cpuActivityPercent = null,
    )
}
