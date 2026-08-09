package dev.devicecontrolcenter

import android.os.PowerManager

enum class DiagnosisSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

enum class DiagnosisFindingType {
    CONDITION,
    DATA_QUALITY,
}

data class DiagnosisFinding(
    val ruleId: String,
    val ruleVersion: Int,
    val type: DiagnosisFindingType,
    val severity: DiagnosisSeverity,
    val title: String,
    val explanation: String,
    val evidence: String,
    val limitation: String? = null,
)

data class DiagnosisReport(
    val engineVersion: Int,
    val evaluatedRuleIds: List<String>,
    val findings: List<DiagnosisFinding>,
)

object DeviceDiagnosisEngine {
    private const val ENGINE_VERSION = 1
    private const val RULE_VERSION = 1
    private const val MEMORY_RULE_ID = "memory.low_state"
    private const val THERMAL_RULE_ID = "thermal.current_status"
    private const val BATTERY_RULE_ID = "battery.voltage_quality"

    private val evaluatedRules = listOf(
        MEMORY_RULE_ID,
        THERMAL_RULE_ID,
        BATTERY_RULE_ID,
    )

    fun analyze(snapshot: DeviceSnapshot): DiagnosisReport {
        val findings = buildList {
            if (snapshot.isLowMemory) {
                add(
                    DiagnosisFinding(
                        ruleId = MEMORY_RULE_ID,
                        ruleVersion = RULE_VERSION,
                        type = DiagnosisFindingType.CONDITION,
                        severity = DiagnosisSeverity.WARNING,
                        title = "Ενεργή πίεση μνήμης",
                        explanation = "Το Android αναφέρει ότι η συσκευή βρίσκεται τώρα σε κατάσταση χαμηλής μνήμης.",
                        evidence = "lowMemory=true · διαθέσιμα ${SnapshotPresentation.gib(snapshot.availableMemoryBytes)} · " +
                            "όριο ${SnapshotPresentation.gib(snapshot.lowMemoryThresholdBytes)}",
                        limitation = "Το σήμα δεν αποδίδει την αιτία σε συγκεκριμένη εφαρμογή και δεν αποτελεί μέτρηση συνολικής απόδοσης.",
                    ),
                )
            }

            thermalFinding(snapshot)?.let(::add)

            if (snapshot.battery.voltageMillivolts == null ||
                snapshot.battery.voltageSource == BatteryVoltageSource.UNAVAILABLE_OR_REJECTED
            ) {
                add(
                    DiagnosisFinding(
                        ruleId = BATTERY_RULE_ID,
                        ruleVersion = RULE_VERSION,
                        type = DiagnosisFindingType.DATA_QUALITY,
                        severity = DiagnosisSeverity.INFO,
                        title = "Μη διαθέσιμη αξιόπιστη τάση μπαταρίας",
                        explanation = "Η τάση δεν μπορεί να χρησιμοποιηθεί σε αυτή τη διάγνωση, επειδή η διαθέσιμη ένδειξη δεν ήταν αξιόπιστη ή δεν υπήρχε.",
                        evidence = BatteryPresentation.voltageLabel(
                            snapshot.battery.voltageMillivolts,
                            snapshot.battery.voltageSource,
                        ),
                        limitation = "Αυτό είναι περιορισμός ποιότητας δεδομένων, όχι εκτίμηση υγείας ή πραγματικής χωρητικότητας.",
                    ),
                )
            }
        }.sortedWith(
            compareByDescending<DiagnosisFinding> { severityRank(it.severity) }
                .thenBy { it.ruleId },
        )

        return DiagnosisReport(
            engineVersion = ENGINE_VERSION,
            evaluatedRuleIds = evaluatedRules,
            findings = findings,
        )
    }

    private fun thermalFinding(snapshot: DeviceSnapshot): DiagnosisFinding? {
        val severity = when (snapshot.thermalStatus) {
            PowerManager.THERMAL_STATUS_LIGHT,
            PowerManager.THERMAL_STATUS_MODERATE,
            -> DiagnosisSeverity.INFO

            PowerManager.THERMAL_STATUS_SEVERE -> DiagnosisSeverity.WARNING

            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN,
            -> DiagnosisSeverity.CRITICAL

            else -> return null
        }
        val statusLabel = SnapshotPresentation.thermalLabel(snapshot.thermalStatus)
        val headroomEvidence = snapshot.thermalHeadroom?.let {
            " · ${SnapshotPresentation.thermalEnvelopeLabel(it)}"
        }.orEmpty()

        return DiagnosisFinding(
            ruleId = THERMAL_RULE_ID,
            ruleVersion = RULE_VERSION,
            type = DiagnosisFindingType.CONDITION,
            severity = severity,
            title = "Ενεργός θερμικός περιορισμός",
            explanation = "Το Android αναφέρει: $statusLabel.",
            evidence = "Κατάσταση: $statusLabel (κωδικός ${snapshot.thermalStatus})$headroomEvidence",
            limitation = "Η κατάσταση είναι στιγμιότυπο και δεν αποδεικνύει ποια εφαρμογή ή ποιο εξάρτημα την προκάλεσε.",
        )
    }

    private fun severityRank(severity: DiagnosisSeverity): Int = when (severity) {
        DiagnosisSeverity.INFO -> 1
        DiagnosisSeverity.WARNING -> 2
        DiagnosisSeverity.CRITICAL -> 3
    }
}

object DiagnosisPresentation {
    fun severityLabel(severity: DiagnosisSeverity): String = when (severity) {
        DiagnosisSeverity.INFO -> "Πληροφορία"
        DiagnosisSeverity.WARNING -> "Προειδοποίηση"
        DiagnosisSeverity.CRITICAL -> "Κρίσιμη ένδειξη"
    }

    fun headline(report: DiagnosisReport): String {
        val activeConditions = report.findings.count { it.type == DiagnosisFindingType.CONDITION }
        return when (activeConditions) {
            0 -> if (report.findings.any { it.type == DiagnosisFindingType.DATA_QUALITY }) {
                "Δεν εντοπίστηκε ενεργή πίεση"
            } else {
                "Δεν εντοπίστηκε ενεργή ένδειξη"
            }

            1 -> "1 ενεργή ένδειξη"
            else -> "$activeConditions ενεργές ενδείξεις"
        }
    }

    fun evaluatedLabel(report: DiagnosisReport): String =
        "Ελέγχθηκαν ${report.evaluatedRuleIds.size} κανόνες: πίεση RAM, θερμική κατάσταση και ποιότητα δεδομένων μπαταρίας."
}
