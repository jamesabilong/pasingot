import { WEEKDAYS, type WorkoutRow } from '../types';

export interface ImportResult {
  imported: number;
  skipped: number;
}

export interface BackupTransferResult {
  message: string;
  error: boolean;
}

export function ImportView({
  result,
  backupResult,
  workouts,
  onImportFile,
  onExportBackup,
  onImportBackupFile,
}: {
  result: ImportResult | null;
  backupResult: BackupTransferResult | null;
  workouts: WorkoutRow[];
  onImportFile: (file: File) => void;
  onExportBackup: () => void;
  onImportBackupFile: (file: File) => void;
}) {
  return (
    <section className="space-y-4">
      <div className="space-y-3 rounded-lg border border-slate-800 bg-slate-900 p-4">
        <div>
          <h2 className="text-base font-semibold text-slate-200">Backup & Restore</h2>
          <p className="mt-1 text-xs leading-relaxed text-slate-500">Export or restore schedule, logs, set history, session events, quests, draft playlist, cue settings, and body metrics. Restoring a backup replaces the current local data after confirmation.</p>
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          <button type="button" onClick={onExportBackup} className="rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400">Export full backup</button>
          <label className="block">
            <span className="sr-only">Choose backup file</span>
            <input
              type="file"
              accept="application/json,.json"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) onImportBackupFile(file);
                event.currentTarget.value = '';
              }}
              className="block w-full cursor-pointer text-sm text-slate-300 file:mr-3 file:rounded-md file:border-0 file:bg-slate-800 file:px-3 file:py-2 file:text-sm file:font-medium file:text-slate-200 hover:file:bg-slate-700"
            />
          </label>
        </div>
        {backupResult && <p className={`rounded-md border p-3 text-sm ${backupResult.error ? 'border-rose-900 bg-rose-950/40 text-rose-300' : 'border-emerald-900 bg-emerald-950/40 text-emerald-300'}`}>{backupResult.message}</p>}
      </div>

      <div className="space-y-3 rounded-lg border border-slate-800 bg-slate-900 p-4">
        <h2 className="text-base font-semibold text-slate-200">Import Schedule (CSV)</h2>
        <p className="text-xs leading-relaxed text-slate-500">Columns: <code className="text-indigo-300">day,time,exercise,sets,reps,rest</code>. Optional load columns: <code className="text-indigo-300">load_weight,load_unit</code>. <code>day</code> must be a full weekday name, <code>time</code> is 24-hour <code>HH:MM</code>, and <code>rest</code> is seconds after each set and before the next exercise.</p>
        <label className="block">
          <span className="sr-only">Choose CSV file</span>
          <input
            type="file"
            accept=".csv,text/csv"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) onImportFile(file);
              event.currentTarget.value = '';
            }}
            className="block w-full cursor-pointer text-sm text-slate-300 file:mr-3 file:rounded-lg file:border-0 file:bg-indigo-600 file:px-3 file:py-2 file:text-sm file:font-medium file:text-white hover:file:bg-indigo-500"
          />
        </label>
        {result && <div className="space-y-1 rounded-lg border border-slate-800 bg-slate-950 p-3 text-sm">
          <p className="text-emerald-400">Imported {result.imported} exercise row{result.imported === 1 ? '' : 's'}.</p>
          {result.skipped > 0 && <p className="text-amber-400">Skipped {result.skipped} malformed row{result.skipped === 1 ? '' : 's'}.</p>}
        </div>}
      </div>

      <div>
        <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Current schedule</h3>
        {workouts.length === 0 ? <p className="text-sm text-slate-400">No schedule imported yet.</p> : <div className="text-sm text-slate-400">
          {WEEKDAYS.filter((day) => workouts.some((row) => row.day === day)).map((day) => {
            const count = workouts.filter((row) => row.day === day).length;
            return <div key={day} className="flex justify-between py-0.5"><span>{day}</span><span className="text-slate-500">{count} exercise{count === 1 ? '' : 's'}</span></div>;
          })}
        </div>}
      </div>
    </section>
  );
}
