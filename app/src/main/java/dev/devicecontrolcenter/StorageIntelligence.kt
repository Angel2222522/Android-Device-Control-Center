package dev.devicecontrolcenter

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

data class StorageFileEntry(
    val name: String,
    val sizeBytes: Long?,
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

enum class StorageScanSource {
    SELECTED_FOLDER,
    SHARED_STORAGE,
}

data class StorageScanState(
    val selectedTreeUri: Uri? = null,
    val result: StorageScanResult? = null,
    val source: StorageScanSource? = null,
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

private class StorageScanAccumulator(
    private val rootName: String,
) {
    private companion object {
        const val RESULT_LIMIT = 5
    }

    private val files = mutableListOf<StorageFileEntry>()
    private val filesBySize = linkedMapOf<Long, MutableList<String>>()
    private var entriesSeen = 0
    private var directoriesScanned = 0
    private var unknownSizeFileCount = 0
    private var unreadableDirectoryCount = 0
    private var knownBytes = 0L

    var wasTruncated: Boolean = false
        private set

    fun acceptEntry(): Boolean {
        if (entriesSeen >= StorageScanner.MAX_ENTRIES) {
            wasTruncated = true
            return false
        }
        entriesSeen++
        return true
    }

    fun recordDirectory() {
        directoriesScanned++
    }

    fun recordUnreadableDirectory() {
        unreadableDirectoryCount++
    }

    fun recordFile(name: String, sizeBytes: Long?, lastModifiedMillis: Long) {
        val entry = StorageFileEntry(
            name = name,
            sizeBytes = sizeBytes,
            lastModifiedMillis = lastModifiedMillis,
        )
        files += entry
        if (sizeBytes == null) {
            unknownSizeFileCount++
        } else {
            knownBytes = knownBytes.saturatingAdd(sizeBytes)
            if (sizeBytes > 0L) {
                filesBySize.getOrPut(sizeBytes) { mutableListOf() }.add(name)
            }
        }
    }

    fun finish(): StorageScanResult {
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
            rootName = rootName,
            scannedAtMillis = System.currentTimeMillis(),
            filesScanned = files.size,
            directoriesScanned = directoriesScanned,
            knownBytes = knownBytes,
            unknownSizeFileCount = unknownSizeFileCount,
            unreadableDirectoryCount = unreadableDirectoryCount,
            wasTruncated = wasTruncated,
            largestFiles = files
                .filter { it.sizeBytes != null && it.sizeBytes > 0L }
                .sortedWith(
                    compareByDescending<StorageFileEntry> { it.sizeBytes ?: 0L }
                        .thenBy { it.name },
                )
                .take(RESULT_LIMIT),
            oldestFiles = oldest,
            sameSizeCandidates = sameSizeCandidates,
        )
    }

    private fun Long.saturatingAdd(value: Long): Long = when {
        value <= 0L -> this
        Long.MAX_VALUE - this < value -> Long.MAX_VALUE
        else -> this + value
    }
}

object StorageScanner {
    const val MAX_ENTRIES = 20_000

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
        val accumulator = StorageScanAccumulator(
            rootName = root.name?.takeIf(String::isNotBlank) ?: "Επιλεγμένος φάκελος",
        )
        pendingDirectories.addLast(root)

        while (pendingDirectories.isNotEmpty()) {
            check(shouldContinue()) { "Η σάρωση ακυρώθηκε." }

            val directory = pendingDirectories.removeFirst()
            if (!visitedDirectories.add(directory.uri.toString())) continue
            accumulator.recordDirectory()

            val children = runCatching { directory.listFiles() }.getOrElse {
                accumulator.recordUnreadableDirectory()
                emptyArray()
            }

            for (child in children) {
                check(shouldContinue()) { "Η σάρωση ακυρώθηκε." }
                if (!accumulator.acceptEntry()) break

                when {
                    child.isDirectory -> pendingDirectories.addLast(child)
                    child.isFile -> accumulator.recordFile(
                        name = child.name?.takeIf(String::isNotBlank) ?: "Χωρίς όνομα",
                        sizeBytes = StorageMetadataReader.documentSize(context, child),
                        lastModifiedMillis = child.lastModified(),
                    )
                }
            }

            if (accumulator.wasTruncated) break
        }

        return accumulator.finish()
    }
}

/**
 * Read-only metadata scan for shared storage after the user explicitly enables
 * MANAGE_EXTERNAL_STORAGE in Android settings. It never reads file contents or mutates files.
 */
