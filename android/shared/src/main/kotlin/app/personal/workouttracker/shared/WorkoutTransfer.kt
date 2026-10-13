package app.personal.workouttracker.shared

/** Both manual phone sends and watch requests use the same schedule projection. */
fun buildWorkoutTransfer(rows: List<ScheduleRow>, date: String, weekday: String): WorkoutSetPayload {
    require(validScheduleCircuits(rows)) { "Invalid circuit schedule" }
    val projected = scheduleRowsOnDate(rows, date, weekday)
    // Reject the entire date: dropping only grouped rows would offer a partial workout.
    if (projected.any { it.circuit != null }) throw CircuitRequiresPhoneException()
    return WorkoutSetPayload(
        date = date,
        exercises = projected
            .map { row ->
                WorkoutExercise(
                    exercise = row.exercise,
                    reps = row.reps,
                    sets = row.sets,
                    rest = row.rest,
                    loadWeight = row.loadWeight,
                    loadUnit = row.loadUnit,
                    workoutRowId = row.workoutRowId,
                    questId = row.questId,
                    questDayIndex = row.questDayIndex,
                    questDayLabel = row.questDayLabel,
                    questLevel = row.questLevel,
                )
            },
    )
}
