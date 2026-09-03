import { useEffect } from 'react';
import {
  checkScheduleAgainstNow,
  createDailyNotificationState,
  resetDailyNotificationStateIfNeeded,
  SCHEDULE_NOTIFICATION_POLL_INTERVAL_MS,
} from '../lib/notifications';
import { type WorkoutRow } from '../types';

export function useScheduleNotifications(workouts: WorkoutRow[], addToast: (message: string) => void) {
  useEffect(() => {
    const state = createDailyNotificationState();
    const check = () => {
      resetDailyNotificationStateIfNeeded(state);
      checkScheduleAgainstNow(workouts, state.notified, addToast);
    };
    check();
    const timer = window.setInterval(check, SCHEDULE_NOTIFICATION_POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [addToast, workouts]);
}
