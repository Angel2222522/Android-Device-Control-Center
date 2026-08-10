package dev.devicecontrolcenter

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

private const val AUTOMATION_NAME = "device_control_center_local_snapshot"
private const val AUTOMATION_PREFERENCES = "device_control_center_automation"
private const val AUTOMATION_ENABLED_KEY = "periodic_snapshot_enabled"

class LocalSnapshotWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = try {
        val capturedAt = System.currentTimeMillis()
        val snapshot = DeviceSnapshotReader.read(applicationContext)
        val writeResult = SnapshotHistoryRepository(applicationContext).let { repository ->
            val writeResult = repository.recordWithAction(
                snapshot = snapshot,
                capturedAtMillis = capturedAt,
                action = "scheduled_snapshot",
                result = "success",
                details = "WorkManager local snapshot",
            )
            if (writeResult.status == HistoryWriteStatus.SKIPPED_BEFORE_CLEAR) {
                repository.recordAction(
                    action = "scheduled_snapshot",
                    result = "skipped",
                    details = "Το δείγμα ήταν παλαιότερο από τη διαγραφή ιστορικού.",
                    createdAtMillis = capturedAt,
                )
            }
            writeResult
        }
        when {
            writeResult.status == HistoryWriteStatus.SKIPPED_BEFORE_CLEAR -> Result.success()
            writeResult.status == HistoryWriteStatus.RECORDED && writeResult.actionRecorded -> Result.success()
            else -> Result.retry()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        Result.retry()
    }
}

object LocalAutomationManager {
    const val DEFAULT_INTERVAL_HOURS = 12L

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(
        AUTOMATION_PREFERENCES,
        Context.MODE_PRIVATE,
    ).getBoolean(AUTOMATION_ENABLED_KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(AUTOMATION_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AUTOMATION_ENABLED_KEY, enabled)
            .apply()
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<LocalSnapshotWorker>(
                DEFAULT_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                AUTOMATION_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            workManager.cancelUniqueWork(AUTOMATION_NAME)
        }
    }
}
