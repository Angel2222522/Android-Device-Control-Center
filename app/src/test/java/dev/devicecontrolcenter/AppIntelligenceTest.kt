package dev.devicecontrolcenter

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIntelligenceTest {
    @Test
    fun foregroundDurationUsesReadableUnits() {
        assertEquals("<1 λεπτό", AppIntelligencePresentation.foregroundLabel(30_000L))
        assertEquals("12 λεπτά", AppIntelligencePresentation.foregroundLabel(12L * 60_000L))
        assertEquals("2 ώρες 5 λεπτά", AppIntelligencePresentation.foregroundLabel(2L * 60L * 60_000L + 5L * 60_000L))
    }

    @Test
    fun storageAndLastUseRemainExplicitWhenUnavailable() {
        assertEquals("Χώρος μη διαθέσιμος", AppIntelligencePresentation.storageLabel(null))
        assertEquals(
            "Τελευταία χρήση: μη διαθέσιμη",
            AppIntelligencePresentation.lastUsedLabel(null, ZoneId.of("UTC")),
        )
    }

    @Test
    fun summaryStatesTheWindowAndUnavailableStorageCount() {
        val result = AppIntelligenceResult(
            scannedAtMillis = 0L,
            windowStartMillis = 0L,
            windowDays = 7,
            appsConsidered = 8,
            storageUnavailableCount = 2,
            entries = listOf(
                AppUsageEntry("a", "A", 0L, null, null),
                AppUsageEntry("b", "B", 60_000L, null, 1_024L),
            ),
        )

        assertEquals(
            "2 εφαρμογές στην αναφορά · παράθυρο 7 ημερών · χώρος μη διαθέσιμος για 2",
            AppIntelligencePresentation.summary(result),
        )
    }
}
