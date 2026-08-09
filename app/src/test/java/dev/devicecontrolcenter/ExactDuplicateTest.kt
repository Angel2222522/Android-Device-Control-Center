package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Test

class ExactDuplicateTest {
    @Test
    fun sha256DigestIsDeterministic() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            ExactDuplicateScanner.sha256Hex("hello".toByteArray()),
        )
    }

    @Test
    fun groupPresentationStatesExactContentEvidence() {
        val result = ExactDuplicateResult(
            scannedAtMillis = 0L,
            filesConsidered = 4,
            filesHashed = 4,
            bytesHashed = 4_096L,
            failedFileCount = 0,
            groups = listOf(
                ExactDuplicateGroup(
                    sizeBytes = 2_048L,
                    fileNames = listOf("a.bin", "b.bin"),
                ),
            ),
            wasTruncated = false,
        )

        assertEquals("1 ακριβείς ομάδες · 4 αρχεία ελέγχθηκαν με SHA-256", ExactDuplicatePresentation.summary(result))
        assertEquals("2 ίδια αρχεία · 2.0 KiB το καθένα", ExactDuplicatePresentation.groupLabel(result.groups.single()))
        assertEquals(
            "Ο έλεγχος συνέκρινε περιεχόμενο με SHA-256 και δεν διαγράφηκε ή μετακινήθηκε αρχείο.",
            ExactDuplicatePresentation.limitation(result),
        )
    }

    @Test
    fun limitationCallsOutAControlledScan() {
        val result = ExactDuplicateResult(
            scannedAtMillis = 0L,
            filesConsidered = 20_000,
            filesHashed = 10,
            bytesHashed = EXACT_DUPLICATE_MAX_HASH_BYTES,
            failedFileCount = 2,
            groups = emptyList(),
            wasTruncated = true,
        )

        assertEquals(
            "Ο έλεγχος συνέκρινε περιεχόμενο με SHA-256 και δεν διαγράφηκε ή μετακινήθηκε αρχείο. Η σάρωση περιορίστηκε για να παραμείνει ελεγχόμενη. Δεν διαβάστηκαν 2 αρχεία.",
            ExactDuplicatePresentation.limitation(result),
        )
    }
}
