package dev.devicecontrolcenter

const val PERSONAL_BASELINE_MIN_SAMPLES = 5

enum class PersonalBaselineState {
    INSUFFICIENT_DATA,
    READY,
}

enum class PersonalBaselineRelation {
    WITHIN_RECENT_RANGE,
    BELOW_RECENT_RANGE,
    ABOVE_RECENT_RANGE,
}

data class PersonalBaselineNumericMetric(
    val currentValue: Long,
    val medianValue: Long,
    val minimumValue: Long,
    val maximumValue: Long,
    val relation: PersonalBaselineRelation,
)

data class PersonalBaselineResult(
    val state: PersonalBaselineState,
    val referenceSampleCount: Int,
    val minimumSamples: Int,
    val currentIsLowMemory: Boolean,
    val previousLowMemoryCount: Int,
    val currentThermalStatus: Int,
    val highestPreviousThermalStatus: Int?,
    val availableMemory: PersonalBaselineNumericMetric?,
    val availableStorage: PersonalBaselineNumericMetric?,
)

object PersonalBaseline {
    fun evaluate(
        current: DeviceSnapshot,
        currentCapturedAtMillis: Long?,
        history: List<SnapshotHistoryEntity>,
        minimumSamples: Int = PERSONAL_BASELINE_MIN_SAMPLES,
    ): PersonalBaselineResult {
        require(minimumSamples > 0) { "minimumSamples must be positive" }

        val referenceEntries = history
            .asSequence()
            .filter { entry ->
                currentCapturedAtMillis == null || entry.capturedAtMillis != currentCapturedAtMillis
            }
            .filter { entry ->
                entry.availableMemoryBytes >= 0L && entry.availableStorageBytes >= 0L
            }
            .toList()
        val referenceSampleCount = referenceEntries.size
        val state = if (referenceSampleCount >= minimumSamples) {
            PersonalBaselineState.READY
        } else {
            PersonalBaselineState.INSUFFICIENT_DATA
        }

        return PersonalBaselineResult(
            state = state,
            referenceSampleCount = referenceSampleCount,
            minimumSamples = minimumSamples,
            currentIsLowMemory = current.isLowMemory,
            previousLowMemoryCount = referenceEntries.count { it.isLowMemory },
            currentThermalStatus = current.thermalStatus,
            highestPreviousThermalStatus = referenceEntries.maxOfOrNull { it.thermalStatus },
            availableMemory = if (state == PersonalBaselineState.READY && current.availableMemoryBytes >= 0L) {
                numericMetric(
                    currentValue = current.availableMemoryBytes,
                    referenceValues = referenceEntries.map { it.availableMemoryBytes },
                )
            } else {
                null
            },
            availableStorage = if (state == PersonalBaselineState.READY && current.availableStorageBytes >= 0L) {
                numericMetric(
                    currentValue = current.availableStorageBytes,
                    referenceValues = referenceEntries.map { it.availableStorageBytes },
                )
            } else {
                null
            },
        )
    }

    private fun numericMetric(
        currentValue: Long,
        referenceValues: List<Long>,
    ): PersonalBaselineNumericMetric {
        val sorted = referenceValues.sorted()
        val minimum = sorted.first()
        val maximum = sorted.last()
        return PersonalBaselineNumericMetric(
            currentValue = currentValue,
            medianValue = median(sorted),
            minimumValue = minimum,
            maximumValue = maximum,
            relation = when {
                currentValue < minimum -> PersonalBaselineRelation.BELOW_RECENT_RANGE
                currentValue > maximum -> PersonalBaselineRelation.ABOVE_RECENT_RANGE
                else -> PersonalBaselineRelation.WITHIN_RECENT_RANGE
            },
        )
    }

    private fun median(sortedValues: List<Long>): Long {
        val middle = sortedValues.size / 2
        if (sortedValues.size % 2 == 1) return sortedValues[middle]

        val lower = sortedValues[middle - 1]
        val upper = sortedValues[middle]
        return lower + (upper - lower) / 2L
    }
}

object PersonalBaselinePresentation {
    fun statusLabel(state: PersonalBaselineState): String = when (state) {
        PersonalBaselineState.INSUFFICIENT_DATA -> "Ανεπαρκές ιστορικό"
        PersonalBaselineState.READY -> "Read-only σύγκριση"
    }

    fun summary(result: PersonalBaselineResult): String = when (result.state) {
        PersonalBaselineState.INSUFFICIENT_DATA ->
            "Χρειάζονται τουλάχιστον ${result.minimumSamples} προηγούμενα στιγμιότυπα· βρέθηκαν ${result.referenceSampleCount}."

        PersonalBaselineState.READY ->
            "Σύγκριση της τρέχουσας λήψης με ${result.referenceSampleCount} προηγούμενα τοπικά στιγμιότυπα."
    }

    fun relationLabel(relation: PersonalBaselineRelation): String = when (relation) {
        PersonalBaselineRelation.WITHIN_RECENT_RANGE -> "Εντός πρόσφατου εύρους"
        PersonalBaselineRelation.BELOW_RECENT_RANGE -> "Κάτω από πρόσφατο εύρος"
        PersonalBaselineRelation.ABOVE_RECENT_RANGE -> "Πάνω από πρόσφατο εύρος"
    }

    fun memoryEvidence(metric: PersonalBaselineNumericMetric): String =
        numericEvidence(metric) { bytes -> SnapshotPresentation.gib(bytes) }

    fun storageEvidence(metric: PersonalBaselineNumericMetric): String =
        numericEvidence(metric) { bytes -> SnapshotPresentation.gib(bytes) }

    fun lowMemoryEvidence(result: PersonalBaselineResult): String =
        "Τώρα: ${flagLabel(result.currentIsLowMemory)} · " +
            "${result.previousLowMemoryCount}/${result.referenceSampleCount} προηγούμενα με Android low-memory flag"

    fun thermalEvidence(result: PersonalBaselineResult): String {
        val previous = result.highestPreviousThermalStatus
            ?.let { status -> OverviewPresentation.thermalShortLabel(status) }
            ?: "Μη διαθέσιμη"
        return "Τώρα: ${OverviewPresentation.thermalShortLabel(result.currentThermalStatus)} · " +
            "υψηλότερη προηγούμενη: $previous"
    }

    fun limitation(): String =
        "Σύγκριση ενδείξεων της ίδιας συσκευής· δεν είναι score ή διάγνωση, δεν αποδεικνύει αιτία ή υγεία και δεν εκτελεί ενέργεια."

    private fun numericEvidence(
        metric: PersonalBaselineNumericMetric,
        formatter: (Long) -> String,
    ): String =
        "Τώρα ${formatter(metric.currentValue)} · διάμεσος ${formatter(metric.medianValue)} · " +
            "εύρος ${formatter(metric.minimumValue)}–${formatter(metric.maximumValue)}"

    private fun flagLabel(value: Boolean): String = if (value) "ναι" else "όχι"
}
