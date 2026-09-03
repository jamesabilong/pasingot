import { type WorkoutRow } from '../types';
import { todayDateKey } from './history-stats';
import { todayName } from './workout-planning';

const POLL_INTERVAL_MS = 30_000;
const NOTIFICATION_TOLERANCE_MINUTES = 1;

function currentHHMM(): string {
  const date = new Date();
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function withinTolerance(scheduled: string, current: string): boolean {
  const [scheduledHour, scheduledMinute] = scheduled.split(':').map(Number);
  const [currentHour, currentMinute] = current.split(':').map(Number);
  return Math.abs((scheduledHour * 60 + scheduledMinute) - (currentHour * 60 + currentMinute)) <= NOTIFICATION_TOLERANCE_MINUTES;
}

export function checkScheduleAgainstNow(workouts: WorkoutRow[], notified: Set<number>, addToast: (message: string) => void): void {
  const day = todayName().toLowerCase();
  workouts.filter((row) => row.day.toLowerCase() === day).forEach((row) => {
    if (row.id == null || notified.has(row.id) || !withinTolerance(row.time, currentHHMM())) return;
    notified.add(row.id);
    const title = `Workout Time - ${row.exercise}`;
    if ('Notification' in window && Notification.permission === 'granted') new Notification(title);
    else addToast(title);
  });
}

export function createDailyNotificationState() {
  return {
    notified: new Set<number>(),
    date: todayDateKey(),
  };
}

export function resetDailyNotificationStateIfNeeded(state: ReturnType<typeof createDailyNotificationState>): void {
  if (todayDateKey() === state.date) return;
  state.date = todayDateKey();
  state.notified.clear();
}

export const SCHEDULE_NOTIFICATION_POLL_INTERVAL_MS = POLL_INTERVAL_MS;
