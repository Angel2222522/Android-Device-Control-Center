package dev.devicecontrolcenter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val DccColorScheme = darkColorScheme(
    primary = Color(0xFF74E5F0),
    onPrimary = Color(0xFF00262B),
    primaryContainer = Color(0xFF164A56),
    onPrimaryContainer = Color(0xFFB5F5FA),
    secondary = Color(0xFFC3AEFF),
    onSecondary = Color(0xFF24124D),
    tertiary = Color(0xFFFFC66D),
    onTertiary = Color(0xFF3C2100),
    background = Color(0xFF080D18),
    onBackground = Color(0xFFF3F6FF),
    surface = Color(0xFF111A2C),
    onSurface = Color(0xFFF3F6FF),
    surfaceVariant = Color(0xFF1B273D),
    onSurfaceVariant = Color(0xFFB4C0D5),
    outline = Color(0xFF46536B),
    error = Color(0xFFFF9C9C),
    onError = Color(0xFF4B080D),
)

private val DccLightColorScheme = lightColorScheme(
    primary = Color(0xFF006A73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF0F4),
    onPrimaryContainer = Color(0xFF002023),
    secondary = Color(0xFF5F4A91),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF211047),
    tertiary = Color(0xFF8A5000),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB2),
    onTertiaryContainer = Color(0xFF2D1600),
    background = Color(0xFFF8FAFF),
    onBackground = Color(0xFF171B23),
    surface = Color.White,
    onSurface = Color(0xFF171B23),
    surfaceVariant = Color(0xFFE0E5F0),
    onSurfaceVariant = Color(0xFF424852),
    outline = Color(0xFF727983),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val NeutralAccent = Color(0xFF74E5F0)
private val InfoAccent = Color(0xFF9DBBFF)
private val WarningAccent = Color(0xFFFFC66D)
private val CriticalAccent = Color(0xFFFF929F)
private val UnavailableAccent = Color(0xFFAEB8C9)

private enum class DccSection {
    OVERVIEW,
    APPS,
    STORAGE,
    SIGNALS,
    PRIVACY,
}

