package dev.devicecontrolcenter

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.math.roundToInt

enum class BatteryVoltageSource {
    ANDROID_BROADCAST,
    SYSFS_POWER_SUPPLY,
    UNAVAILABLE_OR_REJECTED,
}

data class BatterySnapshot(
    val levelPercent: Int?,
    val status: Int?,
    val plugged: Int?,
    val temperatureCelsius: Double?,
    val voltageMillivolts: Int?,
    val voltageSource: BatteryVoltageSource,
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
            Context.RECEIVER_EXPORTED,
        )

        return fromRaw(
            level = broadcast?.intExtraOrNull(BatteryManager.EXTRA_LEVEL),
            scale = broadcast?.intExtraOrNull(BatteryManager.EXTRA_SCALE),
            status = broadcast?.intExtraOrNull(BatteryManager.EXTRA_STATUS),
            plugged = broadcast?.intExtraOrNull(BatteryManager.EXTRA_PLUGGED),
            temperatureTenthsCelsius = broadcast?.intExtraOrNull(BatteryManager.EXTRA_TEMPERATURE),
            voltageMillivolts = broadcast?.intExtraOrNull(BatteryManager.EXTRA_VOLTAGE),
            readIntProperty = { property ->
                runCatching { batteryManager?.getIntProperty(property) ?: Int.MIN_VALUE }
                    .getOrDefault(Int.MIN_VALUE)
            },
            readLongProperty = { property ->
                runCatching { batteryManager?.getLongProperty(property) ?: Long.MIN_VALUE }
                    .getOrDefault(Long.MIN_VALUE)
            },
            readSysfsVoltageMillivolts = BatteryVoltageReader::readSysfsVoltageMillivolts,
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
        readSysfsVoltageMillivolts: () -> Int? = { null },
    ): BatterySnapshot {
        val levelPercent = levelPercent(level, scale)
            ?: optionalIntProperty(readIntProperty, BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
        val voltageReading = BatteryVoltageReader.read(
            broadcastMillivolts = voltageMillivolts,
            readSysfsVoltageMillivolts = readSysfsVoltageMillivolts,
        )

        return BatterySnapshot(
            levelPercent = levelPercent,
            status = normalizeStatus(status) ?: normalizeStatus(
                optionalIntProperty(readIntProperty, BatteryManager.BATTERY_PROPERTY_STATUS),
            ),
            plugged = plugged,
            temperatureCelsius = normalizeTemperature(temperatureTenthsCelsius),
            voltageMillivolts = voltageReading?.millivolts,
            voltageSource = voltageReading?.source ?: BatteryVoltageSource.UNAVAILABLE_OR_REJECTED,
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
            )?.takeIf { it >= 0 },
            energyCounterNanowattHours = optionalLongProperty(
                readLongProperty,
                BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER,
            )?.takeIf { it >= 0 },
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

    private fun normalizeTemperature(tenthsCelsius: Int?): Double? = tenthsCelsius
        ?.takeIf { it in MIN_TEMPERATURE_TENTHS..MAX_TEMPERATURE_TENTHS }
        ?.div(10.0)

    private fun normalizeStatus(status: Int?): Int? = status?.takeIf {
        it == BatteryManager.BATTERY_STATUS_UNKNOWN ||
            it == BatteryManager.BATTERY_STATUS_CHARGING ||
            it == BatteryManager.BATTERY_STATUS_DISCHARGING ||
            it == BatteryManager.BATTERY_STATUS_NOT_CHARGING ||
            it == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun Intent.intExtraOrNull(name: String): Int? =
        getIntExtra(name, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }

    private const val MIN_TEMPERATURE_TENTHS = -500
    private const val MAX_TEMPERATURE_TENTHS = 1_000
}

internal data class BatteryVoltageReading(
    val millivolts: Int,
    val source: BatteryVoltageSource,
)

object BatteryVoltageReader {
    private const val MIN_PLAUSIBLE_MILLIVOLTS = 2_000
    private const val MAX_PLAUSIBLE_MILLIVOLTS = 6_000
    private const val POWER_SUPPLY_ROOT = "/sys/class/power_supply"

    internal fun read(
        broadcastMillivolts: Int?,
        readSysfsVoltageMillivolts: () -> Int?,
    ): BatteryVoltageReading? {
        normalizeBroadcastMillivolts(broadcastMillivolts)?.let {
            return BatteryVoltageReading(it, BatteryVoltageSource.ANDROID_BROADCAST)
        }

        readSysfsVoltageMillivolts()?.let {
            return BatteryVoltageReading(it, BatteryVoltageSource.SYSFS_POWER_SUPPLY)
        }

        return null
    }

    internal fun normalizeBroadcastMillivolts(value: Int?): Int? = value?.takeIf {
        it in MIN_PLAUSIBLE_MILLIVOLTS..MAX_PLAUSIBLE_MILLIVOLTS
    }

    internal fun normalizeSysfsMicrovolts(value: Long?): Int? = value
        ?.takeIf { it in MIN_PLAUSIBLE_MILLIVOLTS * 1_000L..MAX_PLAUSIBLE_MILLIVOLTS * 1_000L }
        ?.div(1_000L)
        ?.toInt()

    internal fun readSysfsVoltageMillivolts(): Int? {
        val root = File(POWER_SUPPLY_ROOT)
        val preferredBattery = File(root, "battery")
        if (preferredBattery.isDirectory) {
            readBatteryVoltage(preferredBattery)?.let { return it }
        }

        val directories = runCatching { Files.newDirectoryStream(root.toPath()) }.getOrNull() ?: return null
        directories.use { children ->
            for (path in children) {
                val directory = path.toFile()
                if (!directory.isDirectory || directory.name.equals("battery", ignoreCase = true)) continue
                readBatteryVoltage(directory)?.let { return it }
            }
        }
        return null
    }

    private fun readBatteryVoltage(directory: File): Int? {
        val type = readText(File(directory, "type"))?.trim()
        val isBattery = directory.name.equals("battery", ignoreCase = true) ||
            type.equals("Battery", ignoreCase = true)
        if (!isBattery) return null

        val rawMicrovolts = readText(File(directory, "voltage_now"))
            ?.trim()
            ?.toLongOrNull()
        return normalizeSysfsMicrovolts(rawMicrovolts)
    }

    private fun readText(file: File): String? = runCatching { file.readText() }.getOrNull()
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

    fun voltageSourceLabel(source: BatteryVoltageSource): String = when (source) {
        BatteryVoltageSource.ANDROID_BROADCAST -> "Android battery broadcast"
        BatteryVoltageSource.SYSFS_POWER_SUPPLY -> "power_supply sysfs"
        BatteryVoltageSource.UNAVAILABLE_OR_REJECTED -> "μη διαθέσιμη ή απορρίφθηκε ως μη αξιόπιστη"
    }

    fun voltageLabel(millivolts: Int?, source: BatteryVoltageSource? = null): String {
        val value = millivolts?.let {
            String.format(Locale.ROOT, "Τάση: %d mV (%.3f V)", it, it / 1000.0)
        } ?: "Τάση: μη διαθέσιμη"
        return source?.let { "$value · Πηγή τάσης: ${voltageSourceLabel(it)}" } ?: value
    }

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
        voltageLabel(snapshot.voltageMillivolts, snapshot.voltageSource),
        "Στιγμιαίο ${currentLabel(snapshot.currentNowMicroamps)}",
        "Μέσο ${currentLabel(snapshot.currentAverageMicroamps)}",
        chargeCounterLabel(snapshot.chargeCounterMicroampHours),
        energyCounterLabel(snapshot.energyCounterNanowattHours),
    ).joinToString(" · ")
}
