import { useState } from 'react';
import type { BackupTransferResult } from '../components/ImportView';
import { backupFileName, buildWorkoutBackup, parseWorkoutBackup, restoreWorkoutBackup, type WorkoutBackup } from '../lib/backup';
import { getAll, STORES } from '../lib/db';
import { pushScheduleToNative } from '../lib/native-bridge';
import type { WorkoutRow } from '../types';

type WorkoutBackupOptions = {
  onRestored: (backup: WorkoutBackup) => Promise<void>;
};

export function useWorkoutBackup({ onRestored }: WorkoutBackupOptions) {
  const [result, setBackupResult] = useState<BackupTransferResult | null>(null);

  async function exportBackup() {
    try {
      const backup = await buildWorkoutBackup();
      const url = URL.createObjectURL(new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = backupFileName();
      document.body.append(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 0);
      setBackupResult({ error: false, message: `Backup exported with ${backup.stores.workouts.length} schedule rows and ${backup.stores.logs.length} logs.` });
    } catch (error) {
      setBackupResult({ error: true, message: error instanceof Error ? error.message : 'Could not export backup.' });
    }
  }

  async function importBackup(file: File) {
    try {
      const backup = parseWorkoutBackup(await file.text());
      const confirmed = window.confirm('Restore this backup? This replaces the local schedule, logs, set history, body metrics, quests, draft playlist, cue settings, and active session state.');
      if (!confirmed) {
        setBackupResult({ error: false, message: 'Backup restore canceled.' });
        return;
      }
      const summary = await restoreWorkoutBackup(backup);
      await pushScheduleToNative(await getAll<WorkoutRow>(STORES.workouts));
      await onRestored(backup);
      setBackupResult({ error: false, message: `Backup restored: ${summary.workouts} schedule rows, ${summary.logs} logs, ${summary.setLogs} set logs, ${summary.bodyMetrics} body metrics, ${summary.customExercises} custom exercises.` });
    } catch (error) {
      setBackupResult({ error: true, message: error instanceof Error ? error.message : 'Could not restore backup.' });
    }
  }

  return { result, exportBackup, importBackup };
}
