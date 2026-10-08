import { WEEKDAYS, type Weekday, type WorkoutRow } from '../types';
import { TIME_RE } from './workout-planning';
import { STORES, transact } from './db';

export function validateScheduleMove(rows: WorkoutRow[], expected: WorkoutRow[], day: Weekday, time: string): string | null {
  if (!WEEKDAYS.includes(day) || !TIME_RE.test(time)) return 'Choose a valid weekday and start time.';
  if (!expected.length || expected.some(row => row.id == null || row.questId != null)) return 'Quest sessions must be managed from Quests.';
  const ids = new Set(expected.map(row => row.id));
  if (ids.size !== expected.length || expected.some(row => row.day !== expected[0].day || row.time !== expected[0].time)) return 'Choose one complete weekly session.';
  const current = rows.filter(row => row.day === expected[0].day && row.time === expected[0].time);
  if (current.length !== expected.length || expected.some(row => {
    const saved = current.find(value => value.id === row.id);
    return !saved || JSON.stringify(saved) !== JSON.stringify(row);
  })) return 'This session changed. Reopen its schedule controls and try again.';
  if (rows.some(row => !ids.has(row.id) && row.day === day && row.time === time)) return 'Another session already uses that day and time. Choose a different time.';
  return null;
}

/** Read the current session and schedule inside one transaction so stale edits
 * cannot replace a changed group or edit a saved unfinished phone workout. */
export async function moveWeeklySession(expected: WorkoutRow[], day: Weekday, time: string): Promise<WorkoutRow[]> {
  let result: WorkoutRow[] = [];
  let error: string | null = null;
  await transact([STORES.workouts, STORES.appState], transaction => {
    const workouts = transaction.objectStore(STORES.workouts);
    const schedule = workouts.getAll();
    const session = transaction.objectStore(STORES.appState).get('activeWorkoutSession');
    let pending = 2;
    const finish = () => {
      if (--pending) return;
      if (session.result && ['active', 'resting', 'paused'].includes(session.result.status)) error = 'Finish or end the current phone workout before moving a session.';
      else error = validateScheduleMove(schedule.result, expected, day, time);
      if (error) return;
      const ids = new Set(expected.map(row => row.id));
      result = (schedule.result as WorkoutRow[]).map(row => ids.has(row.id) ? { ...row, day, time } : row);
      result.filter(row => ids.has(row.id)).forEach(row => workouts.put(row));
    };
    schedule.onsuccess = finish;
    session.onsuccess = finish;
  });
  if (error) throw new Error(error);
  return result;
}
