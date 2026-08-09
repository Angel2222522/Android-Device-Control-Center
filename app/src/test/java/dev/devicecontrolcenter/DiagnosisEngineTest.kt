package dev.devicecontrolcenter

import android.os.BatteryManager
import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisEngineTest {
    @Test
    fun stableSnapshotHasNoActiveConditionAndReportsBatteryDataGap() {
        val report = DeviceDiagnosisEngine.analyze(snapshot())

        assertEquals(1, report.engineVersion)
        assertEquals(
            listOf("memory.low_state", "thermal.current_status", "battery.voltage_quality"),
            report.evaluatedRuleIds,
        )
        assertTrue(report.findings.all { it.type == DiagnosisFindingType.DATA_QUALITY })
        assertEquals("Δεν εντοπίστηκε ενεργή πίεση", DiagnosisPresentation.headline(report))
        assertFalse(report.findings.any { it.severity == DiagnosisSeverity.WARNING })
    }

    @Test
    fun lowMemoryFlagCreatesWarningWithoutInventingRatio() {
        val report = DeviceDiagnosisEngine.analyze(snapshot(isLowMemory = true))
        val finding = report.findings.first { it.ruleId == "memory.low_state" }

        assertEquals(DiagnosisFindingType.CONDITION, finding.type)
        assertEquals(DiagnosisSeverity.WARNING, finding.severity)
        assertTrue(finding.evidence.contains("lowMemory=true"))
        assertTrue(finding.evidence.contains("διαθέσιμα 1.02 GiB"))
        assertTrue(finding.evidence.contains("όριο 0.42 GiB"))
        assertTrue(finding.limitation.orEmpty().contains("συγκεκριμένη εφαρμογή"))
    }

    @Test
    fun severeThermalStatusCreatesWarningAndKeepsCauseUnknown() {
        val report = DeviceDiagnosisEngine.analyze(
            snapshot(thermalStatus = PowerManager.THERMAL_STATUS_SEVERE),
        )
        val finding = report.findings.first { it.ruleId == "thermal.current_status" }

        assertEquals(DiagnosisSeverity.WARNING, finding.severity)
        assertTrue(finding.explanation.contains("Σοβαρός θερμικός περιορισμός"))
        assertTrue(finding.evidence.contains("κωδικός ${PowerManager.THERMAL_STATUS_SEVERE}"))
        assertTrue(finding.limitation.orEmpty().contains("δεν αποδεικνύει"))
    }

    @Test
    fun criticalThermalStatusCreatesCriticalFinding() {
        val report = DeviceDiagnosisEngine.analyze(
            snapshot(thermalStatus = PowerManager.THERMAL_STATUS_CRITICAL),
        )

        assertEquals(
            DiagnosisSeverity.CRITICAL,
            report.findings.first { it.ruleId == "thermal.current_status" }.severity,
        )
    }

    @Test
    fun findingsAreOrderedBySeverityThenRuleId() {
        val report = DeviceDiagnosisEngine.analyze(
            snapshot(
                isLowMemory = true,
                thermalStatus = PowerManager.THERMAL_STATUS_CRITICAL,
            ),
        )

        assertEquals(
            listOf("thermal.current_status", "memory.low_state", "battery.voltage_quality"),
            report.findings.map { it.ruleId },
        )
    }

    private fun snapshot(
        isLowMemory: Boolean = false,
        thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
        thermalHeadroom: Float? = 0.88f,
        voltageMillivolts: Int? = null,
        voltageSource: BatteryVoltageSource = BatteryVoltageSource.UNAVAILABLE_OR_REJECTED,
    ): DeviceSnapshot = DeviceSnapshot(
        advertisedMemoryBytes = 4_000_000_000L,
        totalMemoryBytes = 3_790_259_077L,
        availableMemoryBytes = 1_100_000_000L,
        lowMemoryThresholdBytes = 450_971_566L,
        isLowMemory = isLowMemory,
        thermalStatus = thermalStatus,
        thermalHeadroom = thermalHeadroom,
        totalStorageBytes = 101_760_000_000L,
        availableStorageBytes = 28_300_000_000L,
        hasUsageAccess = false,
        hasAllFilesAccess = false,
        battery = BatterySnapshot(
            levelPercent = 62,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            plugged = 0,
            temperatureCelsius = 35.8,
            voltageMillivolts = voltageMillivolts,
            voltageSource = voltageSource,
            currentNowMicroamps = 762,
            currentAverageMicroamps = null,
            chargeCounterMicroampHours = 2_943_000,
            energyCounterNanowattHours = null,
        ),
    )
}
