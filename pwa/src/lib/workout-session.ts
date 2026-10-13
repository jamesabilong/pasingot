import type { WorkoutSetInput } from '../types';
import { SCHEMA_VERSION, type WorkoutRow } from '../types';
import { todayDateKey } from './history-stats';
import { circuitValueSignature, compileCircuitSteps, validateCircuitGroups, type WorkoutSessionStep } from './workout-circuits';

export const ACTIVE_WORKOUT_SESSION_KEY = 'activeWorkoutSession';
export const ACTIVE_SESSION_IDLE_TIMEOUT_MS = 45 * 60 * 1000;

export type PwaWorkoutSessionStatus = 'active' | 'resting' | 'paused' | 'completed' | 'ended';

export interface ActiveWorkoutSession {
  key: typeof ACTIVE_WORKOUT_SESSION_KEY;
  schemaVersion: number;
  planDate: string;
  rowIds: number[];
  status: PwaWorkoutSessionStatus;
  exerciseIndex: number;
  currentSet: number;
  restUntilEpochMillis: number | null;
  pausedRestRemainingSeconds: number | null;
  accumulatedElapsedMillis: number;
  elapsedStartedAtEpochMillis: number | null;
  lastStopReason: string | null;
  setInputs: Record<string, WorkoutSetInput>;
  lastInteractionAtEpochMillis: number;
  lastRestCueKey: string | null;
  steps?: WorkoutSessionStep[];
  stepIndex?: number;
  handledRowIds?: number[];
  restDurationSeconds?: number | null;
  plannedRows?: WorkoutRow[];
}

export function setInputKey(row: WorkoutRow, setNumber: number): string {
  return `${row.id ?? row.exercise}:${setNumber}`;
}

export function defaultSetInput(row: WorkoutRow): WorkoutSetInput {
  return {
    actualReps: row.reps,
    loadWeight: row.loadWeight != null ? String(row.loadWeight) : '',
    loadUnit: row.loadUnit ?? 'kg',
    rpe: '',
    rir: '',
  };
}

export function normalizeActiveWorkoutSession(session: ActiveWorkoutSession, now = Date.now()): ActiveWorkoutSession {
  return {
    ...session,
    setInputs: session.setInputs ?? {},
    lastInteractionAtEpochMillis: session.lastInteractionAtEpochMillis ?? session.elapsedStartedAtEpochMillis ?? now,
    lastRestCueKey: session.lastRestCueKey ?? null,
  };
}

export function touchSession(session: ActiveWorkoutSession, now = Date.now()): ActiveWorkoutSession {
  return { ...session, lastInteractionAtEpochMillis: now };
}

export function restCueKey(session: ActiveWorkoutSession): string {
  return `${session.planDate}:${session.exerciseIndex}:${session.currentSet}:${session.restUntilEpochMillis ?? 'none'}`;
}

export function elapsedSecondsForSession(session: ActiveWorkoutSession, now = Date.now()): number {
  const runningMillis = session.elapsedStartedAtEpochMillis != null && (session.status === 'active' || session.status === 'resting')
    ? Math.max(0, now - session.elapsedStartedAtEpochMillis)
    : 0;
  return Math.ceil(Math.max(0, session.accumulatedElapsedMillis + runningMillis) / 1000);
}

export function restSecondsForSession(session: ActiveWorkoutSession, now = Date.now()): number {
  if (session.status === 'paused' && session.pausedRestRemainingSeconds != null) return session.pausedRestRemainingSeconds;
  if (session.status !== 'resting' || session.restUntilEpochMillis == null) return 0;
  return Math.max(0, Math.ceil((session.restUntilEpochMillis - now) / 1000));
}

export function stopElapsedSession(session: ActiveWorkoutSession, reason: string, now = Date.now()): ActiveWorkoutSession {
  const runningMillis = session.elapsedStartedAtEpochMillis != null && (session.status === 'active' || session.status === 'resting')
    ? Math.max(0, now - session.elapsedStartedAtEpochMillis)
    : 0;
  return {
    ...session,
    accumulatedElapsedMillis: Math.max(0, session.accumulatedElapsedMillis + runningMillis),
    elapsedStartedAtEpochMillis: null,
    lastStopReason: reason,
  };
}

export function finishElapsedSession(session: ActiveWorkoutSession, status: 'completed' | 'ended', reason: string, now = Date.now()): ActiveWorkoutSession {
  // Account for the running segment before changing to a terminal status.
  return { ...stopElapsedSession(session, reason, now), status };
}

