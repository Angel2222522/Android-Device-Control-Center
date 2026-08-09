package dev.devicecontrolcenter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Durable metadata for private-trash items. The payload itself remains app-private. */
internal object StorageTrashIndex {
    private const val PREFERENCES_NAME = "device_control_center_storage_trash"
    private const val ITEMS_KEY = "items_json"
    private const val TRASH_DIRECTORY_NAME = "storage-trash"
    private const val PAYLOAD_SUFFIX = ".payload"
    private const val TEMPORARY_SUFFIX = ".payload.tmp"

    private val operationLock = Any()

    /**
     * Serializes the move's durable PREPARED window with trash recovery and index updates.
     * This is intentionally process-local: after process death, no owner remains and the
     * durable PREPARED record continues through normal fingerprint-based recovery.
     */
    internal fun <T> withOperationLock(block: () -> T): T = synchronized(operationLock) {
        block()
    }

    private const val ID_KEY = "id"
    private const val DISPLAY_NAME_KEY = "display_name"
    private const val PAYLOAD_NAME_KEY = "payload_name"
    private const val ORIGINAL_URI_KEY = "original_uri"
    private const val PARENT_URI_KEY = "parent_uri"
    private const val STATE_KEY = "state"
    private const val CREATED_AT_KEY = "created_at"
    private const val SOURCE_SIZE_KEY = "source_size"
    private const val SOURCE_MODIFIED_KEY = "source_modified"
    private const val SOURCE_SHA256_KEY = "source_sha256"
    private const val SOURCE_DOCUMENT_ID_KEY = "source_document_id"
    private const val NEEDS_REVIEW_KEY = "needs_review"

    fun payloadFile(context: Context, id: String): File {
        require(isSafeId(id)) { "Μη έγκυρο αναγνωριστικό κάδου." }
        return File(trashDirectory(context), "$id$PAYLOAD_SUFFIX")
    }

    fun temporaryPayloadFile(context: Context, id: String): File {
        require(isSafeId(id)) { "Μη έγκυρο αναγνωριστικό κάδου." }
        return File(trashDirectory(context), "$id$TEMPORARY_SUFFIX")
    }

    fun isSafePayload(context: Context, payload: File): Boolean = runCatching {
        val root = trashDirectory(context).canonicalFile
        val candidate = payload.canonicalFile
        candidate.parentFile == root &&
            candidate.name.endsWith("$PAYLOAD_SUFFIX") &&
            isSafeId(candidate.name.removeSuffix(PAYLOAD_SUFFIX))
    }.getOrDefault(false)

    fun isSafeDisplayName(name: String): Boolean =
        name.isNotBlank() &&
            name == name.trim() &&
            name != "." &&
            name != ".." &&
            name.none { it == '/' || it == '\\' || it == '\u0000' }

    fun addPrepared(context: Context, item: StorageTrashItem): Boolean = update(context) { records ->
        val record = StorageTrashRecord(
            id = item.id,
            displayName = item.displayName,
            payloadName = item.id + PAYLOAD_SUFFIX,
            originalUriString = item.originalUriString,
            parentUriString = item.parentUriString,
            sourceSizeBytes = item.sourceSizeBytes,
            sourceModifiedMillis = item.sourceModifiedMillis,
            sourceSha256 = item.sourceSha256,
            sourceDocumentId = item.sourceDocumentId,
            state = StorageTrashRecordState.PREPARED,
            needsReview = false,
            createdAtMillis = System.currentTimeMillis(),
        )
        listOf(record) + records.filterNot { it.id == item.id }
    }

    fun markTrashed(context: Context, id: String): Boolean = update(context) { records ->
        check(records.any { it.id == id }) { "Ο δείκτης του ιδιωτικού κάδου δεν βρέθηκε." }
        records.map { record ->
            if (record.id == id) record.copy(state = StorageTrashRecordState.TRASHED) else record
        }
    }

    fun remove(context: Context, id: String): Boolean = update(context) { records ->
        records.filterNot { it.id == id }
    }

