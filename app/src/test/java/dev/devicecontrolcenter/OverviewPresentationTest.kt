package dev.devicecontrolcenter

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
