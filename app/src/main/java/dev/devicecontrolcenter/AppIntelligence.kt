package dev.devicecontrolcenter

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager

private fun addNullableBytes(first: Long?, second: Long?): Long? {
    if (first == null && second == null) return null
    val left = first ?: 0L
    val right = second ?: 0L
    return when {
        right <= 0L -> left
        Long.MAX_VALUE - left < right -> Long.MAX_VALUE
        else -> left + right
    }
}

data class AppRecord(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val lastUsedTime: Long?,
    val foregroundMillis: Long?,
    val apkBytes: Long?,
    val dataBytes: Long?,
    val cacheBytes: Long?,
    val wifiBytes: Long?,
    val mobileBytes: Long?,
    val uid: Int,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val hasLauncher: Boolean,
    val hasService: Boolean,
    val hasIcon: Boolean,
    val requestedPermissions: List<String>,
    val networkAttributionStatus: NetworkAttributionStatus = NetworkAttributionStatus.UNKNOWN,
    val networkPackageCountForUid: Int? = null,
) {
    val totalStorageBytes: Long?
        get() = listOfNotNull(apkBytes, dataBytes, cacheBytes).takeIf { it.isNotEmpty() }
            ?.fold(0L) { sum, value -> sum.saturatingAdd(value) }

    val totalNetworkBytes: Long?
        get() = listOfNotNull(wifiBytes, mobileBytes).takeIf { it.isNotEmpty() }
            ?.fold(0L) { sum, value -> sum.saturatingAdd(value) }

    private fun Long.saturatingAdd(value: Long): Long = when {
        value <= 0L -> this
        Long.MAX_VALUE - this < value -> Long.MAX_VALUE
        else -> this + value
    }
}

