package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkHistoryPresentationComparisonTest {
    @Test
    fun comparesLatestSampleDailyAndAgainstSevenSampleWeeklyBaseline() {
        val entries = buildList {
            add(networkSample(capturedAtMillis = 8L * DAY, receivedBytes = 200L))
            repeat(7) { index ->
                add(networkSample(capturedAtMillis = index.toLong() * DAY, receivedBytes = 100L))
            }
        }

        val comparison = NetworkHistoryPresentation.comparison(entries)

        assertEquals(
            "Ημερήσια μεταβολή: +100.0% · " +
                "Εβδομαδιαία ένδειξη: +100.0% έναντι μέσου όρου 7 προηγούμενων δειγμάτων",
            comparison,
        )
    }

    @Test
    fun sortsSamplesBeforeCalculatingDailyAndWeeklyComparison() {
        val comparison = NetworkHistoryPresentation.comparison(
            listOf(
                networkSample(capturedAtMillis = DAY, receivedBytes = 200L),
                networkSample(capturedAtMillis = 0L, receivedBytes = 100L),
            ),
        )

        assertEquals(
            "Ημερήσια μεταβολή: +100.0% · " +
                "Εβδομαδιαία ένδειξη: +100.0% έναντι μέσου όρου 1 προηγούμενων δειγμάτων",
            comparison,
        )
    }

    @Test
    fun reportsInsufficientHistoryWhenFewerThanTwoSamplesExist() {
        assertEquals(
            "Χρειάζονται δύο 24ωρα δείγματα για ημερήσια σύγκριση.",
            NetworkHistoryPresentation.comparison(emptyList()),
        )
        assertEquals(
            "Χρειάζονται δύο 24ωρα δείγματα για ημερήσια σύγκριση.",
            NetworkHistoryPresentation.comparison(
                listOf(networkSample(capturedAtMillis = 0L, receivedBytes = 100L)),
            ),
        )
    }

    @Test
    fun reportsInsufficientMeasurementWhenAComparisonSampleHasNoCounters() {
        val comparison = NetworkHistoryPresentation.comparison(
            listOf(
                networkSample(capturedAtMillis = 0L, receivedBytes = 100L),
                NetworkSampleEntity(
                    capturedAtMillis = DAY,
                    periodStartMillis = 0L,
                    wifiReceivedBytes = null,
                    wifiSentBytes = null,
                    mobileReceivedBytes = null,
                    mobileSentBytes = null,
                    source = "UNAVAILABLE",
                ),
            ),
        )

        assertEquals(
            "Η σύγκριση δεν είναι διαθέσιμη όταν λείπει μέτρηση.",
            comparison,
        )
    }

    private fun networkSample(
        capturedAtMillis: Long,
        receivedBytes: Long,
    ): NetworkSampleEntity = NetworkSampleEntity(
        capturedAtMillis = capturedAtMillis,
        periodStartMillis = capturedAtMillis - DAY,
        wifiReceivedBytes = receivedBytes,
        wifiSentBytes = null,
        mobileReceivedBytes = null,
        mobileSentBytes = null,
        source = "NETWORK_STATS",
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1_000L
    }
}
