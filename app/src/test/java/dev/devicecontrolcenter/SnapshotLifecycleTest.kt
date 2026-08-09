package dev.devicecontrolcenter

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotLifecycleTest {
    @Test
    fun firstRefreshIsAllowedAndConcurrentRefreshIsRejected() {
        val gate = SnapshotRefreshGate()

        assertTrue(gate.tryStart(nowMillis = 1_000L))
        assertFalse(gate.tryStart(nowMillis = 1_001L))
    }

    @Test
    fun refreshIsThrottledForOneSecondAfterCompletion() {
        val gate = SnapshotRefreshGate()

        assertTrue(gate.tryStart(nowMillis = 1_000L))
        gate.complete(nowMillis = 2_000L)

        assertFalse(gate.tryStart(nowMillis = 2_999L))
        assertTrue(gate.tryStart(nowMillis = 3_000L))
    }

    @Test
    fun successfulRefreshClearsPreviousErrorAndRecordsCaptureTime() {
        val initial = SnapshotUiState<Int>()
            .beginRefresh()
            .failure("temporary")
            .beginRefresh()
            .success(snapshot = 42, capturedAtMillis = 123L)

        assertEquals(42, initial.snapshot)
        assertEquals(123L, initial.capturedAtMillis)
        assertFalse(initial.isRefreshing)
        assertNull(initial.errorMessage)
    }

    @Test
    fun captureTimeUsesDeviceLocalClockForDisplay() {
        assertEquals(
            "Τελευταία ενημέρωση: 00:00:00",
            SnapshotPresentation.capturedAtLabel(0L, ZoneId.of("UTC")),
        )
        assertEquals(
            "Δεν υπάρχει έγκυρο στιγμιότυπο",
            SnapshotPresentation.capturedAtLabel(null, ZoneId.of("UTC")),
        )
    }

    @Test
    fun compactCaptureTimeIsSuitableForTheRefreshControl() {
        assertEquals("00:00:00", SnapshotPresentation.capturedTimeLabel(0L, ZoneId.of("UTC")))
        assertEquals("Μη διαθέσιμη", SnapshotPresentation.capturedTimeLabel(null, ZoneId.of("UTC")))
    }
}
