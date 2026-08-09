package dev.devicecontrolcenter

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

data class StorageFileEntry(
    val name: String,
    val sizeBytes: Long?,
    val lastModifiedMillis: Long,
    val uriString: String? = null,
    val parentUriString: String? = null,
)

enum class StorageFileCategory {
    PHOTO,
    VIDEO,
    AUDIO,
    DOCUMENT,
    DOWNLOAD,
    APK,
    OTHER,
}

data class StorageSizeGroup(
    val sizeBytes: Long,
    val fileNames: List<String>,
    val entries: List<StorageFileEntry> = emptyList(),
)

data class StorageCategorySummary(
    val category: StorageFileCategory,
    val fileCount: Int,
    val knownBytes: Long,
)

data class StorageDirectoryEntry(
    val name: String,
    val uriString: String? = null,
    val parentUriString: String? = null,
    val lastModifiedMillis: Long = 0L,
)

data class ExactDuplicateGroup(
    val sha256: String,
    val sizeBytes: Long,
    val entries: List<StorageFileEntry>,
)

data class ExactDuplicateScanResult(
    val groups: List<ExactDuplicateGroup> = emptyList(),
    val filesHashed: Int = 0,
    val skippedFileCount: Int = 0,
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
    val categories: List<StorageCategorySummary> = emptyList(),
    val exactDuplicates: ExactDuplicateScanResult? = null,
    val emptyDirectories: List<StorageDirectoryEntry> = emptyList(),
    val hashCandidates: List<StorageFileEntry> = emptyList(),
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
    val isHashing: Boolean = false,
    val exactDuplicateResult: ExactDuplicateScanResult? = null,
    val lastTrashItem: StorageTrashItem? = null,
    val trashItems: List<StorageTrashItem> = emptyList(),
    val canWriteSource: Boolean = false,
    val isMutating: Boolean = false,
    val actionMessage: String? = null,
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

    fun clear(context: Context) {
        preferences(context).edit().remove(SELECTED_TREE_URI).apply()
    }

    fun hasReadAccess(context: Context, uri: Uri): Boolean = context.contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission
    }

    fun hasWriteAccess(context: Context, uri: Uri): Boolean = context.contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isWritePermission
    }

    private fun preferences(context: Context): SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
}

internal data class StorageDocumentChild(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModifiedMillis: Long,
)

internal enum class DocumentChildEnumerationResult {
    COMPLETED,
    UNREADABLE,
    STOPPED,
}

private val documentChildProjection = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_SIZE,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
)

/**
 * Streams direct SAF children from the provider cursor. It deliberately does not use
 * [DocumentFile.listFiles], whose array materialization happens before callers can apply a cap.
 */
