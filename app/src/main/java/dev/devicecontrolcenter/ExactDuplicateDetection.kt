package dev.devicecontrolcenter

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.min

const val EXACT_DUPLICATE_MAX_ENTRIES = 20_000
const val EXACT_DUPLICATE_MAX_HASH_BYTES = 256L * 1_024L * 1_024L

data class ExactDuplicateGroup(
    val sizeBytes: Long,
    val fileNames: List<String>,
    val digest: String = "",
    val fileUris: List<String> = emptyList(),
)

data class ExactDuplicateResult(
    val scannedAtMillis: Long,
    val filesConsidered: Int,
    val filesHashed: Int,
    val bytesHashed: Long,
    val failedFileCount: Int,
    val groups: List<ExactDuplicateGroup>,
    val wasTruncated: Boolean,
)

data class ExactDuplicateUiState(
    val result: ExactDuplicateResult? = null,
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
    val cleanupMessage: String? = null,
)

data class ExactCleanupResult(
    val deletedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
)

private data class DuplicateCandidate(
    val name: String,
    val sizeBytes: Long,
    val uri: Uri?,
    val openStream: () -> InputStream?,
)

private data class DuplicateMatch(
    val name: String,
    val uri: Uri?,
)

private data class CandidateCollection(
    val groupsBySize: Map<Long, List<DuplicateCandidate>>,
    val filesConsidered: Int,
    val wasTruncated: Boolean,
)

internal data class HashOutcome(
    val digest: String,
    val bytesRead: Long,
    val complete: Boolean,
)

object ExactDuplicateScanner {
    private const val RESULT_GROUP_LIMIT = 10
    private const val BUFFER_SIZE = 64 * 1_024

    fun scan(
        context: Context,
        source: StorageScanSource,
        selectedTreeUri: Uri?,
        shouldContinue: () -> Boolean = { true },
    ): ExactDuplicateResult {
        val candidates = when (source) {
            StorageScanSource.SELECTED_FOLDER -> collectSelectedFolder(context, selectedTreeUri, shouldContinue)
            StorageScanSource.SHARED_STORAGE -> collectSharedStorage(context, shouldContinue)
        }

        var wasTruncated = candidates.wasTruncated
        var bytesHashed = 0L
        var filesHashed = 0
        var failedFileCount = 0
        val confirmedGroups = mutableListOf<ExactDuplicateGroup>()

        val candidateGroups = candidates.groupsBySize
            .asSequence()
            .filter { (_, entries) -> entries.size >= 2 }
            .sortedWith(
                compareByDescending<Map.Entry<Long, List<DuplicateCandidate>>> { it.value.size }
                    .thenBy { it.key },
            )

        for ((sizeBytes, entries) in candidateGroups) {
            if (bytesHashed >= EXACT_DUPLICATE_MAX_HASH_BYTES) {
                wasTruncated = true
                break
            }

            val matchesByDigest = linkedMapOf<String, MutableList<DuplicateMatch>>()
            for (candidate in entries) {
                check(shouldContinue())
                val remainingBytes = EXACT_DUPLICATE_MAX_HASH_BYTES - bytesHashed
                if (remainingBytes <= 0L) {
                    wasTruncated = true
                    break
                }

                val outcome = runCatching {
                    hash(
                        openStream = candidate.openStream,
                        maxBytes = remainingBytes,
                        shouldContinue = shouldContinue,
                    )
                }.getOrNull()

                if (outcome == null) {
                    failedFileCount++
                    continue
                }

                bytesHashed += outcome.bytesRead
                if (!outcome.complete) {
                    wasTruncated = true
                    break
                }

                filesHashed++
                matchesByDigest.getOrPut(outcome.digest) { mutableListOf() }.add(
                    DuplicateMatch(candidate.name, candidate.uri),
                )
            }

            matchesByDigest
                .asSequence()
                .filter { (_, matches) -> matches.size >= 2 }
                .map { (digest, matches) ->
                    ExactDuplicateGroup(
                        sizeBytes = sizeBytes,
                        fileNames = matches.map { it.name }.take(RESULT_GROUP_LIMIT),
                        digest = digest,
                        fileUris = matches.mapNotNull { it.uri?.toString() }.take(RESULT_GROUP_LIMIT),
                    )
                }
                .forEach(confirmedGroups::add)

            if (wasTruncated) break
        }

        return ExactDuplicateResult(
            scannedAtMillis = System.currentTimeMillis(),
            filesConsidered = candidates.filesConsidered,
            filesHashed = filesHashed,
            bytesHashed = bytesHashed,
            failedFileCount = failedFileCount,
            groups = confirmedGroups
                .sortedWith(compareByDescending<ExactDuplicateGroup> { it.fileNames.size }.thenBy { it.sizeBytes })
                .take(RESULT_GROUP_LIMIT),
            wasTruncated = wasTruncated,
        )
    }

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    internal fun hash(
        openStream: () -> InputStream?,
        maxBytes: Long,
        shouldContinue: () -> Boolean,
    ): HashOutcome {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        val input = openStream() ?: error("Δεν ήταν δυνατή η ανάγνωση του αρχείου.")
        input.use {
            var bytesRead = 0L
            while (true) {
                check(shouldContinue())
                val available = maxBytes - bytesRead
                if (available <= 0L) {
                    val hasMore = it.read() >= 0
                    return HashOutcome(
                        digest = digest.digest().toHex(),
                        bytesRead = bytesRead,
                        complete = !hasMore,
                    )
                }

                val read = it.read(buffer, 0, min(buffer.size.toLong(), available).toInt())
                if (read < 0) {
                    return HashOutcome(
                        digest = digest.digest().toHex(),
                        bytesRead = bytesRead,
                        complete = true,
                    )
                }
                if (read == 0) continue
                digest.update(buffer, 0, read)
                bytesRead += read
            }
        }
    }

