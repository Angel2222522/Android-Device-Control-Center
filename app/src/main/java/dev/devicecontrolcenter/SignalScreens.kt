package dev.devicecontrolcenter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

enum class SignalTab {
    BATTERY,
    PERFORMANCE,
    NETWORK,
}

@Composable
fun SignalsScreen(
    snapshot: DeviceSnapshot,
    history: HistoryUiState,
    batteryHistory: BatteryHistoryUiState,
    networkHistory: NetworkHistoryUiState,
    onRefresh: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(SignalTab.BATTERY) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Σήματα συσκευής", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("Μετρήσεις, τάσεις και περιορισμοί χωρίς ψεύτικους ενισχυτές.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = selectedTab == SignalTab.BATTERY, onClick = { selectedTab = SignalTab.BATTERY }, label = { Text("Μπαταρία") })
                FilterChip(selected = selectedTab == SignalTab.PERFORMANCE, onClick = { selectedTab = SignalTab.PERFORMANCE }, label = { Text("Απόδοση") })
                FilterChip(selected = selectedTab == SignalTab.NETWORK, onClick = { selectedTab = SignalTab.NETWORK }, label = { Text("Δίκτυο") })
            }
        }
        item {
            when (selectedTab) {
                SignalTab.BATTERY -> BatteryIntelligencePanel(snapshot, batteryHistory)
                SignalTab.PERFORMANCE -> PerformancePanel(snapshot, history)
                SignalTab.NETWORK -> NetworkIntelligencePanel(snapshot.network, networkHistory)
            }
        }
        item {
            TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Λήψη νέου στιγμιότυπου") }
        }
    }
}

