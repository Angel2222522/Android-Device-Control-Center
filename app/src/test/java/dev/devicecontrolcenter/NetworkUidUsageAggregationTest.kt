package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkUidUsageAggregationTest {
    @Test
    fun aggregatesWifiAndMobileCountersIntoReceivedSentAndTotal() {
        val usage = NetworkUidUsage(
            uid = 10_001,
            wifiReceivedBytes = 1_024L,
            wifiSentBytes = 512L,
            mobileReceivedBytes = 2_048L,
            mobileSentBytes = 256L,
        )

        assertEquals(3_072L, usage.receivedBytes)
        assertEquals(768L, usage.sentBytes)
        assertEquals(3_840L, usage.totalBytes)
    }

    @Test
    fun saturatesAggregatesInsteadOfOverflowingLong() {
        val usage = NetworkUidUsage(
            uid = 10_002,
            wifiReceivedBytes = Long.MAX_VALUE,
            wifiSentBytes = Long.MAX_VALUE,
            mobileReceivedBytes = 1L,
            mobileSentBytes = 1L,
        )

        assertEquals(Long.MAX_VALUE, usage.receivedBytes)
        assertEquals(Long.MAX_VALUE, usage.sentBytes)
        assertEquals(Long.MAX_VALUE, usage.totalBytes)
    }

    @Test
    fun presentsNetworkTotalsAndUnavailableValuesTruthfully() {
        val snapshot = NetworkSnapshot(
            periodStartMillis = 0L,
            capturedAtMillis = 1L,
            wifiReceivedBytes = 1_024L,
            wifiSentBytes = 512L,
            mobileReceivedBytes = 256L,
            mobileSentBytes = 256L,
            source = NetworkDataSource.NETWORK_STATS,
        )

        assertEquals("2.0 KiB", NetworkPresentation.bytes(snapshot.totalBytes))
        assertEquals("2.0 KiB συνολικά", NetworkPresentation.totalLabel(snapshot))
        assertEquals("Μη διαθέσιμα", NetworkPresentation.bytes(null))
        assertEquals(
            "Δεν υπάρχει αξιόπιστο ιστορικό χωρίς Usage Access",
            NetworkPresentation.period(NetworkSnapshot.unavailable(1L)),
        )
    }
}
