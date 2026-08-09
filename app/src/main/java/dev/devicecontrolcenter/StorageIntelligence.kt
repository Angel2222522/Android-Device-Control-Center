package dev.devicecontrolcenter

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

data class StorageFileEntry(
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

data class StorageSizeGroup(
    val sizeBytes: Long,
    val fileNames: List<String>,
)

data class StorageScanResult(
    val rootName: String,
    val scannedAtMillis: Long,
    val filesScanned: Int,
    val directoriesScanned: Int,
    val knownBytes: Long,
    val unknownSizeFileCount: Int,
    val unreadableDirectoryCount: Int,
    val wasTruncated: Boolean,
    val largestFiles: List<StorageFileEntry>,
    val oldestFiles: List<StorageFileEntry>,
    val sameSizeCandidates: List<StorageSizeGroup>,
)

data class StorageScanState(
    val selectedTreeUri: Uri? = null,
    val result: StorageScanResult? = null,
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
)

object StorageSelectionStore {
    private const val PREFERENCES_NAME = "device_control_center_storage"
    private const val SELECTED_TREE_URI = "selected_tree_uri"

    fun read(context: Context): Uri? = preferences(context).getString(SELECTED_TREE_URI, null)
        ?.let(Uri::parse)

    fun save(context: Context, uri: Uri) {
        preferences(context).edit().putString(SELECTED_TREE_URI, uri.toString()).apply()
    }

    private fun preferences(context: Context): SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
}

object StorageScanner {
    const val MAX_ENTRIES = 20_000
    private const val RESULT_LIMIT = 5

    fun scan(
        context: Context,
        treeUri: Uri,
        shouldContinue: () -> Boolean = { true },
    ): StorageScanResult {
        val root = DocumentFile.fromTreeUri(context.applicationContext, treeUri)
            ?.takeIf { it.isDirectory }
            ?: error("Ο επιλεγμένος φάκελος δεν είναι πλέον διαθέσιμος.")

        val pendingDirectories = ArrayDeque<DocumentFile>()
        val visitedDirectories = mutableSetOf<String>()
        val files = mutableListOf<StorageFileEntry>()
        val filesBySize = linkedMapOf<Long, MutableList<String>>()
        pendingDirectories.addLast(root)

        var directoriesScanned = 0
        var unknownSizeFileCount = 0
        var unreadableDirectoryCount = 0
        var knownBytes = 0L
        var wasTruncated = false

        while (pendingDirectories.isNotEmpty()) {
            check(shouldContinue()) { "Η σάρωση ακυρώθηκε." }

            val directory = pendingDirectories.removeFirst()
            if (!visitedDirectories.add(directory.uri.toString())) continue
            directoriesScanned++

            val children = runCatching { directory.listFiles() }.getOrElse {
                unreadableDirectoryCount++
                emptyArray()
            }

            for (child in children) {
                check(shouldContinue()) { "Η σάρωση ακυρώθηκε." }
                if (files.size + pendingDirectories.size + directoriesScanned >= MAX_ENTRIES) {
                    wasTruncated = true
                    break
                }

                when {
                    child.isDirectory -> pendingDirectories.addLast(child)
                    child.isFile -> {
                        val name = child.name?.takeIf(String::isNotBlank) ?: "Χωρίς όνομα"
                        val size = child.length()
                        val modified = child.lastModified()
                        val entry = StorageFileEntry(
                            name = name,
                            sizeBytes = size.coerceAtLeast(0L),
                            lastModifiedMillis = modified,
                        )
                        files += entry
                        if (size > 0L) {
                            knownBytes = knownBytes.saturatingAdd(size)
                            filesBySize.getOrPut(size) { mutableListOf() }.add(name)
                        } else {
                            unknownSizeFileCount++
                        }
                    }
                }
            }

            if (wasTruncated) break
        }

        val oldest = files
            .asSequence()
            .filter { it.lastModifiedMillis > 0L }
            .sortedWith(compareBy<StorageFileEntry> { it.lastModifiedMillis }.thenBy { it.name })
            .take(RESULT_LIMIT)
            .toList()

        val sameSizeCandidates = filesBySize
            .asSequence()
            .filter { (_, names) -> names.size >= 2 }
            .sortedWith(
                compareByDescending<Map.Entry<Long, MutableList<String>>> { it.value.size }
                    .thenBy { it.key },
            )
            .take(RESULT_LIMIT)
            .map { (size, names) -> StorageSizeGroup(size, names.take(RESULT_LIMIT)) }
            .toList()

        return StorageScanResult(
            rootName = root.name?.takeIf(String::isNotBlank) ?: "Επιλεγμένος φάκελος",
            scannedAtMillis = System.currentTimeMillis(),
            filesScanned = files.size,
            directoriesScanned = directoriesScanned,
            knownBytes = knownBytes,
            unknownSizeFileCount = unknownSizeFileCount,
            unreadableDirectoryCount = unreadableDirectoryCount,
            wasTruncated = wasTruncated,
            largestFiles = files
                .filter { it.sizeBytes > 0L }
                .sortedWith(compareByDescending<StorageFileEntry> { it.sizeBytes }.thenBy { it.name })
                .take(RESULT_LIMIT),
            oldestFiles = oldest,
            sameSizeCandidates = sameSizeCandidates,
        )
    }

    private fun Long.saturatingAdd(value: Long): Long =
        if (Long.MAX_VALUE - this < value) Long.MAX_VALUE else this + value
}

object StorageIntelligencePresentation {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ROOT)

    fun summary(result: StorageScanResult): String = buildString {
        append("${result.filesScanned} αρχεία · ${result.directoriesScanned} φάκελοι")
        if (result.wasTruncated) append(" · σταμάτησε στο όριο ασφαλείας")
    }

    fun knownSize(result: StorageScanResult): String =
        "Γνωστό μέγεθος: ${SnapshotPresentation.gib(result.knownBytes)}" +
            if (result.unknownSizeFileCount > 0) {
                " · ${result.unknownSizeFileCount} χωρίς διαθέσιμο μέγεθος"
            } else {
                ""
            }

    fun fileSize(entry: StorageFileEntry): String =
        entry.sizeBytes.takeIf { it > 0L }?.let(SnapshotPresentation::gib)
            ?: "Μέγεθος μη διαθέσιμο"

    fun modifiedAt(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        if (millis <= 0L) "Η ημερομηνία δεν αναφέρθηκε" else {
            Instant.ofEpochMilli(millis).atZone(zoneId).format(dateFormatter)
        }

    fun sameSizeLabel(group: StorageSizeGroup): String =
        "${group.fileNames.size} αρχεία · ${SnapshotPresentation.gib(group.sizeBytes)} το καθένα"

    fun limitation(result: StorageScanResult): String = when {
        result.unreadableDirectoryCount > 0 ->
            "Δεν ήταν δυνατή η ανάγνωση ${result.unreadableDirectoryCount} φακέλων."

        result.wasTruncated ->
            "Η σάρωση περιορίστηκε στα ${StorageScanner.MAX_ENTRIES} entries για να παραμείνει ελεγχόμενη."

        else -> "Read-only σάρωση μεταδεδομένων · δεν διαβάστηκαν περιεχόμενα και δεν εκτελέστηκε καμία ενέργεια."
    }
}