object SharedStorageScanner {
    fun scan(
        context: Context,
        shouldContinue: () -> Boolean = { true },
    ): StorageScanResult {
        check(Environment.isExternalStorageManager()) {
            "Δεν έχει ενεργοποιηθεί η πλήρης πρόσβαση αρχείων."
        }

        val roots = context.getSystemService(StorageManager::class.java)
            ?.storageVolumes
            ?.mapNotNull { volume -> volume.directory }
            ?.filter(File::isDirectory)
            ?.distinctBy { canonicalPath(it) }
            .orEmpty()

        check(roots.isNotEmpty()) { "Δεν βρέθηκε προσβάσιμος κοινόχρηστος χώρος." }

        val pendingDirectories = ArrayDeque<File>()
        val visitedDirectories = mutableSetOf<String>()
        val accumulator = StorageScanAccumulator(rootName = "Κοινόχρηστοι χώροι")
        roots.forEach(pendingDirectories::addLast)

        while (pendingDirectories.isNotEmpty()) {
            check(shouldContinue()) { "Η σάρωση ακυρώθηκε." }

            val directory = pendingDirectories.removeFirst()
            if (!visitedDirectories.add(canonicalPath(directory))) continue
            accumulator.recordDirectory()

            val children = runCatching { directory.listFiles() }.getOrNull()
            if (children == null) {
                accumulator.recordUnreadableDirectory()
                continue
            }

            for (child in children) {
                check(shouldContinue()) { "Η σάρωση ακυρώθηκε." }
                if (!accumulator.acceptEntry()) break

                when {
                    child.isDirectory -> pendingDirectories.addLast(child)
                    child.isFile -> accumulator.recordFile(
                        name = displayPath(roots, child),
                        sizeBytes = child.length().coerceAtLeast(0L),
                        lastModifiedMillis = child.lastModified(),
                    )
                }
            }

            if (accumulator.wasTruncated) break
        }

        return accumulator.finish()
    }

    private fun canonicalPath(file: File): String = runCatching { file.canonicalPath }
        .getOrDefault(file.absolutePath)

    private fun displayPath(roots: List<File>, file: File): String {
        val root = roots.firstOrNull { root ->
            val rootPath = canonicalPath(root)
            val filePath = canonicalPath(file)
            filePath == rootPath || filePath.startsWith("$rootPath${File.separator}")
        }
        val rootLabel = root?.name?.takeIf(String::isNotBlank) ?: "Κοινόχρηστος χώρος"
        val relative = root?.let {
            runCatching { file.relativeTo(it).path }.getOrNull()
        }
        return if (relative.isNullOrBlank()) rootLabel else "$rootLabel/$relative"
    }
}

private object StorageMetadataReader {
    fun documentSize(context: Context, document: DocumentFile): Long? {
        val providerSize = runCatching {
            context.contentResolver.query(
                document.uri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
            }
        }.getOrNull()

        return providerSize?.takeIf { it >= 0L }
            ?: document.length().takeIf { it > 0L }
    }
}

object StorageIntelligencePresentation {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ROOT)

    fun summary(result: StorageScanResult): String = buildString {
        append("${result.filesScanned} αρχεία · ${result.directoriesScanned} φάκελοι")
        if (result.wasTruncated) append(" · σταμάτησε στο όριο ασφαλείας")
    }

    fun knownSize(result: StorageScanResult): String {
        val size = if (result.knownBytes > 0L) {
            storageSize(result.knownBytes)
        } else if (result.unknownSizeFileCount > 0) {
            "μη διαθέσιμο"
        } else {
            "0 B"
        }
        return "Γνωστό μέγεθος: $size" +
            if (result.unknownSizeFileCount > 0) {
                " · ${result.unknownSizeFileCount} χωρίς διαθέσιμο μέγεθος"
            } else {
                ""
            }
    }

    fun storageSize(bytes: Long): String = when {
        bytes < 1_024L -> "$bytes B"
        bytes < 1_024L * 1_024L -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1_024.0)
        bytes < 1_024L * 1_024L * 1_024L ->
            String.format(Locale.ROOT, "%.1f MiB", bytes / (1_024.0 * 1_024.0))

        else -> String.format(Locale.ROOT, "%.2f GiB", bytes / (1_024.0 * 1_024.0 * 1_024.0))
    }

    fun fileSize(entry: StorageFileEntry): String = entry.sizeBytes
        ?.takeIf { it >= 0L }
        ?.let(::storageSize)
        ?: "Μέγεθος μη διαθέσιμο"

    fun modifiedAt(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        if (millis <= 0L) "Η ημερομηνία δεν αναφέρθηκε" else {
            Instant.ofEpochMilli(millis).atZone(zoneId).format(dateFormatter)
        }

    fun sameSizeLabel(group: StorageSizeGroup): String =
        "${group.fileNames.size} αρχεία · ${storageSize(group.sizeBytes)} το καθένα"

    fun limitation(result: StorageScanResult): String = when {
        result.unreadableDirectoryCount > 0 ->
            "Δεν ήταν δυνατή η ανάγνωση ${result.unreadableDirectoryCount} φακέλων."

        result.wasTruncated ->
            "Η σάρωση περιορίστηκε στα ${StorageScanner.MAX_ENTRIES} entries για να παραμείνει ελεγχόμενη."

        else -> "Read-only σάρωση μεταδεδομένων · δεν διαβάστηκαν περιεχόμενα και δεν εκτελέστηκε καμία ενέργεια."
    }
}
