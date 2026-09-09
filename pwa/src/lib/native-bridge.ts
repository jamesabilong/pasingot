import { SCHEMA_VERSION, type BodyMetricEntry, type WorkoutLog, type WorkoutRow, type WorkoutSessionEvent } from '../types';
import { addRecord, getRecord, putRecord, STORES } from './db';

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
  workoutPermissionGranted?: boolean;
  bodyWeightPermissionGranted?: boolean;
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
          writeBodyWeight?: (payload: HealthConnectBodyWeightPayload) => Promise<HealthConnectWriteResult>;
          deleteBodyWeight?: (payload: HealthConnectBodyWeightDeletePayload) => Promise<HealthConnectDeleteResult>;
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

interface HealthConnectBodyWeightPayload {
  clientRecordId: string;
  clientRecordVersion: number;
  time: string;
  kilograms: number;
}

interface HealthConnectBodyWeightDeletePayload {
  clientRecordId: string;
}

export interface HealthConnectWriteResult extends HealthConnectStatus {
  written: boolean;
}

export interface HealthConnectDeleteResult extends HealthConnectStatus {
  deleted: boolean;
}

const HEALTH_CONNECT_PENDING_KEY = 'healthConnectPendingWrites';
export const HEALTH_CONNECT_SETTINGS_KEY = 'healthConnectSettings';

interface HealthConnectPendingQueue {
  key: typeof HEALTH_CONNECT_PENDING_KEY;
  schemaVersion: number;
  operations?: HealthConnectPendingOperation[];
  writes?: HealthConnectWorkoutPayload[];
}

type HealthConnectPendingOperation =
  | { kind: 'workout-write'; payload: HealthConnectWorkoutPayload }
  | { kind: 'body-weight-write'; payload: HealthConnectBodyWeightPayload }
  | { kind: 'body-weight-delete'; payload: HealthConnectBodyWeightDeletePayload };

function pendingOperations(stored: HealthConnectPendingQueue | undefined): HealthConnectPendingOperation[] {
  if (stored?.schemaVersion !== SCHEMA_VERSION) return [];
  if (stored.operations) return stored.operations;
  return (stored.writes ?? []).map((payload) => ({ kind: 'workout-write' as const, payload }));
}

async function queuePendingHealthConnectOperation(operation: HealthConnectPendingOperation): Promise<void> {
  const stored = await getRecord<HealthConnectPendingQueue>(STORES.appState, HEALTH_CONNECT_PENDING_KEY);
  const operations = pendingOperations(stored);
  const clientRecordId = operation.payload.clientRecordId;
  const remaining = operations.filter((existing) => {
    if (existing.payload.clientRecordId !== clientRecordId) return true;
    if (operation.kind === 'workout-write') return existing.kind !== 'workout-write';
    return existing.kind === 'workout-write';
  });
  await putRecord(STORES.appState, {
    key: HEALTH_CONNECT_PENDING_KEY,
    schemaVersion: SCHEMA_VERSION,
    operations: [...remaining, operation],
  } satisfies HealthConnectPendingQueue);
}

async function clearPendingHealthConnectOperation(operation: HealthConnectPendingOperation): Promise<void> {
  const stored = await getRecord<HealthConnectPendingQueue>(STORES.appState, HEALTH_CONNECT_PENDING_KEY);
  const operations = pendingOperations(stored);
  const remaining = operations.filter((existing) => {
    if (existing.payload.clientRecordId !== operation.payload.clientRecordId) return true;
    if (operation.kind === 'workout-write') return existing.kind !== 'workout-write';
    return existing.kind === 'workout-write';
  });
  if (remaining.length === operations.length) return;
  await putRecord(STORES.appState, {
    key: HEALTH_CONNECT_PENDING_KEY,
    schemaVersion: SCHEMA_VERSION,
    operations: remaining,
  } satisfies HealthConnectPendingQueue);
}

