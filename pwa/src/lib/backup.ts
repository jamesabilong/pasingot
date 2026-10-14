import { getAll, replaceStores, STORES } from './db';
import { validateBackupStores } from './backup-validation';
import { ensureCircuitScheduleSupported, withHealthConnectSyncLock } from './native-bridge';
import { repairLegacyCustomExerciseNames } from './custom-exercises';
import { SCHEMA_VERSION, type BodyMeasurementEntry, type BodyMetricEntry, type CustomExercise, type PlaylistDraft, type QuestState, type WorkoutLog, type WorkoutRow, type WorkoutSessionEvent, type WorkoutSetLog } from '../types';

export const BACKUP_FORMAT = 'pasingot.workout-tracker.backup';
export const BACKUP_VERSION = 1;
export const CIRCUIT_BACKUP_VERSION = 2;

export interface WorkoutBackup {
  format: typeof BACKUP_FORMAT;
  backupVersion: typeof BACKUP_VERSION | typeof CIRCUIT_BACKUP_VERSION;
  schemaVersion: number;
  exportedAt: string;
  stores: {
    workouts: WorkoutRow[];
    logs: WorkoutLog[];
    sessionEvents: WorkoutSessionEvent[];
    setLogs: WorkoutSetLog[];
    bodyMetrics: BodyMetricEntry[];
    bodyMeasurements?: BodyMeasurementEntry[];
    customExercises: CustomExercise[];
    appState: Array<PlaylistDraft | QuestState | Record<string, unknown>>;
  };
}

