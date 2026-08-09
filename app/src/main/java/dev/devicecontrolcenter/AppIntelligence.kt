package dev.devicecontrolcenter

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
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
    val isSystemApp: Boolean = false,
    val isLaunchable: Boolean = true,
    val isEnabled: Boolean = true,
)

data class AppIntelligenceResult(
    val scannedAtMillis: Long,
    val windowStartMillis: Long,
    val windowDays: Int,
    val appsConsidered: Int,
    val storageUnavailableCount: Int,
    val entries: List<AppUsageEntry>,
    val userAppsCount: Int = 0,
    val systemAppsCount: Int = 0,
    val notUsedCount: Int = 0,
    val includeSystemApps: Boolean = false,
    val wasTruncated: Boolean = false,
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
    const val MAX_APPS = 1_000
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

    fun scan(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        includeSystemApps: Boolean = false,
    ): AppIntelligenceResult {
        check(UsageAccessReader.isGranted(context)) {
            "Δεν έχει ενεργοποιηθεί η πρόσβαση στα στατιστικά χρήσης."
        }

        val windowStartMillis = nowMillis - WINDOW_DAYS * MILLIS_PER_DAY
        val packageManager = context.packageManager
        val usageManager = context.getSystemService(UsageStatsManager::class.java)
        val usageByPackage = runCatching {
            usageManager?.queryAndAggregateUsageStats(windowStartMillis, nowMillis).orEmpty()
        }.getOrElse { emptyMap() }
        val storageManager = context.getSystemService(StorageStatsManager::class.java)

        val installedApps = runCatching {
            packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
        }.getOrElse { emptyList() }
            .filter { it.packageName != context.packageName }

        val userApps = installedApps.filterNot(::isSystemApp)
        val systemApps = installedApps.filter(::isSystemApp)
        val eligibleApps = if (includeSystemApps) installedApps else userApps
        val wasTruncated = eligibleApps.size > MAX_APPS
        var storageUnavailableCount = 0

        val entries = eligibleApps
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
                    label = runCatching {
                        applicationInfo.loadLabel(packageManager).toString()
                    }.getOrDefault(packageName),
                    foregroundMillis = usage?.totalTimeInForeground ?: 0L,
                    lastUsedMillis = usage?.lastTimeUsed?.takeIf { it > 0L },
                    storageBytes = storageBytes,
                    isSystemApp = isSystemApp(applicationInfo),
                    isLaunchable = packageManager.getLaunchIntentForPackage(packageName) != null,
                    isEnabled = applicationInfo.enabled,
                )
            }
            .sortedWith(
                compareBy<AppUsageEntry> { it.isSystemApp }
                    .thenByDescending { it.foregroundMillis }
                    .thenByDescending { it.storageBytes ?: 0L }
                    .thenBy { it.label.lowercase(Locale.ROOT) },
            )
            .take(MAX_APPS)

        return AppIntelligenceResult(
            scannedAtMillis = nowMillis,
            windowStartMillis = windowStartMillis,
            windowDays = WINDOW_DAYS,
            appsConsidered = eligibleApps.size,
            storageUnavailableCount = storageUnavailableCount,
            entries = entries,
            userAppsCount = userApps.size,
            systemAppsCount = systemApps.size,
            notUsedCount = entries.count { it.foregroundMillis <= 0L && it.lastUsedMillis == null },
            includeSystemApps = includeSystemApps,
            wasTruncated = wasTruncated,
        )
    }

    private fun isSystemApp(applicationInfo: ApplicationInfo): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
            applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0

    private fun saturatingAdd(first: Long, second: Long): Long = when {
        first < 0L || second < 0L -> 0L
        Long.MAX_VALUE - first < second -> Long.MAX_VALUE
        else -> first + second
    }
}

object AppIntelligencePresentation {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ROOT)

    fun summary(result: AppIntelligenceResult): String = buildString {
        if (result.userAppsCount == 0 && result.systemAppsCount == 0) {
            append(result.entries.size)
            append(" εφαρμογές στην αναφορά · ")
        } else {
            append(result.entries.size)
            append(" εφαρμογές στην αναφορά · ")
            append(result.userAppsCount)
            append(" εφαρμογές χρήστη")
            if (result.includeSystemApps) {
                append(" · ")
                append(result.systemAppsCount)
                append(" εφαρμογές συστήματος")
            }
            append(" · ")
            append(result.notUsedCount)
            append(" χωρίς χρήση στο παράθυρο")
            if (result.wasTruncated) {
                append(" · εμφανίζονται οι πρώτες ")
                append(AppIntelligenceScanner.MAX_APPS)
            }
            append(" · ")
        }
        append("παράθυρο ")
        append(result.windowDays)
        append(" ημερών")
        if (result.storageUnavailableCount > 0) {
            append(" · χώρος μη διαθέσιμος για ")
            append(result.storageUnavailableCount)
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
        ?.let { "Χώρος " + StorageIntelligencePresentation.storageSize(it) }
        ?: "Χώρος μη διαθέσιμος"

    fun lastUsedLabel(millis: Long?, zoneId: ZoneId = ZoneId.systemDefault()): String =
        millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).format(dateFormatter) }
            ?: "Τελευταία χρήση: μη διαθέσιμη"

    fun scopeLabel(entry: AppUsageEntry): String = when {
        !entry.isEnabled -> "Απενεργοποιημένη"
        entry.isSystemApp -> "Εφαρμογή συστήματος"
        else -> "Εφαρμογή χρήστη"
    }

    fun launchabilityLabel(entry: AppUsageEntry): String =
        if (entry.isLaunchable) "Εκκινήσιμη" else "Υπηρεσία/χωρίς εικονίδιο"

    fun zeroUsageLabel(entry: AppUsageEntry): String =
        if (entry.foregroundMillis <= 0L && entry.lastUsedMillis == null) {
            "Χωρίς καταγεγραμμένη χρήση στο παράθυρο"
        } else {
            "Υπάρχει καταγεγραμμένη χρήση στο παράθυρο"
        }

    fun limitation(): String =
        "Η αναφορά περιλαμβάνει πακέτα εφαρμογών που βλέπει το Android, μαζί με εφαρμογές χωρίς εικονίδιο. Τα στατιστικά χρήσης και χώρου εξαρτώνται από Android/OEM· δεν είναι μέτρηση CPU ή μπαταρίας ανά εφαρμογή."
}
