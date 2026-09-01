import { SCHEMA_VERSION, type WorkoutLog, type WorkoutRow } from '../types';
import { addRecord, STORES } from './db';

interface PendingWatchLog {
  id: string;
  schemaVersion?: number;
  timestamp: string;
  exercise: string;
  status: WorkoutLog['status'];
  workoutRowId?: number | null;
}

declare global {
  interface Window {
    Capacitor?: {
      Plugins?: {
        ScheduleSync?: { syncSchedule: (payload: { rows: WorkoutRow[] }) => Promise<void> };
        WorkoutLogBridge?: {
          getPendingLogs: () => Promise<{ logs?: PendingWatchLog[] }>;
          ackLogs: (payload: { ids: string[] }) => Promise<void>;
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
    return logs.length;
  } catch (error) {
    console.error('Failed to drain pending watch logs:', error);
    return 0;
  }
}
