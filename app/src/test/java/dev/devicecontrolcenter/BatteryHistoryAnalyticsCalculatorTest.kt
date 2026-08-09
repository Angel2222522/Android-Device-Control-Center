package dev.devicecontrolcenter

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryHistoryAnalyticsCalculatorTest {
    @Test
    fun calculatesObservedChargingDurationFromChronologicalChargingIntervals() {
        val entries = listOf(
            sample(
                capturedAtMillis = 90 * MINUTE,
                levelPercent = 80,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            ),
            sample(
                capturedAtMillis = 0L,
                levelPercent = 20,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
            ),
            sample(
                capturedAtMillis = 30 * MINUTE,
                levelPercent = 50,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
            ),
        )

        val analytics = BatteryHistoryAnalyticsCalculator.calculate(entries)

        assertEquals(90 * MINUTE, analytics.observedChargingMillis)
        assertEquals(2, analytics.chargingSamples)
        assertEquals("1 ώρ. 30 λ. παρατηρούμενης φόρτισης", analytics.observedChargingLabel)
    }

    @Test
    fun estimatesEquivalentFullCyclesFromAbsoluteLevelMovement() {
        val analytics = BatteryHistoryAnalyticsCalculator.calculate(
            listOf(
                sample(capturedAtMillis = 0L, levelPercent = 20),
                sample(capturedAtMillis = 1 * HOUR, levelPercent = 60),
                sample(capturedAtMillis = 2 * HOUR, levelPercent = 40),
            ),
        )

        assertEquals(0.30, analytics.equivalentFullCycles ?: Double.NaN, 0.000_001)
        assertEquals("≈ 0.30 ισοδύναμοι κύκλοι", analytics.cycleLabel)
    }

    @Test
    fun estimatesCapacityFromLatestFuelGaugeCounterAndLevel() {
        val analytics = BatteryHistoryAnalyticsCalculator.calculate(
            listOf(
                sample(
                    capturedAtMillis = 0L,
                    levelPercent = 40,
                    chargeCounterMicroampHours = 1_600_000,
                ),
                sample(
                    capturedAtMillis = HOUR,
                    levelPercent = 50,
                    chargeCounterMicroampHours = 2_000_000,
                ),
            ),
        )

        assertEquals(4_000, analytics.estimatedCapacityMah)
        assertEquals("≈ 4000 mAh από fuel-gauge counter", analytics.capacityLabel)
    }

    @Test
    fun reportsOnlyHighTemperatureSamplesAsAlerts() {
        val analytics = BatteryHistoryAnalyticsCalculator.calculate(
            listOf(
                sample(capturedAtMillis = 0L, temperatureCelsius = 44.9),
                sample(capturedAtMillis = HOUR, temperatureCelsius = 45.0),
                sample(capturedAtMillis = 2 * HOUR, temperatureCelsius = 46.2),
            ),
        )

        assertEquals(
            listOf(
                "Υψηλή θερμοκρασία δείγματος: 45.0 °C",
                "Υψηλή θερμοκρασία δείγματος: 46.2 °C",
            ),
            analytics.alerts,
        )
    }

    @Test
    fun exposesConservativeInsufficientDataState() {
        val analytics = BatteryHistoryAnalyticsCalculator.calculate(
            listOf(
                sample(
                    capturedAtMillis = 1L,
                    levelPercent = null,
                    status = null,
                    temperatureCelsius = null,
                    chargeCounterMicroampHours = null,
                ),
            ),
        )

        assertEquals(0L, analytics.observedChargingMillis)
        assertEquals(0, analytics.chargingSamples)
        assertNull(analytics.equivalentFullCycles)
        assertNull(analytics.estimatedCapacityMah)
        assertEquals(emptyList<String>(), analytics.alerts)
        assertEquals("Κάτω από 1 λεπτό παρατήρησης", analytics.observedChargingLabel)
        assertEquals("Ανεπαρκή δεδομένα για κύκλους", analytics.cycleLabel)
        assertEquals(
            "Δεν είναι διαθέσιμος αξιόπιστος counter χωρητικότητας",
            analytics.capacityLabel,
        )
    }

    private fun sample(
        capturedAtMillis: Long,
        levelPercent: Int? = 50,
        status: Int? = BatteryManager.BATTERY_STATUS_DISCHARGING,
        temperatureCelsius: Double? = 30.0,
        chargeCounterMicroampHours: Int? = null,
    ): BatterySampleEntity = BatterySampleEntity(
        capturedAtMillis = capturedAtMillis,
        levelPercent = levelPercent,
        status = status,
        temperatureCelsius = temperatureCelsius,
        currentNowMicroamps = null,
        chargeCounterMicroampHours = chargeCounterMicroampHours,
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
