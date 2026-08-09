package dev.devicecontrolcenter

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Locale
import kotlin.math.roundToInt

data class BatterySnapshot(
    val levelPercent: Int?,
    val status: Int?,
    val plugged: Int?,
    val temperatureCelsius: Double?,
    val voltageMillivolts: Int?,
    val currentNowMicroamps: Int?,
    val currentAverageMicroamps: Int?,
    val chargeCounterMicroampHours: Int?,
    val energyCounterNanowattHours: Long?,
)

object BatterySnapshotReader {
    fun read(context: Context): BatterySnapshot {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val broadcast = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )

        return fromRaw(
            level = broadcast?.intExtraOrNull(BatteryManager.EXTRA_LEVEL),
            scale = broadcast?.intExtraOrNull(BatteryManager.EXTRA_SCALE),
            status = broadcast?.intExtraOrNull(BatteryManager.EXTRA_STATUS),
            plugged = broadcast?.intExtraOrNull(BatteryManager.EXTRA_PLUGGED),
            temperatureTenthsCelsius = broadcast?.intExtraOrNull(BatteryManager.EXTRA_TEMPERATURE),
            voltageMillivolts = broadcast?.intExtraOrNull(BatteryManager.EXTRA_VOLTAGE),
            readIntProperty = { property ->
                batteryManager?.getIntProperty(property) ?: Int.MIN_VALUE
            },
            readLongProperty = { property ->
                batteryManager?.getLongProperty(property) ?: Long.MIN_VALUE
            },
        )
    }

    internal fun fromRaw(
        level: Int?,
        scale: Int?,
        status: Int?,
        plugged: Int?,
        temperatureTenthsCelsius: Int?,
        voltageMillivolts: Int?,
        readIntProperty: (Int) -> Int,
        readLongProperty: (Int) -> Long,
    ): BatterySnapshot {
        val levelPercent = levelPercent(level, scale)
            ?: optionalIntProperty(readIntProperty, BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }

        return BatterySnapshot(
            levelPercent = levelPercent,
            status = status ?: optionalIntProperty(
                readIntProperty,
                BatteryManager.BATTERY_PROPERTY_STATUS,
            ),
            plugged = plugged,
            temperatureCelsius = temperatureTenthsCelsius?.div(10.0),
            voltageMillivolts = voltageMillivolts?.takeIf { it > 0 },
            currentNowMicroamps = optionalIntProperty(
                readIntProperty,
                BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,
            ),
            currentAverageMicroamps = optionalIntProperty(
                readIntProperty,
                BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE,
            ),
            chargeCounterMicroampHours = optionalIntProperty(
                readIntProperty,
                BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER,
            ),
            energyCounterNanowattHours = optionalLongProperty(
                readLongProperty,
                BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER,
            ),
        )
    }

    private fun levelPercent(level: Int?, scale: Int?): Int? {
        if (level == null || scale == null || level < 0 || scale <= 0) return null
        return (level * 100.0 / scale).roundToInt().coerceIn(0, 100)
    }

    private fun optionalIntProperty(readProperty: (Int) -> Int, property: Int): Int? =
        readProperty(property).takeUnless { it == Int.MIN_VALUE }

    private fun optionalLongProperty(readProperty: (Int) -> Long, property: Int): Long? =
        readProperty(property).takeUnless { it == Long.MIN_VALUE }

    private fun Intent.intExtraOrNull(name: String): Int? =
        getIntExtra(name, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
}

object BatteryPresentation {
    fun levelLabel(percent: Int?): String = percent?.let { "$it%" } ?: "Μη διαθέσιμο ποσοστό"

    fun statusLabel(status: Int?): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Φορτίζει"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Εκφορτίζεται"
        BatteryManager.BATTERY_STATUS_FULL -> "Πλήρης"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Συνδεδεμένη αλλά δεν φορτίζει"
        BatteryManager.BATTERY_STATUS_UNKNOWN -> "Άγνωστη κατάσταση"
        null -> "Μη διαθέσιμη κατάσταση"
        else -> "Άγνωστη κατάσταση ($status)"
    }

    fun pluggedLabel(plugged: Int?): String = when (plugged) {
        0 -> "Από μπαταρία"
        BatteryManager.BATTERY_PLUGGED_AC -> "Τροφοδοσία AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "Τροφοδοσία USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Ασύρματη φόρτιση"
        BatteryManager.BATTERY_PLUGGED_DOCK -> "Τροφοδοσία από βάση"
        null -> "Μη διαθέσιμη πηγή"
        else -> "Άγνωστη πηγή ($plugged)"
    }

    fun temperatureLabel(celsius: Double?): String = celsius?.let {
        String.format(Locale.ROOT, "Θερμοκρασία: %.1f °C", it)
    } ?: "Θερμοκρασία: μη διαθέσιμη"

    fun voltageLabel(millivolts: Int?): String = millivolts?.let {
        String.format(Locale.ROOT, "Τάση: %d mV (%.3f V)", it, it / 1000.0)
    } ?: "Τάση: μη διαθέσιμη"

    fun currentLabel(microamps: Int?): String = microamps?.let {
        String.format(Locale.ROOT, "Ρεύμα: %d μA (%.2f mA)", it, it / 1000.0)
    } ?: "Ρεύμα: μη διαθέσιμο"

    fun chargeCounterLabel(microampHours: Int?): String = microampHours?.let {
        String.format(Locale.ROOT, "Μετρητής φόρτισης: %d μAh (%.2f mAh)", it, it / 1000.0)
    } ?: "Μετρητής φόρτισης: μη διαθέσιμος"

    fun energyCounterLabel(nanowattHours: Long?): String = nanowattHours?.let {
        String.format(Locale.ROOT, "Μετρητής ενέργειας: %d nWh (%.3f Wh)", it, it / 1_000_000_000.0)
    } ?: "Μετρητής ενέργειας: μη διαθέσιμος"

    fun technicalDetail(snapshot: BatterySnapshot): String = listOf(
        "Κατάσταση: ${statusLabel(snapshot.status)}",
        "Πηγή: ${pluggedLabel(snapshot.plugged)}",
        temperatureLabel(snapshot.temperatureCelsius),
        voltageLabel(snapshot.voltageMillivolts),
        "Στιγμιαίο ${currentLabel(snapshot.currentNowMicroamps)}",
        "Μέσο ${currentLabel(snapshot.currentAverageMicroamps)}",
        chargeCounterLabel(snapshot.chargeCounterMicroampHours),
        energyCounterLabel(snapshot.energyCounterNanowattHours),
    ).joinToString(" · ")
}
