import { useState } from 'react';
import { Check, Clock3, Dumbbell, Play, SkipForward } from 'lucide-react';
import { WatchSyncPanel } from './WatchSyncPanel';
import { WeeklyPlan } from './WeeklyPlan';
import { WorkoutPlayer, type WorkoutCueSettingsView, type WorkoutSetInput, type WorkoutPlayerSession } from './WorkoutPlayer';
import { type PlanProgress } from './SummaryCards';
import { type Weekday, type WorkoutLog, type WorkoutRow } from '../types';

function scheduleLoadLabel(row: WorkoutRow): string {
  return row.loadWeight != null && row.loadUnit ? ` · ${row.loadWeight} ${row.loadUnit}` : '';
}

export function TodayView({
  todayName,
  weeklyWorkouts,
  onBuildPlan,
  onBrowseQuests,
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
  todayName: Weekday;
  weeklyWorkouts: WorkoutRow[];
  onBuildPlan: () => void;
  onBrowseQuests: () => void;
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
  const hasWorkout = workouts.length > 0;
  const [queueFilter, setQueueFilter] = useState<'all' | 'pending' | 'done' | 'skipped'>('all');
  const visibleWorkouts = workouts.filter((row) => {
    const status = row.id == null ? undefined : statuses.get(row.id);
    return queueFilter === 'all' || (queueFilter === 'pending' ? !status : status === queueFilter);
  });

  return (
    <section className="today-view space-y-4">
      <div className="today-hero">
        <div className="today-hero__heading">
          <div>
            <p className="section-kicker">Today</p>
            <h2>{hasWorkout ? `${todayName} training` : todayName}</h2>
          </div>
          {hasWorkout && <span className="status-chip">{progress.resolvedPercent}% handled</span>}
        </div>

        {hasWorkout ? <>
          <div className="today-hero__stats" aria-label="Workout summary">
            <div><Dumbbell aria-hidden="true" /><strong>{workouts.length}</strong><span>Exercises</span></div>
            <div><Clock3 aria-hidden="true" /><strong>{estimate.replace(/^Est\.\s*/, '')}</strong><span>Estimated</span></div>
            <div><Check aria-hidden="true" /><strong>{setLogCount}</strong><span>Sets logged</span></div>
          </div>
          <div className="plan-meter" aria-label={`${progress.resolvedPercent}% of today's plan handled`}>
            <span className="plan-meter__done" style={{ width: `${(progress.completed / progress.total) * 100}%` }} />
            <span className="plan-meter__skipped" style={{ width: `${(progress.skipped / progress.total) * 100}%` }} />
          </div>
          <p className="plan-status-line">{progress.completed} completed · {progress.pending} pending · {progress.skipped} skipped</p>
          {progress.pending === 0 && <p className="plan-finished" role="status">{progress.completed === progress.total ? 'Workout complete. Great work today.' : 'Today’s plan is finished.'}</p>}
          {!activeSession && progress.pending > 0 && (
            <button type="button" onClick={onStartPlayer} className="primary-action">
              <Play size={18} fill="currentColor" aria-hidden="true" /> Start workout
            </button>
          )}
        </> : <div className="today-empty">
          <Dumbbell aria-hidden="true" />
          <p>No exercises are scheduled for today.</p>
          <span>Build a playlist or import a schedule to begin.</span>
          <div className="empty-plan-actions"><button type="button" className="primary-action" onClick={onBuildPlan}>Build a playlist</button><button type="button" className="secondary-action" onClick={onBrowseQuests}>Browse quests</button></div>
        </div>}
      </div>

      <WatchSyncPanel hasWorkout={hasWorkout} />

      {activeSession && activeRows.length === 0 && (
        <div className="today-empty" role="alert">
          <Dumbbell aria-hidden="true" />
          <p>Today's schedule changed, and this in-progress workout's exercises are no longer in it.</p>
          <button type="button" onClick={onEnd} className="secondary-action">End workout</button>
        </div>
      )}

      {activeSession && activeRows.length > 0 && <WorkoutPlayer
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
      />}

      {hasWorkout && <div className="plan-section">
        <div className="section-heading">
          <div>
            <p className="section-kicker">Plan</p>
            <h3>Exercise queue</h3>
          </div>
          <span>{progress.pending} remaining</span>
        </div>
        <div className="queue-filters" aria-label="Filter exercise queue">
          {(['all', 'pending', 'done', 'skipped'] as const).map((filter) => <button key={filter} type="button" aria-pressed={queueFilter === filter} onClick={() => setQueueFilter(filter)}>{filter === 'all' ? `All ${progress.total}` : filter === 'done' ? `Done ${progress.completed}` : filter === 'pending' ? `Pending ${progress.pending}` : `Skipped ${progress.skipped}`}</button>)}
        </div>
        <div className="exercise-queue">
          {visibleWorkouts.length === 0 && <p className="week-rest" role="status">No {queueFilter === 'done' ? 'completed' : queueFilter} exercises.</p>}
          {visibleWorkouts.map((row) => {
            const status = row.id == null ? undefined : statuses.get(row.id);
            return <div key={row.id} className={`exercise-row ${status ? `exercise-row--${status}` : ''}`}>
              <span className="exercise-row__index">{String(workouts.indexOf(row) + 1).padStart(2, '0')}</span>
              <div className="exercise-row__body">
                <p>{row.exercise}</p>
                <span className="exercise-status">{status === 'done' ? 'Completed' : status === 'skipped' ? 'Skipped' : 'Pending'}</span>
                <span>{row.time} · {row.sets} × {row.reps}{scheduleLoadLabel(row)} · {row.rest}s rest</span>
              </div>
              <div className="exercise-row__actions">
                <button type="button" onClick={() => onLogExercise(row, 'done')} className={status === 'done' ? 'is-active is-done' : ''} title="Mark as done" aria-label={`Mark ${row.exercise} as done`}><Check size={18} aria-hidden="true" /></button>
                <button type="button" onClick={() => onLogExercise(row, 'skipped')} className={status === 'skipped' ? 'is-active is-skipped' : ''} title="Skip exercise" aria-label={`Skip ${row.exercise}`}><SkipForward size={18} aria-hidden="true" /></button>
              </div>
            </div>;
          })}
        </div>
      </div>}
      <WeeklyPlan workouts={weeklyWorkouts} today={todayName} onBuildPlan={onBuildPlan} />
    </section>
  );
}
