package dev.devicecontrolcenter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StorageTrashRecoveryTest {
    @Test
    fun matchingCompleteFingerprintIsSafeToClassifyAsTheOriginalSource() {
        val expected = fingerprint()

        assertTrue(expected.isComplete)
        assertTrue(expected.matches(fingerprint()))
    }

    @Test
    fun changedHashNeverMatchesEvenWhenUriAndMetadataAreTheSame() {
        val expected = fingerprint(sha256 = "original")
        val reusedUri = fingerprint(sha256 = "different")

        assertFalse(reusedUri.matches(expected))
        assertTrue(reusedUri.hasComparableIdentity)
    }

    @Test
    fun legacyRecordWithoutHashIsNotCompleteAndMustRemainUncertain() {
        val legacy = fingerprint(sha256 = null)

        assertFalse(legacy.isComplete)
        assertTrue(legacy.hasComparableIdentity)
    }

    @Test
    fun recoveryWaitsWhileMoveOwnsTheProcessWideTrashOperationLock() {
        val moveEntered = CountDownLatch(1)
        val releaseMove = CountDownLatch(1)
        val recoveryStarted = CountDownLatch(1)
        val recoveryEntered = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val move = executor.submit {
                StorageTrashIndex.withOperationLock {
                    moveEntered.countDown()
                    assertTrue(releaseMove.await(2, TimeUnit.SECONDS))
                }
            }
            assertTrue(moveEntered.await(2, TimeUnit.SECONDS))

            val recovery = executor.submit {
                recoveryStarted.countDown()
                StorageTrashIndex.withOperationLock {
                    recoveryEntered.countDown()
                }
            }
            assertTrue(recoveryStarted.await(2, TimeUnit.SECONDS))
            assertFalse(recoveryEntered.await(100, TimeUnit.MILLISECONDS))

            releaseMove.countDown()
            move.get(2, TimeUnit.SECONDS)
            recovery.get(2, TimeUnit.SECONDS)
            assertTrue(recoveryEntered.await(2, TimeUnit.SECONDS))
        } finally {
            releaseMove.countDown()
            executor.shutdownNow()
        }
    }

    private fun fingerprint(sha256: String? = "aabbcc"): StorageSourceFingerprint =
        StorageSourceFingerprint(
            sizeBytes = 42L,
            modifiedMillis = 1_700_000_000_000L,
            sha256 = sha256,
            documentId = "document-42",
        )
}
