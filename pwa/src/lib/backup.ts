import { getAll, clearAndBulkInsert, STORES } from './db';
import { SCHEMA_VERSION, type BodyMetricEntry, type CustomExercise, type PlaylistDraft, type QuestState, type WorkoutLog, type WorkoutRow, type WorkoutSessionEvent, type WorkoutSetLog } from '../types';

export const BACKUP_FORMAT = 'pasingot.workout-tracker.backup';
export const BACKUP_VERSION = 1;

export interface WorkoutBackup {
  format: typeof BACKUP_FORMAT;
  backupVersion: typeof BACKUP_VERSION;
  schemaVersion: number;
  exportedAt: string;
  stores: {
    workouts: WorkoutRow[];
    logs: WorkoutLog[];
    sessionEvents: WorkoutSessionEvent[];
    setLogs: WorkoutSetLog[];
    bodyMetrics: BodyMetricEntry[];
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
  customExercises: number;
  appState: number;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function summarizeBackup(backup: WorkoutBackup): BackupSummary {
  return {
    workouts: backup.stores.workouts.length,
    logs: backup.stores.logs.length,
    sessionEvents: backup.stores.sessionEvents.length,
    setLogs: backup.stores.setLogs.length,
    bodyMetrics: backup.stores.bodyMetrics.length,
    customExercises: backup.stores.customExercises.length,
    appState: backup.stores.appState.length,
  };
}

export async function buildWorkoutBackup(): Promise<WorkoutBackup> {
  return {
    format: BACKUP_FORMAT,
    backupVersion: BACKUP_VERSION,
    schemaVersion: SCHEMA_VERSION,
    exportedAt: new Date().toISOString(),
    stores: {
      workouts: await getAll<WorkoutRow>(STORES.workouts),
      logs: await getAll<WorkoutLog>(STORES.logs),
      sessionEvents: await getAll<WorkoutSessionEvent>(STORES.sessionEvents),
      setLogs: await getAll<WorkoutSetLog>(STORES.setLogs),
      bodyMetrics: await getAll<BodyMetricEntry>(STORES.bodyMetrics),
      customExercises: await getAll<CustomExercise>(STORES.customExercises),
      appState: await getAll<PlaylistDraft | QuestState | Record<string, unknown>>(STORES.appState),
    },
  };
}

export function backupFileName(date = new Date()): string {
  const stamp = date.toISOString().slice(0, 19).replace(/[-:T]/g, '');
  return `pasingot-backup-${stamp}.json`;
}

export function parseWorkoutBackup(raw: string): WorkoutBackup {
  const parsed = JSON.parse(raw) as unknown;
  if (!isObject(parsed) || parsed.format !== BACKUP_FORMAT || parsed.backupVersion !== BACKUP_VERSION) {
    throw new Error('This is not a supported Pasingot backup file.');
  }
  const stores = parsed.stores;
  if (!isObject(stores)) throw new Error('Backup file is missing stores.');
  const requiredStores = ['workouts', 'logs', 'sessionEvents', 'setLogs', 'bodyMetrics', 'appState'] as const;
  requiredStores.forEach((storeName) => {
    if (!Array.isArray(stores[storeName])) throw new Error(`Backup file is missing ${storeName}.`);
  });
  return {
    format: BACKUP_FORMAT,
    backupVersion: BACKUP_VERSION,
    schemaVersion: Number(parsed.schemaVersion) || SCHEMA_VERSION,
    exportedAt: typeof parsed.exportedAt === 'string' ? parsed.exportedAt : new Date().toISOString(),
    stores: {
      workouts: stores.workouts as WorkoutRow[],
      logs: stores.logs as WorkoutLog[],
      sessionEvents: stores.sessionEvents as WorkoutSessionEvent[],
      setLogs: stores.setLogs as WorkoutSetLog[],
      bodyMetrics: stores.bodyMetrics as BodyMetricEntry[],
      customExercises: Array.isArray(stores.customExercises) ? stores.customExercises as CustomExercise[] : [],
      appState: stores.appState as Array<PlaylistDraft | QuestState | Record<string, unknown>>,
    },
  };
}

export async function restoreWorkoutBackup(backup: WorkoutBackup): Promise<BackupSummary> {
  await clearAndBulkInsert(STORES.workouts, backup.stores.workouts);
  await clearAndBulkInsert(STORES.logs, backup.stores.logs);
  await clearAndBulkInsert(STORES.sessionEvents, backup.stores.sessionEvents);
  await clearAndBulkInsert(STORES.setLogs, backup.stores.setLogs);
  await clearAndBulkInsert(STORES.bodyMetrics, backup.stores.bodyMetrics);
  await clearAndBulkInsert(STORES.customExercises, backup.stores.customExercises);
  await clearAndBulkInsert(STORES.appState, backup.stores.appState);
  return summarizeBackup(backup);
}
