package dev.devicecontrolcenter

import android.os.PowerManager
import java.util.Locale

enum class OverviewTone {
    NEUTRAL,
    INFO,
    WARNING,
    CRITICAL,
    UNAVAILABLE,
}

data class OverviewStatus(
    val tone: OverviewTone,
    val label: String,
    val headline: String,
    val detail: String,
)

object OverviewPresentation {
    fun status(report: DiagnosisReport): OverviewStatus {
        val highestSeverity = report.findings.maxByOrNull { severityRank(it) }?.severity
        val conditionCount = report.findings.count { it.type == DiagnosisFindingType.CONDITION }
        val dataQualityCount = report.findings.count { it.type == DiagnosisFindingType.DATA_QUALITY }

        return when (highestSeverity) {
            DiagnosisSeverity.CRITICAL -> OverviewStatus(
                tone = OverviewTone.CRITICAL,
                label = "Κρίσιμη ένδειξη",
                headline = "Χρειάζεται άμεση προσοχή",
                detail = findingSummary(conditionCount, dataQualityCount),
            )

            DiagnosisSeverity.WARNING -> OverviewStatus(
                tone = OverviewTone.WARNING,
                label = "Προειδοποίηση",
                headline = "Χρειάζεται προσοχή",
                detail = findingSummary(conditionCount, dataQualityCount),
            )

            DiagnosisSeverity.INFO -> OverviewStatus(
                tone = if (conditionCount > 0) OverviewTone.INFO else OverviewTone.UNAVAILABLE,
                label = if (conditionCount > 0) "Ενημερωτική ένδειξη" else "Ποιότητα δεδομένων",
                headline = if (conditionCount > 0) "Χωρίς ενεργή πίεση" else "Ένα σήμα δεν είναι διαθέσιμο",
                detail = findingSummary(conditionCount, dataQualityCount),
            )

            null -> OverviewStatus(
                tone = OverviewTone.NEUTRAL,
                label = "Χωρίς ενεργή ένδειξη",
                headline = "Η εικόνα είναι καθαρή",
                detail = "Δεν εντοπίστηκε ενεργή κατάσταση από τους ελεγμένους κανόνες.",
            )
        }
    }

    fun thermalShortLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Χωρίς περιορισμό"
        PowerManager.THERMAL_STATUS_LIGHT -> "Ελαφρύς περιορισμός"
        PowerManager.THERMAL_STATUS_MODERATE -> "Μέτριος περιορισμός"
        PowerManager.THERMAL_STATUS_SEVERE -> "Σοβαρός περιορισμός"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Κρίσιμος περιορισμός"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Έκτακτη ανάγκη"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Επικείμενος τερματισμός"
        else -> "Άγνωστη κατάσταση"
    }

    fun thermalTone(status: Int): OverviewTone = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> OverviewTone.NEUTRAL
        PowerManager.THERMAL_STATUS_LIGHT,
        PowerManager.THERMAL_STATUS_MODERATE,
        -> OverviewTone.INFO

        PowerManager.THERMAL_STATUS_SEVERE -> OverviewTone.WARNING

        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN,
        -> OverviewTone.CRITICAL

        else -> OverviewTone.UNAVAILABLE
    }

    fun cpuValue(snapshot: CpuSnapshot): String = snapshot.activityPercent?.let {
        String.format(Locale.ROOT, "%.1f%%", it)
    } ?: "Μη διαθέσιμη"

    fun cpuSupport(snapshot: CpuSnapshot): String = when (snapshot.source) {
        CpuActivitySource.PROC_STAT -> buildString {
            append("Συνολικό σήμα συσκευής")
            snapshot.logicalProcessorCount?.let { append(" · $it λογικοί επεξεργαστές") }
        }

        CpuActivitySource.UNAVAILABLE_OR_RESTRICTED -> "Η read-only πηγή δεν είναι διαθέσιμη τώρα"
    }

    fun batterySupport(snapshot: BatterySnapshot): String = listOf(
        BatteryPresentation.statusLabel(snapshot.status),
        snapshot.temperatureCelsius?.let {
            String.format(Locale.ROOT, "%.1f °C", it)
        },
    ).joinToString(" · ")

    private fun findingSummary(conditionCount: Int, dataQualityCount: Int): String = buildList {
        if (conditionCount > 0) add(countLabel(conditionCount, "ενεργή ένδειξη", "ενεργές ενδείξεις"))
        if (dataQualityCount > 0) add(countLabel(dataQualityCount, "πληροφορία ποιότητας δεδομένων", "πληροφορίες ποιότητας δεδομένων"))
    }.joinToString(" · ").ifEmpty {
        "Ελέγχθηκαν οι τρεις διαθέσιμοι κανόνες"
    }

    fun findingSummaryFor(report: DiagnosisReport): String {
        val conditionCount = report.findings.count { it.type == DiagnosisFindingType.CONDITION }
        val dataQualityCount = report.findings.count { it.type == DiagnosisFindingType.DATA_QUALITY }
        return findingSummary(conditionCount, dataQualityCount)
    }

    private fun countLabel(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}"

    private fun severityRank(severity: DiagnosisFinding): Int = when (severity.severity) {
        DiagnosisSeverity.INFO -> 1
        DiagnosisSeverity.WARNING -> 2
        DiagnosisSeverity.CRITICAL -> 3
    }
}
