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
)

private data class DuplicateCandidate(
    val name: String,
    val sizeBytes: Long,
    val openStream: () -> InputStream?,
)

private data class CandidateCollection(
    val groupsBySize: Map<Long, List<DuplicateCandidate>>,
    val filesConsidered: Int,
    val wasTruncated: Boolean,
)

private data class HashOutcome(
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

            val namesByDigest = linkedMapOf<String, MutableList<String>>()
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
                namesByDigest.getOrPut(outcome.digest) { mutableListOf() }.add(candidate.name)
            }

            namesByDigest
                .asSequence()
                .filter { (_, names) -> names.size >= 2 }
                .map { (_, names) -> ExactDuplicateGroup(sizeBytes, names.take(RESULT_GROUP_LIMIT)) }
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

    private fun documentSize(context: Context, document: DocumentFile): Long? =
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

    private fun hash(
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

    private fun canonicalPath(file: File): String = runCatching { file.canonicalPath }
        .getOrDefault(file.absolutePath)

    private fun displayPath(roots: List<File>, file: File): String {
        val root = roots.firstOrNull { root ->
            val rootPath = canonicalPath(root)
            val filePath = canonicalPath(file)
            filePath == rootPath || filePath.startsWith("$rootPath${File.separator}")
        }
        val rootLabel = root?.name?.takeIf(String::isNotBlank) ?: "Κοινόχρηστος χώρος"
        val relative = root?.let { runCatching { file.relativeTo(it).path }.getOrNull() }
        return if (relative.isNullOrBlank()) rootLabel else "$rootLabel/$relative"
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
}

object ExactDuplicatePresentation {
    fun summary(result: ExactDuplicateResult): String =
        "${result.groups.size} ακριβείς ομάδες · ${result.filesHashed} αρχεία ελέγχθηκαν με SHA-256"

    fun groupLabel(group: ExactDuplicateGroup): String =
        "${group.fileNames.size} ίδια αρχεία · ${StorageIntelligencePresentation.storageSize(group.sizeBytes)} το καθένα"

    fun limitation(result: ExactDuplicateResult): String = buildString {
        append("Ο έλεγχος συνέκρινε περιεχόμενο με SHA-256 και δεν διαγράφηκε ή μετακινήθηκε αρχείο.")
        if (result.wasTruncated) append(" Η σάρωση περιορίστηκε για να παραμείνει ελεγχόμενη.")
        if (result.failedFileCount > 0) append(" Δεν διαβάστηκαν ${result.failedFileCount} αρχεία.")
    }
}
