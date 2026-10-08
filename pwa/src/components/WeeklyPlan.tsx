import { WeeklySessionEditor, type ScheduleEditorProps } from './WeeklySessionEditor';
import { useState } from 'react';
import { CalendarDays, Clock3 } from 'lucide-react';
import { WEEKDAYS, type Weekday, type WorkoutRow } from '../types';
import { estimateLevelFor, estimateWorkoutDurationSeconds, formatEstimatedDuration } from '../lib/workout-planning';

export function WeeklyPlan({ workouts, today, onBuildPlan, scheduleEditor }: {
  workouts: WorkoutRow[];
  today: Weekday;
  onBuildPlan: () => void;
  scheduleEditor?: ScheduleEditorProps;
}) {
  const [selectedDay, setSelectedDay] = useState<Weekday>(today);
  const days = Array.from({ length: 7 }, (_, offset) => WEEKDAYS[(WEEKDAYS.indexOf(today) + offset) % 7]);
  const rows = workouts.filter((row) => row.day === selectedDay).sort((a, b) => a.time.localeCompare(b.time));
  const sessions = [...new Set(rows.map((row) => row.time))];
  const trainingDays = new Set(workouts.map((row) => row.day)).size;

  return <section className="weekly-plan" aria-label="Weekly schedule">
    <div className="section-heading">
      <div><p className="section-kicker">Looking ahead</p><h3><CalendarDays size={18} aria-hidden="true" /> Weekly plan</h3></div>
      <span>{trainingDays} training {trainingDays === 1 ? 'day' : 'days'}</span>
    </div>
    <div className="week-picker" aria-label="Choose a schedule day">
      {days.map((day) => {
        const count = workouts.filter((row) => row.day === day).length;
        return <button key={day} type="button" aria-pressed={day === selectedDay} aria-label={`${day}${day === today ? ', today' : ''}, ${count} exercises`} onClick={() => setSelectedDay(day)}>
          <span>{day.slice(0, 3)}</span><strong>{count || '—'}</strong><small>{day === today ? 'Today' : count ? 'Plan' : 'Rest'}</small>
        </button>;
      })}
    </div>
    <div className="week-preview" aria-live="polite">
      <div className="section-heading"><h3>{selectedDay}</h3><span>Repeats weekly</span></div>
      {sessions.length ? sessions.map((time) => {
        const sessionRows = rows.filter((row) => row.time === time);
        return <div key={time} className="week-session">
          <div className="week-session__heading"><strong>{time}</strong><span><Clock3 size={14} aria-hidden="true" /> {formatEstimatedDuration(estimateWorkoutDurationSeconds(sessionRows, estimateLevelFor(sessionRows)))}</span></div>
          <ul>{sessionRows.map((row, index) => <li key={row.id ?? index}><span>{row.exercise}</span><span>{row.sets} × {row.reps}{row.loadWeight != null ? ` · ${row.loadWeight} ${row.loadUnit ?? 'kg'}` : ''}</span></li>)}</ul>
          {scheduleEditor && <WeeklySessionEditor rows={sessionRows} editor={scheduleEditor} onMoved={setSelectedDay} />}
        </div>;
      }) : <p className="week-rest">Rest day · No exercises scheduled.</p>}
    </div>
    {scheduleEditor?.blocked && <p className="text-xs text-amber-300">Finish or end the current workout before changing a weekly session.</p>}
    {scheduleEditor?.result && <p role={scheduleEditor.result.error ? 'alert' : 'status'} className={scheduleEditor.result.error ? 'text-sm text-amber-300' : 'text-sm text-emerald-300'}>{scheduleEditor.result.message}</p>}
    {scheduleEditor?.retryPending && scheduleEditor.retry && <button type="button" className="secondary-action" disabled={scheduleEditor.busy} onClick={() => void scheduleEditor.retry?.()}>Retry schedule update</button>}
    <button type="button" className="secondary-action" onClick={onBuildPlan}>Build a playlist</button>
  </section>;
}
