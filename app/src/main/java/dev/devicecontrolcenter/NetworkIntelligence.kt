package dev.devicecontrolcenter

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import java.util.Locale

enum class NetworkDataSource {
    NETWORK_STATS,
    UNAVAILABLE_OR_RESTRICTED,
}

/**
 * Describes how confidently traffic can be attributed to one installed package.
 * NetworkStats reports traffic for a UID, not for each package in a shared UID.
 */
enum class NetworkAttributionStatus {
    ATTRIBUTED_TO_UNIQUE_UID,
    PARTIALLY_AVAILABLE_UNIQUE_UID,
    SHARED_UID_UNAVAILABLE,
    UNAVAILABLE_OR_RESTRICTED,
    UNKNOWN,
}

data class NetworkSnapshot(
    val periodStartMillis: Long,
    val capturedAtMillis: Long,
    val wifiReceivedBytes: Long?,
    val wifiSentBytes: Long?,
    val mobileReceivedBytes: Long?,
    val mobileSentBytes: Long?,
    val source: NetworkDataSource,
) {
    companion object {
        fun unavailable(nowMillis: Long = System.currentTimeMillis()): NetworkSnapshot = NetworkSnapshot(
            periodStartMillis = nowMillis - NETWORK_PERIOD_MILLIS,
            capturedAtMillis = nowMillis,
            wifiReceivedBytes = null,
            wifiSentBytes = null,
            mobileReceivedBytes = null,
            mobileSentBytes = null,
            source = NetworkDataSource.UNAVAILABLE_OR_RESTRICTED,
        )
    }

    val totalReceivedBytes: Long?
        get() = addNullable(wifiReceivedBytes, mobileReceivedBytes)

    val totalSentBytes: Long?
        get() = addNullable(wifiSentBytes, mobileSentBytes)

    val totalBytes: Long?
        get() = addNullable(totalReceivedBytes, totalSentBytes)

    private fun addNullable(first: Long?, second: Long?): Long? = when {
        first == null && second == null -> null
        else -> (first ?: 0L).saturatingAdd(second ?: 0L)
    }

    private fun Long.saturatingAdd(value: Long): Long = when {
        value <= 0L -> this
        Long.MAX_VALUE - this < value -> Long.MAX_VALUE
        else -> this + value
    }
}

data class NetworkUidUsage(
    val uid: Int,
    val wifiReceivedBytes: Long,
    val wifiSentBytes: Long,
    val mobileReceivedBytes: Long,
    val mobileSentBytes: Long,
    val wifiAvailable: Boolean = true,
    val mobileAvailable: Boolean = true,
) {
    val receivedBytes: Long
        get() = wifiReceivedBytes.saturatingAdd(mobileReceivedBytes)

    val sentBytes: Long
        get() = wifiSentBytes.saturatingAdd(mobileSentBytes)

    val totalBytes: Long
        get() = receivedBytes.saturatingAdd(sentBytes)

    private fun Long.saturatingAdd(value: Long): Long = when {
        value <= 0L -> this
        Long.MAX_VALUE - this < value -> Long.MAX_VALUE
        else -> this + value
    }
}

data class NetworkUidUsageQuery(
    val usages: Map<Int, NetworkUidUsage>,
    val wifiAvailable: Boolean,
    val mobileAvailable: Boolean,
    val source: NetworkDataSource,
) {
    companion object {
        fun unavailable(): NetworkUidUsageQuery = NetworkUidUsageQuery(
            usages = emptyMap(),
            wifiAvailable = false,
            mobileAvailable = false,
            source = NetworkDataSource.UNAVAILABLE_OR_RESTRICTED,
        )
    }
}

data class NetworkPackageUsage(
    val uid: Int,
    val wifiReceivedBytes: Long?,
    val wifiSentBytes: Long?,
    val mobileReceivedBytes: Long?,
    val mobileSentBytes: Long?,
    val attributionStatus: NetworkAttributionStatus,
    val packageCountForUid: Int,
)

const val NETWORK_PERIOD_MILLIS: Long = 24L * 60L * 60L * 1_000L

