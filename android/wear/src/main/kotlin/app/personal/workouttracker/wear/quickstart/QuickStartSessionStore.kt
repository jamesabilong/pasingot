package app.personal.workouttracker.wear.quickstart

import app.personal.workouttracker.shared.DownloadedWorkoutEntry
import app.personal.workouttracker.shared.LogEntry
import app.personal.workouttracker.shared.WorkoutExercise
import app.personal.workouttracker.shared.SessionProgressState
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.wear.data.LogSender
import app.personal.workouttracker.wear.data.SessionOutcomeAction
import app.personal.workouttracker.wear.data.SessionOutcomeActionType
import app.personal.workouttracker.wear.data.WorkoutSessionEffects
import app.personal.workouttracker.wear.data.WorkoutSessionStore
import app.personal.workouttracker.wear.session.WorkoutOutcomeTransitionType
import kotlinx.coroutines.CancellationException

/** Adapts the existing session engine to the transient Quick Start runtime. */
class QuickStartSessionStore(
    private val requestId: String,
    private val runtimeStore: QuickStartRuntimeStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val resultClient: QuickStartResultClient? = null,
) : WorkoutSessionStore {
    override suspend fun getEntry(entryId: String): DownloadedWorkoutEntry? =
        runtimeStore.current()?.takeIf { it.sessionPackage.request.requestId == entryId }?.toEntry()

    override suspend fun commitSession(
        expected: DownloadedWorkoutEntry,
        updated: DownloadedWorkoutEntry,
        effects: WorkoutSessionEffects?,
        action: SessionOutcomeAction?,
    ): Boolean {
        val current = runtimeStore.current() ?: return false
        if (current.sessionPackage.request.requestId != requestId || current.toEntry() != expected ||
            updated.exercises != expected.exercises
        ) return false
        val next = updated.sessionState ?: return false
        val runtimeAction = action?.let {
            QuickStartRuntimeAction(
                type = when (it.type) {
                    SessionOutcomeActionType.SET_COMPLETED -> WorkoutOutcomeTransitionType.SET_COMPLETED
                    SessionOutcomeActionType.EXERCISE_SKIPPED -> WorkoutOutcomeTransitionType.EXERCISE_SKIPPED
                },
                exerciseIndex = it.exerciseIndex,
            )
        }
        return when (val result = runtimeStore.transition(
            requestId = requestId,
            expectedRevision = current.runtimeRevision,
            expectedSession = current.session,
            nextSession = next,
            action = runtimeAction,
            nowEpochMillis = nowEpochMillis(),
        )) {
            is ApplyQuickStartRuntimeResult.Applied -> {
                result.state.finalResult?.let { finalResult ->
                    try { resultClient?.send(finalResult) }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { Unit }
                }
                true
            }
            is ApplyQuickStartRuntimeResult.Unchanged -> true
            else -> false
        }
    }

    override suspend fun flushPendingEffects(sender: LogSender) = Unit

    private fun QuickStartRuntimeState.toEntry(): DownloadedWorkoutEntry =
        sessionPackage.request.toEntry(session.copy(
            progress = SessionProgressState(
                exerciseStatuses = outcomes.exercises.map { it.status },
                completedSets = outcomes.exercises.map { it.completedSets },
                successExerciseIndex = session.progress?.successExerciseIndex,
            ),
            resultSaved = finalResult != null,
        ))

    private fun QuickStartRequest.toEntry(session: app.personal.workouttracker.shared.SessionState) =
        DownloadedWorkoutEntry(
            id = requestId,
            date = exercises.firstNotNullOfOrNull { it.sourceDate } ?: "quick-start",
            label = title ?: "Quick Start",
            exercises = exercises.map {
                WorkoutExercise(
                    exercise = it.exerciseName,
                    reps = it.prescription,
                    sets = it.sets,
                    rest = it.restSeconds,
                    loadWeight = it.loadWeight,
                    loadUnit = it.loadUnit,
                    workoutRowId = it.sourceWorkoutRowId,
                )
            },
            sessionState = session,
        )
}

object NoOpQuickStartLogSender : LogSender {
    override suspend fun send(exercise: WorkoutExercise, status: String, workoutRowId: Long?) = Unit
    override suspend fun sendEntry(entry: LogEntry) = Unit
}
