import { localDateKey } from './history-stats';
import { calculatePlanProgress, estimateLevelFor, estimateWorkoutDurationSeconds, formatEstimatedDuration, workoutStatusesOnDate } from './workout-planning';
import { type WorkoutLog, type WorkoutRow, type WorkoutSetLog } from '../types';
import { scheduleWeekday, workoutsOnDate } from './schedule-dates';

export function buildTodayOverview(workouts: WorkoutRow[], logs: WorkoutLog[], setLogs: WorkoutSetLog[], dateKey: string) {
  const dayName = scheduleWeekday(dateKey);
  const rows = workoutsOnDate(workouts, dateKey);
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
