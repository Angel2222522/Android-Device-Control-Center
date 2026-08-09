package dev.devicecontrolcenter

import android.os.Bundle
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                CapabilityScreen(snapshot = remember { DeviceSnapshotReader.read(this) })
            }
        }
    }
}

@Composable
private fun CapabilityScreen(snapshot: DeviceSnapshot) {
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
                text = "Πραγματικό στιγμιότυπο από τα δημόσια API του Android. Δεν αποτελεί ακόμη διάγνωση.",
                style = MaterialTheme.typography.bodyLarge,
            )
            MetricCard(
                title = "Μνήμη RAM",
                primary = "${SnapshotPresentation.gib(snapshot.availableMemoryBytes)} διαθέσιμα",
                detail = "Σύνολο ${SnapshotPresentation.gib(snapshot.totalMemoryBytes)} · Όριο χαμηλής μνήμης ${SnapshotPresentation.gib(snapshot.lowMemoryThresholdBytes)}",
                status = if (snapshot.isLowMemory) "Υπάρχει πίεση μνήμης" else "Δεν αναφέρεται χαμηλή μνήμη",
            )
            MetricCard(
                title = "Επεξεργαστής και θερμοκρασία",
                primary = SnapshotPresentation.thermalLabel(snapshot.thermalStatus),
                detail = snapshot.thermalHeadroom?.let(SnapshotPresentation::thermalEnvelopeLabel)
                    ?: "Η συσκευή δεν επέστρεψε μέτρηση θερμικού ορίου τώρα",
                status = "Δεν γίνεται ακόμη απόδοση αιτίας σε εφαρμογή",
            )
            MetricCard(
                title = "Εσωτερική αποθήκευση",
                primary = "${SnapshotPresentation.gib(snapshot.availableStorageBytes)} διαθέσιμα",
                detail = "Σύνολο ${SnapshotPresentation.gib(snapshot.totalStorageBytes)}",
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
