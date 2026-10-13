import { parseSetEffort } from './set-effort';
import { SCHEMA_VERSION, type WorkoutLog, type WorkoutRow, type WorkoutSessionEvent, type WorkoutSetLog } from '../types';
import { STORES, transact } from './db';
import { workoutsOnDate } from './schedule-dates';
import { estimateLevelFor, estimateWorkoutDurationSeconds, validLoadWeight, workoutStatusesOnDate } from './workout-planning';
import { ACTIVE_WORKOUT_SESSION_KEY, circuitSessionRowsMatch, currentSetInput, elapsedSecondsForSession, finishElapsedSession, normalizeActiveWorkoutSession, restOrActive, touchSession, type ActiveWorkoutSession } from './workout-session';

export interface WorkoutSessionTransition {
  session: ActiveWorkoutSession | null;
  setLog?: WorkoutSetLog;
  exerciseLog?: WorkoutLog;
  event?: WorkoutSessionEvent;
}

function sameValue(left: unknown, right: unknown): boolean {
  if (Object.is(left, right)) return true;
  if (left == null || right == null || typeof left !== 'object' || typeof right !== 'object') return false;
  const a = left as Record<string, unknown>;
  const b = right as Record<string, unknown>;
  const keys = Object.keys(a);
  return keys.length === Object.keys(b).length && keys.every((key) => Object.hasOwn(b, key) && sameValue(a[key], b[key]));
}

/** History and the cursor advance share one commit. A stale tab/action cannot log the same set again. */
export async function commitWorkoutSessionTransition(expected: ActiveWorkoutSession | null, next: WorkoutSessionTransition, startRows?: WorkoutRow[]): Promise<boolean> {
  let committed = false;
  const checkGroupedPlan = Boolean(expected?.steps && (next.setLog || next.exerciseLog));
  await transact([STORES.appState, STORES.logs, STORES.setLogs, STORES.sessionEvents, ...(startRows || checkGroupedPlan ? [STORES.workouts] : [])], (transaction) => {
    const appState = transaction.objectStore(STORES.appState);
    const request = appState.get(ACTIVE_WORKOUT_SESSION_KEY);
    request.onsuccess = () => {
      const stored = request.result as ActiveWorkoutSession | undefined;
      const current = stored ? normalizeActiveWorkoutSession(stored, expected?.lastInteractionAtEpochMillis) : null;
      const prior = expected ? normalizeActiveWorkoutSession(expected) : null;
      if (!sameValue(current, prior)) return;
      const write = () => {
        try {
          if (next.setLog) transaction.objectStore(STORES.setLogs).add(next.setLog);
          if (next.exerciseLog) transaction.objectStore(STORES.logs).add(next.exerciseLog);
          if (next.event) transaction.objectStore(STORES.sessionEvents).add(next.event);
          if (next.session) appState.put(next.session);
          else appState.delete(ACTIVE_WORKOUT_SESSION_KEY);
          committed = true;
        } catch {
          transaction.abort();
        }
      };
      if (startRows && next.session) {
        const schedule = transaction.objectStore(STORES.workouts).getAll();
        schedule.onsuccess = () => {
          if (sameValue(workoutsOnDate(schedule.result, next.session!.planDate).filter(row => row.id != null), startRows)) write();
        };
      } else if (checkGroupedPlan && expected) {
        const schedule = transaction.objectStore(STORES.workouts).getAll();
        const logs = transaction.objectStore(STORES.logs).getAll();
        let pending = 2;
        const validate = () => {
          if (--pending) return;
          const projected = workoutsOnDate(schedule.result, expected.planDate);
          const rowId = expected.plannedRows?.[expected.exerciseIndex]?.id;
          if (circuitSessionRowsMatch(expected, projected) && !workoutStatusesOnDate(logs.result, expected.planDate).has(rowId ?? -1)) write();
        };
        schedule.onsuccess = validate;
        logs.onsuccess = validate;
      } else write();
    };
  });
  return committed;
}

export function workoutSessionEvent(session: ActiveWorkoutSession, rows: WorkoutRow[], now = Date.now()): WorkoutSessionEvent {
  return {
    schemaVersion: SCHEMA_VERSION,
    workoutEntryId: `pwa:${session.planDate}`,
    workoutDate: session.planDate,
    eventType: session.status === 'completed' ? 'completed' : 'ended',
    stopReason: session.lastStopReason ?? 'ended_by_user',
    timestamp: new Date(now).toISOString(),
    elapsedSeconds: elapsedSecondsForSession(session, now),
    estimatedDurationSeconds: estimateWorkoutDurationSeconds(rows, estimateLevelFor(rows)),
    exerciseIndex: session.exerciseIndex,
    currentSet: session.currentSet,
    totalExercises: rows.length,
    currentExercise: rows[session.exerciseIndex]?.exercise ?? null,
  };
}

