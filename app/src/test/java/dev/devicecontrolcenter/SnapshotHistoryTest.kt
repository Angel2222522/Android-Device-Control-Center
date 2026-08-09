package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapshotHistoryTest {
    @Test
    fun deviceSnapshotMappingKeepsEvidenceRelevantSignals() {
        val snapshot = DeviceSnapshot(
            advertisedMemoryBytes = 4_000_000_000L,
            totalMemoryBytes = 3_700_000_000L,
            availableMemoryBytes = 1_200_000_000L,
            lowMemoryThresholdBytes = 400_000_000L,
            isLowMemory = false,
            thermalStatus = 1,
            thermalHeadroom = 0.94f,
            totalStorageBytes = 100_000_000_000L,
            availableStorageBytes = 28_000_000_000L,
            hasUsageAccess = false,
            hasAllFilesAccess = false,
            battery = BatterySnapshot(
                levelPercent = 53,
                status = 3,
                plugged = 0,
                temperatureCelsius = 37.1,
                voltageMillivolts = null,
                voltageSource = BatteryVoltageSource.UNAVAILABLE_OR_REJECTED,
                currentNowMicroamps = 973,
                currentAverageMicroamps = null,
                chargeCounterMicroampHours = 2_507_000,
                energyCounterNanowattHours = null,
            ),
            cpu = CpuSnapshot.unavailable(),
        )

        val entity = snapshot.toHistoryEntity(capturedAtMillis = 123L)

        assertEquals(123L, entity.capturedAtMillis)
        assertEquals(1_200_000_000L, entity.availableMemoryBytes)
        assertEquals(28_000_000_000L, entity.availableStorageBytes)
        assertEquals(53, entity.batteryLevelPercent)
        assertEquals(37.1, entity.batteryTemperatureCelsius ?: Double.NaN, 0.0)
        assertEquals(0.94f, entity.thermalHeadroom)
        assertNull(entity.cpuActivityPercent)
    }

    @Test
    fun historyPresentationDoesNotInventUnavailableCpuData() {
        val entry = SnapshotHistoryEntity(
            capturedAtMillis = 0L,
            availableMemoryBytes = 1_000_000_000L,
            isLowMemory = false,
            thermalStatus = 0,
            thermalHeadroom = null,
            availableStorageBytes = 2_000_000_000L,
            batteryLevelPercent = null,
            batteryTemperatureCelsius = null,
            cpuActivityPercent = null,
        )

        assertEquals("Μη διαθέσιμη", SnapshotHistoryPresentation.cpuLabel(entry))
        assertEquals("Μη διαθέσιμη", SnapshotHistoryPresentation.batteryLabel(entry))
    }
}