internal fun enumerateDocumentChildren(
    context: Context,
    directoryUri: Uri,
    shouldContinue: () -> Unit,
    onChild: (StorageDocumentChild) -> Boolean,
): DocumentChildEnumerationResult {
    val childrenUri = try {
        buildChildDocumentsUri(directoryUri)
    } catch (_: Exception) {
        return DocumentChildEnumerationResult.UNREADABLE
    }
    val cursor = try {
        context.contentResolver.query(childrenUri, documentChildProjection, null, null, null)
    } catch (_: Exception) {
        return DocumentChildEnumerationResult.UNREADABLE
    } ?: return DocumentChildEnumerationResult.UNREADABLE

    var result = DocumentChildEnumerationResult.COMPLETED
    cursor.use { childCursor ->
        val documentIdIndex = childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIndex = childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeTypeIndex = childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeIndex = childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val lastModifiedIndex = childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        if (documentIdIndex < 0 || nameIndex < 0 || mimeTypeIndex < 0 || sizeIndex < 0 || lastModifiedIndex < 0) {
            result = DocumentChildEnumerationResult.UNREADABLE
            return@use
        }

        while (true) {
            shouldContinue()
            val hasNext = try {
                childCursor.moveToNext()
            } catch (_: Exception) {
                result = DocumentChildEnumerationResult.UNREADABLE
                break
            }
            if (!hasNext) break

            val documentId = try {
                childCursor.getString(documentIdIndex)?.takeIf(String::isNotBlank)
            } catch (_: Exception) {
                result = DocumentChildEnumerationResult.UNREADABLE
                break
            }
            if (documentId == null) {
                result = DocumentChildEnumerationResult.UNREADABLE
                break
            }

            val child = try {
                StorageDocumentChild(
                    uri = buildChildDocumentUri(directoryUri, documentId),
                    name = childCursor.getString(nameIndex)?.takeIf(String::isNotBlank) ?: "Χωρίς όνομα",
                    mimeType = childCursor.getString(mimeTypeIndex),
                    sizeBytes = if (childCursor.isNull(sizeIndex)) null else childCursor.getLong(sizeIndex),
                    lastModifiedMillis = if (childCursor.isNull(lastModifiedIndex)) 0L else childCursor.getLong(lastModifiedIndex),
                )
            } catch (_: Exception) {
                result = DocumentChildEnumerationResult.UNREADABLE
                break
            }

            if (!onChild(child)) {
                result = DocumentChildEnumerationResult.STOPPED
                break
            }
        }
    }
    return result
}

private fun buildChildDocumentsUri(directoryUri: Uri): Uri {
    val documentId = DocumentsContract.getDocumentId(directoryUri)
    return if (DocumentsContract.isTreeUri(directoryUri)) {
        DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, documentId)
    } else {
        DocumentsContract.buildChildDocumentsUri(directoryUri.authority ?: error("Μη έγκυρο URI φακέλου."), documentId)
    }
}

private fun buildChildDocumentUri(directoryUri: Uri, documentId: String): Uri =
    if (DocumentsContract.isTreeUri(directoryUri)) {
        DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId)
    } else {
        DocumentsContract.buildDocumentUri(directoryUri.authority ?: error("Μη έγκυρο URI φακέλου."), documentId)
    }

