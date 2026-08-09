package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExportBoundsTest {
    @Test
    fun encryptedPlaintextBudgetIncludesDccxHeaderAndGcmTag() {
        val overhead = PendingReportExportStore.MAX_STAGED_BYTES - EncryptedReportExport.MAX_PLAINTEXT_BYTES

        assertEquals(39, overhead)
        assertTrue(
            EncryptedReportExport.MAX_PLAINTEXT_BYTES < PendingReportExportStore.MAX_STAGED_BYTES,
        )
    }
}
