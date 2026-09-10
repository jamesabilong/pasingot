import { useState, type ComponentType } from 'react';
import { Check, Pause, Play, RefreshCcw, SkipForward, Square, Volume2, Vibrate, X } from 'lucide-react';
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
  session, rows, elapsedSeconds, restRemainingSeconds, setInput, onSetInputChange,
  onCompleteSet, onSkip, onPause, onResume, onRestart, onEnd, onStartNow,
  onAddRestSeconds, cueSettings, onCueSettingsChange, onClose,
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
  const loadLabel = row.loadWeight != null && row.loadUnit ? `${row.loadWeight} ${row.loadUnit}` : 'Bodyweight';
  const pausedMessage = session.lastStopReason === 'inactive_timeout'
    ? 'Paused after no activity.'
    : session.pausedRestRemainingSeconds != null ? `Rest paused at ${formatDuration(session.pausedRestRemainingSeconds)}.` : 'Workout paused.';
  const sessionProgress = ((session.exerciseIndex + Math.min(session.currentSet / Math.max(row.sets, 1), 1)) / rows.length) * 100;
  const restTotal = Math.max(row.rest, 1);
  const restProgress = Math.max(0, Math.min(1, restRemainingSeconds / restTotal));
  const ringOffset = 289 * (1 - restProgress);
  const stateLabel = session.status === 'resting' ? 'Rest' : session.status === 'paused' ? 'Paused' : session.status === 'ended' ? 'Ended' : session.status === 'completed' ? 'Complete' : 'In progress';

  return (
    <section className="workout-player" aria-label="Workout player">
      <header className="player-header">
        <div className="player-header__title">
          <span className={`player-state player-state--${session.status}`}>{stateLabel}</span>
          <h3>{row.exercise}</h3>
          <p>{progressLabel} · {setLabel}</p>
        </div>
        <time>{formatDuration(elapsedSeconds)}</time>
      </header>
      <div className="player-progress" aria-hidden="true"><span style={{ width: `${Math.min(sessionProgress, 100)}%` }} /></div>

      {session.status === 'active' && <div className="player-focus player-focus--active">
        <div className="set-callout">
          <span>Current set</span>
          <strong>{Math.min(session.currentSet, row.sets)}<small>/{row.sets}</small></strong>
          <em>{row.reps} · {loadLabel}</em>
        </div>
        <div className="set-inputs">
          <label><span>Reps / min / sec</span><input type="text" maxLength={30} placeholder="8 / 2 min / 30 sec" value={setInput.actualReps} onChange={(event) => onSetInputChange({ actualReps: event.target.value })} /></label>
          <label><span>Load</span><input type="number" min="0" max="2000" step="0.5" inputMode="decimal" placeholder="Optional" value={setInput.loadWeight} onChange={(event) => onSetInputChange({ loadWeight: event.target.value })} /></label>
          <label><span>Unit</span><select value={setInput.loadUnit} onChange={(event) => onSetInputChange({ loadUnit: event.target.value as WeightUnit })}><option value="kg">kg</option><option value="lb">lb</option></select></label>
        </div>
        <p className="text-xs text-slate-500">For duration, include min or sec (e.g. 2 min or 30 sec).</p>
      </div>}

      {session.status === 'resting' && <div className="player-focus player-focus--rest">
        <div className="rest-ring" role="timer" aria-label={`${formatDuration(restRemainingSeconds)} rest remaining`}>
          <svg viewBox="0 0 100 100" aria-hidden="true">
            <circle className="rest-ring__track" cx="50" cy="50" r="46" />
            <circle className="rest-ring__value" cx="50" cy="50" r="46" style={{ strokeDashoffset: ringOffset }} />
          </svg>
          <div><span>Rest</span><strong>{formatDuration(restRemainingSeconds)}</strong><small>Next: {row.exercise}</small></div>
        </div>
        <div className="rest-extensions" aria-label="Extend rest">
          {[5, 10, 30].map((seconds) => <button key={seconds} type="button" onClick={() => onAddRestSeconds(seconds)}>+{seconds}s</button>)}
        </div>
      </div>}

      {session.status === 'paused' && <div className="player-focus player-focus--paused">
        <Pause size={34} aria-hidden="true" />
        <strong>Workout paused</strong>
        <p>{pausedMessage}</p>
      </div>}

      {(session.status === 'active' || session.status === 'resting' || session.status === 'paused') && <div className="cue-controls" aria-label="Workout cues">
        <CueToggle label="Haptics" icon={Vibrate} enabled={cueSettings.hapticsEnabled} onChange={(enabled) => onCueSettingsChange({ hapticsEnabled: enabled })} />
        <CueToggle label="Sound" icon={Volume2} enabled={cueSettings.soundEnabled} onChange={(enabled) => onCueSettingsChange({ soundEnabled: enabled })} />
        <CueToggle label="Voice" icon={Play} enabled={cueSettings.voiceEnabled} onChange={(enabled) => onCueSettingsChange({ voiceEnabled: enabled })} />
      </div>}

      {confirmingRestart ? <Confirmation message="Restart from the first exercise?" confirmLabel="Restart workout" confirmIcon={RefreshCcw} tone="warning" onConfirm={() => { onRestart(); setConfirmingRestart(false); }} onCancel={() => setConfirmingRestart(false)} />
        : confirmingEnd ? <Confirmation message="End this workout now?" confirmLabel="End workout" confirmIcon={Square} tone="danger" onConfirm={() => { onEnd(); setConfirmingEnd(false); }} onCancel={() => setConfirmingEnd(false)} />
          : <PlayerActions status={session.status} onCompleteSet={onCompleteSet} onSkip={onSkip} onPause={onPause} onResume={onResume} onRestart={() => setConfirmingRestart(true)} onEnd={() => setConfirmingEnd(true)} onStartNow={onStartNow} onClose={onClose} />}
    </section>
  );
}

