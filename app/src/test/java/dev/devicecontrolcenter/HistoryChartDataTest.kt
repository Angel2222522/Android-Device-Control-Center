package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryChartDataTest {
    @Test
    fun chartAddsCurrentCaptureAfterPreviousLocalSamples() {
        val current = DeviceSnapshot(
            advertisedMemoryBytes = 4_000_000_000L,
            totalMemoryBytes = 3_700_000_000L,
            availableMemoryBytes = 2_000_000_000L,
            lowMemoryThresholdBytes = 400_000_000L,
            isLowMemory = false,
            thermalStatus = 0,
            thermalHeadroom = null,
            totalStorageBytes = 100_000_000_000L,
            availableStorageBytes = 28_000_000_000L,
            hasUsageAccess = false,
            hasAllFilesAccess = false,
            battery = BatterySnapshot(
                levelPercent = 48,
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
        val points = HistoryChartData.points(
            current = current,
            currentCapturedAtMillis = 3L,
            history = listOf(
                SnapshotHistoryEntity(
                    capturedAtMillis = 1L,
                    availableMemoryBytes = 1_000_000_000L,
                    isLowMemory = false,
                    thermalStatus = 0,
                    thermalHeadroom = null,
                    availableStorageBytes = 30_000_000_000L,
                    batteryLevelPercent = 45,
                    batteryTemperatureCelsius = null,
                    cpuActivityPercent = null,
                ),
                SnapshotHistoryEntity(
                    capturedAtMillis = 3L,
                    availableMemoryBytes = 99L,
                    isLowMemory = false,
                    thermalStatus = 0,
                    thermalHeadroom = null,
                    availableStorageBytes = 99L,
                    batteryLevelPercent = 1,
                    batteryTemperatureCelsius = null,
                    cpuActivityPercent = null,
                ),
            ),
        )

        assertEquals(2, points.size)
        assertEquals(1L, points.first().capturedAtMillis)
        assertEquals(3L, points.last().capturedAtMillis)
        assertEquals(48f, points.last().batteryPercent)
    }
}
