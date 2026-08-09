package dev.devicecontrolcenter

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object ReportExportBuilder {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    fun build(
        snapshot: DeviceSnapshot?,
        history: List<SnapshotHistoryEntity>,
        batteryHistory: List<BatterySampleEntity>,
        networkHistory: List<NetworkSampleEntity>,
        apps: AppCatalogResult,
        storage: StorageScanResult?,
        actions: List<ActionLogEntity> = emptyList(),
    ): String = buildString {
        appendLine("DEVICE CONTROL CENTER — τοπική αναφορά")
        appendLine("Δημιουργήθηκε: ${nowLabel()}")
        appendLine("Δεδομένα: μόνο από αυτή τη συσκευή")
        appendLine("Προσοχή: το αρχείο μπορεί να περιέχει ευαίσθητα στοιχεία συσκευής, εφαρμογών και χρήσης. Αποθήκευσέ το μόνο σε έμπιστο χώρο.")
        appendLine()

        snapshot?.let { current ->
            appendLine("[ΤΡΕΧΟΝ ΣΤΙΓΜΙΟΤΥΠΟ]")
            appendLine("RAM διαθέσιμη: ${SnapshotPresentation.gib(current.availableMemoryBytes)}")
            appendLine("RAM χαμηλής μνήμης: ${current.isLowMemory}")
            appendLine("Θερμική κατάσταση: ${SnapshotPresentation.thermalLabel(current.thermalStatus)}")
            appendLine("Θερμικό όριο: ${current.thermalHeadroom?.let { String.format(Locale.ROOT, "%.0f%%", it * 100) } ?: "μη διαθέσιμο"}")
            appendLine("Αποθήκευση διαθέσιμη: ${SnapshotPresentation.gib(current.availableStorageBytes)} / ${SnapshotPresentation.gib(current.totalStorageBytes)}")
            appendLine("Μπαταρία: ${BatteryPresentation.levelLabel(current.battery.levelPercent)} · ${BatteryPresentation.statusLabel(current.battery.status)}")
            appendLine("Θερμοκρασία μπαταρίας: ${current.battery.temperatureCelsius?.let { "%.1f °C".format(Locale.ROOT, it) } ?: "μη διαθέσιμη"}")
            appendLine("CPU: ${CpuPresentation.activityLabel(current.cpu.activityPercent)}")
            appendLine("Δίκτυο 24ώρου: ${NetworkPresentation.totalLabel(current.network)}")
            appendLine("Usage Access: ${current.hasUsageAccess}")
            appendLine("All Files Access: ${current.hasAllFilesAccess}")
        } ?: appendLine("[ΤΡΕΧΟΝ ΣΤΙΓΜΙΟΤΥΠΟ] μη διαθέσιμο")

        appendLine()
        appendLine("[ΙΣΤΟΡΙΚΟ]")
        appendLine("Στιγμιότυπα: ${history.size}")
        appendLine("Δείγματα μπαταρίας: ${batteryHistory.size}")
        appendLine("Δείγματα δικτύου: ${networkHistory.size}")
        appendLine("Ανάλυση μπαταρίας: ${BatteryHistoryAnalyticsCalculator.calculate(batteryHistory).capacityLabel}")
        appendLine("Παρατηρούμενη φόρτιση: ${BatteryHistoryAnalyticsCalculator.calculate(batteryHistory).observedChargingLabel}")
        appendLine("Σύγκριση δικτύου: ${NetworkHistoryPresentation.comparison(networkHistory)}")
        history.take(20).forEach { entry ->
            appendLine(
                "${timeLabel(entry.capturedAtMillis)} · RAM ${SnapshotHistoryPresentation.memoryLabel(entry)} · " +
                    "μπαταρία ${SnapshotHistoryPresentation.batteryLabel(entry)} · " +
                    "θερμικά ${SnapshotHistoryPresentation.thermalLabel(entry)}",
            )
        }

        appendLine()
        appendLine("[ΑΡΧΕΙΟ ΕΝΕΡΓΕΙΩΝ]")
        if (actions.isEmpty()) {
            appendLine("Δεν υπάρχουν καταγεγραμμένες ενέργειες.")
        } else {
            actions.take(20).forEach { entry ->
                appendLine("${timeLabel(entry.createdAtMillis)} · ${ActionLogPresentation.actionLabel(entry)} · ${ActionLogPresentation.resultLabel(entry)}${entry.details?.let { " · $it" } ?: ""}")
            }
        }

        appendLine()
        appendLine("[ΕΦΑΡΜΟΓΕΣ]")
        appendLine("Ορατές εφαρμογές: ${apps.apps.size}")
        appendLine("Usage Access: ${apps.hasUsageAccess}")
        apps.apps.sortedByDescending { it.totalStorageBytes ?: -1L }.take(20).forEach { app ->
            appendLine("${app.label} (${app.packageName}) · χώρος ${AppPresentation.storageLabel(app)} · δίκτυο ${AppPresentation.networkLabel(app)}")
        }

        appendLine()
        appendLine("[ΑΠΟΘΗΚΕΥΣΗ]")
        if (storage == null) {
            appendLine("Δεν έχει εκτελεστεί read-only σάρωση.")
        } else {
            appendLine(StorageIntelligencePresentation.summary(storage))
            appendLine(StorageIntelligencePresentation.knownSize(storage))
            appendLine(StorageIntelligencePresentation.limitation(storage))
            storage.exactDuplicates?.let { appendLine(StorageIntelligencePresentation.duplicateSummary(it)) }
        }

        appendLine()
        appendLine("[ΠΕΡΙΟΡΙΣΜΟΙ]")
        appendLine("Η αναφορά δεν αποτελεί ιατρική, ασφάλειας ή απόδοσης πιστοποίηση.")
        appendLine("Το Android/OEM μπορεί να αποκρύπτει αισθητήρες, χρήση εφαρμογών, δικτύου ή ιδιωτικά δεδομένα.")
        appendLine("Καμία διαγραφή ή μετακίνηση αρχείων δεν εκτελείται από την εξαγωγή.")
    }

    private fun nowLabel(): String = timeLabel(System.currentTimeMillis())

    private fun timeLabel(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

sealed interface PendingReportExport {
    val content: String
    val action: String
    val successDetails: String
    val successMessage: String
    val failureLabel: String

    data class Plain(override val content: String) : PendingReportExport {
        override val action: String = "export"
        override val successDetails: String = "Η απλή αναφορά αποθηκεύτηκε από τον χρήστη"
        override val successMessage: String = "Η απλή αναφορά αποθηκεύτηκε."
        override val failureLabel: String = "Η απλή εξαγωγή απέτυχε"
    }

    data class Encrypted(override val content: String) : PendingReportExport {
        override val action: String = "export_encrypted"
        override val successDetails: String = "Η κρυπτογραφημένη αναφορά αποθηκεύτηκε από τον χρήστη"
        override val successMessage: String = "Η κρυπτογραφημένη αναφορά αποθηκεύτηκε."
        override val failureLabel: String = "Η κρυπτογραφημένη εξαγωγή απέτυχε"
    }
}

enum class PendingReportExportKind {
    PLAIN,
    ENCRYPTED,
}

/**
 * Owns the small hand-off between CreateDocument and the activity-result callback.
 *
 * The state is split by export kind, so a plain callback can never consume an
 * encrypted request (or vice versa) after recreation. The report is staged in
 * the app-private files directory with an atomic rename, bounded to 8 MiB, and
 * removed on cancellation, completion, failure, or after seven days. This
 * staging file is plaintext even for DCCX exports; it is private to the app and
 * exists only to survive picker/recreation boundaries. The Room database and
 * other app data are not encrypted by this mechanism.
 */
object PendingReportExportStore {
    const val MAX_STAGED_BYTES = 8 * 1024 * 1024

    private const val DIRECTORY_NAME = "pending-report-export"
    private const val STATE_VERSION = "DCC_PENDING_V1"
    private const val MAX_STAGED_AGE_MILLIS = 7L * 24L * 60L * 60L * 1000L

    fun create(context: Context, kind: PendingReportExportKind, content: String): String {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val maxBytes = when (kind) {
            PendingReportExportKind.PLAIN -> MAX_STAGED_BYTES
            PendingReportExportKind.ENCRYPTED -> EncryptedReportExport.MAX_PLAINTEXT_BYTES
        }
        require(bytes.size <= maxBytes) {
            "Η αναφορά είναι μεγαλύτερη από το όριο των 8 MiB."
        }

        val directory = directory(context).apply { mkdirs() }
        clear(context, kind)
        val token = UUID.randomUUID().toString()
        val payload = payloadFile(directory, kind, token)
        val state = stateFile(directory, kind)
        atomicWrite(payload, bytes)
        try {
            atomicWrite(state, "$STATE_VERSION\n$token\n${System.currentTimeMillis()}\n".toByteArray(StandardCharsets.UTF_8))
        } catch (error: Throwable) {
            payload.delete()
            throw error
        }
        return token
    }

    fun hasPending(context: Context, kind: PendingReportExportKind): Boolean = stateFile(directory(context), kind).isFile

    fun load(context: Context, kind: PendingReportExportKind): PendingReportExport {
        val directory = directory(context)
        val state = readState(stateFile(directory, kind))
        val content = readBounded(payloadFile(directory, kind, state.token)).toString(StandardCharsets.UTF_8)
        return when (kind) {
            PendingReportExportKind.PLAIN -> PendingReportExport.Plain(content)
            PendingReportExportKind.ENCRYPTED -> PendingReportExport.Encrypted(content)
        }
    }

    fun clear(context: Context, kind: PendingReportExportKind) {
        val directory = directory(context)
        val state = stateFile(directory, kind)
        val token = runCatching { readState(state).token }.getOrNull()
        if (token != null) {
            payloadFile(directory, kind, token).delete()
        } else {
            forEachChild(directory) { file ->
                if (file.name.startsWith(prefix(kind)) && file.name.endsWith(".payload")) {
                    file.delete()
                }
            }
        }
        state.delete()
        forEachChild(directory) { file ->
            if (file.name.startsWith(prefix(kind)) && file.name.endsWith(".tmp")) {
                file.delete()
            }
        }
    }

    fun cleanup(context: Context) {
        val directory = directory(context)
        val now = System.currentTimeMillis()
        forEachChild(directory) { file ->
            val stale = now - file.lastModified() > MAX_STAGED_AGE_MILLIS
            val temporary = file.name.endsWith(".tmp")
            if (temporary || stale) file.delete()
        }
    }

    /** Enumerates the private staging directory without materializing all children at once. */
    private fun forEachChild(directory: File, action: (File) -> Unit) {
        runCatching {
            Files.newDirectoryStream(directory.toPath()).use { children ->
                for (path in children) action(path.toFile())
            }
        }
    }

    private data class State(val token: String, val createdAt: Long)

    private fun directory(context: Context): File = File(context.filesDir, DIRECTORY_NAME)

    private fun prefix(kind: PendingReportExportKind): String = when (kind) {
        PendingReportExportKind.PLAIN -> "plain-"
        PendingReportExportKind.ENCRYPTED -> "encrypted-"
    }

    private fun stateFile(directory: File, kind: PendingReportExportKind): File =
        File(directory, "${prefix(kind)}state")

    private fun payloadFile(directory: File, kind: PendingReportExportKind, token: String): File {
        require(token.matches(Regex("[0-9a-fA-F-]{36}"))) { "Μη έγκυρη εκκρεμής εξαγωγή." }
        return File(directory, "${prefix(kind)}$token.payload")
    }

    private fun readState(file: File): State {
        require(file.isFile) { "Η εκκρεμής αναφορά δεν είναι πλέον διαθέσιμη." }
        val lines = file.readLines(StandardCharsets.UTF_8)
        require(lines.size == 3 && lines[0] == STATE_VERSION) { "Μη έγκυρη εσωτερική κατάσταση εξαγωγής." }
        val createdAt = lines[2].toLongOrNull() ?: error("Μη έγκυρη ημερομηνία εκκρεμούς εξαγωγής.")
        return State(lines[1], createdAt)
    }

    private fun readBounded(file: File): ByteArray {
        require(file.isFile) { "Το προσωρινό περιεχόμενο της αναφοράς δεν είναι πλέον διαθέσιμο." }
        return FileInputStream(file).use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    require(total <= MAX_STAGED_BYTES) { "Η εκκρεμής αναφορά ξεπερνά το όριο των 8 MiB." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            require(temporary.renameTo(target)) { "Δεν ήταν δυνατή η ασφαλής προετοιμασία της εξαγωγής." }
        } finally {
            temporary.delete()
        }
    }
}

/** Writes only fully prepared bytes; the destination stream is not opened before preparation. */
object ReportExportWriter {
    fun write(context: Context, uri: Uri, bytes: ByteArray) {
        require(bytes.size <= PendingReportExportStore.MAX_STAGED_BYTES) {
            "Η εξαγωγή ξεπερνά το όριο των 8 MiB."
        }
        check(!Thread.currentThread().isInterrupted) {
            "Η εξαγωγή ακυρώθηκε πριν από την εγγραφή."
        }
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                BufferedOutputStream(output).use { stream ->
                    stream.write(bytes)
                    stream.flush()
                }
            } ?: error("Δεν ήταν δυνατή η εγγραφή της αναφοράς.")
        } catch (error: Throwable) {
            // The URI is selected by the user and may refer to an existing document.
            // Never delete it on a provider or flush failure; an incomplete destination
            // must remain recoverable for the user to inspect or replace explicitly.
            throw error
        }
    }
}

