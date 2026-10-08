import type { WorkoutSetLog } from '../types';

export function filterSetHistory(logs: WorkoutSetLog[], search: string, ratedOnly: boolean): WorkoutSetLog[] {
  const query = search.trim().toLowerCase();
  return logs.filter(log => (!query || log.exercise.toLowerCase().includes(query))
    && (!ratedOnly || log.rpe != null || log.rir != null))
    .sort((a, b) => Date.parse(b.date) - Date.parse(a.date) || (b.id ?? 0) - (a.id ?? 0));
}