object NetworkStatsReader {
    fun read(context: Context, nowMillis: Long = System.currentTimeMillis()): NetworkSnapshot {
        if (!hasUsageAccess(context)) return NetworkSnapshot.unavailable(nowMillis)

        val manager = context.getSystemService(NetworkStatsManager::class.java)
            ?: return NetworkSnapshot.unavailable(nowMillis)
        val start = nowMillis - NETWORK_PERIOD_MILLIS
        val wifi = queryDeviceSummary(manager, ConnectivityManager.TYPE_WIFI, start, nowMillis)
        val mobile = queryDeviceSummary(manager, ConnectivityManager.TYPE_MOBILE, start, nowMillis)

        return NetworkSnapshot(
            periodStartMillis = start,
            capturedAtMillis = nowMillis,
            wifiReceivedBytes = wifi?.first,
            wifiSentBytes = wifi?.second,
            mobileReceivedBytes = mobile?.first,
            mobileSentBytes = mobile?.second,
            source = if (wifi != null || mobile != null) {
                NetworkDataSource.NETWORK_STATS
            } else {
                NetworkDataSource.UNAVAILABLE_OR_RESTRICTED
            },
        )
    }

    fun queryUidUsage(
        context: Context,
        startMillis: Long,
        endMillis: Long,
    ): Map<Int, NetworkUidUsage> = queryUidUsageResult(context, startMillis, endMillis).usages

    fun queryUidUsageResult(
        context: Context,
        startMillis: Long,
        endMillis: Long,
    ): NetworkUidUsageQuery {
        if (!hasUsageAccess(context)) return NetworkUidUsageQuery.unavailable()
        val manager = context.getSystemService(NetworkStatsManager::class.java)
            ?: return NetworkUidUsageQuery.unavailable()
        val wifi = queryUidSummary(manager, ConnectivityManager.TYPE_WIFI, startMillis, endMillis)
        val mobile = queryUidSummary(manager, ConnectivityManager.TYPE_MOBILE, startMillis, endMillis)
        val wifiAvailable = wifi != null
        val mobileAvailable = mobile != null
        if (!wifiAvailable && !mobileAvailable) return NetworkUidUsageQuery.unavailable()

        val uids = buildSet {
            addAll(wifi.orEmpty().keys)
            addAll(mobile.orEmpty().keys)
        }
        return NetworkUidUsageQuery(
            usages = uids.associateWith { uid ->
                NetworkUidUsage(
                    uid = uid,
                    wifiReceivedBytes = wifi?.get(uid)?.first ?: 0L,
                    wifiSentBytes = wifi?.get(uid)?.second ?: 0L,
                    mobileReceivedBytes = mobile?.get(uid)?.first ?: 0L,
                    mobileSentBytes = mobile?.get(uid)?.second ?: 0L,
                    wifiAvailable = wifiAvailable,
                    mobileAvailable = mobileAvailable,
                )
            },
            wifiAvailable = wifiAvailable,
            mobileAvailable = mobileAvailable,
            source = NetworkDataSource.NETWORK_STATS,
        )
    }

