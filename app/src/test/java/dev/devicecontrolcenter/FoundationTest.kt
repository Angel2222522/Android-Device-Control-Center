package dev.devicecontrolcenter

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationTest {
    @Test
    fun formatsBinaryGigabytesWithoutMarketingRounding() {
        assertEquals("8.00 GB", SnapshotPresentation.gib(8L * 1_073_741_824L))
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
            "Εγκατεστημένη φυσική RAM 4.00 GB · Προσβάσιμη στον πυρήνα 3.53 GB · " +
                "Όριο χαμηλής μνήμης 0.42 GB",
            SnapshotPresentation.memoryDetail(
                advertisedBytes = 4L * 1_073_741_824L,
                kernelBytes = 3_790_259_077L,
                thresholdBytes = 450_971_566L,
            ),
        )
    }
}
