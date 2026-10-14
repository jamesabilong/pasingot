import type { QuestState, WorkoutRow } from '../types';
import { getAll, STORES, transact } from './db';
import { ensureCircuitScheduleSupported, getLatestWatchSession } from './native-bridge';
import { belongsToQuestRun } from './quest-progress';
import { circuitValueSignature, validateCircuitGroups } from './workout-circuits';

function prescription(rows: WorkoutRow[]): string {
  return circuitValueSignature(rows.map(({ id: _id, ...row }) => row));
}

function project(schedule: WorkoutRow[], state: QuestState, proposed: WorkoutRow[]) {
  const first = proposed[0];
  if (!first || state.status !== 'active' || proposed.some(row => !belongsToQuestRun(row, state)
    || row.questDayIndex !== state.nextDayIndex || row.day !== first.day || row.time !== first.time)
    || (proposed.some(row => row.circuit) && proposed.some(row => row.sets > 100)) || validateCircuitGroups(proposed)) throw new Error('The quest day contains an invalid group.');
  const saved = schedule.filter(row => belongsToQuestRun(row, state) && row.questDayIndex === state.nextDayIndex);
  if (saved.length) {
    if (prescription(saved) !== prescription(proposed)) throw new Error('This quest day is already saved with different settings. Review its schedule before continuing.');
    return { schedule, additions: [] as WorkoutRow[], removals: [] as WorkoutRow[] };
  }
  const completed = new Set(state.completedDays.map(day => day.dayIndex));
  const removals = schedule.filter(row => belongsToQuestRun(row, state) && row.questDayIndex != null
    && completed.has(row.questDayIndex) && row.day === first.day && row.time === first.time);
  const removed = new Set(removals.map(row => row.id));
  const remaining = schedule.filter(row => !removed.has(row.id));
  const occupied = remaining.filter(row => row.day === first.day && row.time === first.time);
  // A grouped day owns its slot; appending to an existing group changes its plan.
  if ((proposed.some(row => row.circuit) || occupied.some(row => row.circuit)) && occupied.length
    || proposed.some(row => occupied.some(existing => existing.exercise.toLowerCase() === row.exercise.toLowerCase()))) {
    throw new Error('Move this quest to an empty time; another session already uses this slot.');
  }
  const next = [...remaining, ...proposed];
  const invalid = validateCircuitGroups(next);
  if (invalid) throw new Error(invalid);
  return { schedule: next, additions: proposed, removals };
}

/** Hold the schedule, enrollment and player under one write lock. Native checks
 * run first; a changed durable projection rejects before any row is removed. */
export async function saveQuestSchedule(state: QuestState, proposed: WorkoutRow[], blocked = false): Promise<{ schedule: WorkoutRow[]; addedCount: number }> {
  state = structuredClone(state);
  proposed = structuredClone(proposed);
  const expected = await getAll<WorkoutRow>(STORES.workouts);
  const planned = project(expected, state, proposed);
  await ensureCircuitScheduleSupported(planned.schedule);
  if (planned.additions.length) {
    if (blocked) throw new Error('Finish or end the current workout before scheduling a quest day.');
    const watch = await getLatestWatchSession();
    if (watch && ['active', 'resting', 'paused'].includes(watch.status)) throw new Error('Finish or end the current watch workout before scheduling a quest day.');
  }
  let result = planned.schedule;
  let failure: Error | null = null;
  await transact([STORES.workouts, STORES.appState], transaction => {
    const store = transaction.objectStore(STORES.workouts);
    const current = store.getAll();
    const enrollment = transaction.objectStore(STORES.appState).get('questState');
    const session = transaction.objectStore(STORES.appState).get('activeWorkoutSession');
    let pending = 3;
    const finish = () => {
      if (--pending) return;
      try {
        if (circuitValueSignature(current.result) !== circuitValueSignature(expected)
          || circuitValueSignature(enrollment.result) !== circuitValueSignature(state)) {
          throw new Error('The schedule or quest changed while saving. Review it and try again.');
        }
        if (planned.additions.length && session.result && ['active', 'resting', 'paused'].includes(session.result.status)) {
          throw new Error('Finish or end the current phone workout before scheduling a quest day.');
        }
        const next = project(current.result, state, proposed);
        result = next.schedule;
        next.removals.forEach(row => { if (row.id != null) store.delete(row.id); });
        next.additions.forEach((row, index) => {
          const request = store.add(row);
          request.onsuccess = () => { result[result.length - next.additions.length + index] = { ...row, id: Number(request.result) }; };
        });
        // Completed players tied to replaced rows must not retain dead IDs.
        if (session.result?.rowIds?.some((id: number) => next.removals.some(row => row.id === id))) {
          transaction.objectStore(STORES.appState).delete('activeWorkoutSession');
        }
      } catch (error) {
        failure = error instanceof Error ? error : new Error('Could not save the quest day.');
        transaction.abort();
      }
    };
    current.onsuccess = finish;
    enrollment.onsuccess = finish;
    session.onsuccess = finish;
  }).catch(error => { throw failure ?? error; });
  return { schedule: result, addedCount: planned.additions.length };
}