    internal fun documentSize(context: Context, document: DocumentFile): Long? =
        runCatching {
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
        }.getOrNull()?.takeIf { it >= 0L }
            ?: document.length().takeIf { it > 0L }

    private fun collectSelectedFolder(
        context: Context,
        selectedTreeUri: Uri?,
        shouldContinue: () -> Boolean,
    ): CandidateCollection {
        val root = selectedTreeUri
            ?.let { DocumentFile.fromTreeUri(context.applicationContext, it) }
            ?.takeIf { it.isDirectory }
            ?: error("Ο επιλεγμένος φάκελος δεν είναι πλέον διαθέσιμος.")

        val pending = ArrayDeque<DocumentFile>()
        val visited = mutableSetOf<String>()
        val groups = linkedMapOf<Long, MutableList<DuplicateCandidate>>()
        var entriesSeen = 0
        var wasTruncated = false
        pending.addLast(root)

        while (pending.isNotEmpty()) {
            check(shouldContinue())
            val directory = pending.removeFirst()
            if (!visited.add(directory.uri.toString())) continue

            val children = runCatching { directory.listFiles() }.getOrElse { emptyArray() }
            for (child in children) {
                check(shouldContinue())
                if (entriesSeen >= EXACT_DUPLICATE_MAX_ENTRIES) {
                    wasTruncated = true
                    break
                }
                entriesSeen++

                when {
                    child.isDirectory -> pending.addLast(child)
                    child.isFile -> {
                        documentSize(context, child)?.takeIf { it > 0L }?.let { size ->
                            groups.getOrPut(size) { mutableListOf() }.add(
                                DuplicateCandidate(
                                    name = child.name?.takeIf(String::isNotBlank) ?: "Χωρίς όνομα",
                                    sizeBytes = size,
                                    uri = child.uri,
                                    openStream = { context.contentResolver.openInputStream(child.uri) },
                                ),
                            )
                        }
                    }
                }
            }
            if (wasTruncated) break
        }

        return CandidateCollection(groups, entriesSeen, wasTruncated)
    }

    private fun collectSharedStorage(
        context: Context,
        shouldContinue: () -> Boolean,
    ): CandidateCollection {
        check(Environment.isExternalStorageManager()) {
            "Δεν έχει ενεργοποιηθεί η πλήρης πρόσβαση αρχείων."
        }

        val roots = context.getSystemService(StorageManager::class.java)
            ?.storageVolumes
            ?.mapNotNull { it.directory }
            ?.filter(File::isDirectory)
            ?.distinctBy { canonicalPath(it) }
            .orEmpty()
        check(roots.isNotEmpty()) { "Δεν βρέθηκε προσβάσιμος κοινόχρηστος χώρος." }

        val pending = ArrayDeque<File>()
        val visited = mutableSetOf<String>()
        val groups = linkedMapOf<Long, MutableList<DuplicateCandidate>>()
        var entriesSeen = 0
        var wasTruncated = false
        roots.forEach(pending::addLast)

        while (pending.isNotEmpty()) {
            check(shouldContinue())
            val directory = pending.removeFirst()
            if (!visited.add(canonicalPath(directory))) continue

            val children = directory.listFiles() ?: emptyArray()
            for (child in children) {
                check(shouldContinue())
                if (entriesSeen >= EXACT_DUPLICATE_MAX_ENTRIES) {
                    wasTruncated = true
                    break
                }
                entriesSeen++

                when {
                    child.isDirectory -> pending.addLast(child)
                    child.isFile -> {
                        val size = child.length().takeIf { it > 0L } ?: continue
                        groups.getOrPut(size) { mutableListOf() }.add(
                            DuplicateCandidate(
                                name = displayPath(roots, child),
                                sizeBytes = size,
                                uri = null,
                                openStream = { FileInputStream(child) },
                            ),
                        )
                    }
                }
            }
            if (wasTruncated) break
        }

        return CandidateCollection(groups, entriesSeen, wasTruncated)
    }

