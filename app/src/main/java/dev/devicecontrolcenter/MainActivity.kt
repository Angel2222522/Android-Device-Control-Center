package dev.devicecontrolcenter

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                CapabilityRoute(context = this@MainActivity)
            }
        }
    }
}

@Composable
private fun CapabilityRoute(context: Context) {
    val state = remember { mutableStateOf(SnapshotUiState<DeviceSnapshot>()) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val active = remember { AtomicBoolean(true) }
    val refreshGate = remember { SnapshotRefreshGate() }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            refreshGate.cancel()
            executor.shutdownNow()
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
                        onSuccess = { snapshot -> state.value.success(snapshot, capturedAtMillis) },
                        onFailure = {
                            state.value.failure("Η συλλογή του στιγμιοτύπου απέτυχε. Δοκίμασε ξανά.")
                        },
                    )
                }
            }
        }

        submitResult.onFailure {
            refreshGate.complete(SystemClock.elapsedRealtime())
            state.value = state.value.failure("Η συλλογή του στιγμιοτύπου απέτυχε. Δοκίμασε ξανά.")
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val currentState = state.value
    val snapshot = currentState.snapshot
    when {
        snapshot != null -> CapabilityScreen(
            snapshot = snapshot,
            state = currentState,
            onRefresh = ::refresh,
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
    Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Κατάσταση συσκευής", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Συλλογή στιγμιότυπου συσκευής…",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Κατάσταση συσκευής", style = MaterialTheme.typography.headlineLarge)
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry) {
                Text(text = "Δοκιμή ξανά")
            }
        }
    }
}

@Composable
private fun CapabilityScreen(
    snapshot: DeviceSnapshot,
    state: SnapshotUiState<DeviceSnapshot>,
    onRefresh: () -> Unit,
) {
    val diagnosis = remember(snapshot) { DeviceDiagnosisEngine.analyze(snapshot) }

    Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Κατάσταση συσκευής",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Πραγματικό στιγμιότυπο από τα δημόσια API του Android. Η πρώτη διάγνωση αξιολογεί μόνο τα σήματα που αναφέρονται παρακάτω.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SnapshotControls(state = state, onRefresh = onRefresh)
            DiagnosisCard(report = diagnosis)
            MetricCard(
                title = "Μνήμη RAM",
                primary = "${SnapshotPresentation.gib(snapshot.availableMemoryBytes)} διαθέσιμα",
                detail = SnapshotPresentation.memoryDetail(
                    advertisedBytes = snapshot.advertisedMemoryBytes,
                    kernelBytes = snapshot.totalMemoryBytes,
                    thresholdBytes = snapshot.lowMemoryThresholdBytes,
                ),
                status = if (snapshot.isLowMemory) "Υπάρχει πίεση μνήμης" else "Δεν αναφέρεται χαμηλή μνήμη",
            )
            MetricCard(
                title = "Θερμική κατάσταση",
                primary = SnapshotPresentation.thermalLabel(snapshot.thermalStatus),
                detail = snapshot.thermalHeadroom?.let(SnapshotPresentation::thermalEnvelopeLabel)
                    ?: "Η συσκευή δεν επέστρεψε μέτρηση θερμικού ορίου τώρα",
                status = "Δεν γίνεται ακόμη απόδοση αιτίας σε εφαρμογή",
            )
            MetricCard(
                title = "Δραστηριότητα CPU",
                primary = CpuPresentation.activityLabel(snapshot.cpu.activityPercent),
                detail = CpuPresentation.detail(snapshot.cpu),
                status = CpuPresentation.statusLabel(snapshot.cpu),
            )
            MetricCard(
                title = "Μπαταρία",
                primary = BatteryPresentation.levelLabel(snapshot.battery.levelPercent),
                detail = BatteryPresentation.technicalDetail(snapshot.battery),
                status = "Δεν εκτιμάται ακόμη η υγεία ή η πραγματική χωρητικότητα",
            )
            MetricCard(
                title = "Χώρος δεδομένων εφαρμογών",
                primary = "${SnapshotPresentation.gib(snapshot.availableStorageBytes)} διαθέσιμα",
                detail = "Προσβάσιμο διαμέρισμα ${SnapshotPresentation.gib(snapshot.totalStorageBytes)} · Δεν είναι η διαφημιζόμενη συνολική χωρητικότητα",
                status = "Δεν έχει εκτελεστεί ανάλυση αρχείων",
            )
            MetricCard(
                title = "Ειδικές προσβάσεις",
                primary = "Στατιστικά χρήσης: ${SnapshotPresentation.accessLabel(snapshot.hasUsageAccess)}",
                detail = "Πρόσβαση όλων των αρχείων: ${SnapshotPresentation.accessLabel(snapshot.hasAllFilesAccess)}",
                status = "Η εφαρμογή δεν ζητά πρόσβαση αυτόματα",
            )
        }
    }
}

@Composable
private fun SnapshotControls(
    state: SnapshotUiState<DeviceSnapshot>,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = SnapshotPresentation.capturedAtLabel(state.capturedAtMillis),
                style = MaterialTheme.typography.bodyMedium,
            )
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8A1C1C),
                )
            }
        }
        Button(
            onClick = onRefresh,
            enabled = !state.isRefreshing,
        ) {
            Text(text = if (state.isRefreshing) "Ανανέωση…" else "Ανανέωση")
        }
    }
}

@Composable
private fun DiagnosisCard(report: DiagnosisReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1EDF4), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Πρώτη διάγνωση", style = MaterialTheme.typography.titleMedium)
        Text(
            text = DiagnosisPresentation.headline(report),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = DiagnosisPresentation.evaluatedLabel(report),
            style = MaterialTheme.typography.bodyMedium,
        )
        report.findings.forEach { finding ->
            Text(
                text = DiagnosisPresentation.severityLabel(finding.severity),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF5B5361),
            )
            Text(text = finding.title, style = MaterialTheme.typography.titleMedium)
            Text(text = finding.explanation, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Τεκμήριο: ${finding.evidence}",
                style = MaterialTheme.typography.bodySmall,
            )
            finding.limitation?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5B5361),
                )
            }
            Text(
                text = "Κανόνας ${finding.ruleId} · έκδοση ${finding.ruleVersion}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5B5361),
            )
        }
        Text(
            text = "Δεν εκτελείται καμία ενέργεια αυτόματα",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF5B5361),
        )
    }
}

@Composable
private fun MetricCard(title: String, primary: String, detail: String, status: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1EDF4), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = primary, style = MaterialTheme.typography.headlineSmall)
        Text(text = detail, style = MaterialTheme.typography.bodyMedium)
        Text(text = status, style = MaterialTheme.typography.labelLarge, color = Color(0xFF5B5361))
    }
}
