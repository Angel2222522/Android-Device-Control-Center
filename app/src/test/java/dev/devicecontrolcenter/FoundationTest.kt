package dev.devicecontrolcenter

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
}
