package dev.devicecontrolcenter

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageIntelligenceTest {
    @Test
    fun summaryCallsOutWhenTheSafeEntryLimitWasReached() {
        val result = sampleResult(wasTruncated = true)

        assertEquals(
            "12 αρχεία · 4 φάκελοι · σταμάτησε στο όριο ασφαλείας",
            StorageIntelligencePresentation.summary(result),
        )
    }

    @Test
    fun equalSizeCandidatesArePresentedAsCandidatesNotDuplicates() {
        val group = StorageSizeGroup(
            sizeBytes = 2_048L,
            fileNames = listOf("a.bin", "b.bin"),
        )

        assertEquals("2 αρχεία · 2.0 KiB το καθένα", StorageIntelligencePresentation.sameSizeLabel(group))
    }

    @Test
    fun fileSizesUseAUnitThatDoesNotRoundSmallFilesToZeroGiB() {
        assertEquals("2.0 KiB", StorageIntelligencePresentation.storageSize(2_048L))
        assertEquals(
            "Μέγεθος μη διαθέσιμο",
            StorageIntelligencePresentation.fileSize(
                StorageFileEntry("unknown.bin", null, 0L),
            ),
        )
    }

    @Test
    fun knownSizeDoesNotClaimZeroWhenAllProviderSizesAreUnknown() {
        val result = sampleResult(wasTruncated = false).copy(
            knownBytes = 0L,
            unknownSizeFileCount = 3,
        )

        assertEquals(
            "Γνωστό μέγεθος: μη διαθέσιμο · 3 χωρίς διαθέσιμο μέγεθος",
            StorageIntelligencePresentation.knownSize(result),
        )
    }

    @Test
    fun modifiedAtUsesTheRequestedDeviceZone() {
        assertEquals(
            "Η ημερομηνία δεν αναφέρθηκε",
            StorageIntelligencePresentation.modifiedAt(0L, ZoneId.of("UTC")),
        )
        assertEquals(
            "01/01 01:00",
            StorageIntelligencePresentation.modifiedAt(3_600_000L, ZoneId.of("UTC")),
        )
    }

    private fun sampleResult(wasTruncated: Boolean): StorageScanResult = StorageScanResult(
        rootName = "Test",
        scannedAtMillis = 0L,
        filesScanned = 12,
        directoriesScanned = 4,
        knownBytes = 2_048L,
        unknownSizeFileCount = 0,
        unreadableDirectoryCount = 0,
        wasTruncated = wasTruncated,
        largestFiles = emptyList(),
        oldestFiles = emptyList(),
        sameSizeCandidates = emptyList(),
    )
}
