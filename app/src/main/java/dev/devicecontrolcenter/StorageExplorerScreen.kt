package dev.devicecontrolcenter

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val MAX_EXPLORER_ENTRIES = 500

private data class ExplorerCandidate(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModifiedMillis: Long,
)

private data class ExplorerEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModifiedMillis: Long,
)

private data class ExplorerDirectory(
    val name: String,
    val entries: List<ExplorerEntry>,
    val hasMoreEntries: Boolean,
)

private val explorerCandidateComparator = Comparator<ExplorerCandidate> { left, right ->
    when {
        left.isDirectory && !right.isDirectory -> -1
        !left.isDirectory && right.isDirectory -> 1
        else -> {
            val nameComparison = String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name)
            if (nameComparison != 0) nameComparison else left.uri.toString().compareTo(right.uri.toString())
        }
    }
}

@Composable
fun StorageExplorerScreen(
    context: Context,
    rootUri: Uri,
    onExit: () -> Unit,
    onOpenFile: (Uri) -> Unit,
) {
    var navigation by remember(rootUri) { mutableStateOf(listOf(rootUri)) }
    val currentUri = navigation.last()
    var entries by remember(currentUri) { mutableStateOf<List<ExplorerEntry>>(emptyList()) }
    var currentName by remember(currentUri) { mutableStateOf("Επιλεγμένος φάκελος") }
    var hasMoreEntries by remember(currentUri) { mutableStateOf(false) }
    var isLoading by remember(currentUri) { mutableStateOf(true) }
    var errorMessage by remember(currentUri) { mutableStateOf<String?>(null) }
    var reloadRequest by remember(currentUri) { mutableStateOf(0) }

    BackHandler {
        if (navigation.size > 1) {
            navigation = navigation.dropLast(1)
        } else {
            onExit()
        }
    }

    LaunchedEffect(currentUri, reloadRequest) {
        isLoading = true
        errorMessage = null
        entries = emptyList()
        hasMoreEntries = false

        try {
            // Only the immutable result crosses the dispatcher boundary. Compose state is
            // updated after withContext returns on the LaunchedEffect coroutine.
            val loadedDirectory = withContext(Dispatchers.IO) {
                loadExplorerDirectory(context, currentUri)
            }
            currentCoroutineContext().ensureActive()
            currentName = loadedDirectory.name
            entries = loadedDirectory.entries
            hasMoreEntries = loadedDirectory.hasMoreEntries
        } catch (cancellation: CancellationException) {
            // Never convert coroutine cancellation into an explorer error. A navigation change
            // must be allowed to cancel the previous provider read immediately.
            throw cancellation
        } catch (error: Exception) {
            errorMessage = error.message ?: "Δεν ήταν δυνατή η ανάγνωση του φακέλου."
        }

        isLoading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                TextButton(onClick = onExit) { Text("← Κέντρο αποθήκευσης") }
                if (navigation.size > 1) {
                    TextButton(onClick = { navigation = navigation.dropLast(1) }) { Text("Πίσω") }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Εξερευνητής αρχείων",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    currentName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Περιήγηση μόνο για ανάγνωση. Δεν τροποποιείται κανένα αρχείο από αυτή την οθόνη.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isLoading) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Φόρτωση φακέλου…")
                }
            }
        } else if (errorMessage != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Ο φάκελος δεν είναι διαθέσιμος ή η πρόσβαση έληξε.", color = MaterialTheme.colorScheme.error)
                    Text(errorMessage ?: "Δεν ήταν δυνατή η ανάγνωση του φακέλου.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { reloadRequest++ }) { Text("Δοκίμασε ξανά") }
                }
            }
        } else if (entries.isEmpty()) {
            item { Text("Ο φάκελος είναι κενός.", modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
        } else {
            if (hasMoreEntries) {
                item {
                    Text(
                        "Εμφανίζονται έως ${entries.size} στοιχεία· η λίστα περιορίστηκε για σταθερή απόδοση.",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries, key = { it.uri.toString() }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = entry.isDirectory,
                            onClickLabel = if (entry.isDirectory) "Άνοιγμα φακέλου ${entry.name}" else null,
                        ) {
                            navigation = navigation + entry.uri
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (entry.isDirectory) {
                                    "Φάκελος"
                                } else {
                                    "${entry.mimeType ?: "Αρχείο"} · ${StorageIntelligencePresentation.fileSize(StorageFileEntry(entry.name, entry.sizeBytes, entry.lastModifiedMillis))}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!entry.isDirectory) {
                            TextButton(
                                onClick = { onOpenFile(entry.uri) },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .semantics { contentDescription = "Άνοιγμα αρχείου ${entry.name}" },
                            ) { Text("Άνοιγμα") }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadExplorerDirectory(
    context: Context,
    uri: Uri,
): ExplorerDirectory {
    currentCoroutineContext().ensureActive()

    val directory = DocumentFile.fromTreeUri(context, uri)
        ?: DocumentFile.fromSingleUri(context, uri)
        ?: error("Ο φάκελος δεν είναι πλέον διαθέσιμος.")
    check(directory.isDirectory) { "Η επιλογή δεν είναι φάκελος." }

    val directoryName = directory.name?.takeIf(String::isNotBlank) ?: "Χωρίς όνομα"
    val coroutineContext = currentCoroutineContext()

    // The provider cursor is allowed to yield only one bounded UI window plus one row to detect
    // truncation. No provider-sized child array crosses into the app, and the UI never claims
    // that an unbounded directory was fully counted.
    val selected = ArrayList<ExplorerCandidate>(MAX_EXPLORER_ENTRIES)
    var observedEntries = 0
    var hasMoreEntries = false
    val enumeration = enumerateDocumentChildren(
        context = context,
        directoryUri = directory.uri,
        shouldContinue = { coroutineContext.ensureActive() },
    ) { child ->
        observedEntries++
        if (observedEntries > MAX_EXPLORER_ENTRIES) {
            hasMoreEntries = true
            false
        } else {
            selected += ExplorerCandidate(
                uri = child.uri,
                name = child.name,
                isDirectory = child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                mimeType = child.mimeType,
                sizeBytes = child.sizeBytes,
                lastModifiedMillis = child.lastModifiedMillis,
            )
            true
        }
    }
    check(enumeration != DocumentChildEnumerationResult.UNREADABLE) {
        "Δεν ήταν δυνατή η ανάγνωση του φακέλου."
    }

    val selectedEntries = selected
        .sortedWith(explorerCandidateComparator)
        .mapIndexed { index, candidate ->
            if (index % 32 == 0) currentCoroutineContext().ensureActive()
            ExplorerEntry(
                uri = candidate.uri,
                name = candidate.name,
                isDirectory = candidate.isDirectory,
                mimeType = candidate.mimeType,
                sizeBytes = if (candidate.isDirectory) null else candidate.sizeBytes?.takeIf { it >= 0L },
                lastModifiedMillis = candidate.lastModifiedMillis,
            )
        }

    currentCoroutineContext().ensureActive()
    return ExplorerDirectory(directoryName, selectedEntries, hasMoreEntries)
}
