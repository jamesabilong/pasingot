import { useState } from 'react';
import { WEEKDAYS, type Weekday, type WorkoutRow } from '../types';

export type ScheduleEditorProps = {
  busy: boolean;
  blocked: boolean;
  result: { error: boolean; message: string } | null;
  move: (rows: WorkoutRow[], day: Weekday, time: string) => Promise<boolean>;
  remove?: (rows: WorkoutRow[]) => Promise<boolean>;
  clearResult: () => void;
};

export function WeeklySessionEditor({ rows, editor, onMoved }: {
  rows: WorkoutRow[];
  editor: ScheduleEditorProps;
  onMoved: (day: Weekday) => void;
}) {
  const [open, setOpen] = useState(false);
  const [removalRows, setRemovalRows] = useState<WorkoutRow[] | null>(null);
  const [day, setDay] = useState(rows[0].day);
  const [time, setTime] = useState(rows[0].time);
  if (rows.some(row => row.questId != null)) return <p className="text-xs text-slate-500">Manage this session from Quests.</p>;
  return <div className="mt-3">
    {removalRows ? <div role="group" aria-label={`Remove ${removalRows[0].day} ${removalRows[0].time} weekly session`} className="space-y-3">
      <p className="text-sm text-amber-300">Remove this weekly session?</p>
      <p className="text-xs text-slate-400">Removes all {removalRows.length} exercises at {removalRows[0].time} every {removalRows[0].day}. Workout history is kept.</p>
      <div className="flex flex-wrap gap-2">
        <button type="button" className="secondary-action" disabled={editor.busy || editor.blocked} onClick={async () => { if (await editor.remove?.(removalRows)) setRemovalRows(null); }}>Confirm removal</button>
        <button type="button" className="secondary-action" disabled={editor.busy} onClick={() => { setRemovalRows(null); editor.clearResult(); }}>Keep session</button>
      </div>
    </div> : open ? <form aria-label={`Move ${rows[0].day} ${rows[0].time} session`} className="space-y-3" onSubmit={async event => {
      event.preventDefault();
      if (await editor.move(rows, day, time)) { setOpen(false); onMoved(day); }
    }}>
      <p className="text-xs text-slate-400">Moves all {rows.length} exercises every week. Workout history is kept.</p>
      <div className="grid grid-cols-2 gap-2">
        <label className="text-xs text-slate-400">New weekday<select className="mt-1 w-full rounded border border-slate-700 bg-slate-950 p-2 text-sm" value={day} disabled={editor.busy || editor.blocked} onChange={event => setDay(event.target.value as Weekday)}>{WEEKDAYS.map(value => <option key={value}>{value}</option>)}</select></label>
        <label className="text-xs text-slate-400">New start time<input type="time" required className="mt-1 w-full min-w-0 rounded border border-slate-700 bg-slate-950 p-2 text-sm" value={time} disabled={editor.busy || editor.blocked} onChange={event => setTime(event.target.value)} /></label>
      </div>
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="secondary-action" disabled={editor.busy || editor.blocked || (day === rows[0].day && time === rows[0].time)}>Save weekly move</button>
        <button type="button" className="secondary-action" disabled={editor.busy} onClick={() => { setOpen(false); editor.clearResult(); }}>Cancel</button>
      </div>
    </form> : <div className="flex flex-wrap gap-2"><button type="button" className="secondary-action" disabled={editor.busy || editor.blocked} onClick={() => { setDay(rows[0].day); setTime(rows[0].time); editor.clearResult(); setOpen(true); }}>Move weekly session</button>
      {editor.remove && <button type="button" className="secondary-action" disabled={editor.busy || editor.blocked} onClick={() => { editor.clearResult(); setRemovalRows(rows.map(row => ({ ...row }))); }}>Remove weekly session</button>}
    </div>}
  </div>;
}
