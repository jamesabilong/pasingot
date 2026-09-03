import { SCHEMA_VERSION, type WorkoutLog, type WorkoutRow, type WorkoutSessionEvent } from '../types';
import { addRecord, STORES } from './db';

interface PendingWatchLog {
  id: string;
  schemaVersion?: number;
  timestamp: string;
  exercise: string;
  status: WorkoutLog['status'];
  workoutRowId?: number | null;
}

type PendingWatchSessionEvent = Omit<WorkoutSessionEvent, 'id'> & {
  id: string;
};

declare global {
  interface Window {
    Capacitor?: {
      Plugins?: {
        ScheduleSync?: { syncSchedule: (payload: { rows: WorkoutRow[] }) => Promise<void> };
        WorkoutLogBridge?: {
          getPendingLogs: () => Promise<{ logs?: PendingWatchLog[] }>;
          ackLogs: (payload: { ids: string[] }) => Promise<void>;
          getPendingSessionEvents?: () => Promise<{ events?: PendingWatchSessionEvent[] }>;
          ackSessionEvents?: (payload: { ids: string[] }) => Promise<void>;
        };
      };
    };
  }
}

export async function pushScheduleToNative(rows: WorkoutRow[]): Promise<void> {
  const bridge = window.Capacitor?.Plugins?.ScheduleSync;
  if (!bridge) return;
  try {
    await bridge.syncSchedule({ rows });
  } catch (error) {
    console.error('Failed to sync schedule to native:', error);
  }
}

export async function drainPendingWatchLogs(): Promise<number> {
  const bridge = window.Capacitor?.Plugins?.WorkoutLogBridge;
  if (!bridge) return 0;
  try {
    const { logs = [] } = await bridge.getPendingLogs();
    for (const log of logs) {
      await addRecord(STORES.logs, {
        schemaVersion: log.schemaVersion ?? SCHEMA_VERSION,
        date: log.timestamp,
        exercise: log.exercise,
        status: log.status,
        workoutRowId: log.workoutRowId ?? null,
      } satisfies WorkoutLog);
    }
    if (logs.length) await bridge.ackLogs({ ids: logs.map((log) => log.id) });

    const { events = [] } = bridge.getPendingSessionEvents ? await bridge.getPendingSessionEvents() : {};
    for (const event of events) {
      await addRecord(STORES.sessionEvents, {
        schemaVersion: event.schemaVersion ?? SCHEMA_VERSION,
        workoutEntryId: event.workoutEntryId,
        workoutDate: event.workoutDate,
        eventType: event.eventType,
        stopReason: event.stopReason,
        timestamp: event.timestamp,
        elapsedSeconds: event.elapsedSeconds,
        estimatedDurationSeconds: event.estimatedDurationSeconds ?? null,
        exerciseIndex: event.exerciseIndex,
        currentSet: event.currentSet,
        totalExercises: event.totalExercises,
        currentExercise: event.currentExercise ?? null,
      } satisfies WorkoutSessionEvent);
    }
    if (events.length && bridge.ackSessionEvents) await bridge.ackSessionEvents({ ids: events.map((event) => event.id) });
    return logs.length + events.length;
  } catch (error) {
    console.error('Failed to drain pending watch logs/session events:', error);
    return 0;
  }
}
