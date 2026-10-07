import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { WorkoutSetInput } from '../components/WorkoutPlayer';
import { SCHEMA_VERSION, type WorkoutLog, type WorkoutRow, type WorkoutSessionEvent } from '../types';
import { getAll, getRecord, STORES } from '../lib/db';
import { todayDateKey } from '../lib/history-stats';
import { todayName, workoutStatusesOnDate } from '../lib/workout-planning';
import { closeHandledWorkoutSession, closeStaleWorkoutSession, commitWorkoutSessionTransition, workoutSessionEvent, workoutStepTransition, type WorkoutSessionTransition } from '../lib/workout-session-persistence';
import { ACTIVE_SESSION_IDLE_TIMEOUT_MS, ACTIVE_WORKOUT_SESSION_KEY, currentSetInput, defaultSetInput, elapsedSecondsForSession, finishElapsedSession, newPwaSession, normalizeActiveWorkoutSession, restCueKey, restSecondsForSession, setInputKey, startElapsedSession, stopElapsedSession, touchSession, type ActiveWorkoutSession } from '../lib/workout-session';

interface WorkoutSessionOptions {
  workouts: WorkoutRow[];
  logs: WorkoutLog[];
  onHistoryChanged: () => Promise<void>;
  onCompleted: (event: WorkoutSessionEvent, rows: WorkoutRow[]) => void;
  playCue: (row?: WorkoutRow) => void;
  addToast: (message: string) => void;
}