class MainActivity : ComponentActivity() {
    private val allFilesAccessState = mutableStateOf(false)
    private val resumeEpoch = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        allFilesAccessState.value = Environment.isExternalStorageManager()
        setContent {
            PremiumTheme {
                CapabilityRoute(
                    context = this@MainActivity,
                    hasAllFilesAccess = allFilesAccessState.value,
                    resumeEpoch = resumeEpoch.value,
                    onEnableAllFilesAccess = ::openAllFilesAccessSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        allFilesAccessState.value = Environment.isExternalStorageManager()
        resumeEpoch.value += 1
    }

    private fun openAllFilesAccessSettings() {
        val appSettingsIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(appSettingsIntent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }
}

@Composable
private fun PremiumTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (isDark) DccColorScheme else DccLightColorScheme,
        content = content,
    )
}

@Composable
private fun CapabilityRoute(
    context: Context,
    hasAllFilesAccess: Boolean,
    resumeEpoch: Int,
    onEnableAllFilesAccess: () -> Unit,
) {
    val state = remember { mutableStateOf(SnapshotUiState<DeviceSnapshot>()) }
    val historyState = remember { mutableStateOf(HistoryUiState()) }
    val batteryHistoryState = remember { mutableStateOf(BatteryHistoryUiState()) }
    val networkHistoryState = remember { mutableStateOf(NetworkHistoryUiState()) }
    val actionLogState = remember { mutableStateOf(ActionLogUiState()) }
    val appCatalogState = remember { mutableStateOf(AppCatalogResult()) }
    val storageState = remember {
        mutableStateOf(StorageScanState(selectedTreeUri = StorageSelectionStore.read(context)))
    }
    val storageExplorerUri = remember { mutableStateOf<Uri?>(null) }
    val selectedSection = remember { mutableStateOf(DccSection.OVERVIEW) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val active = remember { AtomicBoolean(true) }
    val refreshGate = remember { SnapshotRefreshGate() }
    val storageScanInFlight = remember { AtomicBoolean(false) }
    val appLoadInFlight = remember { AtomicBoolean(false) }
    val historyRepository = remember(context) { SnapshotHistoryRepository(context) }
    val periodicSnapshotsEnabled = remember { mutableStateOf(LocalAutomationManager.isEnabled(context)) }
    fun showActionFailure(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            refreshGate.cancel()
            storageScanInFlight.set(false)
            executor.shutdownNow()
        }
    }

    fun readHistory(): HistoryReadResult = historyRepository.readHistory()

    fun loadHistory() {
        historyState.value = historyState.value.copy(isLoading = true, errorMessage = null)
        batteryHistoryState.value = batteryHistoryState.value.copy(isLoading = true, errorMessage = null)
        networkHistoryState.value = networkHistoryState.value.copy(isLoading = true, errorMessage = null)
        actionLogState.value = actionLogState.value.copy(isLoading = true, errorMessage = null)
        runCatching {
            executor.execute {
                val result = runCatching { readHistory() }
                mainHandler.post {
                    if (!active.get()) return@post
                    historyState.value = result.fold(
                        onSuccess = { entries -> HistoryUiState(entries = entries.snapshots) },
                        onFailure = {
                            HistoryUiState(errorMessage = "Το τοπικό ιστορικό δεν είναι διαθέσιμο τώρα.")
                        },
                    )
                    batteryHistoryState.value = result.fold(
                        onSuccess = { entries -> BatteryHistoryUiState(entries = entries.battery) },
                        onFailure = { BatteryHistoryUiState(errorMessage = "Το ιστορικό μπαταρίας δεν είναι διαθέσιμο τώρα.") },
                    )
                    networkHistoryState.value = result.fold(
                        onSuccess = { entries -> NetworkHistoryUiState(entries = entries.network) },
                        onFailure = { NetworkHistoryUiState(errorMessage = "Το ιστορικό δικτύου δεν είναι διαθέσιμο τώρα.") },
                    )
                    actionLogState.value = result.fold(
                        onSuccess = { entries -> ActionLogUiState(entries = entries.actions) },
                        onFailure = { ActionLogUiState(errorMessage = "Το αρχείο ενεργειών δεν είναι διαθέσιμο τώρα.") },
                    )
                }
            }
        }.onFailure {
            historyState.value = HistoryUiState(errorMessage = "Το τοπικό ιστορικό δεν είναι διαθέσιμο τώρα.")
            batteryHistoryState.value = BatteryHistoryUiState(errorMessage = "Το ιστορικό μπαταρίας δεν είναι διαθέσιμο τώρα.")
            networkHistoryState.value = NetworkHistoryUiState(errorMessage = "Το ιστορικό δικτύου δεν είναι διαθέσιμο τώρα.")
            actionLogState.value = ActionLogUiState(errorMessage = "Το αρχείο ενεργειών δεν είναι διαθέσιμο τώρα.")
        }
    }

    fun loadDurableTrash() {
        runCatching {
            executor.execute {
                val result = runCatching { StorageTrashService.loadAll(context) }
                mainHandler.post {
                    if (!active.get()) return@post
                    storageState.value = result.fold(
                        onSuccess = { items ->
                            storageState.value.copy(
                                lastTrashItem = items.firstOrNull(),
                                trashItems = items,
                            )
                        },
                        onFailure = { error ->
                            storageState.value.copy(
                                errorMessage = "Ο ιδιωτικός κάδος δεν φορτώθηκε: ${error.message ?: "δοκίμασε ξανά"}. Τα υπάρχοντα αντίγραφα δεν θεωρούνται χαμένα.",
                            )
                        },
                    )
                }
            }
        }
    }

    fun loadApps() {
        if (!appLoadInFlight.compareAndSet(false, true)) return
        runCatching {
            executor.execute {
                val result = runCatching { AppCatalogReader.read(context) }
                    .getOrElse { AppCatalogResult(errorMessage = "Η λίστα εφαρμογών δεν είναι διαθέσιμη τώρα.") }
                if (result.errorMessage == null) {
                    // App history is best-effort: a Room failure must never turn
                    // a successful package inventory into an App Center error.
                    runCatching { historyRepository.recordAppUsage(result) }
                }
                mainHandler.post {
                    appLoadInFlight.set(false)
                    if (active.get()) appCatalogState.value = result
                }
            }
        }.onFailure {
            appLoadInFlight.set(false)
            appCatalogState.value = AppCatalogResult(errorMessage = "Η λίστα εφαρμογών δεν είναι διαθέσιμη τώρα.")
        }
    }

    fun persistSnapshot(snapshot: DeviceSnapshot, capturedAtMillis: Long) {
        runCatching {
            executor.execute {
                val result = runCatching {
                    val writeResult = historyRepository.recordWithAction(
                        snapshot = snapshot,
                        capturedAtMillis = capturedAtMillis,
                        action = "manual_refresh",
                        result = "success",
                        details = "Το στιγμιότυπο αποθηκεύτηκε τοπικά",
                    )
                    check(writeResult.status == HistoryWriteStatus.RECORDED) {
                        "Το στιγμιότυπο δεν αποθηκεύτηκε επειδή προηγήθηκε διαγραφή ιστορικού."
                    }
                    readHistory()
                }
                mainHandler.post {
                    if (!active.get()) return@post
                    historyState.value = result.fold(
                        onSuccess = { entries -> HistoryUiState(entries = entries.snapshots) },
                        onFailure = {
                            HistoryUiState(errorMessage = "Το στιγμιότυπο εμφανίστηκε, αλλά δεν αποθηκεύτηκε στο ιστορικό.")
                        },
                    )
                    batteryHistoryState.value = result.fold(
                        onSuccess = { entries -> BatteryHistoryUiState(entries = entries.battery) },
                        onFailure = { BatteryHistoryUiState(errorMessage = "Το δείγμα μπαταρίας δεν αποθηκεύτηκε.") },
                    )
                    networkHistoryState.value = result.fold(
                        onSuccess = { entries -> NetworkHistoryUiState(entries = entries.network) },
                        onFailure = { NetworkHistoryUiState(errorMessage = "Το δείγμα δικτύου δεν αποθηκεύτηκε.") },
                    )
                    actionLogState.value = result.fold(
                        onSuccess = { entries -> ActionLogUiState(entries = entries.actions) },
                        onFailure = { ActionLogUiState(errorMessage = "Το αρχείο ενεργειών δεν είναι διαθέσιμο τώρα.") },
                    )
                }
            }
        }.onFailure {
            historyState.value = HistoryUiState(errorMessage = "Το στιγμιότυπο εμφανίστηκε, αλλά δεν αποθηκεύτηκε στο ιστορικό.")
            batteryHistoryState.value = BatteryHistoryUiState(errorMessage = "Το δείγμα μπαταρίας δεν αποθηκεύτηκε.")
            networkHistoryState.value = NetworkHistoryUiState(errorMessage = "Το δείγμα δικτύου δεν αποθηκεύτηκε.")
            actionLogState.value = ActionLogUiState(errorMessage = "Το αρχείο ενεργειών δεν είναι διαθέσιμο τώρα.")
        }
    }

    fun clearHistory() {
        historyState.value = historyState.value.copy(isLoading = true, errorMessage = null)
        batteryHistoryState.value = batteryHistoryState.value.copy(isLoading = true, errorMessage = null)
        networkHistoryState.value = networkHistoryState.value.copy(isLoading = true, errorMessage = null)
        runCatching {
            executor.execute {
                val result = runCatching {
                    historyRepository.clearTelemetryHistoryWithAction(
                        details = "Διαγράφηκαν οι τοπικές μετρήσεις· το audit log διατηρήθηκε.",
                    )
                    readHistory()
                }
                mainHandler.post {
                    if (!active.get()) return@post
                    historyState.value = result.fold(
                        onSuccess = { HistoryUiState(entries = it.snapshots) },
                        onFailure = { HistoryUiState(errorMessage = "Δεν διαγράφηκε το τοπικό ιστορικό.") },
                    )
                    batteryHistoryState.value = result.fold(
                        onSuccess = { BatteryHistoryUiState(entries = it.battery) },
                        onFailure = { BatteryHistoryUiState(errorMessage = "Δεν διαγράφηκε το ιστορικό μπαταρίας.") },
                    )
                    networkHistoryState.value = result.fold(
                        onSuccess = { NetworkHistoryUiState(entries = it.network) },
                        onFailure = { NetworkHistoryUiState(errorMessage = "Δεν διαγράφηκε το ιστορικό δικτύου.") },
                    )
                    actionLogState.value = result.fold(
                        onSuccess = { ActionLogUiState(entries = it.actions) },
                        onFailure = { ActionLogUiState(errorMessage = "Το αρχείο ενεργειών δεν είναι διαθέσιμο τώρα.") },
                    )
                }
            }
        }.onFailure {
            historyState.value = HistoryUiState(errorMessage = "Δεν διαγράφηκε το τοπικό ιστορικό.")
            batteryHistoryState.value = BatteryHistoryUiState(errorMessage = "Δεν διαγράφηκε το ιστορικό μπαταρίας.")
            networkHistoryState.value = NetworkHistoryUiState(errorMessage = "Δεν διαγράφηκε το ιστορικό δικτύου.")
        }
    }

    fun logAction(action: String, result: String, details: String? = null) {
        runCatching {
            executor.execute {
                val entriesResult = runCatching {
                    historyRepository.recordAction(action, result, details)
                    historyRepository.recentActions()
                }
                mainHandler.post {
                    if (!active.get()) return@post
                    actionLogState.value = entriesResult.fold(
                        onSuccess = { entries -> ActionLogUiState(entries = entries) },
                        onFailure = { error ->
                            ActionLogUiState(
                                entries = actionLogState.value.entries,
                                errorMessage = "Η ενέργεια ολοκληρώθηκε, αλλά δεν καταγράφηκε στο τοπικό αρχείο: ${error.message ?: "άγνωστο σφάλμα"}.",
                            )
                        },
                    )
                }
            }
        }.onFailure { error ->
            if (active.get()) {
                val message = "Η ενέργεια ολοκληρώθηκε, αλλά δεν προγραμματίστηκε η καταγραφή: ${error.message ?: "ο executor δεν είναι διαθέσιμος"}."
                actionLogState.value = actionLogState.value.copy(errorMessage = message)
                showActionFailure(message)
            }
        }
    }

    fun scanStorage(uri: android.net.Uri) {
        if (!storageScanInFlight.compareAndSet(false, true)) return

        storageState.value = storageState.value.copy(
            selectedTreeUri = uri,
            source = StorageScanSource.SELECTED_FOLDER,
            canWriteSource = StorageSelectionStore.hasWriteAccess(context, uri),
            isScanning = true,
            isHashing = false,
            exactDuplicateResult = null,
            actionMessage = null,
            errorMessage = null,
        )
        runCatching {
            executor.execute {
                val result = runCatching {
                    StorageScanner.scan(
                        context = context,
                        treeUri = uri,
                        shouldContinue = { active.get() && storageScanInFlight.get() },
                    )
                }
                mainHandler.post {
                    storageScanInFlight.set(false)
                    if (!active.get()) return@post
                    storageState.value = result.fold(
                        onSuccess = { scan ->
                            logAction("storage_scan", "success", "${scan.filesScanned} αρχεία · ${scan.directoriesScanned} φάκελοι")
                            StorageScanState(
                                selectedTreeUri = uri,
                                result = scan,
                                source = StorageScanSource.SELECTED_FOLDER,
                                lastTrashItem = storageState.value.lastTrashItem,
                                trashItems = storageState.value.trashItems,
                                canWriteSource = StorageSelectionStore.hasWriteAccess(context, uri),
                            )
                        },
                        onFailure = { error ->
                            logAction("storage_scan", "failure", error.message)
                            storageState.value.copy(
                                isScanning = false,
                                errorMessage = error.message ?: "Η read-only σάρωση απέτυχε.",
                            )
                        },
                    )
                }
            }
        }.onFailure {
            storageScanInFlight.set(false)
            storageState.value = storageState.value.copy(
                isScanning = false,
                errorMessage = "Η read-only σάρωση απέτυχε.",
            )
        }
    }

    fun scanAllStorage() {
        if (!storageScanInFlight.compareAndSet(false, true)) return

        val selectedTreeUri = storageState.value.selectedTreeUri
        storageState.value = storageState.value.copy(
            source = StorageScanSource.SHARED_STORAGE,
            canWriteSource = hasAllFilesAccess,
            isScanning = true,
            isHashing = false,
            exactDuplicateResult = null,
            actionMessage = null,
            errorMessage = null,
        )
        runCatching {
            executor.execute {
                val result = runCatching {
                    SharedStorageScanner.scan(
                        context = context,
                        shouldContinue = { active.get() && storageScanInFlight.get() },
                    )
                }
                mainHandler.post {
                    storageScanInFlight.set(false)
                    if (!active.get()) return@post
                    storageState.value = result.fold(
                        onSuccess = { scan ->
                            logAction("storage_scan", "success", "Κοινόχρηστοι χώροι · ${scan.filesScanned} αρχεία")
                            StorageScanState(
                                selectedTreeUri = selectedTreeUri,
                                result = scan,
                                source = StorageScanSource.SHARED_STORAGE,
                                lastTrashItem = storageState.value.lastTrashItem,
                                trashItems = storageState.value.trashItems,
                                canWriteSource = hasAllFilesAccess,
                            )
                        },
                        onFailure = { error ->
                            logAction("storage_scan", "failure", error.message)
                            storageState.value.copy(
                                isScanning = false,
                                errorMessage = error.message ?: "Η read-only σάρωση απέτυχε.",
                            )
                        },
                    )
                }
            }
        }.onFailure {
            storageScanInFlight.set(false)
            storageState.value = storageState.value.copy(
                isScanning = false,
                errorMessage = "Η read-only σάρωση απέτυχε.",
            )
        }
    }

    fun hashDuplicates() {
        val result = storageState.value.result ?: return
        if (!storageScanInFlight.compareAndSet(false, true)) return
        storageState.value = storageState.value.copy(isHashing = true, actionMessage = null, errorMessage = null)
        runCatching {
            executor.execute {
                val hashed = runCatching {
                    StorageDuplicateScanner.scan(
                        context = context,
                        result = result,
                        shouldContinue = { active.get() && storageScanInFlight.get() },
                    )
                }
                mainHandler.post {
                    storageScanInFlight.set(false)
                    if (!active.get()) return@post
                    storageState.value = hashed.fold(
                        onSuccess = { value ->
                            logAction("duplicate_scan", "success", StorageIntelligencePresentation.duplicateSummary(value))
                            storageState.value.copy(isHashing = false, exactDuplicateResult = value)
                        },
                        onFailure = { error ->
                            logAction("duplicate_scan", "failure", error.message)
                            storageState.value.copy(isHashing = false, errorMessage = error.message ?: "Ο έλεγχος διπλοτύπων απέτυχε.")
                        },
                    )
                }
            }
        }.onFailure {
            storageScanInFlight.set(false)
            storageState.value = storageState.value.copy(isHashing = false, errorMessage = "Ο έλεγχος διπλοτύπων απέτυχε.")
        }
    }

    fun moveToTrash(entry: StorageFileEntry) {
        if (!storageScanInFlight.compareAndSet(false, true)) return
        storageState.value = storageState.value.copy(isMutating = true, actionMessage = null, errorMessage = null)
        runCatching {
            executor.execute {
                val moved = runCatching { StorageTrashService.moveToTrash(context, entry) }
                mainHandler.post {
                    storageScanInFlight.set(false)
                    if (!active.get()) return@post
                    storageState.value = moved.fold(
                        onSuccess = { item ->
                            logAction("move_to_trash", "success", item.displayName)
                            val items = listOf(item) + storageState.value.trashItems.filterNot { it.id == item.id }
                            storageState.value.copy(isMutating = false, lastTrashItem = item, trashItems = items, actionMessage = "Το αρχείο μετακινήθηκε στον ιδιωτικό κάδο. Μπορείς να το επαναφέρεις από την ενότητα ιδιωτικού κάδου.")
                        },
                        onFailure = { error ->
                            logAction("move_to_trash", "failure", error.message)
                            storageState.value.copy(isMutating = false, errorMessage = error.message ?: "Η ενέργεια αρχείου απέτυχε.")
                        },
                    )
                }
            }
        }.onFailure {
            storageScanInFlight.set(false)
            storageState.value = storageState.value.copy(isMutating = false, errorMessage = "Η ενέργεια αρχείου απέτυχε.")
        }
    }

    fun restoreTrash(item: StorageTrashItem) {
        if (!storageScanInFlight.compareAndSet(false, true)) return
        storageState.value = storageState.value.copy(isMutating = true, actionMessage = null)
        runCatching {
            executor.execute {
                val restored = runCatching { StorageTrashService.restore(context, item) }
                mainHandler.post {
                    storageScanInFlight.set(false)
                    if (!active.get()) return@post
                    storageState.value = restored.fold(
                        onSuccess = {
                            logAction("restore_from_trash", "success", item.displayName)
                            val items = storageState.value.trashItems.filterNot { it.id == item.id }
                            storageState.value.copy(isMutating = false, lastTrashItem = items.firstOrNull(), trashItems = items, actionMessage = "Το αρχείο επαναφέρθηκε με ασφάλεια.")
                        },
                        onFailure = { error ->
                            logAction("restore_from_trash", "failure", error.message)
                            storageState.value.copy(isMutating = false, errorMessage = error.message ?: "Η αναίρεση απέτυχε.")
                        },
                    )
                }
            }
        }.onFailure {
            storageScanInFlight.set(false)
            storageState.value = storageState.value.copy(isMutating = false, errorMessage = "Η αναίρεση απέτυχε.")
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val permissionPersisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                StorageSelectionStore.hasReadAccess(context, uri)
            }.getOrDefault(false)
            if (permissionPersisted) {
                StorageSelectionStore.save(context, uri)
            } else {
                StorageSelectionStore.clear(context)
                showActionFailure("Το Android δεν κράτησε μόνιμη άδεια για τον επιλεγμένο φάκελο. Η τρέχουσα σάρωση θα παραμείνει μόνο για αυτή τη συνεδρία.")
            }
            scanStorage(uri)
        }
    }

