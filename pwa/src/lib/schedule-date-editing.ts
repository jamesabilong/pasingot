import type { WorkoutLog, WorkoutRow, WorkoutSetLog } from '../types';
import { STORES, transact } from './db';
import { localDateKey, todayDateKey } from './history-stats';
import { isScheduleDate, scheduleWeekday, workoutsOnDate } from './schedule-dates';
import { validateWeeklySession } from './schedule-editing';
import { TIME_RE } from './workout-planning';

export type DateScheduleChange = { date: string; time: string } | 'skip' | 'clear';

export function changedDateSchedule(rows: WorkoutRow[], expected: WorkoutRow[], sourceDate: string,
  change: DateScheduleChange, today: string, history: Array<WorkoutLog | WorkoutSetLog> = []): WorkoutRow[] {
  const stale = validateWeeklySession(rows, expected);
  if (stale) throw new Error(stale);
  if (!isScheduleDate(sourceDate) || sourceDate < today) throw new Error('Choose today or a future occurrence.');
  if (change !== 'clear' && scheduleWeekday(sourceDate) !== expected[0].day) throw new Error(`Choose a ${expected[0].day} occurrence.`);
  if (typeof change === 'object' && (!isScheduleDate(change.date) || change.date < today || !TIME_RE.test(change.time))) {
    throw new Error('Choose today or a future date and a valid start time.');
  }
  const ids = new Set(expected.map(row => row.id));
  const affected = new Set([sourceDate]);
  for (const row of expected) {
    const previous = row.dateOverrides?.[sourceDate];
    if (previous) affected.add(previous.date);
  }
  if (typeof change === 'object') affected.add(change.date);
  if (history.some(log => ids.has(log.workoutRowId ?? undefined) && affected.has(localDateKey(log.date)))) {
    throw new Error('This occurrence already has workout history. Choose an unrecorded date.');
  }
  const result = rows.map(row => {
    if (!ids.has(row.id)) return row;
    const dateOverrides = { ...row.dateOverrides };
    if (change === 'clear') delete dateOverrides[sourceDate];
    else dateOverrides[sourceDate] = change === 'skip' ? null : change;
    const { dateOverrides: _old, ...base } = row;
    return Object.keys(dateOverrides).length ? { ...base, dateOverrides } : base;
  });
  // Check both occupied time slots and a second occurrence of the same row ID.
  // The player/logging identity is date + row ID, so two on one date are unsafe.
  for (const date of affected) {
    const projected = workoutsOnDate(result, date);
    const edited = projected.filter(row => ids.has(row.id));
    if (edited.some(row => projected.filter(other => other.id === row.id).length > 1)) {
      throw new Error('That date already contains another occurrence of this session. Skip or move it first.');
    }
    if (edited.some(row => projected.some(other => !ids.has(other.id) && other.time === row.time))) {
      throw new Error('Another session already uses that date and time. Choose a different time.');
    }
  }
  return result;
}

export async function changeSessionDate(expected: WorkoutRow[], sourceDate: string, change: DateScheduleChange): Promise<WorkoutRow[]> {
  let result: WorkoutRow[] = [];
  let failure: Error | null = null;
  await transact([STORES.workouts, STORES.appState, STORES.logs, STORES.setLogs], transaction => {
    const workouts = transaction.objectStore(STORES.workouts);
    const schedule = workouts.getAll();
    const session = transaction.objectStore(STORES.appState).get('activeWorkoutSession');
    const logs = transaction.objectStore(STORES.logs).getAll();
    const sets = transaction.objectStore(STORES.setLogs).getAll();
    let pending = 4;
    const finish = () => {
      if (--pending) return;
      try {
        if (session.result && ['active', 'resting', 'paused'].includes(session.result.status)) {
          throw new Error('Finish or end the current phone workout before changing a session.');
        }
        result = changedDateSchedule(schedule.result, expected, sourceDate, change, todayDateKey(), [...logs.result, ...sets.result]);
      } catch (error) { failure = error instanceof Error ? error : new Error('Could not change the occurrence.'); return; }
      try {
        const ids = new Set(expected.map(row => row.id));
        result.filter(row => ids.has(row.id)).forEach(row => workouts.put(row));
        const cursor = session.result;
        if (cursor && ['completed', 'ended'].includes(cursor.status)
          && cursor.rowIds?.some((id: number) => ids.has(id))) transaction.objectStore(STORES.appState).delete('activeWorkoutSession');
      } catch { transaction.abort(); }
    };
    schedule.onsuccess = finish;
    session.onsuccess = finish;
    logs.onsuccess = finish;
    sets.onsuccess = finish;
  });
  if (failure) throw failure;
  return result;
}
