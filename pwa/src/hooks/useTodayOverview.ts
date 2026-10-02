import { useMemo } from 'react';
import { buildTodayOverview } from '../lib/today-overview';
import type { WorkoutLog, WorkoutRow, WorkoutSetLog } from '../types';

export function useTodayOverview(workouts: WorkoutRow[], logs: WorkoutLog[], setLogs: WorkoutSetLog[], localToday: string) {
  return useMemo(() => buildTodayOverview(workouts, logs, setLogs, localToday), [workouts, logs, setLogs, localToday]);
}