    fun exportAction(kind: PendingReportExportKind): String = when (kind) {
        PendingReportExportKind.PLAIN -> "export_report"
        PendingReportExportKind.ENCRYPTED -> "export_encrypted_report"
    }

    fun exportLabel(kind: PendingReportExportKind): String = when (kind) {
        PendingReportExportKind.PLAIN -> "Η απλή εξαγωγή απέτυχε"
        PendingReportExportKind.ENCRYPTED -> "Η κρυπτογραφημένη εξαγωγή απέτυχε"
    }

    fun exportCancelMessage(kind: PendingReportExportKind): String = when (kind) {
        PendingReportExportKind.PLAIN -> "Η απλή εξαγωγή ακυρώθηκε. Δεν γράφτηκαν δεδομένα."
        PendingReportExportKind.ENCRYPTED -> "Η κρυπτογραφημένη εξαγωγή ακυρώθηκε. Δεν γράφτηκαν δεδομένα."
    }

    fun handleExportResult(kind: PendingReportExportKind, uri: Uri?) {
        if (uri == null) {
            val hadPending = PendingReportExportStore.hasPending(context, kind)
            PendingReportExportStore.clear(context, kind)
            if (hadPending) {
                logAction(exportAction(kind), "cancelled", "Ο χρήστης ακύρωσε την επιλογή προορισμού· δεν άνοιξε αρχείο εγγραφής")
                Toast.makeText(context, exportCancelMessage(kind), Toast.LENGTH_LONG).show()
            } else {
                logAction(exportAction(kind), "failure", "Το Android επέστρεψε ακύρωση χωρίς έγκυρη εκκρεμή κατάσταση")
                showActionFailure("${exportLabel(kind)}: μη έγκυρη εκκρεμής κατάσταση. Δεν γράφτηκαν δεδομένα.")
            }
            return
        }

        val submitResult = runCatching {
            executor.execute {
                val writeResult = runCatching {
                    val export = PendingReportExportStore.load(context, kind)
                    val bytes = when (export) {
                        is PendingReportExport.Plain -> export.content.toByteArray(Charsets.UTF_8)
                        is PendingReportExport.Encrypted -> EncryptedReportExport.encrypt(export.content)
                    }
                    // Prepare everything before opening the destination stream. Keep the
                    // private hand-off until the provider confirms a successful write so a
                    // provider failure can be retried after recreation.
                    writeThenClearPendingReport(
                        write = { ReportExportWriter.write(context, uri, bytes) },
                        clearPending = { PendingReportExportStore.clear(context, kind) },
                    )
                    export
                }
                writeResult.onSuccess { export ->
                    logAction(exportAction(kind), "success", export.successDetails)
                    mainHandler.post {
                        if (active.get()) Toast.makeText(context, export.successMessage, Toast.LENGTH_LONG).show()
                    }
                }.onFailure { error ->
                    logAction(exportAction(kind), "failure", error.message)
                    mainHandler.post {
                        if (active.get()) showActionFailure("${exportLabel(kind)}: ${error.message ?: "άγνωστο σφάλμα"}")
                    }
                }
            }
        }
        submitResult.onFailure { error ->
            logAction(exportAction(kind), "failure", error.message)
            showActionFailure("${exportLabel(kind)}: ${error.message ?: "η εργασία δεν ξεκίνησε"}")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> handleExportResult(PendingReportExportKind.PLAIN, uri) }

    val encryptedExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> handleExportResult(PendingReportExportKind.ENCRYPTED, uri) }