    fun find(context: Context, id: String): StorageTrashItem? = withOperationLock {
        loadRecords(context).firstOrNull {
            it.id == id && it.state == StorageTrashRecordState.TRASHED
        }?.toItem(context)
    }

    fun loadLast(context: Context): StorageTrashItem? = withOperationLock {
        loadRecords(context)
            .firstOrNull { it.state == StorageTrashRecordState.TRASHED }
            ?.toItem(context)
    }

    fun loadAll(context: Context): List<StorageTrashItem> = withOperationLock {
        loadRecords(context)
            .asSequence()
            .filter { it.state == StorageTrashRecordState.TRASHED }
            .mapNotNull { it.toItem(context) }
            .toList()
    }

    private fun loadRecords(context: Context): List<StorageTrashRecord> {
        val parsed = read(context) ?: return emptyList()
        var changed = false
        val recovered = parsed.mapNotNull { record ->
            val payload = payloadFileOrNull(context, record)
            if (payload == null || !payload.isFile) {
                changed = true
                return@mapNotNull null
            }

            if (record.state != StorageTrashRecordState.PREPARED) return@mapNotNull record

            // A prepared record is the recovery point between a durable copy and source
            // deletion. If the source survived, discard only the private copy; if the source
            // is gone or cannot be queried, retain the copy as recoverable trash.
            when (
                StorageTrashService.recoveryState(
                    context = context,
                    originalUriString = record.originalUriString,
                    expected = StorageSourceFingerprint(
                        sizeBytes = record.sourceSizeBytes,
                        modifiedMillis = record.sourceModifiedMillis,
                        sha256 = record.sourceSha256,
                        documentId = record.sourceDocumentId,
                    ),
                )
            ) {
                StorageTrashSourceState.PRESENT_MATCH -> if (payload.delete() || !payload.exists()) {
                    changed = true
                    null
                } else {
                    changed = true
                    record.copy(
                        state = StorageTrashRecordState.TRASHED,
                        needsReview = true,
                    )
                }

                StorageTrashSourceState.ABSENT -> {
                    changed = true
                    record.copy(state = StorageTrashRecordState.TRASHED)
                }

                StorageTrashSourceState.PRESENT_MISMATCH,
                StorageTrashSourceState.UNKNOWN,
                -> {
                    changed = true
                    record.copy(
                        state = StorageTrashRecordState.TRASHED,
                        needsReview = true,
                    )
                }
            }
        }

        if (changed) write(context, recovered)
        return recovered.sortedWith(
            compareByDescending<StorageTrashRecord> { it.createdAtMillis }.thenBy { it.id },
        )
    }

    private fun payloadFileOrNull(context: Context, record: StorageTrashRecord): File? {
        if (record.payloadName != record.id + PAYLOAD_SUFFIX) return null
        return payloadFile(context, record.id)
            .takeIf { isSafePayload(context, it) }
    }

