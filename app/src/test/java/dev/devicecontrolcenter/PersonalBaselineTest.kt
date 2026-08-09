package dev.devicecontrolcenter

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalBaselineTest {
    @Test
    fun baselineRequiresFivePreviousSnapshotsBeforeComparisonIsReady() {
        val result = PersonalBaseline.evaluate(
            current = sampleSnapshot(),
            currentCapturedAtMillis = 100L,
            history = (1L..4L).map { historyEntry(capturedAtMillis = it) },
        )

        assertEquals(PersonalBaselineState.INSUFFICIENT_DATA, result.state)
        assertEquals(4, result.referenceSampleCount)
        assertEquals(
            "Χρειάζονται τουλάχιστον 5 προηγούμενα στιγμιότυπα· βρέθηκαν 4.",
            PersonalBaselinePresentation.summary(result),
        )
    }

    @Test
    fun currentCaptureIsExcludedFromTheReferenceSample() {
        val result = PersonalBaseline.evaluate(
            current = sampleSnapshot(),
            currentCapturedAtMillis = 100L,
            history = listOf(historyEntry(capturedAtMillis = 100L)) +
                (1L..5L).map { historyEntry(capturedAtMillis = it) },
        )

        assertEquals(PersonalBaselineState.READY, result.state)
        assertEquals(5, result.referenceSampleCount)
    }

    @Test
    fun numericEvidenceUsesMedianAndObservedRangeWithoutAHealthScore() {
        val gib = 1_073_741_824L
        val result = PersonalBaseline.evaluate(
            current = sampleSnapshot(availableMemoryBytes = gib / 2L),
            currentCapturedAtMillis = 100L,
            history = listOf(1L, 2L, 3L, 4L, 5L).map { index ->
                historyEntry(
                    capturedAtMillis = index,
                    availableMemoryBytes = index * gib,
                )
            },
        )

        val metric = checkNotNull(result.availableMemory)
        assertEquals(3L * gib, metric.medianValue)
        assertEquals(PersonalBaselineRelation.BELOW_RECENT_RANGE, metric.relation)
        assertEquals(
            "Τώρα 0.50 GiB · διάμεσος 3.00 GiB · εύρος 1.00 GiB–5.00 GiB",
            PersonalBaselinePresentation.memoryEvidence(metric),
        )
        assertTrue(PersonalBaselinePresentation.limitation().contains("δεν είναι score"))
    }

    @Test
    fun flagsArePresentedAsObservedEvidence() {
        val result = PersonalBaseline.evaluate(
            current = sampleSnapshot(
                isLowMemory = false,
                thermalStatus = PowerManager.THERMAL_STATUS_LIGHT,
            ),
            currentCapturedAtMillis = 100L,
            history = listOf(
                historyEntry(capturedAtMillis = 1L, isLowMemory = true, thermalStatus = PowerManager.THERMAL_STATUS_NONE),
                historyEntry(capturedAtMillis = 2L, isLowMemory = false, thermalStatus = PowerManager.THERMAL_STATUS_SEVERE),
                historyEntry(capturedAtMillis = 3L, isLowMemory = true, thermalStatus = PowerManager.THERMAL_STATUS_MODERATE),
                historyEntry(capturedAtMillis = 4L, isLowMemory = false, thermalStatus = PowerManager.THERMAL_STATUS_LIGHT),
                historyEntry(capturedAtMillis = 5L, isLowMemory = false, thermalStatus = PowerManager.THERMAL_STATUS_NONE),
            ),
        )

        assertEquals(
            "Τώρα: όχι · 2/5 προηγούμενα με Android low-memory flag",
            PersonalBaselinePresentation.lowMemoryEvidence(result),
        )
        assertEquals(
            "Τώρα: Ελαφρύς · υψηλότερη προηγούμενη: Σοβαρός",
            PersonalBaselinePresentation.thermalEvidence(result),
        )
    }

    private fun sampleSnapshot(
        availableMemoryBytes: Long = 2_000_000_000L,
        availableStorageBytes: Long = 20_000_000_000L,
        isLowMemory: Boolean = false,
        thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    ): DeviceSnapshot = DeviceSnapshot(
        advertisedMemoryBytes = 4_000_000_000L,
        totalMemoryBytes = 3_700_000_000L,
        availableMemoryBytes = availableMemoryBytes,
        lowMemoryThresholdBytes = 400_000_000L,
        isLowMemory = isLowMemory,
        thermalStatus = thermalStatus,
        thermalHeadroom = null,
        totalStorageBytes = 100_000_000_000L,
        availableStorageBytes = availableStorageBytes,
        hasUsageAccess = false,
        hasAllFilesAccess = false,
        battery = BatterySnapshot(
            levelPercent = null,
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
        availableMemoryBytes: Long = 2_000_000_000L,
        isLowMemory: Boolean = false,
        thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    ): SnapshotHistoryEntity = SnapshotHistoryEntity(
        capturedAtMillis = capturedAtMillis,
        availableMemoryBytes = availableMemoryBytes,
        isLowMemory = isLowMemory,
        thermalStatus = thermalStatus,
        thermalHeadroom = null,
        availableStorageBytes = 20_000_000_000L,
        batteryLevelPercent = null,
        batteryTemperatureCelsius = null,
        cpuActivityPercent = null,
    )
}