    fun buildReport(): String = ReportExportBuilder.build(
        snapshot = state.value.snapshot,
        history = historyState.value.entries,
        batteryHistory = batteryHistoryState.value.entries,
        networkHistory = networkHistoryState.value.entries,
        apps = appCatalogState.value,
        storage = storageState.value.result,
        actions = actionLogState.value.entries,
    )

    fun startPlainExport() {
        val report = runCatching { buildReport() }.getOrElse { error ->
            logAction("export_report", "failure", error.message)
            showActionFailure("Η απλή εξαγωγή απέτυχε: ${error.message ?: "δεν δημιουργήθηκε αναφορά"}")
            return
        }
        runCatching {
            executor.execute {
                val staged = runCatching { PendingReportExportStore.create(context, PendingReportExportKind.PLAIN, report) }
                mainHandler.post {
                    staged.fold(
                        onSuccess = {
                            runCatching { exportLauncher.launch("device-control-center-report.txt") }
                                .onFailure { error ->
                                    PendingReportExportStore.clear(context, PendingReportExportKind.PLAIN)
                                    logAction("export_report", "failure", error.message)
                                    showActionFailure("Η απλή εξαγωγή απέτυχε: ${error.message ?: "ο επιλογέας δεν άνοιξε"}")
                                }
                        },
                        onFailure = { error ->
                            logAction("export_report", "failure", error.message)
                            showActionFailure("Η απλή εξαγωγή απέτυχε: ${error.message ?: "δεν προετοιμάστηκε η αναφορά"}")
                        },
                    )
                }
            }
        }.onFailure { error ->
            logAction("export_report", "failure", error.message)
            showActionFailure("Η απλή εξαγωγή απέτυχε: ${error.message ?: "η εργασία δεν ξεκίνησε"}")
        }
    }

    fun startEncryptedExport() {
        val report = runCatching { buildReport() }.getOrElse { error ->
            logAction("export_encrypted_report", "failure", error.message)
            showActionFailure("Η κρυπτογραφημένη εξαγωγή απέτυχε: ${error.message ?: "δεν δημιουργήθηκε αναφορά"}")
            return
        }
        runCatching {
            executor.execute {
                val staged = runCatching { PendingReportExportStore.create(context, PendingReportExportKind.ENCRYPTED, report) }
                mainHandler.post {
                    staged.fold(
                        onSuccess = {
                            runCatching { encryptedExportLauncher.launch("device-control-center-report.dccx") }
                                .onFailure { error ->
                                    PendingReportExportStore.clear(context, PendingReportExportKind.ENCRYPTED)
                                    logAction("export_encrypted_report", "failure", error.message)
                                    showActionFailure("Η κρυπτογραφημένη εξαγωγή απέτυχε: ${error.message ?: "ο επιλογέας δεν άνοιξε"}")
                                }
                        },
                        onFailure = { error ->
                            logAction("export_encrypted_report", "failure", error.message)
                            showActionFailure("Η κρυπτογραφημένη εξαγωγή απέτυχε: ${error.message ?: "δεν προετοιμάστηκε η αναφορά"}")
                        },
                    )
                }
            }
        }.onFailure { error ->
            logAction("export_encrypted_report", "failure", error.message)
            showActionFailure("Η κρυπτογραφημένη εξαγωγή απέτυχε: ${error.message ?: "η εργασία δεν ξεκίνησε"}")
        }
    }

    fun refresh() {
        if (!refreshGate.tryStart(SystemClock.elapsedRealtime())) return

        state.value = state.value.beginRefresh()
        val submitResult = runCatching {
            executor.execute {
                val result = runCatching { DeviceSnapshotReader.read(context) }
                val completedAtElapsed = SystemClock.elapsedRealtime()
                val capturedAtMillis = System.currentTimeMillis()
                mainHandler.post {
                    if (!active.get()) return@post

                    refreshGate.complete(completedAtElapsed)
                    state.value = result.fold(
                        onSuccess = { snapshot ->
                            persistSnapshot(snapshot, capturedAtMillis)
                            state.value.success(snapshot, capturedAtMillis)
                        },
                        onFailure = {
                            state.value.failure("Η συλλογή του στιγμιότυπου απέτυχε. Δοκίμασε ξανά.")
                        },
                    )
                }
            }
        }

        submitResult.onFailure {
            refreshGate.complete(SystemClock.elapsedRealtime())
            state.value = state.value.failure("Η συλλογή του στιγμιότυπου απέτυχε. Δοκίμασε ξανά.")
        }
    }

    LaunchedEffect(Unit) {
        executor.execute { PendingReportExportStore.cleanup(context) }
        loadHistory()
        loadApps()
        refresh()
    }

    LaunchedEffect(resumeEpoch) {
        loadDurableTrash()
    }

