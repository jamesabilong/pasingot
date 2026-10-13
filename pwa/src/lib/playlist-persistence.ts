import { SCHEMA_VERSION, WEEKDAYS, type PlaylistDraft, type WorkoutRow } from '../types';
import { getAll, STORES, transact } from './db';
import { ensureCircuitScheduleSupported, getLatestWatchSession } from './native-bridge';
import { circuitValueSignature, validateCircuitGroups } from './workout-circuits';
import { isWeightUnit, MAX_PLAYLIST_ITEMS, TIME_RE, validLoadWeight } from './workout-planning';

export interface PlaylistScheduleResult {
  schedule: WorkoutRow[];
  addedCount: number;
}

export function validatePlaylistScheduleDraft(draft: PlaylistDraft): string | null {
  if (!WEEKDAYS.includes(draft.day) || !TIME_RE.test(draft.time)
    || !Array.isArray(draft.items) || draft.items.length > MAX_PLAYLIST_ITEMS
    || draft.items.some(item => !item || !Number.isSafeInteger(item.sourceId)
      || typeof item.name !== 'string' || !item.name.trim()
      || !Number.isSafeInteger(item.sets) || item.sets <= 0
      || typeof item.reps !== 'string' || !item.reps.trim()
      || !Number.isSafeInteger(item.rest) || item.rest < 0
      || (item.loadWeight != null && validLoadWeight(item.loadWeight) == null)
      || (item.loadWeight != null && !isWeightUnit(item.loadUnit)))) {
    return 'Fix invalid day, time, sets, reps, rest, or load values before saving.';
  }
  const groupError = validateCircuitGroups(draft.items);
  if (groupError) return groupError;
  if (draft.items.some(item => item.circuit) && draft.items.some(item => item.sets > 100)) {
    return 'Workout plans containing circuits support 1–100 sets per exercise.';
  }
  return null;
}

function draftRows(draft: PlaylistDraft): WorkoutRow[] {
  return draft.items.map(item => ({
    schemaVersion: SCHEMA_VERSION, day: draft.day, time: draft.time,
    exercise: item.name, exerciseSourceId: item.sourceId,
    sets: item.sets, reps: item.reps.trim(), rest: item.rest,
    loadWeight: item.loadWeight ?? null,
    loadUnit: item.loadWeight != null ? item.loadUnit ?? 'kg' : null,
    ...(item.circuit ? { circuit: { ...item.circuit } } : {}),
  }));
}

/** UUIDs and database IDs do not change an otherwise identical prescription. */
function playlistSignature(rows: WorkoutRow[]): string {
  const groups = new Map<string, number>();
  return circuitValueSignature(rows.map(row => {
    if (row.circuit && !groups.has(row.circuit.id)) groups.set(row.circuit.id, groups.size);
    return {
      exercise: row.exercise.toLowerCase(), exerciseSourceId: row.exerciseSourceId ?? null,
      sets: row.sets, reps: row.reps, rest: row.rest,
      loadWeight: row.loadWeight ?? null, loadUnit: row.loadWeight != null ? row.loadUnit ?? 'kg' : null,
      circuit: row.circuit ? { group: groups.get(row.circuit.id), restSeconds: row.circuit.restSeconds } : null,
    };
  }));
}

function additionsFor(draft: PlaylistDraft, schedule: WorkoutRow[]): WorkoutRow[] {
  const rows = draftRows(draft);
  if (draft.items.some(item => item.circuit)) {
    const occupied = schedule.filter(row => row.day === draft.day && row.time === draft.time);
    if (!occupied.length) return rows;
    if (occupied.every(row => row.questId == null && row.questRunId == null
      && !Object.keys(row.dateOverrides ?? {}).length)
      && !validateCircuitGroups(occupied) && playlistSignature(occupied) === playlistSignature(rows)) return [];
    throw new Error('Another session already uses that day and time. Choose an empty time for this circuit playlist.');
  }
  // Keep the existing straight-set behavior: duplicate names are skipped and
  // other exercises may be added to the chosen time in one atomic write.
  const existing = new Set(schedule.map(row => `${row.day}|${row.time}|${row.exercise.toLowerCase()}`));
  return rows.filter(row => {
    const key = `${row.day}|${row.time}|${row.exercise.toLowerCase()}`;
    if (existing.has(key)) return false;
    existing.add(key);
    return true;
  });
}

/** Preflight the exact projection, then re-read schedule and phone cursor under
 * the write lock. No member can commit alone or overwrite a newer projection. */
export async function savePlaylistSchedule(draft: PlaylistDraft, options: {
  blocked?: boolean;
  beforeWrite?: (projected: WorkoutRow[]) => Promise<void>;
} = {}): Promise<PlaylistScheduleResult> {
  const error = validatePlaylistScheduleDraft(draft);
  if (error) throw new Error(error);
  draft = structuredClone(draft);
  const expected = await getAll<WorkoutRow>(STORES.workouts);
  const additions = additionsFor(draft, expected);
  const projected = [...expected, ...additions];
  const groupError = validateCircuitGroups(projected);
  if (groupError) throw new Error(groupError);
  await ensureCircuitScheduleSupported(projected);
  if (additions.length) {
    await options.beforeWrite?.(projected);
    if (options.blocked) throw new Error('Finish or end the current workout before adding a session.');
    const watch = await getLatestWatchSession();
    if (watch && ['active', 'resting', 'paused'].includes(watch.status)) {
      throw new Error('Finish or end the current watch workout before adding a session.');
    }
  }
  let result: WorkoutRow[] = [];
  let failure: Error | null = null;
  await transact([STORES.workouts, STORES.appState], transaction => {
    const store = transaction.objectStore(STORES.workouts);
    const current = store.getAll();
    const session = transaction.objectStore(STORES.appState).get('activeWorkoutSession');
    let pending = 2;
    const finish = () => {
      if (--pending) return;
      try {
        if (circuitValueSignature(current.result) !== circuitValueSignature(expected)) {
          throw new Error('The schedule changed while saving. Review the current schedule and try again.');
        }
        if (additions.length && session.result && ['active', 'resting', 'paused'].includes(session.result.status)) {
          throw new Error('Finish or end the current phone workout before adding a session.');
        }
        // Validate again in the durable transaction, including full group shape.
        const currentAdditions = additionsFor(draft, current.result);
        result = [...current.result, ...currentAdditions];
        const invalid = validateCircuitGroups(result);
        if (invalid) throw new Error(invalid);
        currentAdditions.forEach((row, index) => {
          const request = store.add(row);
          request.onsuccess = () => { result[current.result.length + index] = { ...row, id: Number(request.result) }; };
        });
      } catch (error) {
        failure = error instanceof Error ? error : new Error('Could not save the playlist.');
        transaction.abort();
      }
    };
    current.onsuccess = finish;
    session.onsuccess = finish;
  }).catch(error => { throw failure ?? error; });
  return { schedule: result, addedCount: additions.length };
}