/** Serializes phone session actions; durable transitions win before UI, cues, or health sync. */
export function useWorkoutSession(options: WorkoutSessionOptions) {
  const latest = useRef(options);
  latest.current = options;
  const current = useRef<ActiveWorkoutSession | null>(null);
  const queue = useRef<Promise<void>>(Promise.resolve());
  const actionInFlight = useRef(false);
  const timerQueued = useRef(false);
  const [session, setSession] = useState<ActiveWorkoutSession | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [restRemainingSeconds, setRestRemainingSeconds] = useState(0);

  const publish = useCallback((next: ActiveWorkoutSession | null) => {
    current.current = next;
    setSession(next);
    setElapsedSeconds(next ? elapsedSecondsForSession(next) : 0);
    setRestRemainingSeconds(next ? restSecondsForSession(next) : 0);
  }, []);
  const rowsFor = useCallback((value: ActiveWorkoutSession, workouts = latest.current.workouts) => value.rowIds
    .map((id) => workouts.find((row) => row.id === id)).filter((row): row is WorkoutRow => Boolean(row)), []);
  const enqueue = useCallback((operation: () => Promise<void>) => {
    const result = queue.current.then(operation);
    queue.current = result.catch((error) => {
      console.error('Could not save workout session:', error);
      latest.current.addToast('Could not save the workout. Try again.');
    });
    return queue.current;
  }, []);
  const userAction = useCallback((operation: () => Promise<void>) => {
    if (actionInFlight.current) return Promise.resolve();
    actionInFlight.current = true;
    return enqueue(operation).finally(() => { actionInFlight.current = false; });
  }, [enqueue]);
  const apply = useCallback(async (expected: ActiveWorkoutSession | null, next: WorkoutSessionTransition, rows: WorkoutRow[] = []) => {
    if (!await commitWorkoutSessionTransition(expected, next)) {
      const stored = await getRecord<ActiveWorkoutSession>(STORES.appState, ACTIVE_WORKOUT_SESSION_KEY);
      publish(stored?.schemaVersion === SCHEMA_VERSION ? normalizeActiveWorkoutSession(stored) : null);
      return false;
    }
    publish(next.session);
    if (next.event) latest.current.onCompleted(next.event, rows);
    if (next.setLog || next.exerciseLog || next.event) await latest.current.onHistoryChanged();
    return true;
  }, [publish]);

  const restore = useCallback(() => enqueue(async () => {
    const stored = await getRecord<ActiveWorkoutSession>(STORES.appState, ACTIVE_WORKOUT_SESSION_KEY);
    if (!stored || stored.schemaVersion !== SCHEMA_VERSION) { publish(null); return; }
    const value = normalizeActiveWorkoutSession(stored);
    const rows = rowsFor(value, await getAll<WorkoutRow>(STORES.workouts));
    const stale = closeHandledWorkoutSession(value, await getAll<WorkoutLog>(STORES.logs))
      ?? closeStaleWorkoutSession(value, rows, todayDateKey());
    if (stale) await apply(value, stale, rows);
    else publish(value);
  }), [apply, enqueue, publish, rowsFor]);

  useEffect(() => {
    if (!session) return;
    void enqueue(async () => {
      const value = current.current;
      if (!value) return;
      const closure = closeHandledWorkoutSession(value, latest.current.logs);
      if (closure) await apply(value, closure);
    });
  }, [apply, enqueue, options.logs, session]);

  const clear = useCallback(() => userAction(async () => { await apply(current.current, { session: null }); }), [apply, userAction]);
  const start = useCallback(() => userAction(async () => {
    if (current.current && ['active', 'resting', 'paused'].includes(current.current.status)) return;
    const rows = latest.current.workouts.filter((row) => row.id != null && row.day.toLowerCase() === todayName().toLowerCase())
      .sort((left, right) => left.time.localeCompare(right.time));
    if (!rows.length) { latest.current.addToast('Add a workout to today before starting the player.'); return; }
    const statuses = workoutStatusesOnDate(latest.current.logs, todayDateKey());
    const firstPendingIndex = rows.findIndex((row) => row.id != null && !statuses.has(row.id));
    if (firstPendingIndex < 0) { latest.current.addToast('Today’s plan is already handled.'); return; }
    await apply(current.current, { session: newPwaSession(rows, firstPendingIndex) });
  }), [apply, userAction]);

  const step = useCallback((action: 'complete' | 'skip') => userAction(async () => {
    const value = current.current;
    if (!value) return;
    const rows = rowsFor(value);
    const next = workoutStepTransition(value, rows, action);
    if (next) await apply(value, next, rows);
  }), [apply, rowsFor, userAction]);
  const updateInput = useCallback((updates: Partial<WorkoutSetInput>) => enqueue(async () => {
    const value = current.current;
    if (!value || value.status !== 'active') return;
    const row = rowsFor(value)[value.exerciseIndex];
    if (!row) return;
    await apply(value, { session: { ...touchSession(value), setInputs: {
      ...value.setInputs, [setInputKey(row, value.currentSet)]: { ...currentSetInput(value, row), ...updates },
    } } });
  }), [apply, enqueue, rowsFor]);
  const pause = useCallback(() => userAction(async () => {
    const value = current.current;
    if (!value || !['active', 'resting'].includes(value.status)) return;
    await apply(value, { session: {
      ...stopElapsedSession(value, 'paused_by_user'), status: 'paused', restUntilEpochMillis: null,
      pausedRestRemainingSeconds: value.status === 'resting' ? Math.max(1, restSecondsForSession(value)) : null,
      lastInteractionAtEpochMillis: Date.now(),
    } });
  }), [apply, userAction]);
  const resume = useCallback(() => userAction(async () => {
    const value = current.current;
    if (!value || value.status !== 'paused') return;
    const now = Date.now();
    const next = value.pausedRestRemainingSeconds != null ? {
      ...value, status: 'resting' as const, restUntilEpochMillis: now + value.pausedRestRemainingSeconds * 1000,
      pausedRestRemainingSeconds: null, elapsedStartedAtEpochMillis: now, lastStopReason: null, lastInteractionAtEpochMillis: now,
    } : touchSession(startElapsedSession({ ...value, status: 'active' }, now), now);
    await apply(value, { session: next });
  }), [apply, userAction]);
  const restart = useCallback(() => userAction(async () => {
    const value = current.current;
    if (!value) return;
    const rows = rowsFor(value);
    if (rows.length) await apply(value, { session: newPwaSession(rows) });
  }), [apply, rowsFor, userAction]);
  const end = useCallback(() => userAction(async () => {
    const value = current.current;
    if (!value || ['completed', 'ended'].includes(value.status)) return;
    const rows = rowsFor(value);
    const now = Date.now();
    const ended = finishElapsedSession(touchSession(value, now), 'ended', 'ended_by_user', now);
    await apply(value, { session: ended, event: workoutSessionEvent(ended, rows, now) }, rows);
  }), [apply, rowsFor, userAction]);
  const startRestNow = useCallback(() => userAction(async () => {
    const value = current.current;
    if (!value || value.status !== 'resting') return;
    await apply(value, { session: touchSession(startElapsedSession({ ...value, status: 'active', restUntilEpochMillis: null, pausedRestRemainingSeconds: null })) });
  }), [apply, userAction]);
  const addRestSeconds = useCallback((seconds: number) => userAction(async () => {
    const value = current.current;
    if (!value || value.status !== 'resting' || seconds <= 0) return;
    const now = Date.now();
    await apply(value, { session: { ...touchSession(value, now), restUntilEpochMillis: Math.max(value.restUntilEpochMillis ?? now, now) + seconds * 1000 } });
  }), [apply, userAction]);

  useEffect(() => {
    const tick = () => {
      const value = current.current;
      setElapsedSeconds(value ? elapsedSecondsForSession(value) : 0);
      setRestRemainingSeconds(value ? restSecondsForSession(value) : 0);
      if (!value || timerQueued.current) return;
      timerQueued.current = true;
      void enqueue(async () => {
        const value = current.current;
        if (!value) return;
        const now = Date.now();
        const rows = rowsFor(value);
        const stale = closeStaleWorkoutSession(value, rows, todayDateKey(), now);
        if (stale) {
          if (await apply(value, stale, rows) && stale.event) latest.current.addToast('Previous workout was closed because the day changed.');
        } else if (['active', 'resting'].includes(value.status) && now - value.lastInteractionAtEpochMillis > ACTIVE_SESSION_IDLE_TIMEOUT_MS) {
          if (await apply(value, { session: {
            ...stopElapsedSession(value, 'inactive_timeout', now), status: 'paused', restUntilEpochMillis: null,
            pausedRestRemainingSeconds: value.status === 'resting' ? Math.max(1, restSecondsForSession(value, now)) : null,
          } })) latest.current.addToast('Workout paused after 45 minutes without activity.');
        } else if (value.status === 'resting' && restSecondsForSession(value, now) <= 0) {
          const cueKey = restCueKey(value);
          if (await apply(value, { session: touchSession(startElapsedSession({
            ...value, status: 'active', restUntilEpochMillis: null, pausedRestRemainingSeconds: null, lastRestCueKey: cueKey,
          }, now), now) }) && value.lastRestCueKey !== cueKey) latest.current.playCue(rows[value.exerciseIndex]);
        }
      }).finally(() => { timerQueued.current = false; });
    };
    tick();
    if (!session || !['active', 'resting'].includes(session.status)) return;
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [apply, enqueue, rowsFor, session]);

  const rows = useMemo(() => session ? rowsFor(session, options.workouts) : [], [options.workouts, rowsFor, session]);
  const row = session ? rows[session.exerciseIndex] : undefined;
  const setInput = session && row ? currentSetInput(session, row) : defaultSetInput({
    schemaVersion: SCHEMA_VERSION, day: todayName(), time: '00:00', exercise: '', sets: 1, reps: '', rest: 0,
  });
  return { session, rows, row, setInput, elapsedSeconds, restRemainingSeconds, restore, clear, start,
    completeSet: () => step('complete'), skip: () => step('skip'), updateInput, pause, resume, restart, end, startRestNow, addRestSeconds };
}
