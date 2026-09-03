import { type WorkoutSetInput } from '../components/WorkoutPlayer';
import { SCHEMA_VERSION, type WorkoutRow } from '../types';
import { todayDateKey } from './history-stats';

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
}

export function setInputKey(row: WorkoutRow, setNumber: number): string {
  return `${row.id ?? row.exercise}:${setNumber}`;
}

export function defaultSetInput(row: WorkoutRow): WorkoutSetInput {
  return {
    actualReps: row.reps,
    loadWeight: row.loadWeight != null ? String(row.loadWeight) : '',
    loadUnit: row.loadUnit ?? 'kg',
  };
}

export function normalizeActiveWorkoutSession(session: ActiveWorkoutSession): ActiveWorkoutSession {
  return {
    ...session,
    setInputs: session.setInputs ?? {},
    lastInteractionAtEpochMillis: session.lastInteractionAtEpochMillis ?? session.elapsedStartedAtEpochMillis ?? Date.now(),
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

export function startElapsedSession(session: ActiveWorkoutSession, now = Date.now()): ActiveWorkoutSession {
  return {
    ...session,
    elapsedStartedAtEpochMillis: session.elapsedStartedAtEpochMillis ?? now,
    lastStopReason: null,
  };
}

export function newPwaSession(rows: WorkoutRow[], startIndex = 0): ActiveWorkoutSession {
  const now = Date.now();
  return {
    key: ACTIVE_WORKOUT_SESSION_KEY,
    schemaVersion: SCHEMA_VERSION,
    planDate: todayDateKey(),
    rowIds: rows.map((row) => row.id).filter((id): id is number => id != null),
    status: 'active',
    exerciseIndex: startIndex,
    currentSet: 1,
    restUntilEpochMillis: null,
    pausedRestRemainingSeconds: null,
    accumulatedElapsedMillis: 0,
    elapsedStartedAtEpochMillis: now,
    lastStopReason: null,
    setInputs: {},
    lastInteractionAtEpochMillis: now,
    lastRestCueKey: null,
  };
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
  } : {
    ...session,
    status: 'active',
    restUntilEpochMillis: null,
    pausedRestRemainingSeconds: null,
    lastStopReason: null,
  };
}
