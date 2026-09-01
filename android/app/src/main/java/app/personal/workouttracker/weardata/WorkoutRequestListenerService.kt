package app.personal.workouttracker.weardata

import android.util.Log
import app.personal.workouttracker.shared.DataLayerPaths
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.shared.WorkoutSetPayload
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prompt 6, req 2-3: listens on [DataLayerPaths.REQUEST_WORKOUT] for a
 * download request from the watch — manual "Download Now" and the scheduled
 * WorkManager job both hit this same path (there's no date field on the
 * request; it always means "today", per the resolved request-scope decision).
 *
 * Manifest-registered (see AndroidManifest.xml) so this fires even when the
 * phone app isn't foregrounded — the scheduled-download path in particular
 * has no user with the app open.
 */
class WorkoutRequestListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != DataLayerPaths.REQUEST_WORKOUT) return

        val cache = ScheduleCache(applicationContext)
        val cached = cache.load()
        val today = todayWeekdayName()

        val todaysExercises: List<WorkoutExercise> = cached?.rows
            ?.filter { it.day.equals(today, ignoreCase = true) }
            ?.sortedBy { it.time }
            ?.map {
                WorkoutExercise(
                    exercise = it.exercise,
                    reps = it.reps,
                    sets = it.sets,
                    rest = it.rest,
                    workoutRowId = it.workoutRowId,
                    questId = it.questId,
                    questDayIndex = it.questDayIndex,
                    questDayLabel = it.questDayLabel,
                    questLevel = it.questLevel,
                )
            }
            ?: emptyList()

        val payload = WorkoutSetPayload(date = todayDateKey(), exercises = todaysExercises)

        // onMessageReceived runs on a binder thread with no guaranteed
        // lifetime beyond this call; launch on a service-scoped coroutine
        // rather than blocking here, and just log failures — there's no
        // request-response channel back to the watch beyond the
        // WORKOUT_SET data item itself.
        serviceScope.launch {
            val result = WearSyncClient.sendWorkoutSet(applicationContext, payload)
            result.onFailure { Log.e(TAG, "Failed to deliver workout set for $today", it) }
        }
    }

    private fun todayWeekdayName(): String =
        SimpleDateFormat("EEEE", Locale.US).format(Date())

    private fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private const val TAG = "WorkoutRequestListener"
    }
}
