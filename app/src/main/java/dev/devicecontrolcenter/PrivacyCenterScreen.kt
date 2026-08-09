package dev.devicecontrolcenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyCenterScreen(
    hasUsageAccess: Boolean,
    hasAllFilesAccess: Boolean,
    periodicSnapshotsEnabled: Boolean,
    onOpenUsageSettings: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
    onTogglePeriodicSnapshots: (Boolean) -> Unit,
    onExportReport: () -> Unit,
    onExportEncryptedReport: () -> Unit,
    actionLog: ActionLogUiState,
    onClearHistory: () -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showEncryptedExportDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Διαγραφή τοπικού ιστορικού") },
            text = {
                Text(
                    "Θα διαγραφούν οι μετρήσεις RAM, θερμικών σημάτων, μπαταρίας και δικτύου. " +
                        "Το αρχείο ενεργειών θα διατηρηθεί και θα προστεθεί καταγραφή αυτής της διαγραφής.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                        feedbackMessage = "Διαγράφηκαν οι τοπικές μετρήσεις. Το αρχείο ενεργειών διατηρείται."
                    },
                ) { Text("Διαγραφή") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Ακύρωση") } },
        )
    }

    if (showEncryptedExportDialog) {
        AlertDialog(
            onDismissRequest = {
                showEncryptedExportDialog = false
                feedbackMessage = "Η κρυπτογραφημένη εξαγωγή ακυρώθηκε."
            },
            title = { Text("Κρυπτογραφημένη αναφορά") },
            text = {
                Text(
                    "Η αναφορά θα κρυπτογραφηθεί τοπικά με AES-GCM και κλειδί Android Keystore πριν ανοίξει ο επιλογέας αρχείου. " +
                        "Το αρχείο είναι δυαδικό DCCX v1 και μπορεί να χρησιμοποιηθεί μόνο από την ίδια εγκατάσταση όσο παραμένει διαθέσιμο το κλειδί. " +
                        "Αυτό κρυπτογραφεί μόνο την εξαγόμενη αναφορά — όχι αυτόματα τη Room βάση ή τα δεδομένα που παραμένουν στη συσκευή.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEncryptedExportDialog = false
                        onExportEncryptedReport()
                        feedbackMessage = "Ζητήθηκε προορισμός για την κρυπτογραφημένη αναφορά."
                    },
                ) { Text("Συνέχεια") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEncryptedExportDialog = false
                        feedbackMessage = "Η κρυπτογραφημένη εξαγωγή ακυρώθηκε."
                    },
                ) { Text("Ακύρωση") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "Απόρρητο και αυτοματισμοί",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Η εφαρμογή λειτουργεί τοπικά: τα ιστορικά δεδομένα αποθηκεύονται μόνο στη συσκευή.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Σχεδιασμός απορρήτου", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    PrivacyLine("Λογαριασμός", "Δεν απαιτείται")
                    PrivacyLine("Συγχρονισμός", "Απενεργοποιημένος")
                    PrivacyLine("Διαφημίσεις", "Δεν υπάρχουν")
                    PrivacyLine("Αποστολή δεδομένων", "Δεν υλοποιείται")
                    PrivacyLine("Άδεια Internet", "Δεν δηλώνεται για τις τρέχουσες λειτουργίες")
                    Text("Οι μετρήσεις, οι σαρώσεις και το αρχείο ενεργειών μένουν στη συσκευή. Η τοπική βάση δεδομένων Room δεν κρυπτογραφείται από την εφαρμογή σε αυτή την έκδοση· προστατεύεται από την ασφάλεια της συσκευής και του Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Κεντρική οθόνη προσβάσεων", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    AccessSettingRow(
                        title = "Ιστορικό χρήσης εφαρμογών",
                        granted = hasUsageAccess,
                        detail = "Επιτρέπει ιστορικό χρήσης εφαρμογών και στατιστικά κίνησης όπου τα παρέχει το Android. Δεν ενεργοποιείται από την εφαρμογή.",
                        onOpen = {
                            onOpenUsageSettings()
                            feedbackMessage = "Στάλθηκε αίτημα για τις ρυθμίσεις ιστορικού χρήσης. Η ενεργοποίηση γίνεται μόνο από εσένα στο Android."
                        },
                    )
                    AccessSettingRow(
                        title = "Πρόσβαση σε όλα τα αρχεία",
                        granted = hasAllFilesAccess,
                        detail = "Παρέχει ευρεία ανάγνωση κοινόχρηστου χώρου και επιτρέπει μόνο μετά από ξεχωριστή επιβεβαίωση τη μετακίνηση επιλεγμένων αρχείων στον ιδιωτικό κάδο. Δεν εκτελείται αυτόματος καθαρισμός.",
                        onOpen = {
                            onOpenAllFilesSettings()
                            feedbackMessage = "Στάλθηκε αίτημα για τις ειδικές ρυθμίσεις πρόσβασης αρχείων. Η επιλογή γίνεται μόνο από εσένα στο Android."
                        },
                    )
                    Text("Καμία ειδική πρόσβαση δεν ενεργοποιείται σιωπηλά. Το QUERY_ALL_PACKAGES δεν είναι διακόπτης χρήστη· επηρεάζει μόνο το ποια εγκατεστημένα πακέτα μπορεί να εμφανίσει ο κατάλογος.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Τοπική αυτόματη καταγραφή", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Στιγμιότυπο κάθε 12 ώρες", style = MaterialTheme.typography.bodyLarge)
                            Text("WorkManager · μόνο όταν η μπαταρία δεν είναι χαμηλή · έως 120 καταγραφές.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = periodicSnapshotsEnabled,
                            onCheckedChange = {
                                onTogglePeriodicSnapshots(it)
                                feedbackMessage = if (it) "Ο τοπικός αυτοματισμός ενεργοποιήθηκε." else "Ο τοπικός αυτοματισμός απενεργοποιήθηκε."
                            },
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .semantics { contentDescription = "Ενεργοποίηση στιγμιότυπου κάθε 12 ώρες" },
                        )
                    }
                    Text("Ο αυτοματισμός δεν εκτελεί καθαρισμό, δεν ζητά νέες άδειες και δεν στέλνει ειδοποιήσεις χωρίς ξεχωριστή ρύθμιση.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Αναφορά και ιστορικό", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text("Η εξαγωγή δημιουργεί ένα απλό αρχείο κειμένου μόνο αφού επιλέξεις εσύ πού θα αποθηκευτεί.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Η αναφορά μπορεί να περιέχει ονόματα πακέτων, μετρήσεις και χρονικά σημεία. Έλεγξε τον προορισμό πριν την κοινοποιήσεις.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            onExportReport()
                            feedbackMessage = "Ζητήθηκε προορισμός για την απλή αναφορά. Η εγγραφή γίνεται μόνο μετά την επιλογή σου."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) { Text("Εξαγωγή τοπικής αναφοράς") }
                    TextButton(
                        onClick = { showEncryptedExportDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Εξαγωγή κρυπτογραφημένης αναφοράς") }
                    Text(
                        "Η κρυπτογραφημένη επιλογή παράγει δυαδικό ${EncryptedReportExport.FORMAT_DESCRIPTION}. Κρυπτογραφείται μόνο το αρχείο εξαγωγής, όχι το ιστορικό Room.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showClearDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Διαγραφή τοπικών μετρήσεων") }
                    feedbackMessage?.let {
                        Text(
                            it,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    when {
                        actionLog.isLoading -> {
                            Text(
                                "Φόρτωση αρχείου ενεργειών…",
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        actionLog.errorMessage != null && actionLog.entries.isEmpty() -> {
                            Text(
                                "Το αρχείο ενεργειών δεν είναι διαθέσιμο: ${actionLog.errorMessage}",
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        actionLog.entries.isEmpty() -> {
                            Text("Δεν έχει καταγραφεί ακόμη ενέργεια. Οι καταγραφές δημιουργούνται μόνο από ενέργειες που εκτελείς εσύ.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            Text("Τελευταίες ενέργειες", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            actionLog.entries.take(8).forEach { entry ->
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(ActionLogPresentation.actionLabel(entry), style = MaterialTheme.typography.bodySmall)
                                    entry.details?.takeIf(String::isNotBlank)?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        "${ActionLogPresentation.resultLabel(entry)} · ${SnapshotPresentation.capturedTimeLabel(entry.createdAtMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (entry.result == "success") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            actionLog.errorMessage?.let {
                                Text(
                                    "Δεν ανανεώθηκε το αρχείο ενεργειών: $it. Εμφανίζονται οι διαθέσιμες καταγραφές.",
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccessSettingRow(
    title: String,
    granted: Boolean,
    detail: String,
    onOpen: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            if (granted) "Έχει δοθεί στο Android" else "Δεν έχει δοθεί στο Android",
            style = MaterialTheme.typography.labelLarge,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!granted) TextButton(onClick = onOpen) { Text("Άνοιγμα ειδικών ρυθμίσεων") }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    }
}
