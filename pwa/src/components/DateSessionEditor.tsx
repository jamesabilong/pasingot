import { useState } from 'react';
import type { WorkoutRow } from '../types';
import { nextScheduleDate } from '../lib/schedule-dates';
import { todayDateKey } from '../lib/history-stats';
import type { ScheduleEditorProps } from './WeeklySessionEditor';

export function DateSessionEditor({ rows, editor }: { rows: WorkoutRow[]; editor: ScheduleEditorProps }) {
  const [snapshot, setSnapshot] = useState<WorkoutRow[] | null>(null);
  const [sourceDate, setSourceDate] = useState('');
  const [date, setDate] = useState('');
  const [time, setTime] = useState('');
  const [action, setAction] = useState<'move' | 'skip' | 'clear'>('move');
  if (!editor.changeDate || rows.some(row => row.questId != null)) return null;
  const today = todayDateKey();
  const changes = [...new Set(rows.flatMap(row => Object.keys(row.dateOverrides ?? {})))].sort();
  const open = (source = nextScheduleDate(rows[0].day, today)) => {
    const target = rows[0].dateOverrides?.[source];
    setSnapshot(structuredClone(rows)); setSourceDate(source); setDate(target?.date ?? source);
    setTime(target?.time ?? rows[0].time); setAction(target === null ? 'skip' : 'move'); editor.clearResult();
  };
  return <div className="mt-3 space-y-3">
    {changes.length > 0 && <ul className="space-y-2 text-xs text-slate-400" aria-label="Date-specific changes">
      {changes.map(source => {
        const target = rows.find(row => Object.hasOwn(row.dateOverrides ?? {}, source))?.dateOverrides?.[source];
        return <li key={source} className="flex flex-wrap items-center justify-between gap-2">
          <span>{source}: {target ? `Moved to ${target.date} · ${target.time}` : 'Skipped'}</span>
          <button type="button" className="secondary-action" disabled={editor.busy || editor.blocked || source < today} onClick={() => open(source)}>Edit date change</button>
        </li>;
      })}
    </ul>}
    {snapshot ? <form aria-label={`Change one ${rows[0].day} ${rows[0].time} occurrence`} className="space-y-3" onSubmit={async event => {
      event.preventDefault();
      if (await editor.changeDate?.(snapshot, sourceDate, action === 'move' ? { date, time } : action)) setSnapshot(null);
    }}>
      <p className="text-xs text-slate-400">Changes all {snapshot.length} exercises on one date. Other weeks and history are kept.</p>
      <div className="grid grid-cols-2 gap-2">
        <label className="text-xs text-slate-400">Occurrence date<input type="date" required min={today} value={sourceDate} disabled={editor.busy || editor.blocked} className="mt-1 w-full min-w-0 rounded border border-slate-700 bg-slate-950 p-2 text-sm" onChange={event => setSourceDate(event.target.value)} /></label>
        <label className="text-xs text-slate-400">Change<select value={action} disabled={editor.busy || editor.blocked} className="mt-1 w-full rounded border border-slate-700 bg-slate-950 p-2 text-sm" onChange={event => setAction(event.target.value as typeof action)}>
          <option value="move">Move this date</option><option value="skip">Skip this date</option><option value="clear">Clear date change</option>
        </select></label>
      </div>
      {action === 'move' && <div className="grid grid-cols-2 gap-2">
        <label className="text-xs text-slate-400">New date<input type="date" required min={today} value={date} disabled={editor.busy || editor.blocked} className="mt-1 w-full min-w-0 rounded border border-slate-700 bg-slate-950 p-2 text-sm" onChange={event => setDate(event.target.value)} /></label>
        <label className="text-xs text-slate-400">New start time<input type="time" required value={time} disabled={editor.busy || editor.blocked} className="mt-1 w-full min-w-0 rounded border border-slate-700 bg-slate-950 p-2 text-sm" onChange={event => setTime(event.target.value)} /></label>
      </div>}
      {action === 'skip' && <p className="text-xs text-amber-300">Skips all {snapshot.length} exercises on {sourceDate || 'the selected date'} only.</p>}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="secondary-action" disabled={editor.busy || editor.blocked}>{action === 'skip' ? 'Confirm date skip' : action === 'clear' ? 'Clear date change' : 'Save date move'}</button>
        <button type="button" className="secondary-action" disabled={editor.busy} onClick={() => { setSnapshot(null); editor.clearResult(); }}>Cancel date change</button>
      </div>
    </form> : <button type="button" className="secondary-action" disabled={editor.busy || editor.blocked} onClick={() => open()}>Change one date</button>}
  </div>;
}