    val currentState = state.value
    val snapshot = currentState.snapshot
    when {
        snapshot != null -> CapabilityScreen(
            snapshot = snapshot,
            state = currentState,
            onRefresh = ::refresh,
            historyState = historyState.value,
            storageState = storageState.value,
            storageExplorerUri = storageExplorerUri.value,
            hasAllFilesAccess = hasAllFilesAccess,
            onChooseStorageFolder = { folderPicker.launch(null) },
            onEnableAllFilesAccess = onEnableAllFilesAccess,
            onScanAllStorage = ::scanAllStorage,
            onHashDuplicates = ::hashDuplicates,
            onMoveToTrash = ::moveToTrash,
            onRestoreTrash = ::restoreTrash,
            onRescanStorage = {
                when (storageState.value.source) {
                    StorageScanSource.SHARED_STORAGE -> scanAllStorage()
                    StorageScanSource.SELECTED_FOLDER, null ->
                        storageState.value.selectedTreeUri?.let(::scanStorage)
                }
            },
            onOpenExplorer = { storageExplorerUri.value = storageState.value.selectedTreeUri },
            onCloseExplorer = { storageExplorerUri.value = null },
            onOpenStorageFile = { uri ->
                runCatching {
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = uri
                        type = context.contentResolver.getType(uri) ?: "*/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(viewIntent)
                }.onFailure { showActionFailure("Δεν ήταν δυνατό το άνοιγμα του αρχείου από το Android.") }
            },
            batteryHistoryState = batteryHistoryState.value,
            networkHistoryState = networkHistoryState.value,
            actionLogState = actionLogState.value,
            appCatalogState = appCatalogState.value,
            selectedSection = selectedSection.value,
            onSectionSelected = { selectedSection.value = it },
            periodicSnapshotsEnabled = periodicSnapshotsEnabled.value,
            onTogglePeriodicSnapshots = {
                LocalAutomationManager.setEnabled(context, it)
                periodicSnapshotsEnabled.value = it
                executor.execute {
                    runCatching {
                        historyRepository.recordAction(
                            action = "automation_toggle",
                            result = "success",
                            details = if (it) "Ενεργοποιήθηκε κάθε 12 ώρες" else "Απενεργοποιήθηκε",
                        )
                        mainHandler.post { if (active.get()) loadHistory() }
                    }
                }
            },
            onOpenUsageSettings = {
                runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    .onFailure { showActionFailure("Δεν ήταν δυνατό το άνοιγμα των ρυθμίσεων χρήσης.") }
            },
            onExportReport = {
                startPlainExport()
            },
            onExportEncryptedReport = {
                startEncryptedExport()
            },
            onClearHistory = ::clearHistory,
            onReloadApps = ::loadApps,
            onOpenAppSettings = { app ->
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
            },
            onLaunchApp = { app ->
                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?: error("Δεν βρέθηκε δραστηριότητα εκκίνησης.")
                context.startActivity(launchIntent)
            },
            onUninstallApp = { app ->
                context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
            },
        )

        currentState.isRefreshing -> LoadingScreen()
        else -> ErrorScreen(
            message = currentState.errorMessage ?: "Δεν υπάρχει διαθέσιμο στιγμιότυπο.",
            onRetry = ::refresh,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "DEVICE CONTROL CENTER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Χτίζουμε την εικόνα της συσκευής…",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Δεν πήραμε στιγμιότυπο",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(text = message, style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(text = "Δοκιμή ξανά")
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityScreen(
    snapshot: DeviceSnapshot,
    state: SnapshotUiState<DeviceSnapshot>,
    onRefresh: () -> Unit,
    historyState: HistoryUiState,
    storageState: StorageScanState,
    storageExplorerUri: Uri?,
    hasAllFilesAccess: Boolean,
    onChooseStorageFolder: () -> Unit,
    onEnableAllFilesAccess: () -> Unit,
    onScanAllStorage: () -> Unit,
    onHashDuplicates: () -> Unit,
    onMoveToTrash: (StorageFileEntry) -> Unit,
    onRestoreTrash: (StorageTrashItem) -> Unit,
    onRescanStorage: () -> Unit,
    onOpenExplorer: () -> Unit,
    onCloseExplorer: () -> Unit,
    onOpenStorageFile: (Uri) -> Unit,
    batteryHistoryState: BatteryHistoryUiState,
    networkHistoryState: NetworkHistoryUiState,
    actionLogState: ActionLogUiState,
    appCatalogState: AppCatalogResult,
    selectedSection: DccSection,
    onSectionSelected: (DccSection) -> Unit,
    periodicSnapshotsEnabled: Boolean,
    onTogglePeriodicSnapshots: (Boolean) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onExportReport: () -> Unit,
    onExportEncryptedReport: () -> Unit,
    onClearHistory: () -> Unit,
    onReloadApps: () -> Unit,
    onOpenAppSettings: (AppRecord) -> Unit,
    onLaunchApp: (AppRecord) -> Unit,
    onUninstallApp: (AppRecord) -> Unit,
) {
    val diagnosis = remember(snapshot) { DeviceDiagnosisEngine.analyze(snapshot) }
    val overviewStatus = OverviewPresentation.status(diagnosis)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            DccNavigationBar(selected = selectedSection, onSelected = onSectionSelected)
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (selectedSection) {
                DccSection.OVERVIEW -> OverviewContent(
                    snapshot = snapshot,
                    state = state,
                    historyState = historyState,
                    storageState = storageState,
                    hasAllFilesAccess = hasAllFilesAccess,
                    diagnosis = diagnosis,
                    overviewStatus = overviewStatus,
                    onRefresh = onRefresh,
                    onChooseStorageFolder = onChooseStorageFolder,
                    onEnableAllFilesAccess = onEnableAllFilesAccess,
                    onScanAllStorage = onScanAllStorage,
                    onHashDuplicates = onHashDuplicates,
                    onMoveToTrash = onMoveToTrash,
                    onRestoreTrash = onRestoreTrash,
                    onRescanStorage = onRescanStorage,
                )

                DccSection.APPS -> AppCenterScreen(
                    context = LocalContext.current,
                    catalog = appCatalogState,
                    onReload = onReloadApps,
                    onOpenSettings = onOpenAppSettings,
                    onLaunch = onLaunchApp,
                    onUninstall = onUninstallApp,
                )

                DccSection.STORAGE -> StorageCenterScreen(
                    snapshot = snapshot,
                    storageState = storageState,
                    explorerUri = storageExplorerUri,
                    hasAllFilesAccess = hasAllFilesAccess,
                    onChooseStorageFolder = onChooseStorageFolder,
                    onEnableAllFilesAccess = onEnableAllFilesAccess,
                    onScanAllStorage = onScanAllStorage,
                    onHashDuplicates = onHashDuplicates,
                    onMoveToTrash = onMoveToTrash,
                    onRestoreTrash = onRestoreTrash,
                    onRescanStorage = onRescanStorage,
                    onOpenExplorer = onOpenExplorer,
                    onCloseExplorer = onCloseExplorer,
                    onOpenFile = onOpenStorageFile,
                )

                DccSection.SIGNALS -> SignalsScreen(
                    snapshot = snapshot,
                    history = historyState,
                    batteryHistory = batteryHistoryState,
                    networkHistory = networkHistoryState,
                    onRefresh = onRefresh,
                )

                DccSection.PRIVACY -> PrivacyCenterScreen(
                    hasUsageAccess = snapshot.hasUsageAccess,
                    hasAllFilesAccess = hasAllFilesAccess,
                    periodicSnapshotsEnabled = periodicSnapshotsEnabled,
                    onOpenUsageSettings = onOpenUsageSettings,
                    onOpenAllFilesSettings = onEnableAllFilesAccess,
                    onTogglePeriodicSnapshots = onTogglePeriodicSnapshots,
                    onExportReport = onExportReport,
                    onExportEncryptedReport = onExportEncryptedReport,
                    actionLog = actionLogState,
                    onClearHistory = onClearHistory,
                )
            }
        }
    }
}

@Composable
private fun OverviewContent(
    snapshot: DeviceSnapshot,
    state: SnapshotUiState<DeviceSnapshot>,
    historyState: HistoryUiState,
    storageState: StorageScanState,
    hasAllFilesAccess: Boolean,
    diagnosis: DiagnosisReport,
    overviewStatus: OverviewStatus,
    onRefresh: () -> Unit,
    onChooseStorageFolder: () -> Unit,
    onEnableAllFilesAccess: () -> Unit,
    onScanAllStorage: () -> Unit,
    onHashDuplicates: () -> Unit,
    onMoveToTrash: (StorageFileEntry) -> Unit,
    onRestoreTrash: (StorageTrashItem) -> Unit,
    onRescanStorage: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { OverviewHeader() }
        item { OverviewHero(status = overviewStatus, report = diagnosis) }
        item { SnapshotControls(state = state, onRefresh = onRefresh) }
        item { HistoryCard(state = historyState) }
        item { SectionHeading(title = "Γρήγορη εικόνα", subtitle = "Τα βασικά σήματα από το τελευταίο στιγμιότυπο") }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(Modifier.weight(1f), "RAM", SnapshotPresentation.gib(snapshot.availableMemoryBytes), "διαθέσιμη", if (snapshot.isLowMemory) OverviewTone.WARNING else OverviewTone.NEUTRAL)
                MetricTile(Modifier.weight(1f), "Μπαταρία", BatteryPresentation.levelLabel(snapshot.battery.levelPercent), OverviewPresentation.batterySupport(snapshot.battery), OverviewTone.INFO)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(Modifier.weight(1f), "Θερμική κατάσταση", OverviewPresentation.thermalShortLabel(snapshot.thermalStatus), OverviewPresentation.thermalSupport(snapshot.thermalHeadroom), OverviewPresentation.thermalTone(snapshot.thermalStatus))
                MetricTile(Modifier.weight(1f), "CPU", OverviewPresentation.cpuValue(snapshot.cpu), OverviewPresentation.cpuSupport(snapshot.cpu), if (snapshot.cpu.activityPercent == null) OverviewTone.UNAVAILABLE else OverviewTone.INFO)
            }
        }
        item { StorageCard(snapshot = snapshot) }
        item {
            StorageIntelligenceCard(
                state = storageState,
                hasAllFilesAccess = hasAllFilesAccess,
                onChooseFolder = onChooseStorageFolder,
                onEnableAllFilesAccess = onEnableAllFilesAccess,
                onScanAllStorage = onScanAllStorage,
                onHashDuplicates = onHashDuplicates,
                onMoveToTrash = onMoveToTrash,
                onRestoreTrash = onRestoreTrash,
                onRescan = onRescanStorage,
            )
        }
        item { DiagnosisCard(report = diagnosis) }
        item { BatteryCard(snapshot = snapshot.battery) }
        item { MemoryDetailsCard(snapshot = snapshot) }
        item { AccessCard(snapshot = snapshot, hasAllFilesAccess = hasAllFilesAccess) }
        item {
            Text("Τα δεδομένα είναι στιγμιότυπο της συσκευής. Δεν εκτελείται καμία ενέργεια αυτόματα.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun StorageCenterScreen(
    snapshot: DeviceSnapshot,
    storageState: StorageScanState,
    explorerUri: Uri?,
    hasAllFilesAccess: Boolean,
    onChooseStorageFolder: () -> Unit,
    onEnableAllFilesAccess: () -> Unit,
    onScanAllStorage: () -> Unit,
    onHashDuplicates: () -> Unit,
    onMoveToTrash: (StorageFileEntry) -> Unit,
    onRestoreTrash: (StorageTrashItem) -> Unit,
    onRescanStorage: () -> Unit,
    onOpenExplorer: () -> Unit,
    onCloseExplorer: () -> Unit,
    onOpenFile: (Uri) -> Unit,
) {
    if (explorerUri != null) {
        StorageExplorerScreen(
            context = LocalContext.current,
            rootUri = explorerUri,
            onExit = onCloseExplorer,
            onOpenFile = onOpenFile,
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Κέντρο αποθήκευσης", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("Ανακάλυψη μετρήσεων, κατηγοριών και διπλοτύπων με προεπισκόπηση πριν από κάθε ενέργεια.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { StorageCard(snapshot) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Εξερεύνηση αρχείων", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (storageState.selectedTreeUri != null) "Περιήγηση στον φάκελο που επέλεξες, με άνοιγμα αρχείων μέσω Android." else "Επίλεξε πρώτα έναν φάκελο από τη σάρωση για να ανοίξεις τον εξερευνητή.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenExplorer, enabled = storageState.selectedTreeUri != null, modifier = Modifier.fillMaxWidth()) { Text("Άνοιγμα εξερευνητή") }
                }
            }
        }
        item {
            StorageIntelligenceCard(
                state = storageState,
                hasAllFilesAccess = hasAllFilesAccess,
                onChooseFolder = onChooseStorageFolder,
                onEnableAllFilesAccess = onEnableAllFilesAccess,
                onScanAllStorage = onScanAllStorage,
                onHashDuplicates = onHashDuplicates,
                onMoveToTrash = onMoveToTrash,
                onRestoreTrash = onRestoreTrash,
                onRescan = onRescanStorage,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ασφαλής λογική ενεργειών", style = MaterialTheme.typography.titleMedium)
                    Text("Καμία μαζική διαγραφή, μετακίνηση ή κρυφή εκκαθάριση. Τα ακριβή διπλότυπα επιβεβαιώνονται μόνο με SHA-256 όταν είναι εφικτό.", style = MaterialTheme.typography.bodyMedium)
                    Text("Τα παρόμοια οπτικά αρχεία και τα υπολείμματα εφαρμογών απαιτούν ξεχωριστό, προαιρετικό ευρετήριο και δεν παρουσιάζονται ως βέβαια από απλές ομοιότητες.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DccNavigationBar(selected: DccSection, onSelected: (DccSection) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        listOf(
            DccSection.OVERVIEW to "Αρχική",
            DccSection.APPS to "Εφαρμογές",
            DccSection.STORAGE to "Χώρος",
            DccSection.SIGNALS to "Σήματα",
            DccSection.PRIVACY to "Απόρρητο",
        ).forEach { (section, label) ->
            NavigationBarItem(selected = selected == section, onClick = { onSelected(section) }, icon = { Text(label.take(1)) }, label = { Text(label) })
        }
    }
}

@Composable
private fun OverviewHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "DEVICE CONTROL CENTER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing,
                )
                Text(
                    text = "Η συσκευή σου, καθαρά.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            StatusBadge(text = "ΤΩΡΑ", tone = OverviewTone.INFO)
        }
        Text(
            text = "Πραγματικά σήματα Android σε μια γρήγορη, ειλικρινή εικόνα.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverviewHero(status: OverviewStatus, report: DiagnosisReport) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(heroBrush(status.tone))
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ΤΩΡΑ",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Bold,
                )
                StatusBadge(text = status.label, tone = status.tone)
            }
            Text(
                text = status.headline,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = status.detail,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.86f),
            )
            Text(
                text = "Βασίζεται σε ${report.evaluatedRuleIds.size} ελεγμένους κανόνες · όχι σε αυθαίρετο σκορ.",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun SnapshotControls(
    state: SnapshotUiState<DeviceSnapshot>,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Τελευταία λήψη",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = SnapshotPresentation.capturedTimeLabel(state.capturedAtMillis),
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Button(
                onClick = onRefresh,
                enabled = !state.isRefreshing,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f),
                ),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 10.dp),
            ) {
                Text(text = if (state.isRefreshing) "Συλλογή…" else "Ανανέωση")
            }
        }
    }
}

