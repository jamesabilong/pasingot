package app.personal.workouttracker.wear.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.personal.workouttracker.wear.data.LogSyncManager
import java.util.concurrent.TimeUnit

/**
 * Retry only while there is unsent work. Successful/empty queues finish, so
 * an idle watch does not wake every 15 minutes indefinitely. BLE Data Layer
 * delivery does not require Android's internet-connected network constraint.
 */
class LogFlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return if (LogSyncManager(applicationContext).flushQueue()) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "pending_log_flush"
        private const val LEGACY_PERIODIC_WORK_NAME = "log_flush_worker"

        fun scheduleRetry(context: Context) {
            val request = OneTimeWorkRequestBuilder<LogFlushWorker>()
                // Allow the immediate foreground send to drain/coalesce first.
                .setInitialDelay(1, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                // A send can enqueue just as the previous worker finishes.
                // KEEP would discard it while that worker is still RUNNING.
                UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }

        /** Remove the old always-on polling job after an app upgrade. */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        }
    }
}
