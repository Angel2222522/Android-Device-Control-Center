package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageHistoryTest {
    @Test
    fun appRecordMappingKeepsExistingAppCenterMetricsAndZeroes() {
        val sample = appRecord(
            lastUsedTime = 123_000L,
            foregroundMillis = 4_000L,
            apkBytes = 0L,
            dataBytes = 2_048L,
            wifiBytes = 0L,
            mobileBytes = 512L,
            networkStatus = NetworkAttributionStatus.ATTRIBUTED_TO_UNIQUE_UID,
        ).toAppUsageHistorySample(capturedAtMillis = 500_000L, hasUsageAccess = true)

        assertEquals("dev.example.app", sample.packageName)
        assertEquals(500_000L, sample.capturedAtMillis)
        assertEquals(123_000L, sample.lastUsedAtMillis)
        assertEquals(4_000L, sample.foregroundMillis)
        assertEquals(0L, sample.apkBytes)
        assertEquals(0L, sample.wifiBytes)
        assertEquals(AppUsageMetricAvailability.AVAILABLE, sample.usageAvailability)
        assertEquals(AppUsageMetricAvailability.AVAILABLE, sample.networkAvailability)
    }

    @Test
    fun missingUsageAccessDoesNotPersistUsageOrNetworkAsAvailable() {
        val sample = appRecord(
            lastUsedTime = 123_000L,
            foregroundMillis = 4_000L,
            wifiBytes = 8_192L,
            mobileBytes = 1_024L,
            networkStatus = NetworkAttributionStatus.ATTRIBUTED_TO_UNIQUE_UID,
        ).toAppUsageHistorySample(capturedAtMillis = 500_000L, hasUsageAccess = false)

        assertNull(sample.lastUsedAtMillis)
        assertNull(sample.foregroundMillis)
        assertNull(sample.wifiBytes)
        assertNull(sample.mobileBytes)
        assertEquals(AppUsageMetricAvailability.UNAVAILABLE_USAGE_ACCESS, sample.usageAvailability)
        assertEquals(AppUsageMetricAvailability.UNAVAILABLE_USAGE_ACCESS, sample.networkAvailability)
    }

    @Test
    fun sharedUidTrafficRemainsUnavailableInsteadOfBeingDuplicated() {
        val sample = appRecord(
            wifiBytes = 12_000L,
            mobileBytes = 3_000L,
            networkStatus = NetworkAttributionStatus.SHARED_UID_UNAVAILABLE,
        ).toAppUsageHistorySample(capturedAtMillis = 500_000L, hasUsageAccess = true)

        assertNull(sample.wifiBytes)
        assertNull(sample.mobileBytes)
        assertEquals(AppUsageMetricAvailability.UNAVAILABLE_SHARED_UID, sample.networkAvailability)
    }

    @Test
    fun entityRoundTripPreservesUnavailableStates() {
        val source = AppUsageHistorySample(
            packageName = "dev.example.app",
            capturedAtMillis = 42L,
            lastUsedAtMillis = null,
            foregroundMillis = null,
            apkBytes = null,
            dataBytes = 0L,
            cacheBytes = null,
            wifiBytes = null,
            mobileBytes = null,
            usageAvailability = AppUsageMetricAvailability.UNAVAILABLE_API,
            storageAvailability = AppUsageMetricAvailability.AVAILABLE,
            networkAvailability = AppUsageMetricAvailability.NOT_COLLECTED,
        )

        assertEquals(source, source.toEntity().toDomain())
    }

    @Test
    fun unknownPersistedAvailabilityFailsClosedAsNotCollected() {
        val entity = AppUsageHistoryEntity(
            packageName = "dev.example.app",
            capturedAtMillis = 42L,
            lastUsedAtMillis = null,
            foregroundMillis = null,
            apkBytes = null,
            dataBytes = null,
            cacheBytes = null,
            wifiBytes = null,
            mobileBytes = null,
            usageAvailability = "FUTURE_VALUE",
            storageAvailability = "FUTURE_VALUE",
            networkAvailability = "FUTURE_VALUE",
        )

        val sample = entity.toDomain()

        assertEquals(AppUsageMetricAvailability.NOT_COLLECTED, sample.usageAvailability)
        assertEquals(AppUsageMetricAvailability.NOT_COLLECTED, sample.storageAvailability)
        assertEquals(AppUsageMetricAvailability.NOT_COLLECTED, sample.networkAvailability)
    }

    @Test
    fun appHistoryLimitIsAlwaysPositiveAndBounded() {
        assertEquals(1, 0.boundedAppUsageLimit())
        assertEquals(APP_USAGE_HISTORY_LIMIT, Int.MAX_VALUE.boundedAppUsageLimit())
        assertEquals(1, 0.boundedAppUsagePackageLimit())
        assertEquals(
            APP_USAGE_HISTORY_PER_PACKAGE_LIMIT,
            Int.MAX_VALUE.boundedAppUsagePackageLimit(),
        )
        assertTrue(APP_USAGE_HISTORY_LIMIT > APP_USAGE_HISTORY_PER_PACKAGE_LIMIT)
    }

    @Test
    fun perPackageRetentionKeepsMultipleSamplesForTwoPackages() {
        val samples = listOf(
            sample("dev.one", 1L),
            sample("dev.one", 2L),
            sample("dev.one", 3L),
            sample("dev.two", 1L),
            sample("dev.two", 2L),
            sample("dev.two", 3L),
        )

        val retained = AppUsageHistoryRetention.latestPerPackage(samples, keep = 2)

        assertEquals(
            listOf(2L, 3L),
            retained.filter { it.packageName == "dev.one" }
                .map(AppUsageHistorySample::capturedAtMillis)
                .sorted(),
        )
        assertEquals(
            listOf(2L, 3L),
            retained.filter { it.packageName == "dev.two" }
                .map(AppUsageHistorySample::capturedAtMillis)
                .sorted(),
        )
    }

    private fun appRecord(
        lastUsedTime: Long? = null,
        foregroundMillis: Long? = null,
        apkBytes: Long? = null,
        dataBytes: Long? = null,
        wifiBytes: Long? = null,
        mobileBytes: Long? = null,
        networkStatus: NetworkAttributionStatus = NetworkAttributionStatus.UNKNOWN,
    ): AppRecord = AppRecord(
        packageName = "dev.example.app",
        label = "Example",
        versionName = "1.0",
        versionCode = 1L,
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        lastUsedTime = lastUsedTime,
        foregroundMillis = foregroundMillis,
        apkBytes = apkBytes,
        dataBytes = dataBytes,
        cacheBytes = null,
        wifiBytes = wifiBytes,
        mobileBytes = mobileBytes,
        uid = 10_003,
        isSystem = false,
        isEnabled = true,
        hasLauncher = true,
        hasService = false,
        hasIcon = true,
        requestedPermissions = emptyList(),
        networkAttributionStatus = networkStatus,
    )

    private fun sample(packageName: String, capturedAtMillis: Long): AppUsageHistorySample =
        AppUsageHistorySample(
            packageName = packageName,
            capturedAtMillis = capturedAtMillis,
            lastUsedAtMillis = null,
            foregroundMillis = 1_000L,
            apkBytes = 0L,
            dataBytes = null,
            cacheBytes = null,
            wifiBytes = 0L,
            mobileBytes = null,
            usageAvailability = AppUsageMetricAvailability.AVAILABLE,
            storageAvailability = AppUsageMetricAvailability.AVAILABLE,
            networkAvailability = AppUsageMetricAvailability.AVAILABLE,
        )
}