    private fun read(context: Context): List<StorageTrashRecord>? {
        val raw = preferences(context).getString(ITEMS_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    val id = json.getString(ID_KEY)
                    val payloadName = json.getString(PAYLOAD_NAME_KEY)
                    val displayName = json.getString(DISPLAY_NAME_KEY)
                    val originalUri = json.getString(ORIGINAL_URI_KEY)
                    val parentUri = if (json.isNull(PARENT_URI_KEY)) {
                        null
                    } else {
                        json.getString(PARENT_URI_KEY).takeIf(String::isNotBlank)
                    }
                    val state = when (json.optString(STATE_KEY)) {
                        StorageTrashRecordState.TRASHED.name -> StorageTrashRecordState.TRASHED
                        StorageTrashRecordState.PREPARED.name -> StorageTrashRecordState.PREPARED
                        else -> error("Άγνωστη κατάσταση ιδιωτικού κάδου.")
                    }
                    check(isSafeId(id))
                    check(payloadName == id + PAYLOAD_SUFFIX)
                    check(isSafeDisplayName(displayName) && originalUri.isNotBlank())
                    add(
                        StorageTrashRecord(
                            id = id,
                            displayName = displayName,
                            payloadName = payloadName,
                            originalUriString = originalUri,
                            parentUriString = parentUri,
                            sourceSizeBytes = json.optNullableLong(SOURCE_SIZE_KEY),
                            sourceModifiedMillis = json.optNullableLong(SOURCE_MODIFIED_KEY),
                            sourceSha256 = json.optNullableString(SOURCE_SHA256_KEY),
                            sourceDocumentId = json.optNullableString(SOURCE_DOCUMENT_ID_KEY),
                            state = state,
                            needsReview = json.optBoolean(NEEDS_REVIEW_KEY, false),
                            createdAtMillis = json.optLong(CREATED_AT_KEY, 0L),
                        ),
                    )
                }
            }
        }.getOrNull()
    }

    private fun update(
        context: Context,
        transform: (List<StorageTrashRecord>) -> List<StorageTrashRecord>,
    ): Boolean = withOperationLock {
        val current = read(context)
            ?: return@withOperationLock false
        write(context, transform(current))
    }

    private fun write(context: Context, records: List<StorageTrashRecord>): Boolean {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put(ID_KEY, record.id)
                    put(DISPLAY_NAME_KEY, record.displayName)
                    put(PAYLOAD_NAME_KEY, record.payloadName)
                    put(ORIGINAL_URI_KEY, record.originalUriString)
                    put(PARENT_URI_KEY, record.parentUriString ?: JSONObject.NULL)
                    put(SOURCE_SIZE_KEY, record.sourceSizeBytes ?: JSONObject.NULL)
                    put(SOURCE_MODIFIED_KEY, record.sourceModifiedMillis ?: JSONObject.NULL)
                    put(SOURCE_SHA256_KEY, record.sourceSha256 ?: JSONObject.NULL)
                    put(SOURCE_DOCUMENT_ID_KEY, record.sourceDocumentId ?: JSONObject.NULL)
                    put(NEEDS_REVIEW_KEY, record.needsReview)
                    put(STATE_KEY, record.state.name)
                    put(CREATED_AT_KEY, record.createdAtMillis)
                },
            )
        }
        val editor = preferences(context).edit()
        if (records.isEmpty()) editor.remove(ITEMS_KEY) else editor.putString(ITEMS_KEY, array.toString())
        return editor.commit()
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key).takeIf { it >= 0L }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun trashDirectory(context: Context): File =
        File(context.applicationContext.filesDir, TRASH_DIRECTORY_NAME).apply { mkdirs() }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private fun isSafeId(id: String): Boolean =
        id.length in 1..128 && id.all { it.isLetterOrDigit() || it == '-' }
}

private enum class StorageTrashRecordState {
    PREPARED,
    TRASHED,
}

private data class StorageTrashRecord(
    val id: String,
    val displayName: String,
    val payloadName: String,
    val originalUriString: String,
    val parentUriString: String?,
    val sourceSizeBytes: Long?,
    val sourceModifiedMillis: Long?,
    val sourceSha256: String?,
    val sourceDocumentId: String?,
    val state: StorageTrashRecordState,
    val needsReview: Boolean,
    val createdAtMillis: Long,
) {
    fun toItem(context: Context): StorageTrashItem? =
        StorageTrashIndex.payloadFile(context, id)
            .takeIf { StorageTrashIndex.isSafePayload(context, it) && it.isFile }
            ?.let { payload ->
                StorageTrashItem(
                    id = id,
                    displayName = displayName,
                    payload = payload,
                    originalUriString = originalUriString,
                    parentUriString = parentUriString,
                    sourceSizeBytes = sourceSizeBytes,
                    sourceModifiedMillis = sourceModifiedMillis,
                    sourceSha256 = sourceSha256,
                    sourceDocumentId = sourceDocumentId,
                    needsRecoveryReview = needsReview,
                )
            }
}
