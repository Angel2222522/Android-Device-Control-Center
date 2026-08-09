package dev.devicecontrolcenter

import java.io.File
import java.util.Locale

enum class CpuActivitySource {
    PROC_STAT,
    UNAVAILABLE_OR_RESTRICTED,
}

data class CpuSnapshot(
    val activityPercent: Double?,
    val logicalProcessorCount: Int?,
    val source: CpuActivitySource,
    val sampleWindowMillis: Long?,
) {
    companion object {
        fun unavailable(logicalProcessorCount: Int? = null): CpuSnapshot = CpuSnapshot(
            activityPercent = null,
            logicalProcessorCount = logicalProcessorCount,
            source = CpuActivitySource.UNAVAILABLE_OR_RESTRICTED,
            sampleWindowMillis = null,
        )
    }
}

internal data class CpuTimes(
    val totalJiffies: Long,
    val idleJiffies: Long,
)

object CpuSnapshotReader {
    internal const val SAMPLE_WINDOW_MILLIS = 250L
    private const val PROC_STAT_PATH = "/proc/stat"
    private const val CPU_FIELDS_USED = 8

    fun read(
        readProcStat: () -> String? = { readProcStatFile() },
        delayMillis: (Long) -> Unit = { Thread.sleep(it) },
        logicalProcessorCount: () -> Int = {
            Runtime.getRuntime().availableProcessors()
        },
    ): CpuSnapshot {
        val processorCount = logicalProcessorCount().takeIf { it > 0 }
        val first = readProcStat()?.let(::parseCpuTimes)
            ?: return CpuSnapshot.unavailable(processorCount)

        runCatching { delayMillis(SAMPLE_WINDOW_MILLIS) }.getOrNull()
            ?: return CpuSnapshot.unavailable(processorCount)

        val second = readProcStat()?.let(::parseCpuTimes)
            ?: return CpuSnapshot.unavailable(processorCount)
        val totalDelta = second.totalJiffies - first.totalJiffies
        val idleDelta = second.idleJiffies - first.idleJiffies
        if (totalDelta <= 0L || idleDelta < 0L || idleDelta > totalDelta) {
            return CpuSnapshot.unavailable(processorCount)
        }

        val busyDelta = totalDelta - idleDelta
        return CpuSnapshot(
            activityPercent = busyDelta * 100.0 / totalDelta,
            logicalProcessorCount = processorCount,
            source = CpuActivitySource.PROC_STAT,
            sampleWindowMillis = SAMPLE_WINDOW_MILLIS,
        )
    }

    internal fun parseCpuTimes(procStat: String): CpuTimes? {
        val cpuLine = procStat.lineSequence().firstOrNull { it.startsWith("cpu ") }
            ?: return null
        val values = cpuLine.trim()
            .split(Regex("\\s+"))
            .drop(1)
            .take(CPU_FIELDS_USED)
            .map { it.toLongOrNull() ?: return null }
        if (values.size < CPU_FIELDS_USED || values.any { it < 0L }) return null

        val total = values.sum()
        val idle = values[3] + values[4]
        if (total <= 0L || idle > total) return null
        return CpuTimes(totalJiffies = total, idleJiffies = idle)
    }

    private fun readProcStatFile(): String? = runCatching {
        File(PROC_STAT_PATH).readText()
    }.getOrNull()
}

object CpuPresentation {
    fun activityLabel(activityPercent: Double?): String = activityPercent?.let {
        String.format(Locale.ROOT, "%.1f%% δραστηριότητα CPU", it)
    } ?: "Μη διαθέσιμη δραστηριότητα CPU"

    fun detail(snapshot: CpuSnapshot): String = when (snapshot.source) {
        CpuActivitySource.PROC_STAT -> buildString {
            append("Πηγή: read-only /proc/stat")
            snapshot.sampleWindowMillis?.let { append(" · Παράθυρο: ${it} ms") }
            snapshot.logicalProcessorCount?.let { append(" · Λογικοί επεξεργαστές: $it") }
        }

        CpuActivitySource.UNAVAILABLE_OR_RESTRICTED ->
            "Η read-only πηγή /proc/stat δεν ήταν διαθέσιμη ή δεν επέστρεψε έγκυρο δείγμα"
    }

    fun statusLabel(snapshot: CpuSnapshot): String = when (snapshot.source) {
        CpuActivitySource.PROC_STAT ->
            "Παράγωγο συνολικό σήμα συσκευής · Δεν αποδίδεται σε εφαρμογή"

        CpuActivitySource.UNAVAILABLE_OR_RESTRICTED ->
            "Δεν υπάρχει αξιόπιστη συσκευή-επίπεδο ένδειξη CPU τώρα"
    }
}
