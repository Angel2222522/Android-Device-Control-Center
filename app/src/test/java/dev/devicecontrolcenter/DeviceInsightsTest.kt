package dev.devicecontrolcenter

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInsightsTest {
    @Test
    fun insightsReportEvidenceWithoutInventingAHealthScore() {
        val result = DeviceInsights.evaluate(
            snapshot = sampleSnapshot(
                availableStorageBytes = 5L * 1_073_741_824L,
                isLowMemory = true,
                thermalStatus = PowerManager.THERMAL_STATUS_SEVERE,
            ),
            appResult = AppIntelligenceResult(
                scannedAtMillis = 0L,
                windowStartMillis = 0L,
                windowDays = 7,
                appsConsidered = 1,
                storageUnavailableCount = 0,
                entries = listOf(
                    AppUsageEntry("large", "Large app", 0L, null, 2L * 1_073_741_824L),
                ),
            ),
        )

        assertTrue(result.insights.any { it.id == "low-memory" })
        assertTrue(result.insights.any { it.id == "thermal" })
        assertTrue(result.insights.any { it.id == "low-storage" })
        assertTrue(result.insights.none { it.title.contains("score", ignoreCase = true) })
    }

    @Test
    fun noSignalIsAnExplicitInformationalObservation() {
        val result = DeviceInsights.evaluate(sampleSnapshot())
        assertEquals("no-signal", result.insights.single().id)
        assertEquals("Πληροφορία", DeviceInsightsPresentation.severityLabel(result.insights.single().severity))
    }

    private fun sampleSnapshot(
        availableStorageBytes: Long = 40L * 1_073_741_824L,
        isLowMemory: Boolean = false,
        thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    ): DeviceSnapshot = DeviceSnapshot(
        advertisedMemoryBytes = 4_000_000_000L,
        totalMemoryBytes = 3_700_000_000L,
        availableMemoryBytes = 1_500_000_000L,
        lowMemoryThresholdBytes = 400_000_000L,
        isLowMemory = isLowMemory,
        thermalStatus = thermalStatus,
        thermalHeadroom = null,
        totalStorageBytes = 100L * 1_073_741_824L,
        availableStorageBytes = availableStorageBytes,
        hasUsageAccess = false,
        hasAllFilesAccess = false,
        battery = BatterySnapshot(
            levelPercent = 50,
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
}
