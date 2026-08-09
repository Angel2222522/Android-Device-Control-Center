package dev.devicecontrolcenter

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val snapshotState = remember { mutableStateOf<DeviceSnapshot?>(null) }

    DisposableEffect(context) {
        val executor = Executors.newSingleThreadExecutor()
        val mainHandler = Handler(Looper.getMainLooper())
        val active = AtomicBoolean(true)
        executor.execute {
            val snapshot = DeviceSnapshotReader.read(context)
            if (active.get()) {
                mainHandler.post {
                    if (active.get()) snapshotState.value = snapshot
                }
            }
        }
        onDispose {
            active.set(false)
            executor.shutdownNow()
        }
    }

    val snapshot = snapshotState.value
    if (snapshot == null) {
        LoadingScreen()
    } else {
        CapabilityScreen(snapshot)
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
private fun CapabilityScreen(snapshot: DeviceSnapshot) {
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
