import { useState } from 'react';
import { formatDuration } from '../lib/format';
import { type WeightUnit, type WorkoutRow } from '../types';

export type WorkoutSetInput = {
  actualReps: string;
  loadWeight: string;
  loadUnit: WeightUnit;
};

export type WorkoutPlayerSession = {
  status: 'active' | 'resting' | 'paused' | 'completed' | 'ended';
  exerciseIndex: number;
  currentSet: number;
  pausedRestRemainingSeconds: number | null;
  lastStopReason?: string | null;
};

export type WorkoutCueSettingsView = {
  hapticsEnabled: boolean;
  soundEnabled: boolean;
  voiceEnabled: boolean;
};

export function WorkoutPlayer({
  session,
  rows,
  elapsedSeconds,
  restRemainingSeconds,
  setInput,
  onSetInputChange,
  onCompleteSet,
  onSkip,
  onPause,
  onResume,
  onRestart,
  onEnd,
  onStartNow,
  onAddRestSeconds,
  cueSettings,
  onCueSettingsChange,
  onClose,
}: {
  session: WorkoutPlayerSession;
  rows: WorkoutRow[];
  elapsedSeconds: number;
  restRemainingSeconds: number;
  setInput: WorkoutSetInput;
  onSetInputChange: (updates: Partial<WorkoutSetInput>) => void;
  onCompleteSet: () => void;
  onSkip: () => void;
  onPause: () => void;
  onResume: () => void;
  onRestart: () => void;
  onEnd: () => void;
  onStartNow: () => void;
  onAddRestSeconds: (seconds: number) => void;
  cueSettings: WorkoutCueSettingsView;
  onCueSettingsChange: (updates: Partial<WorkoutCueSettingsView>) => void;
  onClose: () => void;
}) {
  const row = rows[session.exerciseIndex];
  const [confirmingEnd, setConfirmingEnd] = useState(false);
  const [confirmingRestart, setConfirmingRestart] = useState(false);
  if (!row) return null;

  const setLabel = `Set ${Math.min(session.currentSet, row.sets)} of ${row.sets}`;
  const progressLabel = `Exercise ${session.exerciseIndex + 1} of ${rows.length}`;
  const loadLabel = row.loadWeight != null && row.loadUnit ? ` · planned ${row.loadWeight} ${row.loadUnit}` : '';
  const inputDisabled = session.status !== 'active';
  const pausedMessage = session.lastStopReason === 'inactive_timeout'
    ? 'Workout paused after no activity.'
    : session.pausedRestRemainingSeconds != null ? `Rest paused at ${formatDuration(session.pausedRestRemainingSeconds)}.` : 'Workout paused.';

  return (
    <div className="rounded-lg border border-emerald-900 bg-slate-900 p-4">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wide text-emerald-300">
            {session.status === 'resting' ? 'Rest' : session.status === 'paused' ? 'Paused' : session.status === 'ended' ? 'Ended' : session.status === 'completed' ? 'Complete' : 'Workout Player'}
          </p>
          <h3 className="mt-1 truncate text-lg font-semibold text-slate-100">{row.exercise}</h3>
          <p className="text-xs text-slate-500">{progressLabel} · {setLabel}{loadLabel}</p>
        </div>
        <span className="shrink-0 rounded border border-slate-700 px-2 py-1 text-xs text-slate-300">{formatDuration(elapsedSeconds)}</span>
      </div>

      {session.status === 'active' && <div className="mb-3 grid grid-cols-[1fr_6.25rem_4.5rem] gap-2 rounded-md border border-slate-800 bg-slate-950 p-3">
        <label className="min-w-0 text-[10px] uppercase text-slate-600">
          Reps / duration
          <input
            type="text"
            maxLength={30}
            value={setInput.actualReps}
            onChange={(event) => onSetInputChange({ actualReps: event.target.value })}
            disabled={inputDisabled}
            className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-1.5 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none disabled:opacity-50"
          />
        </label>
        <label className="text-[10px] uppercase text-slate-600">
          Load
          <input
            type="number"
            min="0"
            max="2000"
            step="0.5"
            inputMode="decimal"
            placeholder="Optional"
            value={setInput.loadWeight}
            onChange={(event) => onSetInputChange({ loadWeight: event.target.value })}
            disabled={inputDisabled}
            className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-1.5 text-sm text-slate-100 placeholder:text-slate-700 focus:border-emerald-500 focus:outline-none disabled:opacity-50"
          />
        </label>
        <label className="text-[10px] uppercase text-slate-600">
          Unit
          <select
            value={setInput.loadUnit}
            onChange={(event) => onSetInputChange({ loadUnit: event.target.value as WeightUnit })}
            disabled={inputDisabled}
            className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-1.5 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none disabled:opacity-50"
          >
            <option value="kg">kg</option>
            <option value="lb">lb</option>
          </select>
        </label>
      </div>}

      {session.status === 'resting' && <div className="mb-3 rounded-md border border-slate-800 bg-slate-950 p-3 text-center">
        <p className="text-xs uppercase tracking-wide text-slate-500">Rest remaining</p>
        <p className="mt-1 text-3xl font-bold text-emerald-300">{formatDuration(restRemainingSeconds)}</p>
        <div className="mt-3 grid grid-cols-3 gap-2">
          {[5, 10, 30].map((seconds) => (
            <button key={seconds} type="button" onClick={() => onAddRestSeconds(seconds)} className="rounded-md bg-slate-800 px-2 py-2 text-xs font-semibold text-slate-200 hover:bg-slate-700">+{seconds}s</button>
          ))}
        </div>
      </div>}

      {session.status === 'paused' && <p className="mb-3 rounded-md border border-slate-800 bg-slate-950 p-3 text-sm text-slate-400">{pausedMessage}</p>}

      <div className="mb-3 grid grid-cols-3 gap-1 rounded-md border border-slate-800 bg-slate-950 p-1 text-[11px] font-medium">
        <CueToggle label="Haptic" enabled={cueSettings.hapticsEnabled} onChange={(enabled) => onCueSettingsChange({ hapticsEnabled: enabled })} />
        <CueToggle label="Sound" enabled={cueSettings.soundEnabled} onChange={(enabled) => onCueSettingsChange({ soundEnabled: enabled })} />
        <CueToggle label="Voice" enabled={cueSettings.voiceEnabled} onChange={(enabled) => onCueSettingsChange({ voiceEnabled: enabled })} />
      </div>

      {confirmingRestart ? <div className="grid gap-2">
        <button type="button" onClick={() => { onRestart(); setConfirmingRestart(false); }} className="rounded-md bg-amber-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-amber-400">Restart workout</button>
        <button type="button" onClick={() => setConfirmingRestart(false)} className="rounded-md bg-slate-800 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700">Keep paused</button>
      </div> : confirmingEnd ? <div className="grid gap-2">
        <button type="button" onClick={() => { onEnd(); setConfirmingEnd(false); }} className="rounded-md bg-rose-600 px-3 py-2 text-sm font-semibold text-white hover:bg-rose-500">End workout</button>
        <button type="button" onClick={() => setConfirmingEnd(false)} className="rounded-md bg-slate-800 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700">Keep paused</button>
      </div> : session.status === 'active' ? <div className="grid gap-2">
        <button type="button" onClick={onCompleteSet} className="rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400">Complete set</button>
        <div className="grid grid-cols-2 gap-2">
          <button type="button" onClick={onPause} className="rounded-md bg-slate-800 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700">Pause</button>
          <button type="button" onClick={onSkip} className="rounded-md bg-slate-800 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700">Skip exercise</button>
        </div>
      </div> : session.status === 'resting' ? <div className="grid grid-cols-2 gap-2">
        <button type="button" onClick={onStartNow} className="rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400">Start now</button>
        <button type="button" onClick={onPause} className="rounded-md bg-slate-800 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700">Pause</button>
      </div> : session.status === 'paused' ? <div className="grid gap-2">
        <button type="button" onClick={onResume} className="rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400">Resume</button>
        <div className="grid grid-cols-2 gap-2">
          <button type="button" onClick={() => setConfirmingRestart(true)} className="rounded-md bg-slate-800 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700">Restart</button>
          <button type="button" onClick={() => setConfirmingEnd(true)} className="rounded-md bg-slate-800 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700">End</button>
        </div>
      </div> : <button type="button" onClick={onClose} className="w-full rounded-md bg-slate-800 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700">Close player</button>}
    </div>
  );
}

function CueToggle({
  label,
  enabled,
  onChange,
}: {
  label: string;
  enabled: boolean;
  onChange: (enabled: boolean) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onChange(!enabled)}
      className={`rounded px-2 py-1.5 ${enabled ? 'bg-emerald-500 text-slate-950' : 'text-slate-500 hover:bg-slate-800 hover:text-slate-200'}`}
      aria-pressed={enabled}
    >
      {label}
    </button>
  );
}
