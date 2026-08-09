package dev.devicecontrolcenter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun fingerprint(sha256: String? = "aabbcc"): StorageSourceFingerprint =
        StorageSourceFingerprint(
            sizeBytes = 42L,
            modifiedMillis = 1_700_000_000_000L,
            sha256 = sha256,
            documentId = "document-42",
        )
}
