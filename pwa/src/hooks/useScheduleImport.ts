import { useState } from 'react';
import Papa from 'papaparse';
import type { ImportResult } from '../components/ImportView';
import { clearAndBulkInsert, getAll, STORES } from '../lib/db';
import { pushScheduleToNative } from '../lib/native-bridge';
import { validateWorkoutRow } from '../lib/workout-planning';
import type { WorkoutRow } from '../types';

type ScheduleImportOptions = {
  session: { status: string } | null;
  clearSession: () => Promise<void>;
  onScheduleChanged: (rows: WorkoutRow[]) => void;
};

export function useScheduleImport({ session, clearSession, onScheduleChanged }: ScheduleImportOptions) {
  const [result, setImportResult] = useState<ImportResult | null>(null);

  async function importCsv(file: File) {
    try {
      if (session && ['active', 'resting', 'paused'].includes(session.status)) {
        throw new Error('Finish or end the current workout before replacing the schedule.');
      }
      const parsed = await new Promise<Papa.ParseResult<Record<string, string>>>((resolve, reject) => {
        Papa.parse<Record<string, string>>(file, { header: true, skipEmptyLines: true, complete: resolve, error: reject });
      });
      const valid = parsed.data.map(validateWorkoutRow).filter((row): row is WorkoutRow => row !== null);
      if (parsed.errors.length || !valid.length) throw new Error('The CSV has no usable schedule or could not be parsed. The current schedule was kept.');
      const skipped = parsed.data.length - valid.length;
      if (!window.confirm(`Replace the current schedule with ${valid.length} exercise rows${skipped ? `, skipping ${skipped} invalid rows` : ''}? Workout history will be kept.`)) return;
      await clearAndBulkInsert(STORES.workouts, valid);
      await clearSession();
      const saved = await getAll<WorkoutRow>(STORES.workouts);
      await pushScheduleToNative(saved);
      onScheduleChanged(saved);
      setImportResult({ imported: saved.length, skipped });
    } catch (error) {
      setImportResult({ imported: 0, skipped: 0, error: error instanceof Error ? error.message : 'Could not import the schedule.' });
    }
  }

  return { result, importCsv };
}
