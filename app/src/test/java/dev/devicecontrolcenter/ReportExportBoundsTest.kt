package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun pendingReportIsNotClearedWhenDestinationWriteFails() {
        val events = mutableListOf<String>()

        runCatching {
            writeThenClearPendingReport(
                write = {
                    events += "write"
                    error("provider failure")
                },
                clearPending = { events += "clear" },
            )
        }

        assertEquals(listOf("write"), events)
        assertFalse(events.contains("clear"))
    }

    @Test
    fun pendingReportIsClearedAfterDestinationWriteSucceeds() {
        val events = mutableListOf<String>()

        writeThenClearPendingReport(
            write = {
                events += "write"
                Unit
            },
            clearPending = { events += "clear" },
        )

        assertEquals(listOf("write", "clear"), events)
    }
}
