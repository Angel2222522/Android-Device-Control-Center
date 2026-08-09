package dev.devicecontrolcenter

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AppUsageEntry(
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
    val lastUsedMillis: Long?,
    val storageBytes: Long?,
)

data class AppIntelligenceResult(
    val scannedAtMillis: Long,
    val windowStartMillis: Long,
    val windowDays: Int,
    val appsConsidered: Int,
    val storageUnavailableCount: Int,
    val entries: List<AppUsageEntry>,
)

data class AppIntelligenceUiState(
    val result: AppIntelligenceResult? = null,
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
)

object UsageAccessReader {
    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }
}

object AppIntelligenceScanner {
    const val WINDOW_DAYS = 7
    const val MAX_APPS = 40
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

    fun scan(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): AppIntelligenceResult {
        check(UsageAccessReader.isGranted(context)) {
            "Δεν έχει ενεργοποιηθεί η πρόσβαση στα στατιστικά χρήσης."
        }

        val windowStartMillis = nowMillis - WINDOW_DAYS * MILLIS_PER_DAY
        val usageManager = context.getSystemService(UsageStatsManager::class.java)
        val usageByPackage = runCatching {
            usageManager?.queryAndAggregateUsageStats(windowStartMillis, nowMillis).orEmpty()
        }.getOrElse { emptyMap() }

        val storageManager = context.getSystemService(StorageStatsManager::class.java)
        val visibleApps = context.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL,
            )
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }

        var storageUnavailableCount = 0
        val entries = visibleApps
            .map { applicationInfo ->
                val packageName = applicationInfo.packageName
                val usage = usageByPackage[packageName]
                val storageBytes = runCatching {
                    storageManager?.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        packageName,
                        Process.myUserHandle(),
                    )?.let { stats ->
                        saturatingAdd(
                            saturatingAdd(stats.appBytes, stats.dataBytes),
                            stats.cacheBytes,
                        )
                    }
                }.getOrNull()
                if (storageBytes == null) storageUnavailableCount++

                AppUsageEntry(
                    packageName = packageName,
                    label = applicationInfo.loadLabel(context.packageManager).toString(),
                    foregroundMillis = usage?.totalTimeInForeground ?: 0L,
                    lastUsedMillis = usage?.lastTimeUsed?.takeIf { it > 0L },
                    storageBytes = storageBytes,
                )
            }
            .sortedWith(
                compareByDescending<AppUsageEntry> { it.foregroundMillis }
                    .thenByDescending { it.storageBytes ?: 0L }
                    .thenBy { it.label.lowercase(Locale.ROOT) },
            )
            .take(MAX_APPS)

        return AppIntelligenceResult(
            scannedAtMillis = nowMillis,
            windowStartMillis = windowStartMillis,
            windowDays = WINDOW_DAYS,
            appsConsidered = visibleApps.size,
            storageUnavailableCount = storageUnavailableCount,
            entries = entries,
        )
    }

    private fun saturatingAdd(first: Long, second: Long): Long = when {
        first < 0L || second < 0L -> 0L
        Long.MAX_VALUE - first < second -> Long.MAX_VALUE
        else -> first + second
    }
}

object AppIntelligencePresentation {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ROOT)

    fun summary(result: AppIntelligenceResult): String = buildString {
        append("${result.entries.size} εφαρμογές στην αναφορά · ")
        append("παράθυρο ${result.windowDays} ημερών")
        if (result.storageUnavailableCount > 0) {
            append(" · χώρος μη διαθέσιμος για ${result.storageUnavailableCount}")
        }
    }

    fun foregroundLabel(millis: Long): String {
        if (millis < 60_000L) return "<1 λεπτό"
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) {
            if (minutes == 0L) "$hours ώρες" else "$hours ώρες $minutes λεπτά"
        } else {
            "$minutes λεπτά"
        }
    }

    fun storageLabel(bytes: Long?): String = bytes
        ?.let { "Χώρος ${StorageIntelligencePresentation.storageSize(it)}" }
        ?: "Χώρος μη διαθέσιμος"

    fun lastUsedLabel(millis: Long?, zoneId: ZoneId = ZoneId.systemDefault()): String =
        millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).format(dateFormatter) }
            ?: "Τελευταία χρήση: μη διαθέσιμη"

    fun limitation(): String =
        "Τα στατιστικά είναι συγκεντρωτικά και εξαρτώνται από Android/OEM· δεν είναι μέτρηση CPU ή μπαταρίας ανά εφαρμογή."
}
