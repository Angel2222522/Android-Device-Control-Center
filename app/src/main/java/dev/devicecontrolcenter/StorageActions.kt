package dev.devicecontrolcenter

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.UUID

object StorageDuplicateScanner {
    private const val MAX_FILES_TO_HASH = 1_000
    private const val MAX_FILE_BYTES_TO_HASH = 512L * 1024L * 1024L
    private const val BUFFER_SIZE = 64 * 1024

    fun scan(
        context: Context,
        result: StorageScanResult,
        shouldContinue: () -> Boolean = { true },
    ): ExactDuplicateScanResult {
        val candidates = (result.hashCandidates.takeIf { it.isNotEmpty() }
            ?: result.sameSizeCandidates.flatMap { it.entries })
            .filter { it.sizeBytes != null && it.sizeBytes >= 0L }
            .distinctBy { it.uriString ?: it.name }
        val hashed = mutableListOf<Pair<StorageFileEntry, String>>()
        var skipped = 0

        for (entry in candidates.take(MAX_FILES_TO_HASH)) {
            check(shouldContinue()) { "Ο έλεγχος διπλοτύπων ακυρώθηκε." }
            val size = entry.sizeBytes ?: run {
                skipped++
                continue
            }
            if (size > MAX_FILE_BYTES_TO_HASH || entry.uriString.isNullOrBlank()) {
                skipped++
                continue
            }
            val digest = hash(context, entry)
            if (digest == null) skipped++ else hashed += entry to digest
        }
        skipped += (candidates.size - candidates.take(MAX_FILES_TO_HASH).size).coerceAtLeast(0)

        val groups = hashed
            .groupBy { (entry, digest) -> (entry.sizeBytes ?: -1L) to digest }
            .values
            .filter { it.size >= 2 }
            .map { same ->
                ExactDuplicateGroup(
                    sha256 = same.first().second,
                    sizeBytes = same.first().first.sizeBytes ?: 0L,
                    entries = same.map { it.first },
                )
            }
            .sortedWith(compareByDescending<ExactDuplicateGroup> { it.sizeBytes }.thenBy { it.sha256 })

        return ExactDuplicateScanResult(
            groups = groups,
            filesHashed = hashed.size,
            skippedFileCount = skipped,
        )
    }

    private fun hash(context: Context, entry: StorageFileEntry): String? = try {
        val expectedBytes = entry.sizeBytes ?: return null
        val input = openInputStream(context, entry) ?: return null
        input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            var copied = 0L
            while (true) {
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("Ο έλεγχος διπλοτύπων ακυρώθηκε.")
                }
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (count.toLong() > expectedBytes - copied) return null
                digest.update(buffer, 0, count)
                copied += count.toLong()
            }
            if (copied != expectedBytes) return null
            digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    internal fun openInputStream(context: Context, entry: StorageFileEntry): InputStream? {
        val raw = entry.uriString ?: return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        return when (uri?.scheme?.lowercase()) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> uri.path?.let(::File)?.inputStream()
            else -> File(raw).takeIf(File::isFile)?.inputStream()
        }
    }
}

data class StorageTrashItem(
    val id: String,
    val displayName: String,
    val payload: File,
    val originalUriString: String,
    val parentUriString: String?,
    val sourceSizeBytes: Long? = null,
    val sourceModifiedMillis: Long? = null,
    val sourceSha256: String? = null,
    val sourceDocumentId: String? = null,
    val needsRecoveryReview: Boolean = false,
)

internal data class StorageSourceFingerprint(
    val sizeBytes: Long?,
    val modifiedMillis: Long?,
    val sha256: String?,
    val documentId: String?,
) {
    val isComplete: Boolean
        get() = sizeBytes != null && sha256 != null

    val hasComparableIdentity: Boolean
        get() = sizeBytes != null || modifiedMillis != null || sha256 != null || documentId != null

    fun matches(other: StorageSourceFingerprint): Boolean =
        sizeBytes == other.sizeBytes &&
            sha256 == other.sha256 &&
            (other.modifiedMillis == null || modifiedMillis == other.modifiedMillis) &&
            (other.documentId == null || documentId == other.documentId)
}

internal enum class StorageTrashSourceState {
    ABSENT,
    PRESENT_MATCH,
    PRESENT_MISMATCH,
    UNKNOWN,
}

object StorageTrashService {
    private const val COPY_BUFFER_SIZE = 64 * 1024
    private const val TRASH_FREE_SPACE_MARGIN_BYTES = 64L * 1024L

