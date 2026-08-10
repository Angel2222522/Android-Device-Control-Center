package dev.devicecontrolcenter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AppSort {
    NAME,
    STORAGE,
    NETWORK,
    LAST_USED,
    UPDATED,
}

@Composable
fun AppCenterScreen(
    context: Context,
    catalog: AppCatalogResult,
    onReload: () -> Unit,
    onOpenSettings: (AppRecord) -> Unit,
    onLaunch: (AppRecord) -> Unit,
    onUninstall: (AppRecord) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var includeSystem by remember { mutableStateOf(true) }
    var includeDisabled by remember { mutableStateOf(true) }
    var sort by remember { mutableStateOf(AppSort.NAME) }
    var selected by remember { mutableStateOf<AppRecord?>(null) }
    var reloadFeedback by remember { mutableStateOf<String?>(null) }

    selected?.let { app ->
        AppDetailScreen(
            context = context,
            app = app,
            onBack = { selected = null },
            onOpenSettings = { onOpenSettings(app) },
            onLaunch = { onLaunch(app) },
            onUninstall = { onUninstall(app) },
        )
        return
    }

    val visibleApps = remember(catalog.apps, query, includeSystem, includeDisabled, sort) {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        catalog.apps
            .asSequence()
            .filter { includeSystem || !it.isSystem }
            .filter { includeDisabled || it.isEnabled }
            .filter {
                normalizedQuery.isBlank() ||
                    it.label.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    it.packageName.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
            .sortedWith(
                when (sort) {
                    AppSort.NAME -> compareBy<AppRecord, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
                    AppSort.STORAGE -> compareByDescending<AppRecord> { it.totalStorageBytes ?: -1L }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                    AppSort.NETWORK -> compareByDescending<AppRecord> { it.totalNetworkBytes ?: -1L }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                    AppSort.LAST_USED -> compareByDescending<AppRecord> { it.lastUsedTime ?: -1L }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                    AppSort.UPDATED -> compareByDescending<AppRecord> { it.lastUpdateTime }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                },
            )
            .toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Κέντρο εφαρμογών", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Όλες οι ορατές εφαρμογές, με σαφή διάκριση ανάμεσα σε στοιχεία Android και δεδομένα που δεν είναι διαθέσιμα.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (catalog.capturedAtMillis == null) "Αναζήτηση εφαρμογών…" else "${catalog.apps.size} εφαρμογές",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                onReload()
                                reloadFeedback = "Ζητήθηκε νέα ανάγνωση της λίστας εφαρμογών."
                            },
                        ) { Text("Ανανέωση") }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Αναζήτηση ονόματος ή πακέτου") },
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = includeSystem,
                            onClick = { includeSystem = !includeSystem },
                            label = { Text("Συστήματος") },
                        )
                        FilterChip(
                            selected = includeDisabled,
                            onClick = { includeDisabled = !includeDisabled },
                            label = { Text("Απενεργοποιημένες") },
                        )
                    }
                    Text("Ταξινόμηση", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            AppSort.NAME to "Όνομα",
                            AppSort.STORAGE to "Χώρος",
                            AppSort.NETWORK to "Δίκτυο",
                            AppSort.LAST_USED to "Χρήση",
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = sort == value,
                                onClick = { sort = value },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (!catalog.hasUsageAccess) {
                        Text(
                            "Χωρίς Usage Access, η χρήση και η κίνηση εφαρμογών εμφανίζονται ως μη διαθέσιμες.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    catalog.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    reloadFeedback?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (catalog.capturedAtMillis == null && catalog.errorMessage == null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Ανάκτηση της λίστας από το Android…")
                    Text("Η πρώτη ανάγνωση μπορεί να χρειαστεί λίγα δευτερόλεπτα.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (catalog.apps.isEmpty() && catalog.errorMessage != null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Δεν ήταν δυνατή η ανάγνωση των εφαρμογών.", style = MaterialTheme.typography.titleMedium)
                    Text("Το Android επέστρεψε σφάλμα. Έλεγξε τις ειδικές προσβάσεις και δοκίμασε ξανά.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onReload) { Text("Δοκιμή ξανά") }
                }
            }
        } else if (catalog.apps.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Δεν επιστράφηκε εφαρμογή από το Android.", style = MaterialTheme.typography.titleMedium)
                    Text("Δεν είναι ένδειξη ότι η συσκευή είναι άδεια. Δοκίμασε ανανέωση ή έλεγξε την πρόσβαση πακέτων.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (visibleApps.isEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Δεν βρέθηκε εφαρμογή με αυτά τα φίλτρα.", style = MaterialTheme.typography.titleMedium)
                    Text("Αφαίρεσε ένα φίλτρο ή καθάρισε την αναζήτηση για να δεις ξανά τη λίστα.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(visibleApps, key = { it.packageName }) { app ->
                AppRow(context = context, app = app, onClick = { selected = app })
            }
        }
    }
}

