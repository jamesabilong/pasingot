package app.personal.workouttracker.weardata

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Native -> JS bridge for Prompt 8's IndexedDB bridge decision: the PWA
 * calls `WorkoutLogBridge.getPendingLogs()` on resume/`visibilitychange`,
 * writes each entry into its own `logs` IndexedDB store, and only then
 * calls `ackLogs({ ids })` — ack-after-commit so a failed web-side write
 * can't lose an entry. Mirrors ScheduleSyncPlugin's shape (Prompt 6) in the
 * opposite direction.
 */
@CapacitorPlugin(name = "WorkoutLogBridge")
class WorkoutLogBridgePlugin : Plugin() {

    private val store by lazy { PendingLogsStore(context) }
    private val sessionEventsStore by lazy { PendingSessionEventsStore(context) }

    @PluginMethod
    fun getPendingLogs(call: PluginCall) {
        val logsArray = JSArray()
        for (record in store.loadAll()) {
            val obj = JSObject().apply {
                put("id", record.id)
                put("schemaVersion", record.entry.schemaVersion)
                put("exercise", record.entry.exercise)
                put("status", record.entry.status)
                put("timestamp", record.entry.timestamp)
                put("workoutRowId", record.entry.workoutRowId)
            }
            logsArray.put(obj)
        }
        val result = JSObject()
        result.put("logs", logsArray)
        call.resolve(result)
    }

    @PluginMethod
    fun getPendingSessionEvents(call: PluginCall) {
        val eventsArray = JSArray()
        for (record in sessionEventsStore.loadAll()) {
            val event = record.event
            val obj = JSObject().apply {
                put("id", record.id)
                put("schemaVersion", event.schemaVersion)
                put("workoutEntryId", event.workoutEntryId)
                put("workoutDate", event.workoutDate)
                put("eventType", event.eventType)
                put("stopReason", event.stopReason)
                put("timestamp", event.timestamp)
                put("elapsedSeconds", event.elapsedSeconds)
                put("estimatedDurationSeconds", event.estimatedDurationSeconds)
                put("exerciseIndex", event.exerciseIndex)
                put("currentSet", event.currentSet)
                put("totalExercises", event.totalExercises)
                put("currentExercise", event.currentExercise)
            }
            eventsArray.put(obj)
        }
        val result = JSObject()
        result.put("events", eventsArray)
        call.resolve(result)
    }

    @PluginMethod
    fun ackLogs(call: PluginCall) {
        val idsArray: JSArray = call.getArray("ids") ?: run {
            call.reject("Missing required 'ids' array")
            return
        }
        try {
            val ids = mutableListOf<String>()
            for (i in 0 until idsArray.length()) ids.add(idsArray.getString(i))
            store.ack(ids)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to ack logs: ${e.message}", e)
        }
    }

    @PluginMethod
    fun ackSessionEvents(call: PluginCall) {
        val idsArray: JSArray = call.getArray("ids") ?: run {
            call.reject("Missing required 'ids' array")
            return
        }
        try {
            val ids = mutableListOf<String>()
            for (i in 0 until idsArray.length()) ids.add(idsArray.getString(i))
            sessionEventsStore.ack(ids)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to ack session events: ${e.message}", e)
        }
    }
}
