package dev.devicecontrolcenter

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager

data class AppNetworkUsageEntry(
    val packageName: String,
    val label: String,
    val downloadBytes: Long,
    val uploadBytes: Long,
    val isUnavailable: Boolean = false,
) {
    val totalBytes: Long
        get() = saturatingAdd(downloadBytes, uploadBytes)

    private fun saturatingAdd(first: Long, second: Long): Long = when {
        first < 0L || second < 0L -> 0L
        Long.MAX_VALUE - first < second -> Long.MAX_VALUE
        else -> first + second
    }
}

data class NetworkUsageResult(
    val scannedAtMillis: Long,
    val windowStartMillis: Long,
    val windowDays: Int,
    val appsConsidered: Int,
    val unavailableCount: Int,
    val entries: List<AppNetworkUsageEntry>,
)

data class NetworkUsageUiState(
    val result: NetworkUsageResult? = null,
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
)

object NetworkUsageScanner {
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

    fun scan(
        context: Context,
        appEntries: List<AppUsageEntry>,
        nowMillis: Long = System.currentTimeMillis(),
    ): NetworkUsageResult {
        check(UsageAccessReader.isGranted(context)) {
            "Χρειάζεται ενεργή πρόσβαση στα στατιστικά χρήσης για τα στοιχεία δικτύου."
        }
        val windowStartMillis = nowMillis - AppIntelligenceScanner.WINDOW_DAYS * MILLIS_PER_DAY
        val manager = context.getSystemService(NetworkStatsManager::class.java)
        val packageManager = context.packageManager
        var unavailableCount = 0

        val entries = appEntries.map { app ->
            val uid = runCatching {
                packageManager.getApplicationInfo(app.packageName, 0).uid
            }.getOrNull()
            val totals = uid?.let {
                queryUidBytes(manager, it, windowStartMillis, nowMillis)
            }
            if (uid == null || totals == null) unavailableCount++
            AppNetworkUsageEntry(
                packageName = app.packageName,
                label = app.label,
                downloadBytes = totals?.first ?: 0L,
                uploadBytes = totals?.second ?: 0L,
                isUnavailable = uid == null || totals == null,
            )
        }.sortedByDescending { it.totalBytes }

        return NetworkUsageResult(
            scannedAtMillis = nowMillis,
            windowStartMillis = windowStartMillis,
            windowDays = AppIntelligenceScanner.WINDOW_DAYS,
            appsConsidered = appEntries.size,
            unavailableCount = unavailableCount,
            entries = entries,
        )
    }

    private fun queryUidBytes(
        manager: NetworkStatsManager?,
        uid: Int,
        startMillis: Long,
        endMillis: Long,
    ): Pair<Long, Long>? {
        if (manager == null) return null
        var downloadBytes = 0L
        var uploadBytes = 0L
        var queried = false
        listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE).forEach { networkType ->
            val stats = runCatching {
                manager.queryDetailsForUid(networkType, null, startMillis, endMillis, uid)
            }.getOrNull() ?: return@forEach
            queried = true
            try {
                val bucket = NetworkStats.Bucket()
                while (stats.getNextBucket(bucket)) {
                    downloadBytes = saturatingAdd(downloadBytes, bucket.rxBytes)
                    uploadBytes = saturatingAdd(uploadBytes, bucket.txBytes)
                }
            } finally {
                stats.close()
            }
        }
        return if (queried) downloadBytes to uploadBytes else null
    }

    private fun saturatingAdd(first: Long, second: Long): Long = when {
        first < 0L || second < 0L -> 0L
        Long.MAX_VALUE - first < second -> Long.MAX_VALUE
        else -> first + second
    }
}

object NetworkUsagePresentation {
    fun summary(result: NetworkUsageResult): String = buildString {
        append(result.entries.size)
        append(" εφαρμογές στην αναφορά · παράθυρο ")
        append(result.windowDays)
        append(" ημερών")
        if (result.unavailableCount > 0) {
            append(" · μη διαθέσιμα για ")
            append(result.unavailableCount)
        }
    }

    fun totalLabel(bytes: Long): String =
        StorageIntelligencePresentation.storageSize(bytes)

    fun directionLabel(entry: AppNetworkUsageEntry): String =
        "Λήψη " + StorageIntelligencePresentation.storageSize(entry.downloadBytes) +
            " · αποστολή " + StorageIntelligencePresentation.storageSize(entry.uploadBytes)

    fun limitation(): String =
        "Τα στοιχεία εξαρτώνται από την πρόσβαση χρήσης, το Android/OEM και τη διαθεσιμότητα ιστορικού δικτύου· δεν είναι ζωντανή μέτρηση."
}