function PlayerActions({ status, onCompleteSet, onSkip, onPause, onResume, onRestart, onEnd, onStartNow, onClose }: {
  status: WorkoutPlayerSession['status']; onCompleteSet: () => void; onSkip: () => void; onPause: () => void; onResume: () => void; onRestart: () => void; onEnd: () => void; onStartNow: () => void; onClose: () => void;
}) {
  if (status === 'active') return <div className="player-actions"><button type="button" onClick={onCompleteSet} className="primary-action"><Check size={19} aria-hidden="true" /> Complete set</button><div className="player-actions__secondary"><IconAction label="Pause" icon={Pause} onClick={onPause} /><IconAction label="Skip exercise" icon={SkipForward} onClick={onSkip} /></div></div>;
  if (status === 'resting') return <div className="player-actions player-actions--split"><button type="button" onClick={onStartNow} className="primary-action"><Play size={19} fill="currentColor" aria-hidden="true" /> Start now</button><IconAction label="Pause" icon={Pause} onClick={onPause} showLabel /></div>;
  if (status === 'paused') return <div className="player-actions"><button type="button" onClick={onResume} className="primary-action"><Play size={19} fill="currentColor" aria-hidden="true" /> Resume</button><div className="player-actions__secondary"><IconAction label="Restart" icon={RefreshCcw} onClick={onRestart} /><IconAction label="End workout" icon={Square} onClick={onEnd} /></div></div>;
  return <button type="button" onClick={onClose} className="secondary-action"><X size={18} aria-hidden="true" /> Close player</button>;
}

function IconAction({ label, icon: Icon, onClick, showLabel = false }: { label: string; icon: ComponentType<{ size?: number; 'aria-hidden'?: boolean }>; onClick: () => void; showLabel?: boolean }) {
  return <button type="button" className="icon-action" onClick={onClick} title={label} aria-label={label}><Icon size={19} />{showLabel && <span>{label}</span>}</button>;
}

function Confirmation({ message, confirmLabel, confirmIcon: Icon, tone, onConfirm, onCancel }: { message: string; confirmLabel: string; confirmIcon: ComponentType<{ size?: number; 'aria-hidden'?: boolean }>; tone: 'warning' | 'danger'; onConfirm: () => void; onCancel: () => void }) {
  return <div className="player-confirmation"><p>{message}</p><div><button type="button" onClick={onConfirm} className={`confirm-action confirm-action--${tone}`}><Icon size={18} /> {confirmLabel}</button><button type="button" onClick={onCancel} className="secondary-action">Keep paused</button></div></div>;
}

function CueToggle({ label, icon: Icon, enabled, onChange }: { label: string; icon: ComponentType<{ size?: number; 'aria-hidden'?: boolean }>; enabled: boolean; onChange: (enabled: boolean) => void }) {
  return <button type="button" onClick={() => onChange(!enabled)} className={enabled ? 'is-enabled' : ''} aria-label={`${label} ${enabled ? 'on' : 'off'}`} title={label} aria-pressed={enabled}><Icon size={17} /><span>{label}</span></button>;
}
