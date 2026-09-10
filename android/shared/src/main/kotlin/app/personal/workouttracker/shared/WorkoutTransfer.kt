package app.personal.workouttracker.shared

/** Both manual phone sends and watch requests use the same schedule projection. */
fun buildWorkoutTransfer(rows: List<ScheduleRow>, date: String, weekday: String): WorkoutSetPayload =
    WorkoutSetPayload(
        date = date,
        exercises = rows.filter { it.day.equals(weekday, ignoreCase = true) }
            .sortedBy { it.time }
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
