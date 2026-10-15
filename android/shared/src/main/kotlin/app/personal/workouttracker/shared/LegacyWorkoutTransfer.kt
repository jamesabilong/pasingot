package app.personal.workouttracker.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

const val MAX_LEGACY_WORKOUT_JSON_LENGTH = 262_144

private val legacyWorkoutJson = Json { ignoreUnknownKeys = true }

/** Reject incompatible execution metadata before permissive legacy decoding.
 * A new/incorrect sender must never make grouped records appear as straight sets. */
fun decodeLegacyWorkoutTransfer(raw: String): WorkoutSetPayload {
    require(raw.length <= MAX_LEGACY_WORKOUT_JSON_LENGTH) { "Workout transfer is too large" }
    val envelope = legacyWorkoutJson.parseToJsonElement(raw) as? JsonObject
        ?: throw IllegalArgumentException("Invalid workout transfer")
    val version = envelope["schemaVersion"] as? JsonPrimitive
    require(!envelope.containsKey("schemaVersion") || (version != null && !version.isString && version.intOrNull == CURRENT_SCHEMA_VERSION)) { "Update both apps before syncing this workout" }
    val exercises = envelope["exercises"] as? JsonArray
        ?: throw IllegalArgumentException("Invalid workout exercises")
    require(exercises.none { item -> item is JsonObject && ("circuit" in item || "prescription" in item || "sessionTime" in item) }) {
        "Grouped workouts require a newer watch executor"
    }
    return legacyWorkoutJson.decodeFromString(WorkoutSetPayload.serializer(), raw)
}
