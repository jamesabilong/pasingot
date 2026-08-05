package app.personal.workouttracker.wear.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.personal.workouttracker.wear.data.ScheduledDownloadTime
import app.personal.workouttracker.wear.data.SettingsRepository
import app.personal.workouttracker.wear.data.WearSyncClient
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Prompt 5 req 2: scheduled download, implemented as a self-rescheduling
 * one-shot [androidx.work.OneTimeWorkRequest] rather than a
 * [androidx.work.PeriodicWorkRequest] — a periodic request's phase can't be
 * reset after its first run, so it drifts away from the user's chosen
 * HH:MM over time. Each run computes the next occurrence itself and
 * re-enqueues, so a settings change takes effect on the very next fire.
 *
 * Hits the exact same download path as the manual "Download Now" button
 * ([WearSyncClient.requestWorkout]) — one download mechanism, two triggers.
 * Doze/battery-optimization can delay this by several minutes; that's an
 * accepted tradeoff here, same as the phone's inexact alarms (Prompt 3).
 */
class ScheduleDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WearSyncClient.requestWorkout(applicationContext).onFailure {
            Log.w(TAG, "Scheduled download request failed: ${it.message}")
            // Not treated as a worker failure/retry — a transient "phone
            // unreachable" at 6:30 AM should just wait for tomorrow's run,
            // not hammer retries against a phone that's out of range.
        }

        val time = SettingsRepository(applicationContext).scheduledTime.first()
        enqueueNext(applicationContext, time)
        return Result.success()
    }

    companion object {
        private const val TAG = "ScheduleDownloadWorker"
        private const val UNIQUE_WORK_NAME = "scheduled_workout_download"

        /** Call once (e.g. from Settings screen / app start) to (re)arm the
         *  schedule against [time]. Safe to call repeatedly — REPLACE policy
         *  means changing the time reschedules rather than stacking jobs. */
        fun enqueueNext(context: Context, time: ScheduledDownloadTime) {
            val delayMillis = millisUntilNext(time)
            val request = OneTimeWorkRequestBuilder<ScheduleDownloadWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("hour" to time.hour, "minute" to time.minute))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        private fun millisUntilNext(time: ScheduledDownloadTime): Long {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, time.hour)
                set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}
