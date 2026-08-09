package dev.devicecontrolcenter

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewPresentationTest {
    @Test
    fun warningFindingBecomesAttentionStateWithoutHealthScore() {
        val report = reportWith(DiagnosisSeverity.WARNING, DiagnosisFindingType.CONDITION)

        val status = OverviewPresentation.status(report)

        assertEquals(OverviewTone.WARNING, status.tone)
        assertEquals("Χρειάζεται προσοχή", status.headline)
    }

    @Test
    fun criticalFindingGetsCriticalPresentation() {
        val report = reportWith(DiagnosisSeverity.CRITICAL, DiagnosisFindingType.CONDITION)

        val status = OverviewPresentation.status(report)

        assertEquals(OverviewTone.CRITICAL, status.tone)
        assertEquals("Χρειάζεται άμεση προσοχή", status.headline)
    }

    @Test
    fun dataQualityOnlyStateDoesNotPretendTheDeviceIsUnhealthy() {
        val report = reportWith(DiagnosisSeverity.INFO, DiagnosisFindingType.DATA_QUALITY)

        val status = OverviewPresentation.status(report)

        assertEquals(OverviewTone.UNAVAILABLE, status.tone)
        assertEquals("Ένα σήμα δεν είναι διαθέσιμο", status.headline)
    }

    @Test
    fun informationalConditionDoesNotClaimThereIsNoActiveSignal() {
        val status = OverviewPresentation.status(
            reportWith(DiagnosisSeverity.INFO, DiagnosisFindingType.CONDITION),
        )

        assertEquals(OverviewTone.INFO, status.tone)
        assertEquals("Υπάρχει ενημερωτική ένδειξη", status.headline)
    }

    @Test
    fun compactMetricCopyKeepsThermalStateAndHeadroomReadable() {
        assertEquals("Ελαφρύς", OverviewPresentation.thermalShortLabel(PowerManager.THERMAL_STATUS_LIGHT))
        assertEquals("Θερμικό όριο: 97%", OverviewPresentation.thermalSupport(0.97f))
        assertEquals("Δεν υπάρχει μέτρηση τώρα", OverviewPresentation.thermalSupport(null))
    }

    @Test
    fun emptyReportUsesNeutralState() {
        val status = OverviewPresentation.status(
            DiagnosisReport(
                engineVersion = 1,
                evaluatedRuleIds = listOf("test.rule"),
                findings = emptyList(),
            ),
        )

        assertEquals(OverviewTone.NEUTRAL, status.tone)
        assertEquals("Η εικόνα είναι καθαρή", status.headline)
    }

    private fun reportWith(
        severity: DiagnosisSeverity,
        type: DiagnosisFindingType,
    ): DiagnosisReport = DiagnosisReport(
        engineVersion = 1,
        evaluatedRuleIds = listOf("test.rule"),
        findings = listOf(
            DiagnosisFinding(
                ruleId = "test.rule",
                ruleVersion = 1,
                type = type,
                severity = severity,
                title = "Test finding",
                explanation = "Test explanation",
                evidence = "Test evidence",
            ),
        ),
    )
}
