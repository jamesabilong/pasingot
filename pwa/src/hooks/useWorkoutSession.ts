import { parseSetEffort } from '../lib/set-effort';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { WorkoutSetInput } from '../types';
import { SCHEMA_VERSION, type WorkoutLog, type WorkoutRow, type WorkoutSessionEvent } from '../types';
import { getAll, getRecord, STORES } from '../lib/db';
import { todayDateKey } from '../lib/history-stats';
import { todayName, workoutStatusesOnDate } from '../lib/workout-planning';
import { workoutsOnDate } from '../lib/schedule-dates';
import { validateCircuitGroups } from '../lib/workout-circuits';
import { closeHandledWorkoutSession, closeStaleWorkoutSession, commitWorkoutSessionTransition, reconcileCircuitSessionHistory, workoutSessionEvent, workoutStepTransition, type WorkoutSessionTransition } from '../lib/workout-session-persistence';
import { ACTIVE_SESSION_IDLE_TIMEOUT_MS, ACTIVE_WORKOUT_SESSION_KEY, circuitSessionRowsMatch, currentSetInput, defaultSetInput, elapsedSecondsForSession, finishElapsedSession, newPwaSession, normalizeActiveWorkoutSession, restCueKey, restSecondsForSession, setInputKey, startElapsedSession, stopElapsedSession, touchSession, type ActiveWorkoutSession } from '../lib/workout-session';

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
  const [planChanged, setPlanChanged] = useState(false);

  const publish = useCallback((next: ActiveWorkoutSession | null) => {
    current.current = next;
    setSession(next);
    setElapsedSeconds(next ? elapsedSecondsForSession(next) : 0);
    setRestRemainingSeconds(next ? restSecondsForSession(next) : 0);
    if (!next || ['completed', 'ended'].includes(next.status)) setPlanChanged(false);
  }, []);
  const rowsFor = useCallback((value: ActiveWorkoutSession, workouts = latest.current.workouts) => {
    if (['completed', 'ended'].includes(value.status) && value.plannedRows) return value.plannedRows;
    const projected = workoutsOnDate(workouts, value.planDate);
    if (value.steps && !circuitSessionRowsMatch(value, projected)) return [];
    const rows = value.rowIds.map(id => projected.find(row => row.id === id)
      ?? (['completed', 'ended'].includes(value.status) ? workouts.find(row => row.id === id) : undefined))
      .filter((row): row is WorkoutRow => Boolean(row));
    return circuitSessionRowsMatch(value, rows) ? rows : [];
  }, []);
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
  const apply = useCallback(async (expected: ActiveWorkoutSession | null, next: WorkoutSessionTransition, rows: WorkoutRow[] = [], checkSchedule = false) => {
    if (!await commitWorkoutSessionTransition(expected, next, checkSchedule ? rows : undefined)) {
      const stored = await getRecord<ActiveWorkoutSession>(STORES.appState, ACTIVE_WORKOUT_SESSION_KEY);
      const current = stored?.schemaVersion === SCHEMA_VERSION ? normalizeActiveWorkoutSession(stored) : null;
      if (current?.steps && ['active', 'resting', 'paused'].includes(current.status)) {
        setPlanChanged(!rowsFor(current, await getAll<WorkoutRow>(STORES.workouts)).length);
      }
      publish(current);
      return false;
    }
    publish(next.session);
    if (next.event) latest.current.onCompleted(next.event, rows);
    if (next.setLog || next.exerciseLog || next.event) await latest.current.onHistoryChanged();
    return true;
  }, [publish, rowsFor]);

  const restore = useCallback(() => enqueue(async () => {
    const stored = await getRecord<ActiveWorkoutSession>(STORES.appState, ACTIVE_WORKOUT_SESSION_KEY);
    if (!stored || stored.schemaVersion !== SCHEMA_VERSION) { publish(null); return; }
    const value = normalizeActiveWorkoutSession(stored);
    const rows = rowsFor(value, await getAll<WorkoutRow>(STORES.workouts));
    setPlanChanged(Boolean(value.steps && ['active', 'resting', 'paused'].includes(value.status) && !rows.length));
    const logs = await getAll<WorkoutLog>(STORES.logs);
    const stale = closeHandledWorkoutSession(value, logs)
      ?? closeStaleWorkoutSession(value, rows.length ? rows : value.plannedRows ?? rows, todayDateKey());
    if (stale) await apply(value, stale, rows);
    else {
      const reconciled = reconcileCircuitSessionHistory(value, rows, logs);
      if (reconciled) await apply(value, { session: reconciled });
      else publish(value);
      if (value.steps && !rows.length) latest.current.addToast('This circuit workout’s saved plan changed. End it before starting again.');
    }
  }), [apply, enqueue, publish, rowsFor]);

  useEffect(() => {
    if (!session) return;
    void enqueue(async () => {
      const value = current.current;
      if (!value) return;
      const closure = closeHandledWorkoutSession(value, latest.current.logs);
      if (closure) await apply(value, closure);
      else {
        const reconciled = reconcileCircuitSessionHistory(value, rowsFor(value), latest.current.logs);
        if (reconciled) await apply(value, { session: reconciled });
      }
    });
  }, [apply, enqueue, options.logs, rowsFor, session]);

  const clear = useCallback(() => userAction(async () => { await apply(current.current, { session: null }); }), [apply, userAction]);
  const start = useCallback(() => userAction(async () => {
    if (current.current && ['active', 'resting', 'paused'].includes(current.current.status)) return;
    const rows = workoutsOnDate(await getAll<WorkoutRow>(STORES.workouts), todayDateKey()).filter(row => row.id != null);
    if (!rows.length) { latest.current.addToast('Add a workout to today before starting the player.'); return; }
    const error = validateCircuitGroups(rows);
    if (error) { latest.current.addToast(error); return; }
    const grouped = rows.some(row => row.circuit !== undefined);
    const statuses = workoutStatusesOnDate(grouped ? await getAll<WorkoutLog>(STORES.logs) : latest.current.logs, todayDateKey());
    const firstPendingIndex = rows.findIndex((row) => row.id != null && !statuses.has(row.id));
    if (firstPendingIndex < 0) { latest.current.addToast('Today’s plan is already handled.'); return; }
    let next: ActiveWorkoutSession;
    try { next = newPwaSession(rows, firstPendingIndex, grouped ? [...statuses.keys()] : []); }
    catch (cause) { latest.current.addToast(cause instanceof Error ? cause.message : 'This circuit plan is invalid.'); return; }
    if (!await apply(current.current, { session: next }, rows, true)) latest.current.addToast('The schedule changed. Try starting the workout again.');
    else setPlanChanged(false);
  }), [apply, userAction]);

  const step = useCallback((action: 'complete' | 'skip') => userAction(async () => {
    let value = current.current;
    if (!value) return;
    const rows = rowsFor(value, value.steps ? await getAll<WorkoutRow>(STORES.workouts) : latest.current.workouts);
    if (value.steps && rows.length) {
      const logs = await getAll<WorkoutLog>(STORES.logs);
      const closure = closeHandledWorkoutSession(value, logs);
      if (closure) { await apply(value, closure); return; }
      const reconciled = reconcileCircuitSessionHistory(value, rows, logs);
      if (reconciled) {
        const cursorChanged = reconciled.exerciseIndex !== value.exerciseIndex || reconciled.currentSet !== value.currentSet;
        if (!await apply(value, { session: reconciled })) return;
        value = reconciled;
        if (cursorChanged) { latest.current.addToast('That exercise was already handled in another window.'); return; }
      }
    }
    const row = rows[value.exerciseIndex];
    if (value.steps && !row) { setPlanChanged(true); latest.current.addToast('This circuit workout’s saved plan changed. End it before starting again.'); return; }
    if (action === 'complete' && value.status === 'active' && row) {
      const effort = parseSetEffort(currentSetInput(value, row));
      if (effort.error) { latest.current.addToast(effort.error); return; }
    }
    const next = workoutStepTransition(value, rows, action);
    if (next && !await apply(value, next, rows) && value.steps) latest.current.addToast('The circuit workout changed in another window. Check the current set before continuing.');
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
    let value = current.current;
    if (!value || value.status !== 'paused') return;
    if (value.steps) {
      const rows = rowsFor(value, await getAll<WorkoutRow>(STORES.workouts));
      if (!rows.length) { setPlanChanged(true); latest.current.addToast('This circuit workout’s saved plan changed. End it before starting again.'); return; }
      const logs = await getAll<WorkoutLog>(STORES.logs);
      const closure = closeHandledWorkoutSession(value, logs);
      if (closure) { await apply(value, closure); return; }
      const reconciled = reconcileCircuitSessionHistory(value, rows, logs);
      if (reconciled) {
        if (!await apply(value, { session: reconciled })) return;
        value = reconciled;
      }
    }
    const now = Date.now();
    const next = value.pausedRestRemainingSeconds != null ? {
      ...value, status: 'resting' as const, restUntilEpochMillis: now + value.pausedRestRemainingSeconds * 1000,
      pausedRestRemainingSeconds: null, elapsedStartedAtEpochMillis: now, lastStopReason: null, lastInteractionAtEpochMillis: now,
    } : touchSession(startElapsedSession({ ...value, status: 'active' }, now), now);
    await apply(value, { session: next });
  }), [apply, rowsFor, userAction]);
  const restart = useCallback(() => userAction(async () => {
    const value = current.current;
    if (!value) return;
    const rows = rowsFor(value);
    if (value.steps && rows.length) {
      const statuses = workoutStatusesOnDate(await getAll<WorkoutLog>(STORES.logs), value.planDate);
      const firstPendingIndex = rows.findIndex(row => row.id != null && !statuses.has(row.id));
      if (firstPendingIndex < 0) { latest.current.addToast('Today’s plan is already handled.'); return; }
      await apply(value, { session: newPwaSession(rows, firstPendingIndex, [...statuses.keys()]) }, rows, true);
    } else if (rows.length) await apply(value, { session: newPwaSession(rows) });
    else if (value.steps) latest.current.addToast('This circuit workout’s saved plan changed. End it before starting again.');
  }), [apply, rowsFor, userAction]);
  const end = useCallback(() => userAction(async () => {
    const value = current.current;
    if (!value || ['completed', 'ended'].includes(value.status)) return;
    const rows = rowsFor(value).length ? rowsFor(value) : value.plannedRows ?? [];
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
        const stale = closeStaleWorkoutSession(value, rows.length ? rows : value.plannedRows ?? rows, todayDateKey(), now);
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

  const rows = useMemo(() => session && (!planChanged || ['completed', 'ended'].includes(session.status)) ? rowsFor(session, options.workouts) : [], [options.workouts, planChanged, rowsFor, session]);
  const row = session ? rows[session.exerciseIndex] : undefined;
  const setInput = session && row ? currentSetInput(session, row) : defaultSetInput({
    schemaVersion: SCHEMA_VERSION, day: todayName(), time: '00:00', exercise: '', sets: 1, reps: '', rest: 0,
  });
  return { session, rows, row, setInput, elapsedSeconds, restRemainingSeconds, restore, clear, start,
    completeSet: () => step('complete'), skip: () => step('skip'), updateInput, pause, resume, restart, end, startRestNow, addRestSeconds };
}