export interface BackupSummary {
  workouts: number;
  logs: number;
  sessionEvents: number;
  setLogs: number;
  bodyMetrics: number;
  bodyMeasurements: number;
  customExercises: number;
  appState: number;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function hasCircuitData(stores: Record<string, unknown>): boolean {
  return (Array.isArray(stores.workouts) && stores.workouts.some(row => isObject(row) && row.circuit !== undefined))
    || (Array.isArray(stores.appState) && stores.appState.some(row => isObject(row) && (row.steps !== undefined
      || (row.key === 'customQuests' && Array.isArray(row.quests) && row.quests.some(quest => isObject(quest)
        && Array.isArray(quest.rows) && quest.rows.some(item => isObject(item) && item.circuit !== undefined)))
      || (row.key === 'playlistDraft' && Array.isArray(row.items) && row.items.some(item => isObject(item) && item.circuit !== undefined)))));
}

export function summarizeBackup(backup: WorkoutBackup): BackupSummary {
  return {
    workouts: backup.stores.workouts.length,
    logs: backup.stores.logs.length,
    sessionEvents: backup.stores.sessionEvents.length,
    setLogs: backup.stores.setLogs.length,
    bodyMetrics: backup.stores.bodyMetrics.length,
    bodyMeasurements: backup.stores.bodyMeasurements?.length ?? 0,
    customExercises: backup.stores.customExercises.length,
    appState: backup.stores.appState.length,
  };
}

export async function buildWorkoutBackup(): Promise<WorkoutBackup> {
  const [workouts, logs, sessionEvents, setLogs, bodyMetrics, bodyMeasurements, customExercises, appState] = await Promise.all([
    getAll<WorkoutRow>(STORES.workouts),
    getAll<WorkoutLog>(STORES.logs),
    getAll<WorkoutSessionEvent>(STORES.sessionEvents),
    getAll<WorkoutSetLog>(STORES.setLogs),
    getAll<BodyMetricEntry>(STORES.bodyMetrics),
    getAll<BodyMeasurementEntry>(STORES.bodyMeasurements),
    getAll<CustomExercise>(STORES.customExercises),
    getAll<PlaylistDraft | QuestState | Record<string, unknown>>(STORES.appState),
  ]);
  return {
    format: BACKUP_FORMAT,
    // Older apps reject v2 before mutation instead of playing grouped records as straight sets.
    backupVersion: hasCircuitData({ workouts, appState }) ? CIRCUIT_BACKUP_VERSION : BACKUP_VERSION,
    schemaVersion: SCHEMA_VERSION,
    exportedAt: new Date().toISOString(),
    stores: { workouts, logs, sessionEvents, setLogs, bodyMetrics, bodyMeasurements, customExercises, appState },
  };
}

export function backupFileName(date = new Date()): string {
  const stamp = date.toISOString().slice(0, 19).replace(/[-:T]/g, '');
  return `pasingot-backup-${stamp}.json`;
}

export function parseWorkoutBackup(raw: string): WorkoutBackup {
  const parsed = JSON.parse(raw) as unknown;
  if (!isObject(parsed) || parsed.format !== BACKUP_FORMAT || ![BACKUP_VERSION, CIRCUIT_BACKUP_VERSION].includes(Number(parsed.backupVersion))
    || typeof parsed.backupVersion !== 'number') {
    throw new Error('This is not a supported Pasingot backup file.');
  }
  const stores = parsed.stores;
  if (!isObject(stores)) throw new Error('Backup file is missing stores.');
  if (parsed.backupVersion === BACKUP_VERSION && hasCircuitData(stores)) throw new Error('Grouped workouts require a version 2 backup. Nothing was restored.');
  if (parsed.schemaVersion !== SCHEMA_VERSION) throw new Error('This backup uses an unsupported data schema.');
  // Older backups predate the custom-exercise store.
  if (stores.customExercises === undefined) stores.customExercises = [];
  validateBackupStores(stores);
  const requiredStores = ['workouts', 'logs', 'sessionEvents', 'setLogs', 'bodyMetrics', 'appState'] as const;
  requiredStores.forEach((storeName) => {
    if (!Array.isArray(stores[storeName])) throw new Error(`Backup file is missing ${storeName}.`);
  });
  return {
    format: BACKUP_FORMAT,
    backupVersion: parsed.backupVersion as WorkoutBackup['backupVersion'],
    schemaVersion: Number(parsed.schemaVersion) || SCHEMA_VERSION,
    exportedAt: typeof parsed.exportedAt === 'string' ? parsed.exportedAt : new Date().toISOString(),
    stores: {
      workouts: stores.workouts as WorkoutRow[],
      logs: stores.logs as WorkoutLog[],
      sessionEvents: stores.sessionEvents as WorkoutSessionEvent[],
      setLogs: stores.setLogs as WorkoutSetLog[],
      bodyMetrics: stores.bodyMetrics as BodyMetricEntry[],
      bodyMeasurements: (stores.bodyMeasurements ?? []) as BodyMeasurementEntry[],
      customExercises: Array.isArray(stores.customExercises) ? stores.customExercises as CustomExercise[] : [],
      appState: stores.appState as Array<PlaylistDraft | QuestState | Record<string, unknown>>,
    },
  };
}

export async function restoreWorkoutBackup(backup: WorkoutBackup): Promise<BackupSummary> {
  if (![BACKUP_VERSION, CIRCUIT_BACKUP_VERSION].includes(backup.backupVersion)
    || (backup.backupVersion === BACKUP_VERSION && hasCircuitData(backup.stores))) {
    throw new Error('This backup version cannot preserve grouped workouts. Nothing was restored.');
  }
  validateBackupStores(backup.stores);
  const drafts = backup.stores.appState.flatMap(state => {
    const value = state as Record<string, unknown>;
    return value.key === 'playlistDraft' && Array.isArray(value.items) ? value.items as PlaylistDraft['items'] : [];
  });
  const questRows = backup.stores.appState.flatMap(state => {
    const value = state as Record<string, unknown>;
    return value.key === 'customQuests' && Array.isArray(value.quests)
      ? (value.quests as Array<{ rows: import('../types').QuestWorkoutRow[] }>).flatMap(quest => quest.rows) : [];
  });
  await ensureCircuitScheduleSupported([...backup.stores.workouts, ...drafts, ...questRows]);
  await withHealthConnectSyncLock(() => replaceStores({ ...backup.stores, bodyMeasurements: backup.stores.bodyMeasurements ?? [] }));
  await repairLegacyCustomExerciseNames();
  return summarizeBackup(backup);
}