export function startElapsedSession(session: ActiveWorkoutSession, now = Date.now()): ActiveWorkoutSession {
  return {
    ...session,
    elapsedStartedAtEpochMillis: session.elapsedStartedAtEpochMillis ?? now,
    lastStopReason: null,
  };
}

export function newPwaSession(rows: WorkoutRow[], startIndex = 0, handledRowIds: number[] = []): ActiveWorkoutSession {
  const error = validateCircuitGroups(rows);
  if (error) throw new Error(error);
  const now = Date.now();
  const grouped = rows.some(row => row.circuit !== undefined);
  if (grouped && rows.some(row => row.id == null)) throw new Error('Save every circuit exercise before starting the workout.');
  const rowIds = rows.map(row => row.id).filter((id): id is number => id != null);
  if (grouped && new Set(rowIds).size !== rows.length) throw new Error('This circuit plan contains duplicate exercise rows.');
  const steps = grouped ? compileCircuitSteps(rows) : undefined;
  const validIds = new Set(rowIds);
  const handled = new Set(handledRowIds.filter(id => validIds.has(id)));
  rows.slice(0, startIndex).forEach(row => { if (row.id != null) handled.add(row.id); });
  const stepIndex = steps?.findIndex(step => rows[step.exerciseIndex]?.id == null || !handled.has(rows[step.exerciseIndex].id!));
  if (steps && stepIndex === -1) throw new Error('Today’s plan is already handled.');
  const step = steps?.[stepIndex ?? 0];
  return {
    key: ACTIVE_WORKOUT_SESSION_KEY,
    schemaVersion: SCHEMA_VERSION,
    planDate: todayDateKey(),
    rowIds,
    status: 'active',
    exerciseIndex: step?.exerciseIndex ?? startIndex,
    currentSet: step?.setNumber ?? 1,
    restUntilEpochMillis: null,
    pausedRestRemainingSeconds: null,
    accumulatedElapsedMillis: 0,
    elapsedStartedAtEpochMillis: now,
    lastStopReason: null,
    setInputs: {},
    lastInteractionAtEpochMillis: now,
    lastRestCueKey: null,
    ...(steps ? {
      steps, stepIndex: Math.max(0, stepIndex ?? 0), handledRowIds: [...handled], restDurationSeconds: null,
      plannedRows: structuredClone(rows),
    } : {}),
  };
}

/** A restored circuit cursor must never fall back to a changed or flattened schedule. */
export function circuitSessionRowsMatch(session: ActiveWorkoutSession, rows: WorkoutRow[]): boolean {
  const grouped = rows.some(row => row.circuit !== undefined) || session.plannedRows?.some(row => row.circuit !== undefined);
  if (!grouped && !session.steps) return true;
  if (!session.steps || !session.plannedRows || validateCircuitGroups(rows)) return false;
  if (!session.plannedRows.some(row => row.circuit !== undefined) || !session.steps.length
    || new Set(session.rowIds).size !== session.rowIds.length
    || rows.some(row => row.id == null)
    || circuitValueSignature(rows.map(row => row.id)) !== circuitValueSignature(session.rowIds)) return false;
  const handled = session.handledRowIds ?? [];
  if (new Set(handled).size !== handled.length || handled.some(id => !session.rowIds.includes(id))) return false;
  if (['active', 'resting', 'paused'].includes(session.status)) {
    const step = session.steps[session.stepIndex ?? -1];
    if (!Number.isSafeInteger(session.stepIndex) || !step || step.exerciseIndex !== session.exerciseIndex
      || step.setNumber !== session.currentSet || handled.includes(rows[step.exerciseIndex]?.id ?? -1)) return false;
  }
  try {
    return circuitValueSignature(rows) === circuitValueSignature(session.plannedRows)
      && circuitValueSignature(session.steps) === circuitValueSignature(compileCircuitSteps(session.plannedRows));
  } catch { return false; }
}

export function currentSetInput(session: ActiveWorkoutSession, row: WorkoutRow): WorkoutSetInput {
  return session.setInputs[setInputKey(row, session.currentSet)] ?? defaultSetInput(row);
}

export function restOrActive(session: ActiveWorkoutSession, restSeconds: number, now = Date.now()): ActiveWorkoutSession {
  return restSeconds > 0 ? {
    ...session,
    status: 'resting',
    restUntilEpochMillis: now + restSeconds * 1000,
    pausedRestRemainingSeconds: null,
    lastStopReason: null,
    ...(session.steps ? { restDurationSeconds: restSeconds } : {}),
  } : {
    ...session,
    status: 'active',
    restUntilEpochMillis: null,
    pausedRestRemainingSeconds: null,
    lastStopReason: null,
    ...(session.steps ? { restDurationSeconds: null } : {}),
  };
}