export function workoutStepTransition(session: ActiveWorkoutSession, rows: WorkoutRow[], action: 'complete' | 'skip', now = Date.now()): WorkoutSessionTransition | null {
  const row = rows[session.exerciseIndex];
  if (session.status !== 'active' || !row || !circuitSessionRowsMatch(session, rows)) return null;
  const touched = touchSession(session, now);
  const transition: WorkoutSessionTransition = { session: touched };
  const sequenceStep = touched.steps?.[touched.stepIndex ?? -1];
  if (touched.steps && (!sequenceStep || sequenceStep.exerciseIndex !== touched.exerciseIndex || sequenceStep.setNumber !== touched.currentSet)) return null;
  if (action === 'complete') {
    const input = currentSetInput(touched, row);
    const effort = parseSetEffort(input);
    if (effort.error !== null) return null;
    const loadWeight = validLoadWeight(input.loadWeight);
    transition.setLog = {
      schemaVersion: SCHEMA_VERSION, date: new Date(now).toISOString(), workoutRowId: row.id ?? null,
      exercise: row.exercise, exerciseSourceId: row.exerciseSourceId ?? null, setNumber: touched.currentSet,
      plannedReps: row.reps, actualReps: input.actualReps.trim() || row.reps,
      loadWeight, loadUnit: loadWeight != null ? input.loadUnit : null,
      rpe: effort.rpe, rir: effort.rir,
    };
    if (!touched.steps && touched.currentSet < row.sets) {
      transition.session = restOrActive({ ...touched, currentSet: touched.currentSet + 1 }, row.rest, now);
      return transition;
    }
  }
  if (row.id != null && (!touched.steps || action === 'skip' || touched.currentSet === row.sets)) transition.exerciseLog = {
    schemaVersion: SCHEMA_VERSION, date: new Date(now).toISOString(), exercise: row.exercise,
    exerciseSourceId: row.exerciseSourceId ?? null, status: action === 'complete' ? 'done' : 'skipped', workoutRowId: row.id,
  };
  if (touched.steps && sequenceStep) {
    const handled = new Set(touched.handledRowIds ?? []);
    if (transition.exerciseLog && row.id != null) handled.add(row.id);
    let stepIndex = (touched.stepIndex ?? 0) + 1;
    while (stepIndex < touched.steps.length && handled.has(rows[touched.steps[stepIndex].exerciseIndex]?.id ?? -1)) stepIndex++;
    if (stepIndex >= touched.steps.length) {
      transition.session = finishElapsedSession({ ...touched, handledRowIds: [...handled], stepIndex: touched.steps.length, restDurationSeconds: null }, 'completed', 'completed', now);
      transition.event = workoutSessionEvent(transition.session, rows, now);
    } else {
      const nextStep = touched.steps[stepIndex];
      const next = { ...touched, handledRowIds: [...handled], stepIndex, exerciseIndex: nextStep.exerciseIndex, currentSet: nextStep.setNumber };
      const nextRow = rows[nextStep.exerciseIndex];
      const sameRound = row.circuit && nextRow.circuit?.id === row.circuit.id && nextRow.day === row.day
        && nextRow.time === row.time && nextStep.setNumber === touched.currentSet;
      const restSeconds = row.circuit ? (sameRound ? 0 : row.circuit.restSeconds) : sequenceStep.restSeconds;
      transition.session = restOrActive(next, action === 'complete' ? restSeconds : 0, now);
    }
    return transition;
  }
  const nextIndex = touched.exerciseIndex + 1;
  if (nextIndex >= rows.length) {
    transition.session = finishElapsedSession(touched, 'completed', 'completed', now);
    transition.event = workoutSessionEvent(transition.session, rows, now);
  } else {
    const next = { ...touched, exerciseIndex: nextIndex, currentSet: 1 };
    transition.session = action === 'complete' ? restOrActive(next, row.rest, now) : next;
  }
  return transition;
}

/** External Today/history actions can handle a member while a grouped cursor is saved. */
export function reconcileCircuitSessionHistory(session: ActiveWorkoutSession, rows: WorkoutRow[], logs: WorkoutLog[]): ActiveWorkoutSession | null {
  if (!session.steps || !['active', 'resting', 'paused'].includes(session.status) || !circuitSessionRowsMatch(session, rows)) return null;
  const handled = new Set(session.handledRowIds ?? []);
  const statuses = workoutStatusesOnDate(logs, session.planDate);
  session.rowIds.forEach(id => { if (statuses.has(id)) handled.add(id); });
  let stepIndex = session.stepIndex ?? 0;
  while (stepIndex < session.steps.length && handled.has(rows[session.steps[stepIndex].exerciseIndex]?.id ?? -1)) stepIndex++;
  // closeHandledWorkoutSession owns the all-handled case without creating a second event.
  if (stepIndex >= session.steps.length) return null;
  if (stepIndex === session.stepIndex && handled.size === (session.handledRowIds ?? []).length) return null;
  const step = session.steps[stepIndex];
  return { ...session, handledRowIds: [...handled], stepIndex, exerciseIndex: step.exerciseIndex, currentSet: step.setNumber };
}

export function closeStaleWorkoutSession(session: ActiveWorkoutSession, rows: WorkoutRow[], today: string, now = Date.now()): WorkoutSessionTransition | null {
  if (session.planDate === today) return null;
  if (session.status === 'completed' || session.status === 'ended') return { session: null };
  const ended = finishElapsedSession(session, 'ended', 'stale_next_day', now);
  return { session: null, event: workoutSessionEvent(ended, rows, now) };
}

/** Synced exercise history can finish the same plan while a phone cursor is still open.
 * Remove only that fully handled cursor; the existing history remains authoritative.
 */
export function closeHandledWorkoutSession(session: ActiveWorkoutSession, logs: WorkoutLog[]): WorkoutSessionTransition | null {
  if (!['active', 'resting', 'paused'].includes(session.status) || !session.rowIds.length) return null;
  const statuses = workoutStatusesOnDate(logs, session.planDate);
  return session.rowIds.every((id) => statuses.has(id)) ? { session: null } : null;
}