@Composable
private fun HistoryCard(state: HistoryUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, toneAccent(OverviewTone.INFO).copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = "Ιστορικό στιγμιότυπων", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = SnapshotHistoryPresentation.summary(state.entries),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(text = "Μόνο τοπικά", tone = OverviewTone.INFO)
            }

            if (state.isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = "Φόρτωση ιστορικού…", style = MaterialTheme.typography.bodySmall)
                }
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.entries.take(3).forEach { entry ->
                HistoryRow(entry = entry)
            }

            Text(
                text = "Δεν συγχρονίζεται και δεν αποστέλλεται εκτός συσκευής.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: SnapshotHistoryEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = SnapshotPresentation.capturedTimeLabel(entry.capturedAtMillis),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "RAM ${SnapshotHistoryPresentation.memoryLabel(entry)}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text = "Μπαταρία ${SnapshotHistoryPresentation.batteryLabel(entry)} · Θερμική κατάσταση ${SnapshotHistoryPresentation.thermalLabel(entry)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier,
    title: String,
    value: String,
    supporting: String,
    tone: OverviewTone,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = toneContainer(tone)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, toneAccent(tone).copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(toneAccent(tone)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageIntelligenceCard(
    state: StorageScanState,
    hasAllFilesAccess: Boolean,
    onChooseFolder: () -> Unit,
    onEnableAllFilesAccess: () -> Unit,
    onScanAllStorage: () -> Unit,
    onHashDuplicates: () -> Unit,
    onMoveToTrash: (StorageFileEntry) -> Unit,
    onRestoreTrash: (StorageTrashItem) -> Unit,
    onRescan: () -> Unit,
) {
    var expanded by remember(state.result) { mutableStateOf(false) }
    var pendingTrash by remember { mutableStateOf<StorageFileEntry?>(null) }
    var pendingRestore by remember { mutableStateOf<StorageTrashItem?>(null) }
    val result = state.result
    val canMutateSource = state.canWriteSource
    val badgeTone = when {
        state.isScanning || state.isHashing || state.isMutating -> OverviewTone.INFO
        state.source == StorageScanSource.SHARED_STORAGE -> OverviewTone.NEUTRAL
        result != null -> OverviewTone.INFO
        state.selectedTreeUri != null -> OverviewTone.NEUTRAL
        else -> OverviewTone.UNAVAILABLE
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = toneContainer(OverviewTone.INFO)),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, toneAccent(OverviewTone.INFO).copy(alpha = 0.30f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = "Έξυπνη εικόνα αποθήκευσης", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Πλήρης συλλογή με ρητή άδεια ή μόνο σε φάκελο που επιλέγεις εσύ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(
                    text = when {
                        state.isScanning || state.isHashing || state.isMutating -> "Εργασία…"
                        state.source == StorageScanSource.SHARED_STORAGE -> "Πλήρης πρόσβαση"
                        result != null -> "Ανάλυση μεταδεδομένων"
                        state.selectedTreeUri != null -> "Έχει επιλεγεί"
                        else -> "Περιορισμένη"
                    },
                    tone = badgeTone,
                )
            }

            if (result == null) {
                Text(
                    text = if (hasAllFilesAccess) {
                        "Η πλήρης πρόσβαση είναι ενεργή. Η σάρωση θα εξετάσει μόνο μεταδεδομένα στον κοινόχρηστο χώρο."
                    } else {
                        "Δεν υπάρχει ακόμη ανάλυση αρχείων. Χωρίς πλήρη πρόσβαση, η εφαρμογή βλέπει μόνο φάκελο που επιλέγεις ρητά."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = result.rootName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = StorageIntelligencePresentation.summary(result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Πηγή: " + if (state.source == StorageScanSource.SHARED_STORAGE) {
                        "κοινόχρηστοι χώροι"
                    } else {
                        "επιλεγμένος φάκελος"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = StorageIntelligencePresentation.knownSize(result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(text = if (expanded) "Απόκρυψη ευρημάτων" else "Προβολή ευρημάτων")
                }
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        Text(
                            text = "Μεγαλύτερα αρχεία",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (result.largestFiles.isEmpty()) {
                            Text(text = "Δεν βρέθηκαν αρχεία με διαθέσιμο μέγεθος.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            result.largestFiles.take(3).forEach { entry ->
                                StorageFileRow(entry = entry, onRequestTrash = if (canMutateSource) ({ pendingTrash = entry }) else null)
                            }
                        }

                        Text(
                            text = "Παλαιότερα αρχεία",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (result.oldestFiles.isEmpty()) {
                            Text(text = "Δεν βρέθηκε αξιόπιστη ημερομηνία τροποποίησης.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            result.oldestFiles.take(3).forEach { entry ->
                                StorageFileRow(entry = entry, showSize = false)
                            }
                        }

                        Text(
                            text = "Ομάδες ίδιου μεγέθους · όχι επιβεβαιωμένοι διπλότυποι",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (result.sameSizeCandidates.isEmpty()) {
                            Text(text = "Δεν βρέθηκαν ομάδες ίδιου μεγέθους.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            result.sameSizeCandidates.take(3).forEach { group ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = StorageIntelligencePresentation.sameSizeLabel(group),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = group.fileNames.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Κατηγορίες",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        result.categories.take(6).forEach { category ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(StorageIntelligencePresentation.categoryLabel(category.category), style = MaterialTheme.typography.bodySmall)
                                Text("${category.fileCount} · ${StorageIntelligencePresentation.storageSize(category.knownBytes)}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(
                            text = "Κενοί φάκελοι",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(StorageIntelligencePresentation.emptyDirectorySummary(result), style = MaterialTheme.typography.bodySmall)
                        result.emptyDirectories.forEach { directory ->
                            Text(
                                text = directory.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "Ακριβή διπλότυπα με SHA-256",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        state.exactDuplicateResult?.let { exact ->
                            Text(StorageIntelligencePresentation.duplicateSummary(exact), style = MaterialTheme.typography.bodySmall)
                            exact.groups.take(3).forEach { group ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "${StorageIntelligencePresentation.storageSize(group.sizeBytes)} · SHA-256 ${group.sha256.take(12)}…",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    group.entries.forEach { duplicateEntry ->
                                        StorageFileRow(
                                            entry = duplicateEntry,
                                            onRequestTrash = if (canMutateSource) ({ pendingTrash = duplicateEntry }) else null,
                                        )
                                    }
                                }
                            }
                        } ?: Text("Δεν έχει εκτελεστεί έλεγχος περιεχομένου.", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = StorageIntelligencePresentation.limitation(result),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = if (hasAllFilesAccess) onScanAllStorage else onEnableAllFilesAccess,
                enabled = !state.isScanning && !state.isHashing && !state.isMutating,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = when {
                        state.isScanning -> "Ανάλυση…"
                        hasAllFilesAccess -> "Σάρωση κοινόχρηστων χώρων"
                        else -> "Ενεργοποίηση πλήρους πρόσβασης"
                    },
                )
            }
            TextButton(
                onClick = onChooseFolder,
                enabled = !state.isScanning && !state.isHashing && !state.isMutating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Ή επίλεξε μόνο έναν φάκελο")
            }
            if (state.result != null && !state.isScanning && !state.isHashing && !state.isMutating) {
                TextButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Σάρωση ξανά")
                }
            }
            if (result != null) {
                TextButton(
                    onClick = onHashDuplicates,
                    enabled = !state.isScanning && !state.isHashing && !state.isMutating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isHashing) "Έλεγχος SHA-256…" else "Έλεγχος ακριβών διπλοτύπων")
                }
            }
            state.actionMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (state.trashItems.isNotEmpty()) {
                Text(
                    text = "Ιδιωτικός κάδος",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Τα αντίγραφα παραμένουν μόνο στη συσκευή μέχρι να τα επαναφέρεις. Η εφαρμογή δεν τα διαγράφει αυτόματα.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.trashItems.take(8).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.needsRecoveryReview) {
                                Text(
                                    text = "Απαιτείται έλεγχος προέλευσης πριν από την επαναφορά.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        TextButton(
                            onClick = { pendingRestore = item },
                            enabled = !state.isMutating,
                        ) { Text("Επαναφορά") }
                    }
                }
                if (state.trashItems.size > 8) {
                    Text(
                        text = "Εμφανίζονται τα 8 πιο πρόσφατα από ${state.trashItems.size} αντικείμενα.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (result != null && !canMutateSource) {
                Text(
                    text = "Δεν υπάρχει ενεργή άδεια εγγραφής για την πηγή της σάρωσης. Οι ενέργειες αρχείου παραμένουν απενεργοποιημένες μέχρι να δοθεί από το Android.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (hasAllFilesAccess) {
                    "Η πλήρης πρόσβαση ενεργοποιείται μόνο από τις ρυθμίσεις Android. Η ανάλυση είναι read-only· η ρητή ενέργεια αρχείου γίνεται μόνο μετά από προεπισκόπηση και επιβεβαίωση."
                } else {
                    "Η πλήρης πρόσβαση δεν ζητείται αυτόματα. Η επιλογή φακέλου παραμένει διαθέσιμη χωρίς ευρεία άδεια και χωρίς αυτόματη ενέργεια."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingTrash?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingTrash = null },
            title = { Text("Μετακίνηση στον ιδιωτικό κάδο;") },
            text = {
                Text("Θα δημιουργηθεί προσωρινό αντίγραφο στον ιδιωτικό κάδο και θα ζητηθεί διαγραφή του αρχικού αρχείου. Προχωράς για το ${entry.name};")
            },
            confirmButton = {
                Button(onClick = { pendingTrash = null; onMoveToTrash(entry) }) { Text("Μετακίνηση") }
            },
            dismissButton = { TextButton(onClick = { pendingTrash = null }) { Text("Ακύρωση") } },
        )
    }

    pendingRestore?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Επαναφορά από τον ιδιωτικό κάδο;") },
            text = {
                Text("Θα δημιουργηθεί ξανά το ${item.displayName} στην αρχική του θέση χωρίς αντικατάσταση υπάρχοντος αρχείου. Προχωράς;")
            },
            confirmButton = {
                Button(onClick = { pendingRestore = null; onRestoreTrash(item) }) { Text("Επαναφορά") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Ακύρωση") } },
        )
    }
}

@Composable
private fun StorageFileRow(
    entry: StorageFileEntry,
    showSize: Boolean = true,
    onRequestTrash: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = StorageIntelligencePresentation.modifiedAt(entry.lastModifiedMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showSize) {
            Text(
                text = StorageIntelligencePresentation.fileSize(entry),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
            )
        }
        onRequestTrash?.let {
            TextButton(onClick = it) { Text("Κάδος") }
        }
    }
}

@Composable
private fun StorageCard(snapshot: DeviceSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = toneContainer(OverviewTone.NEUTRAL)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, toneAccent(OverviewTone.NEUTRAL).copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Χώρος δεδομένων εφαρμογών",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${SnapshotPresentation.gib(snapshot.availableStorageBytes)} διαθέσιμα",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${SnapshotPresentation.gib(snapshot.totalStorageBytes)} στο προσβάσιμο διαμέρισμα · όχι η διαφημιζόμενη συνολική χωρητικότητα",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Δεν έχει εκτελεστεί ανάλυση αρχείων",
                style = MaterialTheme.typography.labelMedium,
                color = toneAccent(OverviewTone.INFO),
            )
        }
    }
}

@Composable
private fun DiagnosisCard(report: DiagnosisReport) {
    val status = OverviewPresentation.status(report)
    var expanded by remember(report) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, toneAccent(status.tone).copy(alpha = 0.34f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = "Πρώτη διάγνωση", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = OverviewPresentation.findingSummaryFor(report),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(text = status.label, tone = status.tone)
            }
            Text(
                text = DiagnosisPresentation.headline(report),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Ελέγχθηκαν ${report.evaluatedRuleIds.size} κανόνες. Τα τεχνικά τεκμήρια παραμένουν διαθέσιμα παρακάτω.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(text = if (expanded) "Απόκρυψη τεκμηρίων" else "Προβολή τεκμηρίων")
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    if (report.findings.isEmpty()) {
                        Text(
                            text = "Δεν υπάρχουν ευρήματα για παρουσίαση.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        report.findings.forEach { finding ->
                            FindingDetail(finding = finding)
                        }
                    }
                    Text(
                        text = "Δεν εκτελείται καμία ενέργεια αυτόματα.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FindingDetail(finding: DiagnosisFinding) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(text = DiagnosisPresentation.severityLabel(finding.severity), tone = finding.tone())
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = finding.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(text = finding.explanation, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Τεκμήριο",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = finding.evidence, style = MaterialTheme.typography.bodySmall)
        finding.limitation?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Κανόνας ${finding.ruleId} · έκδοση ${finding.ruleVersion}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BatteryCard(snapshot: BatterySnapshot) {
    var expanded by remember(snapshot) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, toneAccent(OverviewTone.INFO).copy(alpha = 0.30f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = "Μπαταρία", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = BatteryPresentation.levelLabel(snapshot.levelPercent),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                StatusBadge(text = BatteryPresentation.statusLabel(snapshot.status), tone = OverviewTone.INFO)
            }
            Text(
                text = OverviewPresentation.batterySupport(snapshot),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Η υγεία και η πραγματική χωρητικότητα δεν εκτιμώνται.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(text = if (expanded) "Απόκρυψη τεχνικών στοιχείων" else "Τεχνικά στοιχεία μπαταρίας")
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    DetailRow("Κατάσταση", BatteryPresentation.statusLabel(snapshot.status))
                    DetailRow("Πηγή", BatteryPresentation.pluggedLabel(snapshot.plugged))
                    DetailRow("Θερμοκρασία", BatteryPresentation.temperatureLabel(snapshot.temperatureCelsius))
                    DetailRow("Τάση", BatteryPresentation.voltageLabel(snapshot.voltageMillivolts, snapshot.voltageSource))
                    DetailRow("Στιγμιαίο ρεύμα", BatteryPresentation.currentLabel(snapshot.currentNowMicroamps))
                    DetailRow("Μέσο ρεύμα", BatteryPresentation.currentLabel(snapshot.currentAverageMicroamps))
                    DetailRow("Μετρητής φόρτισης", BatteryPresentation.chargeCounterLabel(snapshot.chargeCounterMicroampHours))
                    DetailRow("Μετρητής ενέργειας", BatteryPresentation.energyCounterLabel(snapshot.energyCounterNanowattHours))
                }
            }
        }
    }
}

@Composable
private fun MemoryDetailsCard(snapshot: DeviceSnapshot) {
    var expanded by remember(snapshot) { mutableStateOf(false) }
    val tone = if (snapshot.isLowMemory) OverviewTone.WARNING else OverviewTone.NEUTRAL

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = toneContainer(tone)),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, toneAccent(tone).copy(alpha = 0.30f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = "Μνήμη RAM", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${SnapshotPresentation.gib(snapshot.availableMemoryBytes)} διαθέσιμα",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                StatusBadge(
                    text = if (snapshot.isLowMemory) "Πίεση" else "Σταθερή ένδειξη",
                    tone = tone,
                )
            }
            Text(
                text = if (snapshot.isLowMemory) {
                    "Το Android αναφέρει χαμηλή μνήμη τώρα."
                } else {
                    "Το Android δεν αναφέρει χαμηλή μνήμη τώρα."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(text = if (expanded) "Απόκρυψη στοιχείων RAM" else "Στοιχεία μνήμης")
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    DetailRow(
                        "Διαφημιζόμενη φυσική RAM",
                        "${SnapshotPresentation.decimalGb(snapshot.advertisedMemoryBytes)} (${SnapshotPresentation.gib(snapshot.advertisedMemoryBytes)})",
                    )
                    DetailRow("Προσβάσιμη στον πυρήνα", SnapshotPresentation.gib(snapshot.totalMemoryBytes))
                    DetailRow("Όριο χαμηλής μνήμης", SnapshotPresentation.gib(snapshot.lowMemoryThresholdBytes))
                }
            }
        }
    }
}

@Composable
private fun AccessCard(snapshot: DeviceSnapshot, hasAllFilesAccess: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, toneAccent(OverviewTone.UNAVAILABLE).copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Ειδικές προσβάσεις", style = MaterialTheme.typography.titleMedium)
            AccessRow("Στατιστικά χρήσης", snapshot.hasUsageAccess)
            AccessRow("Πρόσβαση όλων των αρχείων", hasAllFilesAccess)
            Text(
                text = "Η εφαρμογή δεν ζητά πρόσβαση αυτόματα.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccessRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        StatusBadge(
            text = SnapshotPresentation.accessLabel(granted),
            tone = if (granted) OverviewTone.NEUTRAL else OverviewTone.UNAVAILABLE,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StatusBadge(text: String, tone: OverviewTone) {
    Surface(
        modifier = Modifier.widthIn(max = 150.dp),
        shape = RoundedCornerShape(50),
        color = toneAccent(tone).copy(alpha = 0.16f),
        contentColor = toneAccent(tone),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun DiagnosisFinding.tone(): OverviewTone = when (severity) {
    DiagnosisSeverity.INFO -> OverviewTone.INFO
    DiagnosisSeverity.WARNING -> OverviewTone.WARNING
    DiagnosisSeverity.CRITICAL -> OverviewTone.CRITICAL
}

private fun toneAccent(tone: OverviewTone): Color = when (tone) {
    OverviewTone.NEUTRAL -> NeutralAccent
    OverviewTone.INFO -> InfoAccent
    OverviewTone.WARNING -> WarningAccent
    OverviewTone.CRITICAL -> CriticalAccent
    OverviewTone.UNAVAILABLE -> UnavailableAccent
}

private fun toneContainer(tone: OverviewTone): Color = when (tone) {
    OverviewTone.NEUTRAL -> Color(0xFF142238)
    OverviewTone.INFO -> Color(0xFF172B42)
    OverviewTone.WARNING -> Color(0xFF382A1C)
    OverviewTone.CRITICAL -> Color(0xFF41202C)
    OverviewTone.UNAVAILABLE -> Color(0xFF222B3B)
}

private fun heroBrush(tone: OverviewTone): Brush = when (tone) {
    OverviewTone.NEUTRAL -> Brush.linearGradient(listOf(Color(0xFF173B5B), Color(0xFF1A2754)))
    OverviewTone.INFO -> Brush.linearGradient(listOf(Color(0xFF145466), Color(0xFF272552)))
    OverviewTone.WARNING -> Brush.linearGradient(listOf(Color(0xFF704022), Color(0xFF3E2351)))
    OverviewTone.CRITICAL -> Brush.linearGradient(listOf(Color(0xFF762B3C), Color(0xFF3D204F)))
    OverviewTone.UNAVAILABLE -> Brush.linearGradient(listOf(Color(0xFF354157), Color(0xFF252D45)))
}
