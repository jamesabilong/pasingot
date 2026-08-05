package app.personal.workouttracker.wear.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.personal.workouttracker.wear.data.LogSyncManager
import java.util.concurrent.TimeUnit

/**
 * Periodic retry for the offline log queue (Prompt 8 req 5) — WorkManager's
 * minimum periodic interval is 15 minutes; drift/timing doesn't matter here,
 * unlike the schedule-download worker, since this only needs to "try again
 * periodically," not fire at a specific wall-clock time.
 */
class LogFlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        LogSyncManager(applicationContext).flushQueue()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "log_flush_worker"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<LogFlushWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