private class StorageScanAccumulator(
    private val rootName: String,
) {
    private companion object {
        const val RESULT_LIMIT = 5
    }

    private val files = mutableListOf<StorageFileEntry>()
    private val directories = linkedMapOf<String, StorageDirectoryEntry>()
    private val directoryChildCounts = linkedMapOf<String, Int>()
    private val unreadableDirectoryUris = mutableSetOf<String>()
    private val filesBySize = linkedMapOf<Long, MutableList<StorageFileEntry>>()
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

    fun recordDirectory(
        name: String,
        uriString: String,
        parentUriString: String? = null,
        lastModifiedMillis: Long = 0L,
    ) {
        directoriesScanned++
        directories.putIfAbsent(
            uriString,
            StorageDirectoryEntry(
                name = name,
                uriString = uriString,
                parentUriString = parentUriString,
                lastModifiedMillis = lastModifiedMillis,
            ),
        )
        directoryChildCounts.putIfAbsent(uriString, 0)
    }

    fun recordChild(parentUriString: String) {
        directoryChildCounts[parentUriString] = (directoryChildCounts[parentUriString] ?: 0) + 1
    }

    fun recordUnreadableDirectory(uriString: String) {
        if (unreadableDirectoryUris.add(uriString)) {
            unreadableDirectoryCount++
        }
    }

    fun recordFile(
        name: String,
        sizeBytes: Long?,
        lastModifiedMillis: Long,
        uriString: String? = null,
        parentUriString: String? = null,
    ) {
        val entry = StorageFileEntry(
            name = name,
            sizeBytes = sizeBytes,
            lastModifiedMillis = lastModifiedMillis,
            uriString = uriString,
            parentUriString = parentUriString,
        )
        files += entry
        if (sizeBytes == null) {
            unknownSizeFileCount++
        } else {
            knownBytes = knownBytes.saturatingAdd(sizeBytes)
            if (sizeBytes > 0L) {
                filesBySize.getOrPut(sizeBytes) { mutableListOf() }.add(entry)
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
            .filter { (_, entries) -> entries.size >= 2 }
            .sortedWith(
                compareByDescending<Map.Entry<Long, MutableList<StorageFileEntry>>> { it.value.size }
                    .thenBy { it.key },
            )
            .take(RESULT_LIMIT)
            .map { (size, entries) ->
                val selected = entries.take(RESULT_LIMIT)
                StorageSizeGroup(size, selected.map(StorageFileEntry::name), selected)
            }
            .toList()

        val hashCandidates = filesBySize
            .asSequence()
            .filter { (_, entries) -> entries.size >= 2 }
            .flatMap { (_, entries) -> entries.asSequence() }
            .toList()

        val categories = files
            .groupBy { categoryFor(it.name) }
            .map { (category, entries) ->
                StorageCategorySummary(
                    category = category,
                    fileCount = entries.size,
                    knownBytes = entries.mapNotNull(StorageFileEntry::sizeBytes)
                        .fold(0L) { sum, value -> sum.saturatingAdd(value) },
                )
            }
            .sortedByDescending(StorageCategorySummary::knownBytes)

        val emptyDirectories = directories
            .asSequence()
            .filter { (uri, _) ->
                directoryChildCounts[uri] == 0 && uri !in unreadableDirectoryUris
            }
            .map { it.value }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .take(RESULT_LIMIT)
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
            categories = categories,
            emptyDirectories = emptyDirectories,
            hashCandidates = hashCandidates,
        )
    }

    private fun categoryFor(name: String): StorageFileCategory {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.contains("/download") || lower.startsWith("download") -> StorageFileCategory.DOWNLOAD
            lower.endsWith(".apk") || lower.endsWith(".apks") || lower.endsWith(".xapk") -> StorageFileCategory.APK
            setOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".gif", ".dng").any { lower.endsWith(it) } ->
                StorageFileCategory.PHOTO
            setOf(".mp4", ".mkv", ".webm", ".mov", ".avi", ".3gp").any { lower.endsWith(it) } ->
                StorageFileCategory.VIDEO
            setOf(".mp3", ".m4a", ".wav", ".flac", ".ogg", ".aac").any { lower.endsWith(it) } ->
                StorageFileCategory.AUDIO
            setOf(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv", ".zip", ".rar").any { lower.endsWith(it) } ->
                StorageFileCategory.DOCUMENT
            else -> StorageFileCategory.OTHER
        }
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

        data class PendingDocumentDirectory(
            val uri: Uri,
            val name: String,
            val lastModifiedMillis: Long,
            val parentUriString: String?,
        )

        val pendingDirectories = ArrayDeque<PendingDocumentDirectory>()
        val visitedDirectories = mutableSetOf<String>()
        val accumulator = StorageScanAccumulator(
            rootName = root.name?.takeIf(String::isNotBlank) ?: "Επιλεγμένος φάκελος",
        )
        pendingDirectories.addLast(
            PendingDocumentDirectory(
                uri = root.uri,
                name = root.name?.takeIf(String::isNotBlank) ?: "Επιλεγμένος φάκελος",
                lastModifiedMillis = root.lastModified(),
                parentUriString = null,
            ),
        )

        while (pendingDirectories.isNotEmpty()) {
            check(shouldContinue()) { "Η σάρωση ακυρώθηκε." }

            val pending = pendingDirectories.removeFirst()
            if (!visitedDirectories.add(pending.uri.toString())) continue
            accumulator.recordDirectory(
                name = pending.name,
                uriString = pending.uri.toString(),
                parentUriString = pending.parentUriString,
                lastModifiedMillis = pending.lastModifiedMillis,
            )

            when (
                enumerateDocumentChildren(
                    context = context,
                    directoryUri = pending.uri,
                    shouldContinue = { check(shouldContinue()) { "Η σάρωση ακυρώθηκε." } },
                    onChild = { child ->
                        accumulator.recordChild(pending.uri.toString())
                        if (!accumulator.acceptEntry()) {
                            false
                        } else {
                            if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                                pendingDirectories.addLast(
                                    PendingDocumentDirectory(
                                        uri = child.uri,
                                        name = child.name,
                                        lastModifiedMillis = child.lastModifiedMillis,
                                        parentUriString = pending.uri.toString(),
                                    ),
                                )
                            } else {
                                accumulator.recordFile(
                                    name = child.name,
                                    sizeBytes = StorageMetadataReader.documentSize(context, child),
                                    lastModifiedMillis = child.lastModifiedMillis,
                                    uriString = child.uri.toString(),
                                    parentUriString = pending.uri.toString(),
                                )
                            }
                            true
                        }
                    },
                ),
            ) {
                DocumentChildEnumerationResult.UNREADABLE ->
                    accumulator.recordUnreadableDirectory(pending.uri.toString())

                DocumentChildEnumerationResult.COMPLETED,
                DocumentChildEnumerationResult.STOPPED,
                -> Unit
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

        data class PendingFileDirectory(val directory: File, val parentUriString: String?)

        val pendingDirectories = ArrayDeque<PendingFileDirectory>()
        val visitedDirectories = mutableSetOf<String>()
        val accumulator = StorageScanAccumulator(rootName = "Κοινόχρηστοι χώροι")
        roots.forEach { root ->
            pendingDirectories.addLast(PendingFileDirectory(root, null))
        }

        while (pendingDirectories.isNotEmpty()) {
            check(shouldContinue()) { "Η σάρωση ακυρώθηκε." }

            val pending = pendingDirectories.removeFirst()
            val directory = pending.directory
            if (!visitedDirectories.add(canonicalPath(directory))) continue
            accumulator.recordDirectory(
                name = directory.name.takeIf(String::isNotBlank) ?: "Χωρίς όνομα",
                uriString = directory.toURI().toString(),
                parentUriString = pending.parentUriString,
                lastModifiedMillis = directory.lastModified(),
            )

            val children = runCatching { Files.newDirectoryStream(directory.toPath()) }.getOrNull()
            if (children == null) {
                accumulator.recordUnreadableDirectory(directory.toURI().toString())
                continue
            }

            try {
                val iterator = runCatching { children.iterator() }.getOrNull()
                if (iterator == null) {
                    accumulator.recordUnreadableDirectory(directory.toURI().toString())
                } else {
                    while (true) {
                        check(shouldContinue()) { "Η σάρωση ακυρώθηκε." }
                        val hasNext = try {
                            iterator.hasNext()
                        } catch (_: Exception) {
                            accumulator.recordUnreadableDirectory(directory.toURI().toString())
                            break
                        }
                        if (!hasNext) break

                        val child = try {
                            iterator.next().toFile()
                        } catch (_: Exception) {
                            accumulator.recordUnreadableDirectory(directory.toURI().toString())
                            break
                        }
                        accumulator.recordChild(directory.toURI().toString())
                        if (!accumulator.acceptEntry()) break

                        when {
                            child.isDirectory -> pendingDirectories.addLast(
                                PendingFileDirectory(child, directory.toURI().toString()),
                            )
                            child.isFile -> accumulator.recordFile(
                                name = displayPath(roots, child),
                                sizeBytes = child.length().coerceAtLeast(0L),
                                lastModifiedMillis = child.lastModified(),
                                uriString = child.toURI().toString(),
                                parentUriString = child.parentFile?.toURI()?.toString(),
                            )
                        }
                    }
                }
            } finally {
                runCatching { children.close() }.onFailure {
                    accumulator.recordUnreadableDirectory(directory.toURI().toString())
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
    fun documentSize(context: Context, child: StorageDocumentChild): Long? =
        child.sizeBytes?.takeIf { it >= 0L }
            ?: DocumentFile.fromSingleUri(context, child.uri)?.length()?.takeIf { it >= 0L }
}

object StorageIntelligencePresentation {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ROOT)

    fun summary(result: StorageScanResult): String = buildString {
        append("${result.filesScanned} αρχεία · ${result.directoriesScanned} φάκελοι")
        if (result.unknownSizeFileCount > 0) {
            append(" · ${result.unknownSizeFileCount} μεγέθη μη διαθέσιμα")
        }
        if (result.unreadableDirectoryCount > 0) {
            append(" · ${result.unreadableDirectoryCount} φάκελοι μη αναγνώσιμοι")
        }
        if (result.wasTruncated) append(" · ελέγχθηκε μόνο μέρος στο όριο ασφαλείας")
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

    fun categoryLabel(category: StorageFileCategory): String = when (category) {
        StorageFileCategory.PHOTO -> "Φωτογραφίες"
        StorageFileCategory.VIDEO -> "Βίντεο"
        StorageFileCategory.AUDIO -> "Ήχος"
        StorageFileCategory.DOCUMENT -> "Έγγραφα / αρχεία"
        StorageFileCategory.DOWNLOAD -> "Λήψεις"
        StorageFileCategory.APK -> "Εγκαταστάτες APK"
        StorageFileCategory.OTHER -> "Λοιπά"
    }

    fun emptyDirectorySummary(result: StorageScanResult): String = when {
        result.unreadableDirectoryCount > 0 && result.emptyDirectories.isEmpty() ->
            "Δεν επιβεβαιώθηκαν κενοί φάκελοι: ${result.unreadableDirectoryCount} φάκελοι δεν ήταν αναγνώσιμοι."

        result.wasTruncated && result.emptyDirectories.isEmpty() ->
            "Δεν επιβεβαιώθηκαν κενοί φάκελοι: η σάρωση σταμάτησε πριν ελεγχθεί όλο το scope."

        result.unreadableDirectoryCount > 0 || result.wasTruncated ->
            "Βρέθηκαν έως ${result.emptyDirectories.size} κενοί φάκελοι· ο έλεγχος δεν κάλυψε όλο το scope."

        result.emptyDirectories.isEmpty() -> "Δεν βρέθηκαν κενοί φάκελοι στο ελεγχόμενο scope."
        result.emptyDirectories.size == 1 -> "Βρέθηκε 1 κενός φάκελος στο ελεγχόμενο scope."
        else -> "Βρέθηκαν έως ${result.emptyDirectories.size} κενοί φάκελοι στο ελεγχόμενο scope."
    }

    fun duplicateSummary(result: ExactDuplicateScanResult): String = when {
        result.groups.isEmpty() && result.skippedFileCount == 0 ->
            "Δεν επιβεβαιώθηκε ακριβές διπλότυπο στα αρχεία που ελέγχθηκαν."

        result.groups.isEmpty() ->
            "Δεν βρέθηκαν διπλότυπα· ${result.skippedFileCount} αρχεία δεν ελέγχθηκαν."

        else -> "${result.groups.size} ομάδες ακριβών διπλοτύπων · ${result.filesHashed} αρχεία με SHA-256"
    }

    fun limitation(result: StorageScanResult): String = buildString {
        if (result.unreadableDirectoryCount > 0) {
            append("Δεν ήταν δυνατή η ανάγνωση ${result.unreadableDirectoryCount} φακέλων.")
        }
        if (result.wasTruncated) {
            if (isNotEmpty()) append(" ")
            append("Η σάρωση σταμάτησε στο όριο των ${StorageScanner.MAX_ENTRIES} entries· ο αριθμός των μη ελεγμένων entries δεν είναι γνωστός.")
        }
        if (isEmpty()) {
            append("Read-only σάρωση μεταδεδομένων · δεν διαβάστηκαν περιεχόμενα και δεν εκτελέστηκε καμία ενέργεια.")
        }
    }
}
