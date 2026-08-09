package dev.devicecontrolcenter

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoundationTest {
    @Test
    fun formatsBinaryGigabytesWithoutMarketingRounding() {
        assertEquals("8.00 GiB", SnapshotPresentation.gib(8L * 1_073_741_824L))
    }

    @Test
    fun formatsDecimalGigabytesAsAdvertisedCapacity() {
        assertEquals("4.00 GB", SnapshotPresentation.decimalGb(4_000_000_000L))
    }

    @Test
    fun accessStateIsExplicit() {
        assertEquals("Ενεργή", SnapshotPresentation.accessLabel(true))
        assertEquals("Δεν έχει δοθεί", SnapshotPresentation.accessLabel(false))
    }

    @Test
    fun severeThermalStatusDescribesThrottlingRatherThanTemperature() {
        assertEquals(
            "Σοβαρός θερμικός περιορισμός",
            SnapshotPresentation.thermalLabel(PowerManager.THERMAL_STATUS_SEVERE),
        )
    }

    @Test
    fun thermalEnvelopeMakesTheSevereThresholdExplicit() {
        assertEquals(
            "Χρήση θερμικού ορίου: 102% · Το 100% είναι το κατώφλι σοβαρού περιορισμού",
            SnapshotPresentation.thermalEnvelopeLabel(1.02f),
        )
    }

    @Test
    fun memoryDetailSeparatesAdvertisedFromKernelAccessibleRam() {
        assertEquals(
            "Εγκατεστημένη φυσική RAM 4.00 GB (3.73 GiB) · " +
                "Προσβάσιμη στον πυρήνα 3.53 GiB · Όριο χαμηλής μνήμης 0.42 GiB",
            SnapshotPresentation.memoryDetail(
                advertisedBytes = 4_000_000_000L,
                kernelBytes = 3_790_259_077L,
                thresholdBytes = 450_971_566L,
            ),
        )
    }

    @Test
    fun batterySnapshotUsesBroadcastValuesAndOptionalProperties() {
        val snapshot = BatterySnapshotReader.fromRaw(
            level = 80,
            scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = 2,
            temperatureTenthsCelsius = 275,
            voltageMillivolts = 4_200,
            readIntProperty = { property ->
                when (property) {
                    android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW -> 1_500_000
                    android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE -> 1_200_000
                    android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER -> 4_000_000
                    else -> Int.MIN_VALUE
                }
            },
            readLongProperty = { property ->
                if (property == android.os.BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) {
                    12_345_678_901L
                } else {
                    Long.MIN_VALUE
                }
            },
        )

        assertEquals(80, snapshot.levelPercent)
        assertEquals(android.os.BatteryManager.BATTERY_STATUS_CHARGING, snapshot.status)
        assertEquals(2, snapshot.plugged)
        assertEquals(27.5, snapshot.temperatureCelsius, 0.001)
        assertEquals(4_200, snapshot.voltageMillivolts)
        assertEquals(1_500_000, snapshot.currentNowMicroamps)
        assertEquals(1_200_000, snapshot.currentAverageMicroamps)
        assertEquals(4_000_000, snapshot.chargeCounterMicroampHours)
        assertEquals(12_345_678_901L, snapshot.energyCounterNanowattHours)
    }

    @Test
    fun batterySnapshotKeepsUnsupportedPropertiesUnavailable() {
        val snapshot = BatterySnapshotReader.fromRaw(
            level = null,
            scale = null,
            status = null,
            plugged = null,
            temperatureTenthsCelsius = null,
            voltageMillivolts = 0,
            readIntProperty = { Int.MIN_VALUE },
            readLongProperty = { Long.MIN_VALUE },
        )

        assertNull(snapshot.levelPercent)
        assertNull(snapshot.status)
        assertNull(snapshot.plugged)
        assertNull(snapshot.temperatureCelsius)
        assertNull(snapshot.voltageMillivolts)
        assertNull(snapshot.currentNowMicroamps)
        assertNull(snapshot.currentAverageMicroamps)
        assertNull(snapshot.chargeCounterMicroampHours)
        assertNull(snapshot.energyCounterNanowattHours)
        assertEquals("Μη διαθέσιμο ποσοστό", BatteryPresentation.levelLabel(null))
        assertEquals("Τάση: μη διαθέσιμη", BatteryPresentation.voltageLabel(null))
    }

    @Test
    fun batteryPresentationUsesExplicitUnitsAndSourceLabels() {
        assertEquals("Φορτίζει", BatteryPresentation.statusLabel(android.os.BatteryManager.BATTERY_STATUS_CHARGING))
        assertEquals("Τροφοδοσία USB", BatteryPresentation.pluggedLabel(android.os.BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals("Θερμοκρασία: 27.5 °C", BatteryPresentation.temperatureLabel(27.5))
        assertEquals("Τάση: 4200 mV (4.200 V)", BatteryPresentation.voltageLabel(4_200))
        assertEquals("Ρεύμα: -500000 μA (-500.00 mA)", BatteryPresentation.currentLabel(-500_000))
        assertEquals("Μετρητής φόρτισης: 4000000 μAh (4000.00 mAh)", BatteryPresentation.chargeCounterLabel(4_000_000))
        assertEquals("Μετρητής ενέργειας: 12345678901 nWh (12.346 Wh)", BatteryPresentation.energyCounterLabel(12_345_678_901L))
    }
}
