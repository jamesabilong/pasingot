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

export type HealthConnectAvailability = 'available' | 'provider_update_required' | 'unavailable';

export interface HealthConnectStatus {
  availability: HealthConnectAvailability;
  permissionGranted: boolean;
}

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
        HealthConnectBridge?: {
          getStatus: () => Promise<HealthConnectStatus>;
          requestHealthConnectPermissions: () => Promise<{ opened: boolean; availability: HealthConnectAvailability }>;
          writeWorkoutSession: (payload: HealthConnectWorkoutPayload) => Promise<HealthConnectWriteResult>;
        };
      };
    };
  }
}

interface HealthConnectWorkoutPayload {
  clientRecordId: string;
  clientRecordVersion: number;
  title: string;
  notes?: string;
  startTime: string;
  endTime: string;
}

export interface HealthConnectWriteResult extends HealthConnectStatus {
  written: boolean;
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

export async function getHealthConnectStatus(): Promise<HealthConnectStatus> {
  const bridge = window.Capacitor?.Plugins?.HealthConnectBridge;
  if (!bridge) return { availability: 'unavailable', permissionGranted: false };
  try {
    return await bridge.getStatus();
  } catch (error) {
    console.error('Failed to read Health Connect status:', error);
    return { availability: 'unavailable', permissionGranted: false };
  }
}

export async function requestHealthConnectPermissions(): Promise<HealthConnectStatus> {
  const bridge = window.Capacitor?.Plugins?.HealthConnectBridge;
  if (!bridge) return { availability: 'unavailable', permissionGranted: false };
  try {
    await bridge.requestHealthConnectPermissions();
    return await getHealthConnectStatus();
  } catch (error) {
    console.error('Failed to request Health Connect permissions:', error);
    return await getHealthConnectStatus();
  }
}

export async function writeSessionEventToHealthConnect(
  event: WorkoutSessionEvent,
  rows: WorkoutRow[],
): Promise<HealthConnectWriteResult> {
  const bridge = window.Capacitor?.Plugins?.HealthConnectBridge;
  if (!bridge) return { availability: 'unavailable', permissionGranted: false, written: false };
  if (event.eventType !== 'completed' || event.elapsedSeconds <= 0) {
    return { ...(await getHealthConnectStatus()), written: false };
  }

  const endTime = new Date(event.timestamp);
  const startTime = new Date(endTime.getTime() - event.elapsedSeconds * 1000);
  const exerciseNames = rows.map((row) => row.exercise).filter(Boolean);
  const title = exerciseNames.length === 1 ? exerciseNames[0] : `Workout (${exerciseNames.length || event.totalExercises} exercises)`;
  const notes = exerciseNames.length ? exerciseNames.join(', ') : undefined;

  try {
    return await bridge.writeWorkoutSession({
      clientRecordId: `pasingot:${event.workoutEntryId}:${event.timestamp}`,
      clientRecordVersion: 1,
      title,
      notes,
      startTime: startTime.toISOString(),
      endTime: endTime.toISOString(),
    });
  } catch (error) {
    console.error('Failed to write workout session to Health Connect:', error);
    return { ...(await getHealthConnectStatus()), written: false };
  }
}
