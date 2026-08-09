package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkIntelligenceTest {
    @Test
    fun networkPresentationKeepsDirectionAndWindowExplicit() {
        val result = NetworkUsageResult(
            scannedAtMillis = 0L,
            windowStartMillis = 0L,
            windowDays = 7,
            appsConsidered = 1,
            unavailableCount = 0,
            entries = listOf(
                AppNetworkUsageEntry(
                    packageName = "a",
                    label = "A",
                    downloadBytes = 2_048L,
                    uploadBytes = 1_024L,
                ),
            ),
        )

        assertEquals("1 εφαρμογές στην αναφορά · παράθυρο 7 ημερών", NetworkUsagePresentation.summary(result))
        assertEquals("Λήψη 2.0 KiB · αποστολή 1.0 KiB", NetworkUsagePresentation.directionLabel(result.entries.single()))
        assertEquals(3_072L, result.entries.single().totalBytes)
    }
}
