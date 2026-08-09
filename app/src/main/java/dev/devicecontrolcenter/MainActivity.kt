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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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

private val NeutralAccent = Color(0xFF74E5F0)
private val InfoAccent = Color(0xFF9DBBFF)
private val WarningAccent = Color(0xFFFFC66D)
private val CriticalAccent = Color(0xFFFF929F)
private val UnavailableAccent = Color(0xFFAEB8C9)

class MainActivity : ComponentActivity() {
    private val allFilesAccessState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        allFilesAccessState.value = Environment.isExternalStorageManager()
        setContent {
            PremiumTheme {
                CapabilityRoute(
                    context = this@MainActivity,
                    hasAllFilesAccess = allFilesAccessState.value,
                    onEnableAllFilesAccess = ::openAllFilesAccessSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        allFilesAccessState.value = Environment.isExternalStorageManager()
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
    MaterialTheme(
        colorScheme = DccColorScheme,
        content = content,
    )
}

@Composable
private fun CapabilityRoute(
    context: Context,
    hasAllFilesAccess: Boolean,
    onEnableAllFilesAccess: () -> Unit,
) {
    val state = remember { mutableStateOf(SnapshotUiState<DeviceSnapshot>()) }
    val historyState = remember { mutableStateOf(HistoryUiState()) }
    val storageState = remember {
        mutableStateOf(StorageScanState(selectedTreeUri = StorageSelectionStore.read(context)))
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val active = remember { AtomicBoolean(true) }
    val refreshGate = remember { SnapshotRefreshGate() }
    val storageScanInFlight = remember { AtomicBoolean(false) }
    val historyRepository = remember(context) { SnapshotHistoryRepository(context) }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            refreshGate.cancel()
            storageScanInFlight.set(false)
            executor.shutdownNow()
        }
    }

    fun loadHistory() {
        historyState.value = historyState.value.copy(isLoading = true, errorMessage = null)
        runCatching {
            executor.execute {
                val result = runCatching { historyRepository.recent() }
                mainHandler.post {
                    if (!active.get()) return@post
                    historyState.value = result.fold(
                        onSuccess = { entries -> HistoryUiState(entries = entries) },
                        onFailure = {
                            HistoryUiState(errorMessage = "Το τοπικό ιστορικό δεν είναι διαθέσιμο τώρα.")
                        },
                    )
                }
            }
        }.onFailure {
            historyState.value = HistoryUiState(errorMessage = "Το τοπικό ιστορικό δεν είναι διαθέσιμο τώρα.")
        }
    }

    fun persistSnapshot(snapshot: DeviceSnapshot, capturedAtMillis: Long) {
        runCatching {
            executor.execute {
                val result = runCatching {
                    historyRepository.record(snapshot, capturedAtMillis)
                    historyRepository.recent()
                }
                mainHandler.post {
                    if (!active.get()) return@post
                    historyState.value = result.fold(
                        onSuccess = { entries -> HistoryUiState(entries = entries) },
                        onFailure = {
                            HistoryUiState(errorMessage = "Το στιγμιότυπο εμφανίστηκε, αλλά δεν αποθηκεύτηκε στο ιστορικό.")
                        },
                    )
                }
            }
        }.onFailure {
            historyState.value = HistoryUiState(errorMessage = "Το στιγμιότυπο εμφανίστηκε, αλλά δεν αποθηκεύτηκε στο ιστορικό.")
        }
    }

    fun scanStorage(uri: android.net.Uri) {
        if (!storageScanInFlight.compareAndSet(false, true)) return

        storageState.value = storageState.value.copy(
            selectedTreeUri = uri,
            source = StorageScanSource.SELECTED_FOLDER,
            isScanning = true,
            errorMessage = null,
        )
        runCatching {
            executor.execute {
                val result = runCatching { StorageScanner.scan(context, uri) }
                mainHandler.post {
                    storageScanInFlight.set(false)
                    if (!active.get()) return@post
                    storageState.value = result.fold(
                        onSuccess = { scan ->
                            StorageScanState(
                                selectedTreeUri = uri,
                                result = scan,
                                source = StorageScanSource.SELECTED_FOLDER,
                            )
                        },
                        onFailure = { error ->
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
            isScanning = true,
            errorMessage = null,
        )
        runCatching {
            executor.execute {
                val result = runCatching { SharedStorageScanner.scan(context) }
                mainHandler.post {
                    storageScanInFlight.set(false)
                    if (!active.get()) return@post
                    storageState.value = result.fold(
                        onSuccess = { scan ->
                            StorageScanState(
                                selectedTreeUri = selectedTreeUri,
                                result = scan,
                                source = StorageScanSource.SHARED_STORAGE,
                            )
                        },
                        onFailure = { error ->
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

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            StorageSelectionStore.save(context, uri)
            scanStorage(uri)
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
        loadHistory()
        refresh()
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
            hasAllFilesAccess = hasAllFilesAccess,
            onChooseStorageFolder = { folderPicker.launch(null) },
            onEnableAllFilesAccess = onEnableAllFilesAccess,
            onScanAllStorage = ::scanAllStorage,
            onRescanStorage = {
                when (storageState.value.source) {
                    StorageScanSource.SHARED_STORAGE -> scanAllStorage()
                    StorageScanSource.SELECTED_FOLDER, null ->
                        storageState.value.selectedTreeUri?.let(::scanStorage)
                }
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
    hasAllFilesAccess: Boolean,
    onChooseStorageFolder: () -> Unit,
    onEnableAllFilesAccess: () -> Unit,
    onScanAllStorage: () -> Unit,
    onRescanStorage: () -> Unit,
) {
    val diagnosis = remember(snapshot) { DeviceDiagnosisEngine.analyze(snapshot) }
    val overviewStatus = OverviewPresentation.status(diagnosis)
    val personalBaseline = remember(snapshot, historyState.entries, state.capturedAtMillis) {
        PersonalBaseline.evaluate(
            current = snapshot,
            currentCapturedAtMillis = state.capturedAtMillis,
            history = historyState.entries,
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OverviewHeader()
            }
            item {
                OverviewHero(status = overviewStatus, report = diagnosis)
            }
            item {
                SnapshotControls(state = state, onRefresh = onRefresh)
            }
            item {
                HistoryCard(state = historyState)
            }
            item {
                PersonalBaselineCard(
                    result = personalBaseline,
                    historyState = historyState,
                )
            }
            item {
                SectionHeading(
                    title = "Γρήγορη εικόνα",
                    subtitle = "Τα βασικά σήματα από το τελευταίο στιγμιότυπο",
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        title = "RAM",
                        value = SnapshotPresentation.gib(snapshot.availableMemoryBytes),
                        supporting = "διαθέσιμη",
                        tone = if (snapshot.isLowMemory) OverviewTone.WARNING else OverviewTone.NEUTRAL,
                    )
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        title = "Μπαταρία",
                        value = BatteryPresentation.levelLabel(snapshot.battery.levelPercent),
                        supporting = OverviewPresentation.batterySupport(snapshot.battery),
                        tone = OverviewTone.INFO,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        title = "Θερμική κατάσταση",
                        value = OverviewPresentation.thermalShortLabel(snapshot.thermalStatus),
                        supporting = OverviewPresentation.thermalSupport(snapshot.thermalHeadroom),
                        tone = OverviewPresentation.thermalTone(snapshot.thermalStatus),
                    )
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        title = "CPU",
                        value = OverviewPresentation.cpuValue(snapshot.cpu),
                        supporting = OverviewPresentation.cpuSupport(snapshot.cpu),
                        tone = if (snapshot.cpu.activityPercent == null) {
                            OverviewTone.UNAVAILABLE
                        } else {
                            OverviewTone.INFO
                        },
                    )
                }
            }
            item {
                StorageCard(snapshot = snapshot)
            }
            item {
                StorageIntelligenceCard(
                    state = storageState,
                    hasAllFilesAccess = hasAllFilesAccess,
                    onChooseFolder = onChooseStorageFolder,
                    onEnableAllFilesAccess = onEnableAllFilesAccess,
                    onScanAllStorage = onScanAllStorage,
                    onRescan = onRescanStorage,
                )
            }
            item {
                DiagnosisCard(report = diagnosis)
            }
            item {
                BatteryCard(snapshot = snapshot.battery)
            }
            item {
                MemoryDetailsCard(snapshot = snapshot)
            }
            item {
                AccessCard(snapshot = snapshot, hasAllFilesAccess = hasAllFilesAccess)
            }
            item {
                Text(
                    text = "Τα δεδομένα είναι στιγμιότυπο της συσκευής. Δεν εκτελείται καμία ενέργεια αυτόματα.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
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
            StatusBadge(text = "LIVE", tone = OverviewTone.INFO)
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
private fun PersonalBaselineCard(
    result: PersonalBaselineResult,
    historyState: HistoryUiState,
) {
    val tone = when {
        historyState.errorMessage != null -> OverviewTone.UNAVAILABLE
        result.state == PersonalBaselineState.INSUFFICIENT_DATA -> OverviewTone.UNAVAILABLE
        else -> OverviewTone.INFO
    }
    val badge = when {
        historyState.errorMessage != null -> "Μη διαθέσιμο"
        else -> PersonalBaselinePresentation.statusLabel(result.state)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = toneContainer(tone)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, toneAccent(tone).copy(alpha = 0.28f)),
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
                    Text(text = "Προσωπική αναφορά", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Σύγκριση με προηγούμενες λήψεις της ίδιας συσκευής",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(text = badge, tone = tone)
            }

            when {
                historyState.errorMessage != null -> {
                    Text(
                        text = historyState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                historyState.isLoading && historyState.entries.isEmpty() -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(text = "Φόρτωση προηγούμενων λήψεων…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                result.state == PersonalBaselineState.INSUFFICIENT_DATA -> {
                    Text(
                        text = PersonalBaselinePresentation.summary(result),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Η σύγκριση ενεργοποιείται μόνο με επιτυχείς προηγούμενες λήψεις. Η τρέχουσα λήψη δεν μετράει ως προηγούμενο δείγμα.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Text(
                        text = PersonalBaselinePresentation.summary(result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    result.availableMemory?.let { metric ->
                        PersonalBaselineMetricRow(
                            title = "Διαθέσιμη RAM",
                            metric = metric,
                            evidence = PersonalBaselinePresentation.memoryEvidence(metric),
                        )
                    }
                    result.availableStorage?.let { metric ->
                        PersonalBaselineMetricRow(
                            title = "Διαθέσιμος χώρος δεδομένων",
                            metric = metric,
                            evidence = PersonalBaselinePresentation.storageEvidence(metric),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Σήματα πλαισίου",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = PersonalBaselinePresentation.lowMemoryEvidence(result),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = PersonalBaselinePresentation.thermalEvidence(result),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text(
                text = PersonalBaselinePresentation.limitation(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PersonalBaselineMetricRow(
    title: String,
    metric: PersonalBaselineNumericMetric,
    evidence: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = PersonalBaselinePresentation.relationLabel(metric.relation),
                style = MaterialTheme.typography.labelSmall,
                color = toneAccent(OverviewTone.INFO),
            )
        }
        Text(text = evidence, style = MaterialTheme.typography.bodySmall)
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
    onRescan: () -> Unit,
) {
    var expanded by remember(state.result) { mutableStateOf(false) }
    val result = state.result
    val badgeTone = when {
        state.isScanning -> OverviewTone.INFO
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
                        state.isScanning -> "Ανάλυση…"
                        state.source == StorageScanSource.SHARED_STORAGE -> "Πλήρης πρόσβαση"
                        result != null -> "Read-only"
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
                                StorageFileRow(entry = entry)
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
                enabled = !state.isScanning,
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
                enabled = !state.isScanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Ή επίλεξε μόνο έναν φάκελο")
            }
            if (state.result != null && !state.isScanning) {
                TextButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Σάρωση ξανά")
                }
            }
            Text(
                text = if (hasAllFilesAccess) {
                    "Η πλήρης πρόσβαση ενεργοποιείται μόνο από τις ρυθμίσεις Android. Η εφαρμογή διαβάζει μόνο μεταδεδομένα και δεν διαγράφει ή μετακινεί αρχεία."
                } else {
                    "Η πλήρης πρόσβαση δεν ζητείται αυτόματα. Η επιλογή φακέλου παραμένει διαθέσιμη χωρίς ευρεία άδεια."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageFileRow(entry: StorageFileEntry, showSize: Boolean = true) {
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
