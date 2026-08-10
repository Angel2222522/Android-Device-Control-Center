package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionLogPresentationTest {
    @Test
    fun mapsRecordedActionsToGreekLabels() {
        assertEquals("Ανανέωση στιγμιότυπου", actionLabel("manual_refresh"))
        assertEquals("Σάρωση αποθήκευσης", actionLabel("storage_scan"))
        assertEquals("Έλεγχος διπλοτύπων", actionLabel("duplicate_scan"))
        assertEquals("Μετακίνηση στον κάδο", actionLabel("move_to_trash"))
        assertEquals("Εξαγωγή αναφοράς", actionLabel("export_report"))
        assertEquals("Εξαγωγή κρυπτογραφημένης αναφοράς", actionLabel("export_encrypted_report"))
    }

    @Test
    fun mapsResultsAndPreservesUnknownValues() {
        assertEquals("Ολοκληρώθηκε", resultLabel("success"))
        assertEquals("Απέτυχε", resultLabel("failure"))
        assertEquals("σε αναμονή", resultLabel("σε αναμονή"))
        assertEquals("custom_action", actionLabel("custom_action"))
    }

    private fun actionLabel(action: String): String = ActionLogPresentation.actionLabel(
        ActionLogEntity(
            createdAtMillis = 0L,
            action = action,
            result = "success",
            details = null,
        ),
    )

    private fun resultLabel(result: String): String = ActionLogPresentation.resultLabel(
        ActionLogEntity(
            createdAtMillis = 0L,
            action = "manual_refresh",
            result = result,
            details = null,
        ),
    )
}