    private enum class DeleteResult {
        DELETED,
        STILL_PRESENT,
        UNKNOWN,
    }

    private enum class SourcePresence {
        PRESENT,
        ABSENT,
        UNKNOWN,
    }

    private data class SourceObservation(
        val state: SourcePresence,
        val sizeBytes: Long? = null,
        val modifiedMillis: Long? = null,
        val documentId: String? = null,
        val sha256: String? = null,
    )

    /**
     * Returns the most recent durable trash item, including after process death.
     * The caller remains responsible for presenting an explicit restore confirmation.
     */
    fun loadLast(context: Context): StorageTrashItem? = StorageTrashIndex.loadLast(context)

    /** Returns all durable trash items, newest first. */
    fun loadAll(context: Context): List<StorageTrashItem> = StorageTrashIndex.loadAll(context)

    private fun observeSource(context: Context, rawUri: String): SourceObservation {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull()
            ?: return SourceObservation(SourcePresence.UNKNOWN)
        return when (uri.scheme?.lowercase()) {
            "content" -> {
                val document = resolveDocument(context, uri)
                    ?: return SourceObservation(SourcePresence.UNKNOWN)
                when (runCatching { document.exists() }.getOrNull()) {
                    false -> SourceObservation(SourcePresence.ABSENT)
                    true -> SourceObservation(
                        state = SourcePresence.PRESENT,
                        sizeBytes = contentSize(context, uri),
                        modifiedMillis = document.lastModified().takeIf { it > 0L },
                        documentId = documentId(uri),
                    )
                    null -> SourceObservation(SourcePresence.UNKNOWN)
                }
            }

            "file" -> uri.path?.let(::File)?.let(::observeFile)
                ?: SourceObservation(SourcePresence.UNKNOWN)

            null -> observeFile(File(rawUri))
            else -> SourceObservation(SourcePresence.UNKNOWN)
        }
    }

    private fun observeFile(file: File): SourceObservation = when {
        !file.exists() -> SourceObservation(SourcePresence.ABSENT)
        !file.isFile -> SourceObservation(SourcePresence.UNKNOWN)
        else -> SourceObservation(
            state = SourcePresence.PRESENT,
            sizeBytes = file.length().takeIf { it >= 0L },
            modifiedMillis = file.lastModified().takeIf { it > 0L },
        )
    }

    private fun observeSourceWithHash(context: Context, rawUri: String): SourceObservation {
        val observed = observeSource(context, rawUri)
        if (observed.state != SourcePresence.PRESENT) return observed
        val input = StorageDuplicateScanner.openInputStream(
            context,
            StorageFileEntry(
                name = "source",
                sizeBytes = observed.sizeBytes,
                lastModifiedMillis = observed.modifiedMillis ?: 0L,
                uriString = rawUri,
            ),
        ) ?: return SourceObservation(SourcePresence.UNKNOWN)
        val sha256 = input.use { hashInput(it, observed.sizeBytes) }
            ?: return SourceObservation(SourcePresence.UNKNOWN)
        return observed.copy(sha256 = sha256)
    }

