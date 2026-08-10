package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageTrashQuotaTest {
    @Test
    fun acceptsARecordWhenBothAggregateLimitsRemainAvailable() {
        val usage = StorageTrashQuota.calculateUsage(listOf(4L, 8L))

        assertTrue(StorageTrashQuota.canAccept(usage, 16L))
    }

    @Test
    fun rejectsARecordAtTheItemCountLimit() {
        val usage = StorageTrashQuota.calculateUsage(
            List(StorageTrashQuota.MAX_ITEM_COUNT.toInt()) { 0L },
        )

        assertFalse(StorageTrashQuota.canAccept(usage, 0L))
    }

    @Test
    fun rejectsARecordAtThePayloadByteLimit() {
        val usage = StorageTrashQuota.calculateUsage(
            listOf(StorageTrashQuota.MAX_TOTAL_PAYLOAD_BYTES - 1L),
        )

        assertFalse(StorageTrashQuota.canAccept(usage, 2L))
    }

    @Test
    fun unknownLegacySizeDoesNotBecomeZero() {
        val usage = StorageTrashQuota.calculateUsage(listOf(4L, null, 8L))

        assertEquals(3L, usage.itemCount)
        assertNull(usage.totalPayloadBytes)
        assertFalse(StorageTrashQuota.canAccept(usage, 16L))
    }

    @Test
    fun payloadAdditionSaturatesInsteadOfWrapping() {
        val usage = StorageTrashQuota.calculateUsage(listOf(Long.MAX_VALUE, 1L))

        assertEquals(Long.MAX_VALUE, usage.totalPayloadBytes)
        assertEquals(Long.MAX_VALUE, StorageTrashQuota.saturatingAdd(Long.MAX_VALUE, 1L))
        assertFalse(StorageTrashQuota.canAccept(usage, 0L))
    }
}