data class AppCatalogResult(
    val apps: List<AppRecord> = emptyList(),
    val capturedAtMillis: Long? = null,
    val hasUsageAccess: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Converts the App Center's existing package snapshot into the bounded local
 * history contract. No label, permission, path, or user content is persisted.
 */
fun AppRecord.toAppUsageHistorySample(
    capturedAtMillis: Long,
    hasUsageAccess: Boolean,
): AppUsageHistorySample = AppUsageHistorySample(
    packageName = packageName,
    capturedAtMillis = capturedAtMillis,
    lastUsedAtMillis = lastUsedTime.takeIf { usageAvailability(hasUsageAccess) == AppUsageMetricAvailability.AVAILABLE },
    foregroundMillis = foregroundMillis.takeIf { usageAvailability(hasUsageAccess) == AppUsageMetricAvailability.AVAILABLE },
    apkBytes = apkBytes,
    dataBytes = dataBytes,
    cacheBytes = cacheBytes,
    wifiBytes = wifiBytes.takeIf {
        networkAvailability(hasUsageAccess) in setOf(
            AppUsageMetricAvailability.AVAILABLE,
            AppUsageMetricAvailability.PARTIAL,
        )
    },
    mobileBytes = mobileBytes.takeIf {
        networkAvailability(hasUsageAccess) in setOf(
            AppUsageMetricAvailability.AVAILABLE,
            AppUsageMetricAvailability.PARTIAL,
        )
    },
    usageAvailability = usageAvailability(hasUsageAccess),
    storageAvailability = storageAvailability(),
    networkAvailability = networkAvailability(hasUsageAccess),
)

fun AppCatalogResult.toAppUsageHistorySamples(
    capturedAtMillis: Long? = null,
): List<AppUsageHistorySample> = apps.map {
    it.toAppUsageHistorySample(
        capturedAtMillis = capturedAtMillis ?: this.capturedAtMillis ?: System.currentTimeMillis(),
        hasUsageAccess = hasUsageAccess,
    )
}

private fun AppRecord.usageAvailability(hasUsageAccess: Boolean): AppUsageMetricAvailability = when {
    !hasUsageAccess -> AppUsageMetricAvailability.UNAVAILABLE_USAGE_ACCESS
    lastUsedTime != null || foregroundMillis != null -> AppUsageMetricAvailability.AVAILABLE
    else -> AppUsageMetricAvailability.UNAVAILABLE_API
}

private fun AppRecord.storageAvailability(): AppUsageMetricAvailability = when {
    apkBytes != null || dataBytes != null || cacheBytes != null -> AppUsageMetricAvailability.AVAILABLE
    else -> AppUsageMetricAvailability.UNAVAILABLE_API
}

private fun AppRecord.networkAvailability(hasUsageAccess: Boolean): AppUsageMetricAvailability = when {
    !hasUsageAccess -> AppUsageMetricAvailability.UNAVAILABLE_USAGE_ACCESS
    networkAttributionStatus == NetworkAttributionStatus.ATTRIBUTED_TO_UNIQUE_UID ->
        AppUsageMetricAvailability.AVAILABLE
    networkAttributionStatus == NetworkAttributionStatus.PARTIALLY_AVAILABLE_UNIQUE_UID ->
        AppUsageMetricAvailability.PARTIAL
    networkAttributionStatus == NetworkAttributionStatus.SHARED_UID_UNAVAILABLE ->
        AppUsageMetricAvailability.UNAVAILABLE_SHARED_UID
    networkAttributionStatus == NetworkAttributionStatus.UNAVAILABLE_OR_RESTRICTED ->
        AppUsageMetricAvailability.UNAVAILABLE_API
    wifiBytes != null || mobileBytes != null -> AppUsageMetricAvailability.AVAILABLE
    else -> AppUsageMetricAvailability.NOT_COLLECTED
}

data class AppUsageData(
    val lastUsedTime: Long?,
    val foregroundMillis: Long?,
)

object AppCatalogReader {
    fun read(context: Context, nowMillis: Long = System.currentTimeMillis()): AppCatalogResult {
        return runCatching {
            val packageManager = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS.toLong() or
                PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
            val packages = packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
            val usageAccess = hasUsageAccess(context)
            val usage = if (usageAccess) UsageReader.read(context, nowMillis) else emptyMap()
            val networkQuery = if (usageAccess) {
                NetworkStatsReader.queryUidUsageResult(
                    context,
                    nowMillis - NETWORK_PERIOD_MILLIS,
                    nowMillis,
                )
            } else {
                NetworkUidUsageQuery.unavailable()
            }
            val packageUids = packages.mapNotNull { packageInfo ->
                packageInfo.applicationInfo?.let { packageInfo.packageName to it.uid }
            }.toMap()
            val network = NetworkStatsReader.attributeToPackages(packageUids, networkQuery)
            val storageStats = context.getSystemService(StorageStatsManager::class.java)

            AppCatalogResult(
                apps = packages
                    .asSequence()
                    .mapNotNull { packageInfo ->
                        packageInfo.toRecord(
                            context = context,
                            usage = usage[packageInfo.packageName],
                            network = network[packageInfo.packageName],
                            storageStats = storageStats,
                        )
                    }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                    .toList(),
                capturedAtMillis = nowMillis,
                hasUsageAccess = usageAccess,
            )
        }.getOrElse { error ->
            AppCatalogResult(
                capturedAtMillis = nowMillis,
                hasUsageAccess = hasUsageAccess(context),
                errorMessage = error.message ?: "Η λίστα εφαρμογών δεν είναι διαθέσιμη τώρα.",
            )
        }
    }

    private fun PackageInfo.toRecord(
        context: Context,
        usage: AppUsageData?,
        network: NetworkPackageUsage?,
        storageStats: StorageStatsManager?,
    ): AppRecord? {
        val applicationInfo = applicationInfo ?: return null
        val packageManager = context.packageManager
        val label = runCatching { applicationInfo.loadLabel(packageManager).toString() }
            .getOrDefault(packageName)
            .ifBlank { packageName }
        val stats = storageStats?.let {
            runCatching {
                it.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    packageName,
                    Process.myUserHandle(),
                )
            }.getOrNull()
        }
        return AppRecord(
            packageName = packageName,
            label = label,
            versionName = versionName,
            versionCode = longVersionCode,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            lastUsedTime = usage?.lastUsedTime,
            foregroundMillis = usage?.foregroundMillis,
            apkBytes = stats?.appBytes,
            dataBytes = stats?.dataBytes,
            cacheBytes = stats?.cacheBytes,
            wifiBytes = addNullableBytes(network?.wifiReceivedBytes, network?.wifiSentBytes),
            mobileBytes = addNullableBytes(network?.mobileReceivedBytes, network?.mobileSentBytes),
            uid = applicationInfo.uid,
            isSystem = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isEnabled = applicationInfo.enabled,
            hasLauncher = packageManager.getLaunchIntentForPackage(packageName) != null,
            hasService = runCatching {
                packageManager.queryIntentServices(
                    Intent().setPackage(packageName),
                    PackageManager.ResolveInfoFlags.of(0L),
                ).isNotEmpty()
            }.getOrDefault(false),
            hasIcon = applicationInfo.icon != 0,
            requestedPermissions = requestedPermissions?.toList().orEmpty(),
            networkAttributionStatus = network?.attributionStatus ?: NetworkAttributionStatus.UNAVAILABLE_OR_RESTRICTED,
            networkPackageCountForUid = network?.packageCountForUid,
        )
    }

    private fun Long.saturatingAdd(value: Long): Long = when {
        value <= 0L -> this
        Long.MAX_VALUE - this < value -> Long.MAX_VALUE
        else -> this + value
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps?.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }
}