    /**
     * Converts UID-level counters to package-level counters only when that UID
     * belongs to one visible installed package. Shared UIDs remain unavailable
     * instead of assigning the same bytes to every package sharing the UID.
     */
    fun attributeToPackages(
        packageUids: Map<String, Int>,
        query: NetworkUidUsageQuery,
    ): Map<String, NetworkPackageUsage> {
        val packagesByUid = packageUids.entries.groupBy(
            keySelector = { it.value },
            valueTransform = { it.key },
        )

        return packageUids.mapValues { (_, uid) ->
            val packageCount = packagesByUid[uid].orEmpty().size
            when {
                query.source != NetworkDataSource.NETWORK_STATS -> NetworkPackageUsage(
                    uid = uid,
                    wifiReceivedBytes = null,
                    wifiSentBytes = null,
                    mobileReceivedBytes = null,
                    mobileSentBytes = null,
                    attributionStatus = NetworkAttributionStatus.UNAVAILABLE_OR_RESTRICTED,
                    packageCountForUid = packageCount,
                )

                packageCount > 1 -> NetworkPackageUsage(
                    uid = uid,
                    wifiReceivedBytes = null,
                    wifiSentBytes = null,
                    mobileReceivedBytes = null,
                    mobileSentBytes = null,
                    attributionStatus = NetworkAttributionStatus.SHARED_UID_UNAVAILABLE,
                    packageCountForUid = packageCount,
                )

                else -> {
                    val usage = query.usages[uid] ?: NetworkUidUsage(
                        uid = uid,
                        wifiReceivedBytes = 0L,
                        wifiSentBytes = 0L,
                        mobileReceivedBytes = 0L,
                        mobileSentBytes = 0L,
                        wifiAvailable = query.wifiAvailable,
                        mobileAvailable = query.mobileAvailable,
                    )
                    val wifiIsAvailable = query.wifiAvailable && usage.wifiAvailable
                    val mobileIsAvailable = query.mobileAvailable && usage.mobileAvailable
                    NetworkPackageUsage(
                        uid = uid,
                        wifiReceivedBytes = usage.wifiReceivedBytes.takeIf { wifiIsAvailable },
                        wifiSentBytes = usage.wifiSentBytes.takeIf { wifiIsAvailable },
                        mobileReceivedBytes = usage.mobileReceivedBytes.takeIf { mobileIsAvailable },
                        mobileSentBytes = usage.mobileSentBytes.takeIf { mobileIsAvailable },
                        attributionStatus = if (wifiIsAvailable && mobileIsAvailable) {
                            NetworkAttributionStatus.ATTRIBUTED_TO_UNIQUE_UID
                        } else {
                            NetworkAttributionStatus.PARTIALLY_AVAILABLE_UNIQUE_UID
                        },
                        packageCountForUid = packageCount,
                    )
                }
            }
        }
    }

    private fun queryDeviceSummary(
        manager: NetworkStatsManager,
        networkType: Int,
        startMillis: Long,
        endMillis: Long,
    ): Pair<Long, Long>? = runCatching {
        val bucket = manager.querySummaryForDevice(networkType, null, startMillis, endMillis)
        bucket.rxBytes to bucket.txBytes
    }.getOrNull()

    private fun queryUidSummary(
        manager: NetworkStatsManager,
        networkType: Int,
        startMillis: Long,
        endMillis: Long,
    ): Map<Int, Pair<Long, Long>>? {
        val stats = runCatching {
            manager.querySummary(networkType, null, startMillis, endMillis)
        }.getOrNull() ?: return null
        val totals = linkedMapOf<Int, LongArray>()
        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                if (!stats.getNextBucket(bucket)) break
                val values = totals.getOrPut(bucket.uid) { LongArray(2) }
                values[0] = values[0].saturatingAdd(bucket.rxBytes)
                values[1] = values[1].saturatingAdd(bucket.txBytes)
            }
            return totals.mapValues { (_, values) -> values[0] to values[1] }
        } catch (_: RuntimeException) {
            return null
        } finally {
            stats.close()
        }
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps?.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun Long.saturatingAdd(value: Long): Long = when {
        value <= 0L -> this
        Long.MAX_VALUE - this < value -> Long.MAX_VALUE
        else -> this + value
    }
}

object NetworkPresentation {
    fun bytes(bytes: Long?): String = bytes?.let { StorageIntelligencePresentation.storageSize(it) } ?: "Μη διαθέσιμα"

    fun period(snapshot: NetworkSnapshot): String = if (snapshot.source == NetworkDataSource.NETWORK_STATS) {
        "Συγκεντρωτικά στοιχεία τελευταίων 24 ωρών"
    } else {
        "Δεν υπάρχει αξιόπιστο ιστορικό χωρίς Usage Access"
    }

    fun source(snapshot: NetworkSnapshot): String = when (snapshot.source) {
        NetworkDataSource.NETWORK_STATS -> "Πηγή: Android NetworkStats"
        NetworkDataSource.UNAVAILABLE_OR_RESTRICTED -> "Πηγή: μη διαθέσιμη ή περιορισμένη"
    }

    fun totalLabel(snapshot: NetworkSnapshot): String = snapshot.totalBytes?.let {
        String.format(Locale.ROOT, "%s συνολικά", bytes(it))
    } ?: "Μη διαθέσιμη συνολική κίνηση"
}
