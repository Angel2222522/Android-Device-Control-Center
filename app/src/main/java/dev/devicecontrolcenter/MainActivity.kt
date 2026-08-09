package dev.devicecontrolcenter

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PremiumTheme {
                CapabilityRoute(context = this@MainActivity)
            }
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
) {
    val diagnosis = remember(snapshot) { DeviceDiagnosisEngine.analyze(snapshot) }
    val overviewStatus = OverviewPresentation.status(diagnosis)

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
                        supporting = snapshot.thermalHeadroom?.let(SnapshotPresentation::thermalEnvelopeLabel)
                            ?: "Δεν υπάρχει μέτρηση θερμικού ορίου τώρα",
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
                DiagnosisCard(report = diagnosis)
            }
            item {
                BatteryCard(snapshot = snapshot.battery)
            }
            item {
                MemoryDetailsCard(snapshot = snapshot)
            }
            item {
                AccessCard(snapshot = snapshot)
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
                    text = SnapshotPresentation.capturedAtLabel(state.capturedAtMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
private fun AccessCard(snapshot: DeviceSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, toneAccent(OverviewTone.UNAVAILABLE).copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Ειδικές προσβάσεις", style = MaterialTheme.typography.titleMedium)
            AccessRow("Στατιστικά χρήσης", snapshot.hasUsageAccess)
            AccessRow("Πρόσβαση όλων των αρχείων", snapshot.hasAllFilesAccess)
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
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
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
