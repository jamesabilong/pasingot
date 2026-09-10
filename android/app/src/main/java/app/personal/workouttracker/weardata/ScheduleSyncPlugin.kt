package app.personal.workouttracker.weardata

import app.personal.workouttracker.shared.ScheduleRow
import com.getcapacitor.JSObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.getcapacitor.JSArray
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * JS -> native bridge for Prompt 6's "schedule bridge" decision: the PWA
 * calls `ScheduleSync.syncSchedule({ rows })` every time it parses a CSV
 * (see pwa/src/lib/native-bridge.ts), handing the full multi-day schedule to native code so
 * WorkoutRequestListenerService has something to read without reaching into
 * IndexedDB (which a background Kotlin service can't do anyway).
 *
 * Mirrors WorkoutLogBridgePlugin's shape (Prompt 8) in the opposite
 * direction — that one is native -> JS (pending logs), this one is JS -> native.
 */
@CapacitorPlugin(name = "ScheduleSync")
class ScheduleSyncPlugin : Plugin() {

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache by lazy { ScheduleCache(context) }

    @PluginMethod
    fun syncSchedule(call: PluginCall) {
        val rowsArray: JSArray = call.getArray("rows") ?: run {
            call.reject("Missing required 'rows' array")
            return
        }

        try {
            val rows = mutableListOf<ScheduleRow>()
            for (i in 0 until rowsArray.length()) {
                val obj = rowsArray.getJSONObject(i)
                rows.add(
                    ScheduleRow(
                        day = obj.getString("day"),
                        time = obj.getString("time"),
                        exercise = obj.getString("exercise"),
                        sets = obj.getInt("sets"),
                        reps = obj.getString("reps"),
                        rest = obj.getInt("rest"),
                        loadWeight = if (obj.has("loadWeight") && !obj.isNull("loadWeight")) obj.getDouble("loadWeight") else null,
                        loadUnit = if (obj.has("loadUnit") && !obj.isNull("loadUnit")) obj.getString("loadUnit") else null,
                        workoutRowId = if (obj.has("id") && !obj.isNull("id")) obj.getLong("id") else null,
                        questId = if (obj.has("questId") && !obj.isNull("questId")) obj.getString("questId") else null,
                        questDayIndex = if (obj.has("questDayIndex") && !obj.isNull("questDayIndex")) obj.getInt("questDayIndex") else null,
                        questDayLabel = if (obj.has("questDayLabel") && !obj.isNull("questDayLabel")) obj.getString("questDayLabel") else null,
                        questLevel = if (obj.has("questLevel") && !obj.isNull("questLevel")) obj.getString("questLevel") else null,
                    )
                )
            }
            cache.save(rows)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to sync schedule: ${e.message}", e)
        }
    }
    @PluginMethod
    fun sendTodayToWatch(call: PluginCall) {
        syncScope.launch {
            try {
                val payload = cache.todaysWorkout()
                if (payload.exercises.isEmpty()) {
                    call.reject("No exercises scheduled for today. Add a playlist or quest first.")
                    return@launch
                }
                withTimeout(15_000) {
                    WearSyncClient.sendWorkoutSet(context, payload).getOrThrow()
                }
                // DataClient has accepted the payload; receipt is reported on the watch.
                call.resolve(JSObject().apply {
                    put("exerciseCount", payload.exercises.size)
                    put("date", payload.date)
                })
            } catch (error: Exception) {
                call.reject(error.message ?: "Could not send workout. Check your watch connection.", error)
            }
        }
    }

    override fun handleOnDestroy() {
        syncScope.cancel()
        super.handleOnDestroy()
    }

}