    private fun hashInput(input: InputStream, expectedBytes: Long?): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            checkNotCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (expectedBytes != null && count.toLong() > expectedBytes - copied) return null
            digest.update(buffer, 0, count)
            copied += count.toLong()
        }
        if (expectedBytes != null && copied != expectedBytes) return null
        return digest.digest().toHex()
    }

    private fun documentId(uri: Uri): String? = runCatching {
        DocumentsContract.getDocumentId(uri)
    }.getOrNull()?.takeIf(String::isNotBlank)

    private fun SourceObservation.matchesCopiedSource(
        item: StorageTrashItem,
        expectedDocumentId: String?,
    ): Boolean =
        state == SourcePresence.PRESENT &&
            sizeBytes == item.sourceSizeBytes &&
            sha256 == item.sourceSha256 &&
            (expectedDocumentId == null || documentId == expectedDocumentId)

    /**
     * Copies the selected file to the app-private trash only after a free-space preflight,
     * persists the recovery metadata, and then deletes the original explicitly requested by
     * the caller. This method never asks for confirmation itself.
     */
    fun moveToTrash(context: Context, entry: StorageFileEntry): StorageTrashItem {
        val appContext = context.applicationContext
        val original = entry.uriString?.takeIf(String::isNotBlank)
            ?: error("Δεν υπάρχει ασφαλής αναφορά στο αρχείο.")
        val displayName = leafName(entry.name)
        val parent = entry.parentUriString?.takeIf(String::isNotBlank)
            ?: inferParentUri(original)
        val expectedBytes = sourceSizeBytes(appContext, entry)
            ?: error("Δεν είναι διαθέσιμο το μέγεθος του αρχείου· η ασφαλής ενέργεια ακυρώθηκε.")

        val initialSource = observeSource(appContext, original)
        check(initialSource.state == SourcePresence.PRESENT) {
            "Το αρχικό αρχείο δεν είναι πλέον διαθέσιμο με ασφαλή τρόπο."
        }
        initialSource.sizeBytes?.let {
            check(it == expectedBytes) {
                "Το αρχείο άλλαξε πριν από τη μετακίνηση και η ενέργεια ακυρώθηκε."
            }
        }

        check(expectedBytes >= 0L) { "Το μέγεθος του αρχείου δεν είναι έγκυρο." }
        checkTrashSpace(appContext, expectedBytes)

        val id = UUID.randomUUID().toString()
        val payload = StorageTrashIndex.payloadFile(appContext, id)
        val temporaryPayload = StorageTrashIndex.temporaryPayloadFile(appContext, id)
        val item = StorageTrashItem(
            id = id,
            displayName = displayName,
            payload = payload,
            originalUriString = original,
            parentUriString = parent,
            sourceSizeBytes = expectedBytes,
            sourceModifiedMillis = initialSource.modifiedMillis,
            sourceDocumentId = initialSource.documentId,
        )
        var preparedItem = item
        var preparedPersisted = false
        var sourceDeleted = false
        var deletionResult: DeleteResult? = null

        try {
            cleanupIfPresent(temporaryPayload)
            cleanupIfPresent(payload)
            val input = StorageDuplicateScanner.openInputStream(
                appContext,
                entry.copy(name = displayName),
            ) ?: error("Δεν ήταν δυνατή η ανάγνωση του αρχείου.")
            input.use { source ->
                FileOutputStream(temporaryPayload).use { output ->
                    val digest = copyExactly(source, output, expectedBytes)
                    output.flush()
                    output.fd.sync()
                    preparedItem = item.copy(sourceSha256 = digest)
                }
            }
            val afterCopy = observeSourceWithHash(appContext, original)
            check(afterCopy.matchesCopiedSource(preparedItem, initialSource.documentId)) {
                "Το αρχικό αρχείο άλλαξε ή δεν επαληθεύτηκε μετά την αντιγραφή· δεν έγινε διαγραφή."
            }
            check(temporaryPayload.renameTo(payload)) {
                "Δεν ήταν δυνατή η ασφαλής προετοιμασία του ιδιωτικού κάδου."
            }

            check(StorageTrashIndex.addPrepared(appContext, preparedItem)) {
                "Δεν αποθηκεύτηκε ο δείκτης του ιδιωτικού κάδου· το αρχικό αρχείο έμεινε ανέγγιχτο."
            }
            preparedPersisted = true

            checkNotCancelled()
            val beforeDelete = observeSourceWithHash(appContext, original)
            check(beforeDelete.matchesCopiedSource(preparedItem, initialSource.documentId)) {
                "Το αρχικό αρχείο άλλαξε πριν από τη διαγραφή· δεν έγινε διαγραφή."
            }
            deletionResult = runCatching { deleteOriginal(appContext, original) }
                .getOrDefault(DeleteResult.UNKNOWN)
            when (deletionResult) {
                DeleteResult.DELETED -> sourceDeleted = true
                DeleteResult.STILL_PRESENT ->
                    error("Το Android δεν επέτρεψε τη διαγραφή του αρχικού αρχείου.")
                DeleteResult.UNKNOWN, null ->
                    error("Δεν επιβεβαιώθηκε η διαγραφή του αρχικού αρχείου. Το αντίγραφο διατηρήθηκε για έλεγχο.")
            }

            check(StorageTrashIndex.markTrashed(appContext, preparedItem.id)) {
                "Το αρχείο μετακινήθηκε με ασφάλεια, αλλά ο δείκτης κάδου δεν ενημερώθηκε."
            }
            return preparedItem
        } catch (failure: Exception) {
            cleanupIfPresent(temporaryPayload)
            if (!sourceDeleted) {
                // Once the payload and PREPARED record are durable, keep them for recovery
                // unless deletion conclusively left the original source present. This retains
                // the only safe copy across cancellation, source mismatch, and provider
                // uncertainty before or during deletion.
                val canDiscardPreparedCopy = !preparedPersisted ||
                    deletionResult == DeleteResult.STILL_PRESENT
                if (canDiscardPreparedCopy) {
                    if (preparedPersisted) StorageTrashIndex.remove(appContext, preparedItem.id)
                    cleanupIfPresent(payload)
                }
                // UNKNOWN is deliberately retained with PREPARED metadata. Recovery will
                // re-check the fingerprint and never discard the payload on uncertainty.
            }
            throw failure
        }
    }

    /**
     * Restores a durable item without overwriting an existing destination. SAF directory
     * resolution accepts both tree URIs and child/document URIs.
     */
    fun restore(context: Context, item: StorageTrashItem) {
        val appContext = context.applicationContext
        val durableItem = StorageTrashIndex.find(appContext, item.id) ?: item
        check(StorageTrashIndex.isSafeDisplayName(durableItem.displayName)) {
            "Το όνομα του αρχείου στον ιδιωτικό κάδο δεν είναι ασφαλές για επαναφορά."
        }
        check(StorageTrashIndex.isSafePayload(appContext, durableItem.payload)) {
            "Ο ιδιωτικός κάδος δεν περιέχει ασφαλή αναφορά στο αντίγραφο."
        }
        check(durableItem.payload.isFile) { "Το αρχείο δεν υπάρχει πλέον στον ιδιωτικό κάδο." }

        val target = createRestoreTarget(appContext, durableItem)
        try {
            target.writeFrom(durableItem.payload)
        } catch (failure: Exception) {
            target.cleanup()
            throw failure
        }

        if (durableItem.payload.exists() && !durableItem.payload.delete()) {
            error("Το αρχείο επαναφέρθηκε, αλλά ο ιδιωτικός κάδος δεν καθαρίστηκε ακόμη.")
        }
        // If this commit is interrupted after the payload delete, loadAll() self-heals the
        // stale metadata because the payload no longer exists.
        StorageTrashIndex.remove(appContext, durableItem.id)
    }

    private fun checkTrashSpace(context: Context, expectedBytes: Long) {
        val requiredBytes = expectedBytes
            .takeIf { Long.MAX_VALUE - it >= TRASH_FREE_SPACE_MARGIN_BYTES }
            ?.plus(TRASH_FREE_SPACE_MARGIN_BYTES)
            ?: error("Το αρχείο είναι υπερβολικά μεγάλο για ασφαλή μετακίνηση στον κάδο.")
        val availableBytes = runCatching {
            StatFs(context.filesDir.absolutePath).availableBytes
        }.getOrElse { error("Δεν ήταν δυνατός ο έλεγχος του διαθέσιμου χώρου του ιδιωτικού κάδου.") }
        check(availableBytes >= requiredBytes) {
            "Δεν υπάρχει αρκετός διαθέσιμος χώρος στον ιδιωτικό κάδο: απαιτούνται " +
                "${StorageIntelligencePresentation.storageSize(requiredBytes)}, " +
                "διαθέσιμα ${StorageIntelligencePresentation.storageSize(availableBytes)}."
        }
    }

    private fun sourceSizeBytes(context: Context, entry: StorageFileEntry): Long? {
        entry.sizeBytes?.takeIf { it >= 0L }?.let { return it }
        val raw = entry.uriString ?: return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        return when (uri?.scheme?.lowercase()) {
            "content" -> contentSize(context, uri)
            "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.length()
            null -> File(raw).takeIf(File::isFile)?.length()
            else -> null
        }?.takeIf { it >= 0L }
    }

    private fun contentSize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        } ?: resolveDocument(context, uri)?.length()?.takeIf { it >= 0L }
    }.getOrNull()

    private fun copyExactly(input: InputStream, output: OutputStream, expectedBytes: Long): String {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        while (true) {
            checkNotCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count.toLong() > expectedBytes - copied) {
                throw IOException("Το αρχείο άλλαξε κατά την αντιγραφή και η ενέργεια ακυρώθηκε.")
            }
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            copied += count.toLong()
        }
        check(copied == expectedBytes) {
            "Το αρχείο άλλαξε κατά την αντιγραφή και η ενέργεια ακυρώθηκε."
        }
        return digest.digest().toHex()
    }

    private fun createRestoreTarget(context: Context, item: StorageTrashItem): RestoreTarget {
        val parent = item.parentUriString?.takeIf(String::isNotBlank)
            ?: inferParentUri(item.originalUriString)
            ?: error("Δεν υπάρχει καταγεγραμμένος αρχικός φάκελος.")
        val parentUri = runCatching { Uri.parse(parent) }.getOrNull()
            ?: error("Ο αρχικός φάκελος δεν έχει έγκυρη αναφορά.")

        return when (parentUri.scheme?.lowercase()) {
            "content" -> {
                val directory = resolveDirectory(context, parentUri)
                    ?: error("Ο αρχικός φάκελος SAF δεν είναι πλέον διαθέσιμος.")
                check(directory.canWrite()) {
                    "Δεν υπάρχει δικαίωμα εγγραφής στον αρχικό φάκελο SAF."
                }
                check(directory.findFile(item.displayName) == null) {
                    "Υπάρχει ήδη αρχείο με το ίδιο όνομα στον αρχικό φάκελο. Δεν έγινε αντικατάσταση."
                }
                val temporaryName = ".dcc-restore-${item.id}.tmp"
                check(directory.findFile(temporaryName) == null) {
                    "Υπάρχει ημιτελής προηγούμενη επαναφορά στον αρχικό φάκελο."
                }
                val extension = item.displayName.substringAfterLast('.', "")
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: "application/octet-stream"
                val temporary = directory.createFile(mime, temporaryName)
                    ?: error("Δεν δημιουργήθηκε προσωρινό αρχείο επαναφοράς.")
                RestoreTarget.Document(context, directory, temporary, item.displayName)
            }

            "file" -> {
                val directory = parentUri.path?.let(::File)?.takeIf(File::isDirectory)
                    ?: error("Ο αρχικός φάκελος δεν είναι πλέον διαθέσιμος.")
                createFileRestoreTarget(directory, item)
            }

            null -> {
                val directory = File(parent).takeIf(File::isDirectory)
                    ?: error("Ο αρχικός φάκελος δεν είναι πλέον διαθέσιμος.")
                createFileRestoreTarget(directory, item)
            }

            else -> error("Ο αρχικός φάκελος έχει μη υποστηριζόμενη αναφορά.")
        }
    }

    private fun createFileRestoreTarget(directory: File, item: StorageTrashItem): RestoreTarget {
        val target = File(directory, item.displayName)
        check(!target.exists()) {
            "Υπάρχει ήδη αρχείο με το ίδιο όνομα στον αρχικό φάκελο. Δεν έγινε αντικατάσταση."
        }
        val temporary = File(directory, ".dcc-restore-${item.id}.tmp")
        check(!temporary.exists()) { "Υπάρχει ημιτελής προηγούμενη επαναφορά στον αρχικό φάκελο." }
        return RestoreTarget.FileTarget(directory, temporary, target)
    }

    private fun deleteOriginal(context: Context, rawUri: String): DeleteResult {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return DeleteResult.UNKNOWN
        return when (uri.scheme?.lowercase()) {
            "content" -> {
                val document = resolveDocument(context, uri) ?: return DeleteResult.UNKNOWN
                val exists = runCatching { document.exists() }.getOrNull()
                if (exists == false) return DeleteResult.DELETED
                if (exists != true || !document.canWrite()) return DeleteResult.UNKNOWN
                if (runCatching { document.delete() }.getOrDefault(false)) {
                    DeleteResult.DELETED
                } else {
                    classifyFilePresence(runCatching { document.exists() }.getOrNull())
                }
            }

            "file" -> uri.path?.let(::File)?.let(::deleteFileSafely) ?: DeleteResult.UNKNOWN
            null -> deleteFileSafely(File(rawUri))
            else -> DeleteResult.UNKNOWN
        }
    }

    private fun deleteFileSafely(file: File): DeleteResult {
        if (!file.exists()) return DeleteResult.DELETED
        if (runCatching { file.delete() }.getOrDefault(false)) return DeleteResult.DELETED
        return classifyFilePresence(runCatching { file.exists() }.getOrNull())
    }

    private fun classifyFilePresence(exists: Boolean?): DeleteResult = when (exists) {
        false -> DeleteResult.DELETED
        true -> DeleteResult.STILL_PRESENT
        null -> DeleteResult.UNKNOWN
    }

    private fun resolveDocument(context: Context, uri: Uri): DocumentFile? =
        runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
            ?: runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()

    private fun resolveDirectory(context: Context, uri: Uri): DocumentFile? {
        val candidates = buildList {
            if (DocumentsContract.isTreeUri(uri)) {
                runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()?.let(::add)
            }
            runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()?.let(::add)

            val authority = uri.authority
            val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            if (!authority.isNullOrBlank() && !documentId.isNullOrBlank()) {
                val treeUri = runCatching {
                    DocumentsContract.buildTreeDocumentUri(authority, documentId)
                }.getOrNull()
                if (treeUri != null) {
                    runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()?.let(::add)
                }
            }
        }
        return candidates
            .distinctBy { it.uri.toString() }
            .firstOrNull { it.isDirectory && it.canWrite() }
    }

    internal fun recoveryState(
        context: Context,
        originalUriString: String,
        expected: StorageSourceFingerprint,
    ): StorageTrashSourceState {
        val observed = observeSourceWithHash(context, originalUriString)
        return when (observed.state) {
            SourcePresence.ABSENT -> StorageTrashSourceState.ABSENT
            SourcePresence.UNKNOWN -> StorageTrashSourceState.UNKNOWN
            SourcePresence.PRESENT -> {
                val actual = StorageSourceFingerprint(
                    sizeBytes = observed.sizeBytes,
                    modifiedMillis = observed.modifiedMillis,
                    sha256 = observed.sha256,
                    documentId = observed.documentId,
                )
                if (expected.isComplete && actual.matches(expected)) {
                    StorageTrashSourceState.PRESENT_MATCH
                } else if (expected.isComplete && actual.hasComparableIdentity) {
                    StorageTrashSourceState.PRESENT_MISMATCH
                } else {
                    StorageTrashSourceState.UNKNOWN
                }
            }
        }
    }

    private fun inferParentUri(rawUri: String): String? {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let(::File)?.parentFile?.toURI()?.toString()
            null -> File(rawUri).parentFile?.toURI()?.toString()
            else -> null
        }
    }

    private fun leafName(rawName: String): String {
        val candidate = rawName
            .replace('\\', '/')
            .substringAfterLast('/')
            .trim()
        check(candidate.isNotBlank() && candidate != "." && candidate != ".." && !candidate.contains('\u0000')) {
            "Το όνομα του αρχείου δεν είναι ασφαλές για επαναφορά."
        }
        return candidate
    }

    private fun cleanupIfPresent(file: File) {
        if (file.exists()) file.delete()
    }

    private fun checkNotCancelled() {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("Η εργασία αποθήκευσης ακυρώθηκε.")
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private sealed interface RestoreTarget {
        fun writeFrom(payload: File)

        fun cleanup()

        data class Document(
            val context: Context,
            val directory: DocumentFile,
            val temporary: DocumentFile,
            val targetName: String,
        ) : RestoreTarget {
            override fun writeFrom(payload: File) {
                val output = context.contentResolver.openOutputStream(temporary.uri)
                    ?: error("Δεν άνοιξε το προσωρινό αρχείο επαναφοράς.")
                output.use { stream ->
                    payload.inputStream().use { input ->
                        copyPayload(input, stream, payload.length())
                    }
                }
                check(temporary.renameTo(targetName)) {
                    "Ο πάροχος SAF δεν υποστήριξε την ασφαλή ολοκλήρωση της επαναφοράς."
                }
            }

            override fun cleanup() {
                runCatching { temporary.delete() }
            }
        }

        data class FileTarget(
            val directory: File,
            val temporary: File,
            val target: File,
        ) : RestoreTarget {
            override fun writeFrom(payload: File) {
                FileOutputStream(temporary).use { output ->
                    payload.inputStream().use { input ->
                        copyPayload(input, output, payload.length())
                    }
                    output.flush()
                    output.fd.sync()
                }
                runCatching {
                    // With ATOMIC_MOVE, replacement of an existing target is implementation-
                    // specific. The default move instead fails if a destination appeared after
                    // the no-overwrite preflight.
                    Files.move(temporary.toPath(), target.toPath())
                }.getOrElse { error ->
                    throw IOException("Δεν ολοκληρώθηκε με ασφάλεια η επαναφορά του αρχείου.", error)
                }
            }

            override fun cleanup() {
                runCatching { temporary.delete() }
            }
        }
    }

    private fun copyPayload(input: InputStream, output: OutputStream, expectedBytes: Long) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            checkNotCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count.toLong() > expectedBytes - copied) {
                throw IOException("Το αντίγραφο του κάδου δεν είναι σταθερό.")
            }
            output.write(buffer, 0, count)
            copied += count.toLong()
        }
        check(copied == expectedBytes) { "Το αντίγραφο του κάδου δεν είναι πλήρες." }
    }
}