@Composable
private fun AppRow(context: Context, app: AppRecord, onClick: () -> Unit) {
    val iconState = produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.applicationContext.packageManager
                    .getApplicationIcon(app.packageName)
                    .toImageBitmap()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        }
    }
    val icon = iconState.value
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Image(icon, contentDescription = "Εικονίδιο ${app.label}", modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(app.label.take(1).uppercase(), fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${AppPresentation.typeLabel(app)} · ${AppPresentation.storageLabel(app)} · ${AppPresentation.networkLabel(app)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusBadge(text = if (app.isEnabled) "Ενεργή" else "Απενεργοποιημένη", enabled = app.isEnabled)
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, enabled: Boolean = true) {
    Surface(
        color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AppDetailScreen(
    context: Context,
    app: AppRecord,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit,
) {
    var permissionsExpanded by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<AppActionFeedback?>(null) }
    var historyReloadToken by remember { mutableStateOf(0) }
    BackHandler(onBack = onBack)
    val permissionStatuses = remember(app.packageName, app.requestedPermissions) {
        app.requestedPermissions.map { permission ->
            permission to (context.packageManager.checkPermission(permission, app.packageName) == PackageManager.PERMISSION_GRANTED)
        }
    }
    val grantedPermissions = permissionStatuses.count { it.second }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("← Πίσω στις εφαρμογές") } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(26.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(app.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusBadge(text = AppPresentation.typeLabel(app), enabled = app.isEnabled)
                        StatusBadge(text = if (app.hasIcon) "Εικονίδιο" else "Χωρίς εικονίδιο")
                        if (app.hasService) StatusBadge(text = "Υπηρεσία")
                    }
                    Text("Έκδοση ${app.versionName ?: "μη διαθέσιμη"} · κωδικός ${app.versionCode}", style = MaterialTheme.typography.bodyMedium)
                    Text("Εγκατάσταση: ${SnapshotPresentation.capturedTimeLabel(app.firstInstallTime)} · ενημέρωση: ${SnapshotPresentation.capturedTimeLabel(app.lastUpdateTime)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Μετρήσεις εφαρμογής", style = MaterialTheme.typography.titleMedium)
                    DetailLine("Χώρος εφαρμογής", app.apkBytes?.let(StorageIntelligencePresentation::storageSize) ?: "Μη διαθέσιμος")
                    DetailLine("Δεδομένα", app.dataBytes?.let(StorageIntelligencePresentation::storageSize) ?: "Μη διαθέσιμα")
                    DetailLine("Cache", app.cacheBytes?.let(StorageIntelligencePresentation::storageSize) ?: "Μη διαθέσιμη")
                    DetailLine("Χρήση στο προσκήνιο", AppPresentation.foregroundLabel(app))
                    DetailLine("Τελευταία χρήση", AppPresentation.lastUsedLabel(app))
                    DetailLine("Wi‑Fi τελευταίου 24ώρου", AppPresentation.networkPartLabel(app.wifiBytes))
                    DetailLine("Κινητά δεδομένα τελευταίου 24ώρου", AppPresentation.networkPartLabel(app.mobileBytes))
                    DetailLine("Σύνολο δικτύου τελευταίου 24ώρου", AppPresentation.networkLabel(app))
                    Text("Οι μετρήσεις εξαρτώνται από τις άδειες και την ακρίβεια του Android/OEM.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            AppUsageHistoryCard(
                context = context,
                packageName = app.packageName,
                reloadToken = historyReloadToken,
                onRetry = { historyReloadToken += 1 },
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Δικαιώματα", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { permissionsExpanded = !permissionsExpanded }) { Text(if (permissionsExpanded) "Απόκρυψη" else "Προβολή") }
                    }
                    Text(
                        "${app.requestedPermissions.size} δηλωμένα · $grantedPermissions αναφέρονται ως παραχωρημένα από το Android · ${permissionStatuses.count { !it.second }} δεν αναφέρονται ως παραχωρημένα.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (permissionsExpanded) {
                        HorizontalDivider()
                        if (permissionStatuses.isEmpty()) {
                            Text("Δεν δηλώθηκαν δικαιώματα στο πακέτο.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            permissionStatuses.forEach { (permission, granted) ->
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(AppPresentation.permissionLabel(permission), style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        if (granted) "Δηλωμένο και παραχωρημένο από το Android" else "Δηλωμένο, αλλά δεν αναφέρεται ως παραχωρημένο",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        Text("Η λίστα αφορά δηλωμένα δικαιώματα πακέτου. Ειδικές προσβάσεις, όπως Usage Access, ελέγχονται ξεχωριστά από το Android.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Ασφαλείς ενέργειες", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            actionFeedback = runAppAction(
                                action = onLaunch,
                                neutralMessage = "Δεν υπάρχει επιβεβαίωση ολοκλήρωσης από την εφαρμογή· το Android χειρίζεται το αποτέλεσμα του ανοίγματος.",
                                failureMessage = "Δεν ήταν δυνατό να ζητηθεί το άνοιγμα της εφαρμογής.",
                            )
                        },
                        enabled = app.hasLauncher,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) { Text("Άνοιγμα εφαρμογής") }
                    if (!app.hasLauncher) Text("Δεν βρέθηκε δραστηριότητα εκκίνησης για αυτό το πακέτο.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        onClick = {
                            actionFeedback = runAppAction(
                                action = onOpenSettings,
                                neutralMessage = "Δεν υπάρχει επιβεβαίωση ολοκλήρωσης από την εφαρμογή· το Android χειρίζεται το αποτέλεσμα των ρυθμίσεων.",
                                failureMessage = "Δεν ήταν δυνατό να ζητηθούν οι ρυθμίσεις Android της εφαρμογής.",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Ρυθμίσεις Android εφαρμογής") }
                    if (!app.isSystem) {
                        TextButton(
                            onClick = {
                                actionFeedback = runAppAction(
                                    action = onUninstall,
                                    neutralMessage = "Δεν υπάρχει επιβεβαίωση ολοκλήρωσης από την εφαρμογή· η τελική απόφαση απεγκατάστασης ανήκει στο Android.",
                                    failureMessage = "Δεν ήταν δυνατό να ζητηθεί η απεγκατάσταση μέσω Android.",
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Απεγκατάσταση μέσω Android") }
                    }
                    actionFeedback?.let {
                        Text(
                            it.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text("Δεν εκτελείται κρυφή εκκαθάριση cache ή force-stop από την εφαρμογή.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private sealed interface AppUsageHistoryUiState {
    data object Loading : AppUsageHistoryUiState
    data class Loaded(val samples: List<AppUsageHistorySample>) : AppUsageHistoryUiState
    data class Error(val message: String) : AppUsageHistoryUiState
}

@Composable
private fun AppUsageHistoryCard(
    context: Context,
    packageName: String,
    reloadToken: Int,
    onRetry: () -> Unit,
) {
    val historyState by produceState<AppUsageHistoryUiState>(
        initialValue = AppUsageHistoryUiState.Loading,
        key1 = packageName,
        key2 = reloadToken,
    ) {
        value = try {
            val samples = withContext(Dispatchers.IO) {
                // The repository enforces the per-package cap; this keeps this screen bounded too.
                SnapshotHistoryRepository(context.applicationContext)
                    .recentAppUsage(packageName = packageName, limit = APP_USAGE_HISTORY_SCREEN_LIMIT)
            }
            AppUsageHistoryUiState.Loaded(samples)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppUsageHistoryUiState.Error("Δεν ήταν δυνατή η ανάγνωση του τοπικού ιστορικού χρήσης.")
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Τοπικό ιστορικό χρήσης", style = MaterialTheme.typography.titleMedium)
            Text(
                "Εμφανίζονται μόνο δείγματα που έχουν καταγραφεί τοπικά για αυτή την εφαρμογή.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val state = historyState) {
                AppUsageHistoryUiState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Φόρτωση ιστορικού…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                is AppUsageHistoryUiState.Error -> {
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Δοκιμή ξανά") }
                }

                is AppUsageHistoryUiState.Loaded -> {
                    if (state.samples.isEmpty()) {
                        Text(
                            "Δεν υπάρχουν ακόμη δείγματα για αυτή την εφαρμογή. Το ιστορικό θα εμφανιστεί όταν καταγραφεί τοπική μέτρηση.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "${state.samples.size} από τα πιο πρόσφατα δείγματα",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        state.samples.forEach { sample ->
                            AppUsageHistorySampleCard(sample)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageHistorySampleCard(sample: AppUsageHistorySample) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                "Καταγραφή · ${appUsageHistoryTimestampLabel(sample.capturedAtMillis)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            AppUsageHistoryDetail(
                label = "Προσκήνιο",
                value = appUsageForegroundLabel(sample),
            )
            AppUsageHistoryDetail(
                label = "Χώρος",
                value = appUsageStorageLabel(sample),
            )
            AppUsageHistoryDetail(
                label = "Δίκτυο",
                value = appUsageNetworkLabel(sample),
            )
        }
    }
}

@Composable
private fun AppUsageHistoryDetail(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private const val APP_USAGE_HISTORY_SCREEN_LIMIT = 24

private val appUsageHistoryFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale.ROOT)

private fun appUsageHistoryTimestampLabel(timestampMillis: Long): String = runCatching {
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(appUsageHistoryFormatter)
}.getOrDefault("Άγνωστη χρονική στιγμή")

private fun appUsageForegroundLabel(sample: AppUsageHistorySample): String = when (sample.usageAvailability) {
    AppUsageMetricAvailability.AVAILABLE -> {
        val foreground = sample.foregroundMillis?.let(::appUsageDurationLabel) ?: "Μη διαθέσιμος χρόνος"
        sample.lastUsedAtMillis?.let {
            "$foreground · τελευταία χρήση ${appUsageHistoryTimestampLabel(it)}"
        } ?: foreground
    }
    else -> appUsageAvailabilityLabel(sample.usageAvailability)
}

private fun appUsageStorageLabel(sample: AppUsageHistorySample): String = when (sample.storageAvailability) {
    AppUsageMetricAvailability.AVAILABLE,
    AppUsageMetricAvailability.PARTIAL,
    -> {
        val bytes = listOfNotNull(sample.apkBytes, sample.dataBytes, sample.cacheBytes)
            .fold(0L) { total, value -> total.saturatingAddForUi(value) }
        val qualifier = if (sample.storageAvailability == AppUsageMetricAvailability.PARTIAL) " · μερικά στοιχεία" else ""
        "${StorageIntelligencePresentation.storageSize(bytes)}$qualifier"
    }
    else -> appUsageAvailabilityLabel(sample.storageAvailability)
}

private fun appUsageNetworkLabel(sample: AppUsageHistorySample): String = when (sample.networkAvailability) {
    AppUsageMetricAvailability.AVAILABLE,
    AppUsageMetricAvailability.PARTIAL,
    -> {
        val wifi = sample.wifiBytes?.let(StorageIntelligencePresentation::storageSize) ?: "Wi‑Fi μη διαθέσιμο"
        val mobile = sample.mobileBytes?.let(StorageIntelligencePresentation::storageSize) ?: "κινητά μη διαθέσιμα"
        val qualifier = if (sample.networkAvailability == AppUsageMetricAvailability.PARTIAL) " · μερικά στοιχεία" else ""
        "$wifi · $mobile$qualifier"
    }
    else -> appUsageAvailabilityLabel(sample.networkAvailability)
}

private fun appUsageDurationLabel(milliseconds: Long): String {
    val minutes = (milliseconds.coerceAtLeast(0L) / 60_000L)
    return if (minutes < 60L) "$minutes λεπτά" else "${minutes / 60L} ώρ. ${minutes % 60L} λ."
}

private fun appUsageAvailabilityLabel(availability: AppUsageMetricAvailability): String = when (availability) {
    AppUsageMetricAvailability.AVAILABLE -> "Διαθέσιμο"
    AppUsageMetricAvailability.PARTIAL -> "Μερικώς διαθέσιμο"
    AppUsageMetricAvailability.UNAVAILABLE_USAGE_ACCESS -> "Μη διαθέσιμο: απαιτείται Usage Access"
    AppUsageMetricAvailability.UNAVAILABLE_SHARED_UID -> "Μη διαθέσιμο: κοινόχρηστο UID"
    AppUsageMetricAvailability.UNAVAILABLE_API -> "Μη διαθέσιμο από το Android/OEM"
    AppUsageMetricAvailability.NOT_COLLECTED -> "Δεν έχει καταγραφεί"
}

private fun Long.saturatingAddForUi(value: Long): Long = when {
    value <= 0L -> this
    Long.MAX_VALUE - this < value -> Long.MAX_VALUE
    else -> this + value
}

private data class AppActionFeedback(
    val message: String,
    val isError: Boolean,
)

private fun runAppAction(
    action: () -> Unit,
    neutralMessage: String,
    failureMessage: String,
): AppActionFeedback = try {
    action()
    AppActionFeedback(message = neutralMessage, isError = false)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    AppActionFeedback(message = failureMessage, isError = true)
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun Drawable.toImageBitmap() = toBitmap().asImageBitmap()

private fun Drawable.toBitmap(): Bitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
    }
}
