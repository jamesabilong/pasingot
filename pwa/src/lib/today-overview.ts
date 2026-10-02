import { localDateKey } from './history-stats';
import { calculatePlanProgress, estimateLevelFor, estimateWorkoutDurationSeconds, formatEstimatedDuration, workoutStatusesOnDate } from './workout-planning';
import { WEEKDAYS, type WorkoutLog, type WorkoutRow, type WorkoutSetLog } from '../types';

export function buildTodayOverview(workouts: WorkoutRow[], logs: WorkoutLog[], setLogs: WorkoutSetLog[], dateKey: string) {
  const dayName = WEEKDAYS[new Date(`${dateKey}T12:00:00`).getDay()];
  const rows = workouts.filter(row => row.day.toLowerCase() === dayName.toLowerCase())
    .sort((left, right) => left.time.localeCompare(right.time));
  const statuses = workoutStatusesOnDate(logs, dateKey);
  return {
    dayName,
    workouts: rows,
    estimate: formatEstimatedDuration(estimateWorkoutDurationSeconds(rows, estimateLevelFor(rows))),
    statuses,
    progress: calculatePlanProgress(rows, statuses),
    setLogCount: setLogs.filter(entry => localDateKey(entry.date) === dateKey).length,
  };
}