    private fun canonicalPath(file: File): String = runCatching { file.canonicalPath }
        .getOrDefault(file.absolutePath)

    private fun displayPath(roots: List<File>, file: File): String {
        val root = roots.firstOrNull { root ->
            val rootPath = canonicalPath(root)
            val filePath = canonicalPath(file)
            filePath == rootPath || filePath.startsWith(rootPath + File.separator)
        }
        val rootLabel = root?.name?.takeIf(String::isNotBlank) ?: "Κοινόχρηστος χώρος"
        val relative = root?.let { runCatching { file.relativeTo(it).path }.getOrNull() }
        return if (relative.isNullOrBlank()) rootLabel else rootLabel + "/" + relative
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
}

object ExactDuplicateCleanup {
    fun deleteSelectedFolderDuplicates(
        context: Context,
        selectedTreeUri: Uri,
        result: ExactDuplicateResult,
        shouldContinue: () -> Boolean = { true },
    ): ExactCleanupResult {
        val hasWriteGrant = context.contentResolver.persistedUriPermissions.any {
            it.uri == selectedTreeUri && it.isWritePermission
        }
        check(hasWriteGrant) {
            "Δεν υπάρχει αποθηκευμένη εγγραφή για διαγραφή στον επιλεγμένο φάκελο."
        }

        var deletedCount = 0
        var skippedCount = 0
        var failedCount = 0

        result.groups.forEach { group ->
            group.fileUris.drop(1).forEach { uriString ->
                check(shouldContinue())
                val document = DocumentFile.fromSingleUri(context, Uri.parse(uriString))
                if (document == null || !document.exists()) {
                    skippedCount++
                    return@forEach
                }
                val currentSize = ExactDuplicateScanner.documentSize(context, document)
                if (currentSize != group.sizeBytes || group.digest.isBlank()) {
                    skippedCount++
                    return@forEach
                }
                val verified = runCatching {
                    ExactDuplicateScanner.hash(
                        openStream = { context.contentResolver.openInputStream(document.uri) },
                        maxBytes = group.sizeBytes + 1L,
                        shouldContinue = shouldContinue,
                    )
                }.getOrNull()
                if (verified == null || !verified.complete || verified.digest != group.digest) {
                    skippedCount++
                    return@forEach
                }
                when {
                    runCatching { document.delete() }.getOrDefault(false) -> deletedCount++
                    else -> failedCount++
                }
            }
        }

        return ExactCleanupResult(
            deletedCount = deletedCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
        )
    }
}

object ExactDuplicatePresentation {
    fun summary(result: ExactDuplicateResult): String =
        result.groups.size.toString() + " ακριβείς ομάδες · " +
            result.filesHashed + " αρχεία ελέγχθηκαν με SHA-256"

    fun groupLabel(group: ExactDuplicateGroup): String =
        group.fileNames.size.toString() + " ίδια αρχεία · " +
            StorageIntelligencePresentation.storageSize(group.sizeBytes) + " το καθένα"

    fun limitation(result: ExactDuplicateResult): String = buildString {
        append("Ο έλεγχος συνέκρινε περιεχόμενο με SHA-256 και δεν διαγράφηκε ή μετακινήθηκε αρχείο.")
        if (result.wasTruncated) append(" Η σάρωση περιορίστηκε για να παραμείνει ελεγχόμενη.")
        if (result.failedFileCount > 0) append(" Δεν διαβάστηκαν ")
        if (result.failedFileCount > 0) append(result.failedFileCount).append(" αρχεία.")
    }

    fun cleanupLabel(result: ExactCleanupResult): String =
        "Διαγράφηκαν " + result.deletedCount + " αντίγραφα · παραλείφθηκαν " +
            result.skippedCount + " · αποτυχίες " + result.failedCount
}