// Unsuccessful mutations are retried on app-open/visibility triggers, matching the watch-log
// queue. The enabled check ensures turning sync off also pauses queued native mutations.
export async function drainPendingHealthConnectWrites(): Promise<number> {
  const bridge = window.Capacitor?.Plugins?.HealthConnectBridge;
  if (!bridge) return 0;
  const settings = await getRecord<{ schemaVersion: number; enabled: boolean }>(STORES.appState, HEALTH_CONNECT_SETTINGS_KEY);
  if (settings?.schemaVersion !== SCHEMA_VERSION || !settings.enabled) return 0;
  const stored = await getRecord<HealthConnectPendingQueue>(STORES.appState, HEALTH_CONNECT_PENDING_KEY);
  const operations = pendingOperations(stored);
  if (!operations.length) return 0;

  const remaining: HealthConnectPendingOperation[] = [];
  let succeeded = 0;
  for (const operation of operations) {
    try {
      let completed = false;
      if (operation.kind === 'workout-write') {
        completed = (await bridge.writeWorkoutSession(operation.payload)).written;
      } else if (operation.kind === 'body-weight-write' && bridge.writeBodyWeight) {
        completed = (await bridge.writeBodyWeight(operation.payload)).written;
      } else if (operation.kind === 'body-weight-delete' && bridge.deleteBodyWeight) {
        completed = (await bridge.deleteBodyWeight(operation.payload)).deleted;
      }
      if (completed) succeeded += 1;
      else remaining.push(operation);
    } catch (error) {
      console.error('Failed to retry queued Health Connect operation:', error);
      remaining.push(operation);
    }
  }
  await putRecord(STORES.appState, {
    key: HEALTH_CONNECT_PENDING_KEY,
    schemaVersion: SCHEMA_VERSION,
    operations: remaining,
  } satisfies HealthConnectPendingQueue);
  return succeeded;
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

  const payload: HealthConnectWorkoutPayload = {
    clientRecordId: `pasingot:${event.workoutEntryId}:${event.timestamp}`,
    clientRecordVersion: 1,
    title,
    notes,
    startTime: startTime.toISOString(),
    endTime: endTime.toISOString(),
  };
  try {
    const result = await bridge.writeWorkoutSession(payload);
    const operation = { kind: 'workout-write' as const, payload };
    if (result.written) await clearPendingHealthConnectOperation(operation);
    else await queuePendingHealthConnectOperation(operation);
    return result;
  } catch (error) {
    console.error('Failed to write workout session to Health Connect:', error);
    await queuePendingHealthConnectOperation({ kind: 'workout-write', payload });
    return { ...(await getHealthConnectStatus()), written: false };
  }
}

function bodyMetricRecordId(entryId: number): string {
  return `pasingot:body-weight:${entryId}`;
}

function bodyMetricTime(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day, 12).toISOString();
}

export async function writeBodyMetricToHealthConnect(entry: BodyMetricEntry): Promise<HealthConnectWriteResult> {
  const bridge = window.Capacitor?.Plugins?.HealthConnectBridge;
  if (!bridge?.writeBodyWeight || entry.id == null) {
    return { availability: 'unavailable', permissionGranted: false, written: false };
  }
  const payload: HealthConnectBodyWeightPayload = {
    clientRecordId: bodyMetricRecordId(entry.id),
    clientRecordVersion: Date.now(),
    time: bodyMetricTime(entry.date),
    kilograms: entry.unit === 'lb' ? entry.weight * 0.45359237 : entry.weight,
  };
  try {
    const result = await bridge.writeBodyWeight(payload);
    const operation = { kind: 'body-weight-write' as const, payload };
    if (result.written) await clearPendingHealthConnectOperation(operation);
    else await queuePendingHealthConnectOperation(operation);
    return result;
  } catch (error) {
    console.error('Failed to write body weight to Health Connect:', error);
    await queuePendingHealthConnectOperation({ kind: 'body-weight-write', payload });
    return { ...(await getHealthConnectStatus()), written: false };
  }
}

export async function deleteBodyMetricFromHealthConnect(entry: BodyMetricEntry): Promise<HealthConnectDeleteResult> {
  const bridge = window.Capacitor?.Plugins?.HealthConnectBridge;
  if (!bridge?.deleteBodyWeight || entry.id == null) {
    return { availability: 'unavailable', permissionGranted: false, deleted: false };
  }
  const payload: HealthConnectBodyWeightDeletePayload = { clientRecordId: bodyMetricRecordId(entry.id) };
  try {
    const result = await bridge.deleteBodyWeight(payload);
    const operation = { kind: 'body-weight-delete' as const, payload };
    if (result.deleted) await clearPendingHealthConnectOperation(operation);
    else await queuePendingHealthConnectOperation(operation);
    return result;
  } catch (error) {
    console.error('Failed to delete body weight from Health Connect:', error);
    await queuePendingHealthConnectOperation({ kind: 'body-weight-delete', payload });
    return { ...(await getHealthConnectStatus()), deleted: false };
  }
}

export async function discardPendingBodyMetricSync(entry: BodyMetricEntry): Promise<void> {
  if (entry.id == null) return;
  await clearPendingHealthConnectOperation({
    kind: 'body-weight-delete',
    payload: { clientRecordId: bodyMetricRecordId(entry.id) },
  });
}
