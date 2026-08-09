package dev.devicecontrolcenter

enum class HistoryTrendState {
    INSUFFICIENT_DATA,
    READY,
}

enum class HistoryTrendDirection {
    INCREASED,
    DECREASED,
    STABLE,
}

data class HistoryTrendMetric(
    val firstValue: Long,
    val currentValue: Long,
    val delta: Long,
    val direction: HistoryTrendDirection,
)

data class HistoryTrendResult(
    val state: HistoryTrendState,
    val referenceSampleCount: Int,
    val minimumSamples: Int,
    val battery: HistoryTrendMetric?,
    val availableStorage: HistoryTrendMetric?,
    val previousThermalRestrictionCount: Int,
    val currentThermalStatus: Int,
)

object HistoryTrends {
    private const val STABLE_BATTERY_DELTA = 2L
    private const val STABLE_BYTES_DELTA = 64L * 1_024L * 1_024L

    fun evaluate(
        current: DeviceSnapshot,
        currentCapturedAtMillis: Long?,
        history: List<SnapshotHistoryEntity>,
        minimumSamples: Int = PERSONAL_BASELINE_MIN_SAMPLES,
    ): HistoryTrendResult {
        require(minimumSamples > 0) { "minimumSamples must be positive" }

        val referenceEntries = history
            .asSequence()
            .filter { entry ->
                currentCapturedAtMillis == null || entry.capturedAtMillis != currentCapturedAtMillis
            }
            .sortedBy { it.capturedAtMillis }
            .toList()

        val batteryValues = referenceEntries.mapNotNull { it.batteryLevelPercent }
        val storageValues = referenceEntries.map { it.availableStorageBytes }
        val state = if (referenceEntries.size >= minimumSamples) {
            HistoryTrendState.READY
        } else {
            HistoryTrendState.INSUFFICIENT_DATA
        }

        return HistoryTrendResult(
            state = state,
            referenceSampleCount = referenceEntries.size,
            minimumSamples = minimumSamples,
            battery = if (state == HistoryTrendState.READY && current.battery.levelPercent != null && batteryValues.isNotEmpty()) {
                metric(
                    firstValue = batteryValues.first().toLong(),
                    currentValue = current.battery.levelPercent.toLong(),
                    stableDelta = STABLE_BATTERY_DELTA,
                )
            } else {
                null
            },
            availableStorage = if (state == HistoryTrendState.READY && storageValues.isNotEmpty()) {
                metric(
                    firstValue = storageValues.first(),
                    currentValue = current.availableStorageBytes,
                    stableDelta = STABLE_BYTES_DELTA,
                )
            } else {
                null
            },
            previousThermalRestrictionCount = referenceEntries.count {
                it.thermalStatus >= android.os.PowerManager.THERMAL_STATUS_LIGHT
            },
            currentThermalStatus = current.thermalStatus,
        )
    }

    private fun metric(
        firstValue: Long,
        currentValue: Long,
        stableDelta: Long,
    ): HistoryTrendMetric {
        val delta = currentValue - firstValue
        val direction = when {
            delta > stableDelta -> HistoryTrendDirection.INCREASED
            delta < -stableDelta -> HistoryTrendDirection.DECREASED
            else -> HistoryTrendDirection.STABLE
        }
        return HistoryTrendMetric(
            firstValue = firstValue,
            currentValue = currentValue,
            delta = delta,
            direction = direction,
        )
    }
}

object HistoryTrendPresentation {
    fun summary(result: HistoryTrendResult): String = when (result.state) {
        HistoryTrendState.INSUFFICIENT_DATA ->
            "Χρειάζονται τουλάχιστον ${result.minimumSamples} προηγούμενα στιγμιότυπα· βρέθηκαν ${result.referenceSampleCount}."

        HistoryTrendState.READY ->
            "Παρατήρηση μεταβολών σε ${result.referenceSampleCount} προηγούμενες επιτυχείς λήψεις."
    }

    fun directionLabel(direction: HistoryTrendDirection): String = when (direction) {
        HistoryTrendDirection.INCREASED -> "Αυξήθηκε"
        HistoryTrendDirection.DECREASED -> "Μειώθηκε"
        HistoryTrendDirection.STABLE -> "Σχεδόν σταθερό"
    }

    fun batteryEvidence(metric: HistoryTrendMetric): String =
        "Από ${metric.firstValue}% σε ${metric.currentValue}% · ${directionLabel(metric.direction)} κατά ${kotlin.math.abs(metric.delta)} μονάδες"

    fun storageEvidence(metric: HistoryTrendMetric): String =
        "Από ${SnapshotPresentation.gib(metric.firstValue)} σε ${SnapshotPresentation.gib(metric.currentValue)} · " +
            "${directionLabel(metric.direction)} κατά ${StorageIntelligencePresentation.storageSize(kotlin.math.abs(metric.delta))}"

    fun thermalEvidence(result: HistoryTrendResult): String =
        "Θερμικός περιορισμός σε ${result.previousThermalRestrictionCount}/${result.referenceSampleCount} προηγούμενες λήψεις · " +
            "τώρα: ${OverviewPresentation.thermalShortLabel(result.currentThermalStatus)}"

    fun limitation(): String =
        "Δεν είναι συνεχής παρακολούθηση: βασίζεται μόνο σε επιτυχείς, χειροκίνητες λήψεις και δεν αποδεικνύει αιτία."
}
