package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageEmptyDirectoryPresentationTest {
    @Test
    fun explainsWhenTheScopeHasNoEmptyDirectories() {
        assertEquals(
            "Δεν βρέθηκαν κενοί φάκελοι στο ελεγχόμενο scope.",
            StorageIntelligencePresentation.emptyDirectorySummary(resultWithEmptyDirectories()),
        )
    }

    @Test
    fun reportsOneEmptyDirectoryPrecisely() {
        assertEquals(
            "Βρέθηκε 1 κενός φάκελος στο ελεγχόμενο scope.",
            StorageIntelligencePresentation.emptyDirectorySummary(
                resultWithEmptyDirectories(StorageDirectoryEntry("Empty")),
            ),
        )
    }

    @Test
    fun usesBoundedPluralWordingForSeveralEmptyDirectories() {
        assertEquals(
            "Βρέθηκαν έως 3 κενοί φάκελοι στο ελεγχόμενο scope.",
            StorageIntelligencePresentation.emptyDirectorySummary(
                resultWithEmptyDirectories(
                    StorageDirectoryEntry("One"),
                    StorageDirectoryEntry("Two"),
                    StorageDirectoryEntry("Three"),
                ),
            ),
        )
    }

    @Test
    fun doesNotClaimNoEmptyDirectoriesWhenScanWasTruncated() {
        assertEquals(
            "Δεν επιβεβαιώθηκαν κενοί φάκελοι: η σάρωση σταμάτησε πριν ελεγχθεί όλο το scope.",
            StorageIntelligencePresentation.emptyDirectorySummary(
                resultWithEmptyDirectories().copy(wasTruncated = true),
            ),
        )
    }

    private fun resultWithEmptyDirectories(
        vararg directories: StorageDirectoryEntry,
    ): StorageScanResult = StorageScanResult(
        rootName = "Test",
        scannedAtMillis = 0L,
        filesScanned = 0,
        directoriesScanned = 1,
        knownBytes = 0L,
        unknownSizeFileCount = 0,
        unreadableDirectoryCount = 0,
        wasTruncated = false,
        largestFiles = emptyList(),
        oldestFiles = emptyList(),
        sameSizeCandidates = emptyList(),
        emptyDirectories = directories.toList(),
    )
}
