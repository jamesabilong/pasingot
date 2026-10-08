import { WEEKDAYS, type Weekday, type WorkoutRow } from '../types';
import { TIME_RE } from './workout-planning';
import { STORES, transact } from './db';

export function validateWeeklySession(rows: WorkoutRow[], expected: WorkoutRow[]): string | null {
  if (!expected.length || expected.some(row => row.id == null || row.questId != null)) return 'Quest sessions must be managed from Quests.';
  const ids = new Set(expected.map(row => row.id));
  if (ids.size !== expected.length || expected.some(row => row.day !== expected[0].day || row.time !== expected[0].time)) return 'Choose one complete weekly session.';
  const current = rows.filter(row => row.day === expected[0].day && row.time === expected[0].time);
  if (current.length !== expected.length || expected.some(row => {
    const saved = current.find(value => value.id === row.id);
    return !saved || JSON.stringify(saved) !== JSON.stringify(row);
  })) return 'This session changed. Reopen its schedule controls and try again.';
  return null;
}

export function validateScheduleMove(rows: WorkoutRow[], expected: WorkoutRow[], day: Weekday, time: string): string | null {
  if (!WEEKDAYS.includes(day) || !TIME_RE.test(time)) return 'Choose a valid weekday and start time.';
  const error = validateWeeklySession(rows, expected);
  if (error) return error;
  const ids = new Set(expected.map(row => row.id));
  if (rows.some(row => !ids.has(row.id) && row.day === day && row.time === time)) return 'Another session already uses that day and time. Choose a different time.';
  return null;
}

/** Read the current session and schedule inside one transaction so stale edits
 * cannot replace a changed group or edit a saved unfinished phone workout. */
async function editWeeklySession(expected: WorkoutRow[], destination: { day: Weekday; time: string } | null): Promise<WorkoutRow[]> {
  let result: WorkoutRow[] = [];
  let error: string | null = null;
  await transact([STORES.workouts, STORES.appState], transaction => {
    const workouts = transaction.objectStore(STORES.workouts);
    const schedule = workouts.getAll();
    const session = transaction.objectStore(STORES.appState).get('activeWorkoutSession');
    let pending = 2;
    const finish = () => {
      if (--pending) return;
      if (session.result && ['active', 'resting', 'paused'].includes(session.result.status)) error = 'Finish or end the current phone workout before changing a session.';
      else error = destination ? validateScheduleMove(schedule.result, expected, destination.day, destination.time) : validateWeeklySession(schedule.result, expected);
      if (error) return;
      const ids = new Set(expected.map(row => row.id));
      if (destination) {
        result = (schedule.result as WorkoutRow[]).map(row => ids.has(row.id) ? { ...row, ...destination } : row);
        result.filter(row => ids.has(row.id)).forEach(row => workouts.put(row));
      } else {
        result = (schedule.result as WorkoutRow[]).filter(row => !ids.has(row.id));
        expected.forEach(row => workouts.delete(row.id!));
        // A finished player retains row IDs for its inline summary. Removing
        // those rows must dismiss that cursor, while keeping durable history.
        const cursor = session.result;
        if (cursor && ['completed', 'ended'].includes(cursor.status)
          && Array.isArray(cursor.rowIds) && cursor.rowIds.some((id: number) => ids.has(id))) {
          transaction.objectStore(STORES.appState).delete('activeWorkoutSession');
        }
      }
    };
    schedule.onsuccess = finish;
    session.onsuccess = finish;
  });
  if (error) throw new Error(error);
  return result;
}

export function moveWeeklySession(expected: WorkoutRow[], day: Weekday, time: string): Promise<WorkoutRow[]> {
  return editWeeklySession(expected, { day, time });
}

export function removeWeeklySession(expected: WorkoutRow[]): Promise<WorkoutRow[]> {
  return editWeeklySession(expected, null);
}
