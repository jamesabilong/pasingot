package app.personal.workouttracker.quickstart

import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private val phoneRequestJson = Json { ignoreUnknownKeys = true }

/** Check markers before forward-compatible decoding can discard unsupported grouping. */
internal fun decodePhoneQuickStartRequest(raw: String): QuickStartRequest {
    val input = phoneRequestJson.parseToJsonElement(raw) as? JsonObject
        ?: throw IllegalArgumentException("Invalid Quick Start request")
    val exercises = input["exercises"] as? JsonArray
    require(!input.containsKey("circuit") && exercises?.any {
        (it as? JsonObject)?.containsKey("circuit") == true
    } != true) { "Supersets and circuits require the phone workout player." }
    return phoneRequestJson.decodeFromString(raw)
}
