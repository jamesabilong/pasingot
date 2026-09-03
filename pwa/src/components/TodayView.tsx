import { WorkoutPlayer, type WorkoutCueSettingsView, type WorkoutSetInput, type WorkoutPlayerSession } from './WorkoutPlayer';
import { EstimateSummary, PlanProgressSummary, type PlanProgress } from './SummaryCards';
import { type WorkoutLog, type WorkoutRow } from '../types';

function scheduleLoadLabel(row: WorkoutRow): string {
  return row.loadWeight != null && row.loadUnit ? ` · ${row.loadWeight} ${row.loadUnit}` : '';
}

export function TodayView({
  todayName,
  workouts,
  estimate,
  progress,
  statuses,
  setLogCount,
  activeSession,
  activeRows,
  elapsedSeconds,
  restRemainingSeconds,
  activeSetInput,
  cueSettings,
  onStartPlayer,
  onSetInputChange,
  onCueSettingsChange,
  onCompleteSet,
  onSkipExercise,
  onPause,
  onResume,
  onRestart,
  onEnd,
  onStartNow,
  onAddRestSeconds,
  onClosePlayer,
  onLogExercise,
}: {
  todayName: string;
  workouts: WorkoutRow[];
  estimate: string;
  progress: PlanProgress;
  statuses: Map<number, WorkoutLog['status']>;
  setLogCount: number;
  activeSession: WorkoutPlayerSession | null;
  activeRows: WorkoutRow[];
  elapsedSeconds: number;
  restRemainingSeconds: number;
  activeSetInput: WorkoutSetInput;
  cueSettings: WorkoutCueSettingsView;
  onStartPlayer: () => void;
  onSetInputChange: (updates: Partial<WorkoutSetInput>) => void;
  onCueSettingsChange: (updates: Partial<WorkoutCueSettingsView>) => void;
  onCompleteSet: () => void;
  onSkipExercise: () => void;
  onPause: () => void;
  onResume: () => void;
  onRestart: () => void;
  onEnd: () => void;
  onStartNow: () => void;
  onAddRestSeconds: (seconds: number) => void;
  onClosePlayer: () => void;
  onLogExercise: (row: WorkoutRow, status: WorkoutLog['status']) => void;
}) {
  return (
    <section className="space-y-3">
      <div className="flex items-baseline justify-between">
        <h2 className="text-base font-semibold text-slate-200">Today's Workout</h2>
        <span className="text-xs text-slate-500">{workouts.length ? `${todayName} · ${estimate}` : todayName}</span>
      </div>
      {workouts.length > 0 && <EstimateSummary value={estimate} />}
      {workouts.length > 0 && <PlanProgressSummary progress={progress} />}
      {setLogCount > 0 && <p className="rounded-md border border-slate-800 bg-slate-900 px-3 py-2 text-xs text-slate-400">{setLogCount} completed set{setLogCount === 1 ? '' : 's'} logged today.</p>}
      {activeSession && activeRows.length > 0 ? <WorkoutPlayer
        session={activeSession}
        rows={activeRows}
        elapsedSeconds={elapsedSeconds}
        restRemainingSeconds={restRemainingSeconds}
        setInput={activeSetInput}
        onSetInputChange={onSetInputChange}
        cueSettings={cueSettings}
        onCueSettingsChange={onCueSettingsChange}
        onCompleteSet={onCompleteSet}
        onSkip={onSkipExercise}
        onPause={onPause}
        onResume={onResume}
        onRestart={onRestart}
        onEnd={onEnd}
        onStartNow={onStartNow}
        onAddRestSeconds={onAddRestSeconds}
        onClose={onClosePlayer}
      /> : workouts.length > 0 && progress.pending > 0 && <button type="button" onClick={onStartPlayer} className="w-full rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400">Start workout player</button>}
      {workouts.length === 0 ? <p className="py-10 text-center text-sm text-slate-500">No exercises scheduled for today. Import a CSV to get started.</p> : <div className="space-y-2">
        {workouts.map((row) => {
          const status = row.id == null ? undefined : statuses.get(row.id);
          return <div key={row.id} className={`flex items-center gap-3 rounded-lg border border-slate-800 bg-slate-900 p-3 ${status === 'done' ? 'opacity-60' : ''}`}>
            <div className="min-w-0 flex-1">
              <p className={`truncate font-medium ${status === 'done' ? 'text-slate-500 line-through' : 'text-slate-100'}`}>{row.exercise}</p>
              <p className="text-xs text-slate-500">{row.time} · {row.sets} × {row.reps}{scheduleLoadLabel(row)} · rest {row.rest}s</p>
            </div>
            <div className="flex shrink-0 gap-1.5">
              <button type="button" onClick={() => onLogExercise(row, 'done')} className={`rounded-md px-2.5 py-1.5 text-xs font-medium ${status === 'done' ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-300 hover:bg-emerald-600 hover:text-white'}`}>{status === 'done' ? 'Done' : 'Mark as Done'}</button>
              <button type="button" onClick={() => onLogExercise(row, 'skipped')} className={`rounded-md px-2.5 py-1.5 text-xs font-medium ${status === 'skipped' ? 'bg-slate-700 text-white' : 'bg-slate-800 text-slate-400 hover:bg-slate-700'}`}>Skip</button>
            </div>
          </div>;
        })}
      </div>}
    </section>
  );
}
