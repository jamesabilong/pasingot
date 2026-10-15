package app.personal.workouttracker.shared

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Separate envelope/path: never send this contract to the legacy workout path. */
object GroupedWorkoutProtocol {
    const val FORMAT = "pasingot-grouped-workout"
    const val VERSION = 2
    const val DATA_PATH = "/grouped-workout-set-v2"
    const val MAX_EXERCISES = 240
    const val MAX_JSON_LENGTH = 65_536
    const val MAX_UTF8_BYTES = 65_536
}

@Serializable
data class GroupedWorkoutExercise(
    val prescription: WorkoutExercise,
    /** Scheduled time isolates repeated group IDs in different daily sessions. */
    val sessionTime: String,
    val circuit: WorkoutCircuit? = null,
)

@Serializable
data class GroupedWorkoutTransfer(
    val format: String = GroupedWorkoutProtocol.FORMAT,
    val schemaVersion: Int = GroupedWorkoutProtocol.VERSION,
    val date: String,
    val exercises: List<GroupedWorkoutExercise>,
)

@Serializable
data class GroupedWorkoutStep(val exerciseIndex: Int, val setNumber: Int, val restSeconds: Int)

private val groupedJson = Json { ignoreUnknownKeys = false; encodeDefaults = true }
private val groupedTime = Regex("([01]\\d|2[0-3]):[0-5]\\d")

fun validGroupedWorkoutTransfer(payload: GroupedWorkoutTransfer): Boolean {
    if (payload.format != GroupedWorkoutProtocol.FORMAT || payload.schemaVersion != GroupedWorkoutProtocol.VERSION ||
        !Regex("\\d{4}-\\d{2}-\\d{2}").matches(payload.date) || payload.date < "1900-01-01" ||
        !runCatching { LocalDate.parse(payload.date).toString() == payload.date }.getOrDefault(false) ||
        payload.exercises.size !in 2..GroupedWorkoutProtocol.MAX_EXERCISES ||
        payload.exercises.none { it.circuit != null }) return false
    val identities = mutableSetOf<Long>()
    if (payload.exercises.any { item ->
            val row = item.prescription
            !groupedTime.matches(item.sessionTime) || row.exercise.isBlank() || row.exercise.length > 200 ||
                row.reps.isBlank() || row.reps.length > 100 || row.sets !in 1..100 || row.rest !in 0..3600 ||
                row.workoutRowId == null || row.workoutRowId <= 0 || !identities.add(row.workoutRowId) ||
                (row.loadWeight != null && (!row.loadWeight.isFinite() || row.loadWeight < 0 || row.loadUnit !in listOf("kg", "lb"))) ||
                (row.questId != null && (row.questId.isBlank() || row.questId.length > 256 || row.questDayIndex == null ||
                    row.questDayIndex <= 0 || row.questLevel !in listOf("beginner", "intermediate", "advanced")))
        }) return false
    if (payload.exercises.zipWithNext().any { (left, right) -> left.sessionTime > right.sessionTime }) return false
    return validScheduleCircuits(payload.exercises.map { item ->
        val row = item.prescription
        ScheduleRow(day = "", time = item.sessionTime, exercise = row.exercise, sets = row.sets,
            reps = row.reps, rest = row.rest, questId = row.questId, questDayIndex = row.questDayIndex,
            questLevel = row.questLevel, circuit = item.circuit)
    })
}

/** This builder is a foundation API only; existing production senders stay gated. */
fun buildGroupedWorkoutTransfer(rows: List<ScheduleRow>, date: String, weekday: String): GroupedWorkoutTransfer {
    require(validScheduleCircuits(rows)) { "Invalid circuit schedule" }
    val exercises = scheduleRowsOnDate(rows, date, weekday).map { row ->
        GroupedWorkoutExercise(
            prescription = WorkoutExercise(row.exercise, row.reps, row.sets, row.rest, row.loadWeight, row.loadUnit,
                row.workoutRowId, row.questId, row.questDayIndex, row.questDayLabel, row.questLevel),
            sessionTime = row.time,
            circuit = row.circuit,
        )
    }
    return GroupedWorkoutTransfer(date = date, exercises = exercises).also {
        require(validGroupedWorkoutTransfer(it)) { "Invalid grouped workout transfer" }
    }
}

/** Shared round ordering/rest contract for the later durable watch executor. */
fun compileGroupedWorkoutSteps(payload: GroupedWorkoutTransfer): List<GroupedWorkoutStep> {
    require(validGroupedWorkoutTransfer(payload)) { "Invalid grouped workout transfer" }
    val steps = mutableListOf<GroupedWorkoutStep>()
    var index = 0
    while (index < payload.exercises.size) {
        val item = payload.exercises[index]
        if (item.circuit == null) {
            for (set in 1..item.prescription.sets) steps += GroupedWorkoutStep(index, set, item.prescription.rest)
            index++
        } else {
            var end = index
            while (end + 1 < payload.exercises.size && payload.exercises[end + 1].sessionTime == item.sessionTime &&
                payload.exercises[end + 1].circuit?.id == item.circuit.id) end++
            for (round in 1..item.prescription.sets) {
                for (member in index..end) steps += GroupedWorkoutStep(member, round,
                    if (member == end) item.circuit.restSeconds else 0)
            }
            index = end + 1
        }
    }
    if (steps.isNotEmpty()) steps[steps.lastIndex] = steps.last().copy(restSeconds = 0)
    return steps
}

fun encodeGroupedWorkoutTransfer(payload: GroupedWorkoutTransfer): String {
    require(validGroupedWorkoutTransfer(payload)) { "Invalid grouped workout transfer" }
    return groupedJson.encodeToString(payload).also {
        require(it.length <= GroupedWorkoutProtocol.MAX_JSON_LENGTH && it.toByteArray(Charsets.UTF_8).size <= GroupedWorkoutProtocol.MAX_UTF8_BYTES) { "Grouped workout transfer is too large" }
    }
}

/** Strict decoding rejects unknown execution fields instead of losing their meaning. */
fun decodeGroupedWorkoutTransfer(raw: String): GroupedWorkoutTransfer {
    require(raw.length <= GroupedWorkoutProtocol.MAX_JSON_LENGTH && raw.toByteArray(Charsets.UTF_8).size <= GroupedWorkoutProtocol.MAX_UTF8_BYTES) { "Grouped workout transfer is too large" }
    val envelope = groupedJson.parseToJsonElement(raw) as? JsonObject
        ?: throw IllegalArgumentException("Invalid grouped workout envelope")
    val version = envelope["schemaVersion"] as? JsonPrimitive
    require(version != null && !version.isString && version.intOrNull == GroupedWorkoutProtocol.VERSION &&
        (envelope["format"] as? JsonPrimitive)?.content == GroupedWorkoutProtocol.FORMAT) { "Unsupported grouped workout envelope" }
    return groupedJson.decodeFromString(GroupedWorkoutTransfer.serializer(), raw).also {
        require(validGroupedWorkoutTransfer(it)) { "Invalid grouped workout transfer" }
    }
}
