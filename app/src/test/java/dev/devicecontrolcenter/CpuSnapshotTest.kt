package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuSnapshotTest {
    @Test
    fun parsesAggregateCpuCountersAndIncludesIoWaitInIdle() {
        val times = CpuSnapshotReader.parseCpuTimes(
            "cpu 100 20 30 400 10 5 6 7 8 9\n" +
                "cpu0 20 4 6 80 2 1 1 1 2 2\n",
        )

        assertEquals(CpuTimes(totalJiffies = 578L, idleJiffies = 410L), times)
    }

    @Test
    fun derivesBusyActivityFromTwoCounterSamples() {
        val samples = mutableListOf(
            "cpu 500 0 100 350 50 0 0 0\n",
            "cpu 600 0 100 450 50 0 0 0\n",
        )

        val snapshot = CpuSnapshotReader.read(
            readProcStat = { samples.removeAt(0) },
            delayMillis = {},
            logicalProcessorCount = { 8 },
        )

        assertEquals(50.0, checkNotNull(snapshot.activityPercent), 0.001)
        assertEquals(8, snapshot.logicalProcessorCount)
        assertEquals(CpuActivitySource.PROC_STAT, snapshot.source)
        assertEquals(250L, snapshot.sampleWindowMillis)
    }

    @Test
    fun invalidProcStatIsExplicitlyUnavailable() {
        assertNull(CpuSnapshotReader.parseCpuTimes("cpu not-a-counter\n"))

        val snapshot = CpuSnapshotReader.read(
            readProcStat = { null },
            delayMillis = {},
            logicalProcessorCount = { 8 },
        )

        assertNull(snapshot.activityPercent)
        assertEquals(8, snapshot.logicalProcessorCount)
        assertEquals(CpuActivitySource.UNAVAILABLE_OR_RESTRICTED, snapshot.source)
        assertEquals("Μη διαθέσιμη δραστηριότητα CPU", CpuPresentation.activityLabel(snapshot.activityPercent))
    }
}
