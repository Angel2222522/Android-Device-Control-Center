package dev.devicecontrolcenter

import android.os.PowerManager

enum class DeviceInsightSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

data class DeviceInsight(
    val id: String,
    val title: String,
    val evidence: String,
    val severity: DeviceInsightSeverity,
)

data class DeviceInsightsResult(
    val insights: List<DeviceInsight>,
)

object DeviceInsights {
    private const val LOW_STORAGE_BYTES = 10L * 1_073_741_824L
    private const val LARGE_APP_BYTES = 1L * 1_073_741_824L

    fun evaluate(
        snapshot: DeviceSnapshot,
        appResult: AppIntelligenceResult? = null,
        storageResult: StorageScanResult? = null,
        duplicateResult: ExactDuplicateResult? = null,
        networkResult: NetworkUsageResult? = null,
    ): DeviceInsightsResult {
        val insights = mutableListOf<DeviceInsight>()
        if (snapshot.isLowMemory) {
            insights += DeviceInsight(
                id = "low-memory",
                title = "Το Android έχει ενεργή ένδειξη χαμηλής μνήμης",
                evidence = "Διαθέσιμη RAM: " + SnapshotPresentation.gib(snapshot.availableMemoryBytes) +
                    " · το σήμα προέρχεται από το Android.",
                severity = DeviceInsightSeverity.WARNING,
            )
        }
        if (snapshot.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            insights += DeviceInsight(
                id = "thermal",
                title = "Υπάρχει σοβαρός θερμικός περιορισμός",
                evidence = "Τρέχουσα θερμική κατάσταση: " +
                    OverviewPresentation.thermalShortLabel(snapshot.thermalStatus) + ".",
                severity = DeviceInsightSeverity.CRITICAL,
            )
        }
        if (snapshot.availableStorageBytes in 1 until LOW_STORAGE_BYTES) {
            insights += DeviceInsight(
                id = "low-storage",
                title = "Ο διαθέσιμος χώρος είναι περιορισμένος",
                evidence = "Διαθέσιμος χώρος: " + SnapshotPresentation.gib(snapshot.availableStorageBytes) +
                    " · όριο ένδειξης: 10 GiB.",
                severity = DeviceInsightSeverity.WARNING,
            )
        }
        if (!duplicateResult?.groups.isNullOrEmpty()) {
            insights += DeviceInsight(
                id = "duplicates",
                title = "Βρέθηκαν ακριβή διπλότυπα",
                evidence = (duplicateResult?.groups?.size ?: 0).toString() +
                    " ομάδες επιβεβαιώθηκαν με SHA-256.",
                severity = DeviceInsightSeverity.INFO,
            )
        }
        val largestApp = appResult?.entries
            ?.filter { it.storageBytes != null }
            ?.maxByOrNull { it.storageBytes ?: 0L }
        if ((largestApp?.storageBytes ?: 0L) >= LARGE_APP_BYTES) {
            insights += DeviceInsight(
                id = "large-app",
                title = "Μια εφαρμογή καταλαμβάνει σημαντικό χώρο",
                evidence = largestApp?.label.orEmpty() + ": " +
                    AppIntelligencePresentation.storageLabel(largestApp?.storageBytes) + ".",
                severity = DeviceInsightSeverity.INFO,
            )
        }
        val topNetwork = networkResult?.entries?.maxByOrNull { it.totalBytes }
        if ((topNetwork?.totalBytes ?: 0L) > 0L) {
            insights += DeviceInsight(
                id = "network",
                title = "Μια εφαρμογή έχει τη μεγαλύτερη κίνηση στο παράθυρο",
                evidence = topNetwork?.label.orEmpty() + ": " +
                    NetworkUsagePresentation.totalLabel(topNetwork?.totalBytes ?: 0L) + ".",
                severity = DeviceInsightSeverity.INFO,
            )
        }
        if (storageResult?.wasTruncated == true) {
            insights += DeviceInsight(
                id = "storage-limit",
                title = "Η σάρωση χώρου σταμάτησε στο ασφαλές όριο",
                evidence = "Η ένδειξη βασίζεται σε μερική, ελεγχόμενη σάρωση.",
                severity = DeviceInsightSeverity.INFO,
            )
        }
        if (insights.isEmpty()) {
            insights += DeviceInsight(
                id = "no-signal",
                title = "Δεν εντοπίστηκε ενεργό σήμα προτεραιότητας",
                evidence = "Τα διαθέσιμα στοιχεία δεν έδειξαν ένδειξη που να απαιτεί ενέργεια τώρα.",
                severity = DeviceInsightSeverity.INFO,
            )
        }
        return DeviceInsightsResult(insights = insights.take(8))
    }
}

object DeviceInsightsPresentation {
    fun summary(result: DeviceInsightsResult): String = when (result.insights.size) {
        0 -> "Δεν υπάρχει διαθέσιμη σύνοψη."
        1 -> "1 παρατήρηση από τα διαθέσιμα δεδομένα."
        else -> result.insights.size.toString() + " παρατηρήσεις από τα διαθέσιμα δεδομένα."
    }

    fun severityLabel(severity: DeviceInsightSeverity): String = when (severity) {
        DeviceInsightSeverity.INFO -> "Πληροφορία"
        DeviceInsightSeverity.WARNING -> "Προσοχή"
        DeviceInsightSeverity.CRITICAL -> "Κρίσιμο"
    }
}