/**
 * Encrypts an exported report for local use with a key held by Android Keystore.
 *
 * DCCX v1 binary format (big-endian): magic `DCCX`, one-byte format version,
 * one-byte algorithm id, one-byte IV length, IV, four-byte ciphertext length,
 * and AES-GCM ciphertext including its authentication tag. The UTF-8 report is
 * limited so the complete encrypted output, including its format overhead, remains within 8 MiB.
 * The Keystore key is app/device-bound; this export is not intended to be
 * decrypted on another device or after the key is invalidated.
 */
object EncryptedReportExport {
    const val FORMAT_DESCRIPTION = "DCCX v1 · AES-GCM · IV 12 bytes · όριο τελικού αρχείου 8 MiB"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "device_control_center_report_v1"
    private const val MAGIC = 0x44434358 // ASCII DCCX
    private const val FORMAT_VERSION = 1
    private const val AES_GCM_ALGORITHM_ID = 1
    private const val EXPECTED_IV_BYTES = 12
    private const val AES_GCM_TAG_BYTES = 16
    private const val FIXED_HEADER_BYTES = 4 + 1 + 1 + 1 + 4

    /** Maximum UTF-8 report size that still produces an 8 MiB-or-smaller DCCX file. */
    const val MAX_PLAINTEXT_BYTES =
        PendingReportExportStore.MAX_STAGED_BYTES -
            FIXED_HEADER_BYTES - EXPECTED_IV_BYTES - AES_GCM_TAG_BYTES

    fun encrypt(report: String): ByteArray {
        val plaintext = report.toByteArray(StandardCharsets.UTF_8)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) {
            "Η αναφορά είναι μεγαλύτερη από το όριο των 8 MiB."
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size == EXPECTED_IV_BYTES) { "Το Android επέστρεψε μη αναμενόμενο IV." }
        val ciphertext = cipher.doFinal(plaintext)

        return ByteArrayOutputStream(16 + iv.size + ciphertext.size).use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(MAGIC)
                output.writeByte(FORMAT_VERSION)
                output.writeByte(AES_GCM_ALGORITHM_ID)
                output.writeByte(iv.size)
                output.write(iv)
                output.writeInt(ciphertext.size)
                output.write(ciphertext)
            }
            buffer.toByteArray()
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