@Composable
private fun BatteryIntelligencePanel(snapshot: DeviceSnapshot, state: BatteryHistoryUiState) {
    val entries = state.entries.asReversed()
    val analytics = remember(state.entries) { BatteryHistoryAnalyticsCalculator.calculate(state.entries) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SignalCard {
            SignalSectionTitle("Μπαταρία τώρα")
            Text(BatteryPresentation.levelLabel(snapshot.battery.levelPercent), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(OverviewPresentation.batterySupport(snapshot.battery), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(BatteryPresentation.voltageLabel(snapshot.battery.voltageMillivolts, snapshot.battery.voltageSource), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Δεν εμφανίζεται αυθαίρετη υγεία ή χωρητικότητα. Τα διαθέσιμα στοιχεία του μετρητή μπαταρίας είναι προαιρετικά και εξαρτώνται από τον κατασκευαστή.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SignalCard {
            SignalSectionTitle("Ιστορικό φόρτισης και χρήσης")
            when {
                state.isLoading -> SignalStatusText("Φόρτωση ιστορικού μπαταρίας…")
                state.errorMessage != null && state.entries.isEmpty() -> SignalStatusText("Το ιστορικό μπαταρίας δεν είναι διαθέσιμο: ${state.errorMessage}", isError = true)
                state.entries.isEmpty() -> SignalStatusText("Δεν υπάρχουν ακόμη τοπικές καταγραφές μπαταρίας.")
                else -> {
                    Text(BatteryHistoryPresentation.summary(state.entries), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(BatteryHistoryPresentation.direction(state.entries), style = MaterialTheme.typography.bodyMedium)
                    val levels = entries.mapNotNull { it.levelPercent?.toFloat() }
                    if (levels.size >= 2) {
                        val chartDescription = batteryChartDescription(levels)
                        Sparkline(
                            values = levels,
                            color = Color(0xFF74E5F0),
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            description = chartDescription,
                        )
                        Text(chartDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Χρειάζονται τουλάχιστον δύο επιτυχημένες καταγραφές για γράφημα.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.errorMessage?.let { SignalStatusText("Δεν ανανεώθηκε το ιστορικό μπαταρίας: $it. Εμφανίζονται οι διαθέσιμες καταγραφές.", isError = true) }
                }
            }
        }
        SignalCard {
            SignalSectionTitle("Ανάλυση μπαταρίας")
            MetricLine("Παρατηρούμενη διάρκεια φόρτισης", analytics.observedChargingLabel)
            MetricLine("Δείγματα σε κατάσταση φόρτισης", analytics.chargingSamples.toString())
            MetricLine("Κύκλοι", analytics.cycleLabel)
            MetricLine("Εκτίμηση χωρητικότητας", analytics.capacityLabel)
            Text(analytics.wearLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Οι κύκλοι προσεγγίζονται από μεταβολές ποσοστού μεταξύ τοπικών δειγμάτων και δεν αποτελούν OEM μετρητή.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            analytics.alerts.forEach { alert ->
                Text(alert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        SignalCard {
            SignalSectionTitle("Πρόσφατα δείγματα")
            when {
                state.isLoading -> SignalStatusText("Φόρτωση πρόσφατων δειγμάτων μπαταρίας…")
                state.errorMessage != null && state.entries.isEmpty() -> SignalStatusText("Τα πρόσφατα δείγματα μπαταρίας δεν είναι διαθέσιμα: ${state.errorMessage}", isError = true)
                state.entries.isEmpty() -> SignalStatusText("Δεν υπάρχουν πρόσφατα δείγματα μπαταρίας.")
                else -> state.entries.take(6).forEach { entry ->
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(SnapshotPresentation.capturedTimeLabel(entry.capturedAtMillis), style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${entry.levelPercent?.let { "$it%" } ?: "—"} · ${BatteryHistoryPresentation.temperature(entry)} · ${BatteryHistoryPresentation.current(entry)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformancePanel(snapshot: DeviceSnapshot, history: HistoryUiState) {
    val entries = history.entries.asReversed()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SignalCard {
            SignalSectionTitle("Απόδοση τώρα")
            MetricLine("Διαθέσιμη RAM", SnapshotPresentation.gib(snapshot.availableMemoryBytes))
            MetricLine("Σήμα χαμηλής μνήμης", if (snapshot.isLowMemory) "Το Android αναφέρει πίεση" else "Δεν αναφέρεται πίεση")
            MetricLine("Δραστηριότητα CPU", CpuPresentation.activityLabel(snapshot.cpu.activityPercent))
            MetricLine("Θερμική κατάσταση", SnapshotPresentation.thermalLabel(snapshot.thermalStatus))
            Text(CpuPresentation.detail(snapshot.cpu), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Δεν υπάρχει λειτουργία που να αυξάνει τεχνητά CPU ή RAM. Οι προτάσεις βασίζονται σε πραγματικά σήματα.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SignalCard {
            SignalSectionTitle("Ιστορικό RAM και θερμικής κατάστασης")
            when {
                history.isLoading -> SignalStatusText("Φόρτωση ιστορικού RAM και θερμικής κατάστασης…")
                history.errorMessage != null && history.entries.isEmpty() -> SignalStatusText("Το ιστορικό RAM και θερμικής κατάστασης δεν είναι διαθέσιμο: ${history.errorMessage}", isError = true)
                history.entries.isEmpty() -> SignalStatusText("Δεν υπάρχουν ακόμη τοπικές καταγραφές RAM και θερμικής κατάστασης.")
                entries.size >= 2 -> {
                    val memoryValues = entries.map { it.availableMemoryBytes / 1_073_741_824f }
                    val chartDescription = memoryChartDescription(memoryValues)
                    Sparkline(
                        values = memoryValues,
                        color = Color(0xFFC3AEFF),
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        description = chartDescription,
                    )
                    Text(
                        "$chartDescription Η ιστορική συσχέτιση δεν αποδεικνύει αιτία.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    history.errorMessage?.let { SignalStatusText("Δεν ανανεώθηκε το ιστορικό RAM και θερμικής κατάστασης: $it. Εμφανίζονται οι διαθέσιμες καταγραφές.", isError = true) }
                }
                else -> Text("Χρειάζονται τουλάχιστον δύο τοπικές καταγραφές για γράφημα.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SignalCard {
            SignalSectionTitle("Τι μπορεί πραγματικά να βοηθήσει")
            Text("• Μείωσε θερμικό φορτίο όταν το Android αναφέρει περιορισμό.", style = MaterialTheme.typography.bodyMedium)
            Text("• Έλεγξε εφαρμογές με μεγάλη χρήση ή χώρο από το Κέντρο εφαρμογών.", style = MaterialTheme.typography.bodyMedium)
            Text("• Μην κλείνεις μαζικά εφαρμογές χωρίς ένδειξη· συχνά αυξάνει την κατανάλωση.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NetworkIntelligencePanel(snapshot: NetworkSnapshot, state: NetworkHistoryUiState) {
    val entries = state.entries.asReversed()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SignalCard {
            SignalSectionTitle("Κίνηση τελευταίου 24ώρου")
            Text(NetworkPresentation.totalLabel(snapshot), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(NetworkPresentation.period(snapshot), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MetricLine("Wi‑Fi λήψη", NetworkPresentation.bytes(snapshot.wifiReceivedBytes))
            MetricLine("Wi‑Fi αποστολή", NetworkPresentation.bytes(snapshot.wifiSentBytes))
            MetricLine("Κινητή λήψη", NetworkPresentation.bytes(snapshot.mobileReceivedBytes))
            MetricLine("Κινητή αποστολή", NetworkPresentation.bytes(snapshot.mobileSentBytes))
            Text(NetworkPresentation.source(snapshot), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SignalCard {
            SignalSectionTitle("Τοπικό ιστορικό δικτύου")
            when {
                state.isLoading -> SignalStatusText("Φόρτωση τοπικού ιστορικού δικτύου…")
                state.errorMessage != null && state.entries.isEmpty() -> SignalStatusText("Το ιστορικό δικτύου δεν είναι διαθέσιμο: ${state.errorMessage}", isError = true)
                state.entries.isEmpty() -> SignalStatusText("Δεν υπάρχουν ακόμη τοπικές καταγραφές δικτύου.")
                else -> {
                    Text(NetworkHistoryPresentation.summary(state.entries), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val networkValues = entries.mapNotNull { networkTotalBytes(it)?.let { bytes -> bytes / 1_073_741_824f } }
                    if (networkValues.size >= 2) {
                        val chartDescription = networkChartDescription(networkValues)
                        Sparkline(
                            values = networkValues,
                            color = Color(0xFF8BE28B),
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            description = chartDescription,
                        )
                        Text(chartDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Χρειάζονται δύο διαθέσιμα δείγματα για γράφημα κίνησης.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.entries.take(8).forEach { entry ->
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(SnapshotPresentation.capturedTimeLabel(entry.capturedAtMillis), style = MaterialTheme.typography.labelMedium)
                            Text(NetworkHistoryPresentation.total(entry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(NetworkHistoryPresentation.comparison(state.entries), style = MaterialTheme.typography.bodySmall)
                    state.errorMessage?.let { SignalStatusText("Δεν ανανεώθηκε το ιστορικό δικτύου: $it. Εμφανίζονται οι διαθέσιμες καταγραφές.", isError = true) }
                }
            }
            Text("Τα στατιστικά του Android μπορεί να είναι καθυστερημένα ή ομαδοποιημένα ανά κατασκευαστή.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SignalCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun SignalSectionTitle(label: String) {
    Text(
        label,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun SignalStatusText(message: String, isError: Boolean = false) {
    Text(
        message,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MetricLine(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    description: String,
) {
    val safe = values.filter { it.isFinite() }
    if (safe.size < 2) return
    Canvas(
        modifier = modifier
            .padding(vertical = 8.dp)
            .semantics { contentDescription = description },
    ) {
        val min = safe.minOrNull() ?: return@Canvas
        val max = safe.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0.0001f } ?: 1f
        val path = Path()
        safe.forEachIndexed { index, value ->
            val x = size.width * index / (safe.lastIndex).coerceAtLeast(1)
            val y = size.height - ((value - min) / range * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(color.copy(alpha = 0.18f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
        drawPath(path, color, style = Stroke(width = 4f))
        safe.forEachIndexed { index, value ->
            val x = size.width * index / (safe.lastIndex).coerceAtLeast(1)
            val y = size.height - ((value - min) / range * size.height)
            drawCircle(color, radius = 4f, center = Offset(x, y))
        }
    }
}

private fun batteryChartDescription(values: List<Float>): String {
    val safe = values.filter { it.isFinite() }
    if (safe.size < 2) return "Γράφημα στάθμης μπαταρίας χωρίς αρκετά δεδομένα"
    return "Γράφημα στάθμης μπαταρίας: ${safe.size} δείγματα, από ${safe.minOrNull()!!.toInt()}% έως ${safe.maxOrNull()!!.toInt()}%, τελευταίο ${safe.last().toInt()}%"
}

private fun memoryChartDescription(values: List<Float>): String {
    val safe = values.filter { it.isFinite() }
    if (safe.size < 2) return "Γράφημα διαθέσιμης RAM χωρίς αρκετά δεδομένα"
    return "Γράφημα διαθέσιμης RAM: ${safe.size} δείγματα, από ${formatGib(safe.minOrNull()!!)} έως ${formatGib(safe.maxOrNull()!!)}, τελευταίο ${formatGib(safe.last())}"
}

private fun networkChartDescription(values: List<Float>): String {
    val safe = values.filter { it.isFinite() }
    if (safe.size < 2) return "Γράφημα κίνησης δικτύου χωρίς αρκετά δεδομένα"
    return "Γράφημα συνολικής κίνησης δικτύου ανά καταγραφή 24ώρου: ${safe.size} δείγματα, από ${formatGib(safe.minOrNull()!!)} έως ${formatGib(safe.maxOrNull()!!)}, τελευταία ${formatGib(safe.last())}"
}

private fun formatGib(value: Float): String = String.format(Locale.ROOT, "%.2f GiB", value)

private fun networkTotalBytes(entry: NetworkSampleEntity): Long? {
    val values = listOfNotNull(
        entry.wifiReceivedBytes,
        entry.wifiSentBytes,
        entry.mobileReceivedBytes,
        entry.mobileSentBytes,
    )
    if (values.isEmpty()) return null
    return values.fold(0L) { total, value ->
        when {
            value <= 0L -> total
            Long.MAX_VALUE - total < value -> Long.MAX_VALUE
            else -> total + value
        }
    }
}
