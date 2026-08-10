package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotHistoryContractTest {
    @Test
    fun clearResultDistinguishesTelemetryFromAllLocalHistory() {
        val telemetryOnly = HistoryClearResult(
            scope = HistoryClearScope.TELEMETRY_ONLY,
            clearedAtMillis = 10L,
            actionLogRetained = true,
        )
        val allLocal = HistoryClearResult(
            scope = HistoryClearScope.ALL_LOCAL_HISTORY,
            clearedAtMillis = 10L,
            actionLogRetained = false,
        )

        assertEquals(HistoryClearScope.TELEMETRY_ONLY, telemetryOnly.scope)
        assertTrue(telemetryOnly.actionLogRetained)
        assertEquals(HistoryClearScope.ALL_LOCAL_HISTORY, allLocal.scope)
        assertFalse(allLocal.actionLogRetained)
    }

    @Test
    fun linkedCaptureFieldsRemainNullableForPreMigrationRows() {
        assertNull(SnapshotHistoryEntity(
            capturedAtMillis = 1L,
            availableMemoryBytes = 1L,
            isLowMemory = false,
            thermalStatus = 0,
            thermalHeadroom = null,
            availableStorageBytes = 1L,
            batteryLevelPercent = null,
            batteryTemperatureCelsius = null,
            cpuActivityPercent = null,
        ).captureId)
        assertNull(BatterySampleEntity(
            capturedAtMillis = 1L,
            levelPercent = null,
            status = null,
            temperatureCelsius = null,
            currentNowMicroamps = null,
            chargeCounterMicroampHours = null,
        ).captureId)
        assertNull(NetworkSampleEntity(
            capturedAtMillis = 1L,
            periodStartMillis = 1L,
            wifiReceivedBytes = null,
            wifiSentBytes = null,
            mobileReceivedBytes = null,
            mobileSentBytes = null,
            source = "unavailable",
        ).captureId)
        assertNull(ActionLogEntity(
            createdAtMillis = 1L,
            action = "test",
            result = "success",
            details = null,
        ).captureId)
    }

    @Test
    fun migrationChainIsContinuousThroughCurrentSchema() {
        val chain = SnapshotHistoryDatabase.MIGRATION_VERSION_CHAIN

        assertEquals(1, chain.first().first)
        assertEquals(SnapshotHistoryDatabase.CURRENT_SCHEMA_VERSION, chain.last().second)
        assertTrue(chain.zipWithNext().all { (current, next) -> current.second == next.first })
        assertEquals(listOf(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8), chain)
    }

    @Test
    fun atomicWriteResultCannotClaimActionWhenTelemetryWasSkipped() {
        val skipped = HistoryWriteResult(
            status = HistoryWriteStatus.SKIPPED_BEFORE_CLEAR,
            captureId = null,
            actionRecorded = false,
        )

        assertEquals(HistoryWriteStatus.SKIPPED_BEFORE_CLEAR, skipped.status)
        assertNull(skipped.captureId)
        assertFalse(skipped.actionRecorded)
    }
}
