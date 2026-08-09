package dev.devicecontrolcenter

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationTest {
    @Test
    fun formatsBinaryGigabytesWithoutMarketingRounding() {
        assertEquals("8.00 GiB", SnapshotPresentation.gib(8L * 1_073_741_824L))
    }

    @Test
    fun formatsDecimalGigabytesAsAdvertisedCapacity() {
        assertEquals("4.00 GB", SnapshotPresentation.decimalGb(4_000_000_000L))
    }

    @Test
    fun accessStateIsExplicit() {
        assertEquals("Ενεργή", SnapshotPresentation.accessLabel(true))
        assertEquals("Δεν έχει δοθεί", SnapshotPresentation.accessLabel(false))
    }

    @Test
    fun severeThermalStatusDescribesThrottlingRatherThanTemperature() {
        assertEquals(
            "Σοβαρός θερμικός περιορισμός",
            SnapshotPresentation.thermalLabel(PowerManager.THERMAL_STATUS_SEVERE),
        )
    }

    @Test
    fun thermalEnvelopeMakesTheSevereThresholdExplicit() {
        assertEquals(
            "Χρήση θερμικού ορίου: 102% · Το 100% είναι το κατώφλι σοβαρού περιορισμού",
            SnapshotPresentation.thermalEnvelopeLabel(1.02f),
        )
    }

    @Test
    fun memoryDetailSeparatesAdvertisedFromKernelAccessibleRam() {
        assertEquals(
            "Εγκατεστημένη φυσική RAM 4.00 GB (3.73 GiB) · " +
                "Προσβάσιμη στον πυρήνα 3.53 GiB · Όριο χαμηλής μνήμης 0.42 GiB",
            SnapshotPresentation.memoryDetail(
                advertisedBytes = 4_000_000_000L,
                kernelBytes = 3_790_259_077L,
                thresholdBytes = 450_971_566L,
            ),
        )
    }
}
