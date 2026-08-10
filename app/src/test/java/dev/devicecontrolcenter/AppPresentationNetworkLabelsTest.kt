package dev.devicecontrolcenter

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPresentationNetworkLabelsTest {
    @Test
    fun presentsWifiMobilePartsAndTheirCombinedTotal() {
        val app = appRecord(wifiBytes = 1_024L, mobileBytes = 2_048L)

        assertEquals("1.0 KiB", AppPresentation.networkPartLabel(app.wifiBytes))
        assertEquals("2.0 KiB", AppPresentation.networkPartLabel(app.mobileBytes))
        assertEquals("3.0 KiB", AppPresentation.networkLabel(app))
    }

    @Test
    fun distinguishesUnavailableNetworkCountersFromZero() {
        val app = appRecord(wifiBytes = null, mobileBytes = null)

        assertEquals("Μη διαθέσιμα", AppPresentation.networkPartLabel(app.wifiBytes))
        assertEquals("Μη διαθέσιμα", AppPresentation.networkPartLabel(app.mobileBytes))
        assertEquals("Μη διαθέσιμη κίνηση", AppPresentation.networkLabel(app))
    }

    private fun appRecord(wifiBytes: Long?, mobileBytes: Long?): AppRecord = AppRecord(
        packageName = "dev.example.app",
        label = "Example",
        versionName = "1.0",
        versionCode = 1L,
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        lastUsedTime = null,
        foregroundMillis = null,
        apkBytes = null,
        dataBytes = null,
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
    )
}