object UsageReader {
    private const val LOOKBACK_MILLIS = 30L * 24L * 60L * 60L * 1_000L

    fun read(context: Context, nowMillis: Long): Map<String, AppUsageData> = runCatching {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        manager.queryAndAggregateUsageStats(nowMillis - LOOKBACK_MILLIS, nowMillis)
            .mapValues { (_, value) ->
                AppUsageData(
                    lastUsedTime = value.lastTimeUsed.takeIf { it > 0L },
                    foregroundMillis = value.totalTimeInForeground.takeIf { it >= 0L },
                )
            }
    }.getOrDefault(emptyMap())
}

object AppPresentation {
    fun storageLabel(app: AppRecord): String = app.totalStorageBytes
        ?.let(StorageIntelligencePresentation::storageSize)
        ?: "Μη διαθέσιμος χώρος"

    fun networkLabel(app: AppRecord): String = when (app.networkAttributionStatus) {
        NetworkAttributionStatus.SHARED_UID_UNAVAILABLE -> {
            val count = app.networkPackageCountForUid?.takeIf { it > 1 }
            if (count == null) "Μη διαθέσιμη κίνηση: κοινόχρηστο UID"
            else "Μη διαθέσιμη κίνηση: κοινόχρηστο UID ($count εφαρμογές)"
        }

        NetworkAttributionStatus.PARTIALLY_AVAILABLE_UNIQUE_UID -> app.totalNetworkBytes
            ?.let { "${StorageIntelligencePresentation.storageSize(it)} · μερικά στοιχεία" }
            ?: "Μη διαθέσιμη κίνηση: μερικά στοιχεία"

        else -> app.totalNetworkBytes
            ?.let(StorageIntelligencePresentation::storageSize)
            ?: "Μη διαθέσιμη κίνηση"
    }

    fun networkPartLabel(bytes: Long?): String = bytes
        ?.let(StorageIntelligencePresentation::storageSize)
        ?: "Μη διαθέσιμα"

    fun lastUsedLabel(app: AppRecord): String = app.lastUsedTime?.let {
        SnapshotPresentation.capturedTimeLabel(it)
    } ?: "Δεν υπάρχει Usage Access"

    fun foregroundLabel(app: AppRecord): String = app.foregroundMillis?.let {
        val minutes = it / 60_000L
        if (minutes < 60L) "$minutes λεπτά" else "${minutes / 60L} ώρ. ${minutes % 60L} λ."
    }
        ?: "Μη διαθέσιμος χρόνος"

    fun typeLabel(app: AppRecord): String = when {
        !app.isEnabled -> "Απενεργοποιημένη"
        app.isSystem -> "Συστήματος"
        else -> "Χρήστη"
    }

    fun permissionLabel(permission: String): String = permission.substringAfterLast('.')
        .lowercase()
        .replace('_', ' ')
}
