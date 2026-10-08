import { useRef, useState } from 'react';
import type { Weekday, WorkoutRow } from '../types';
import { moveWeeklySession, removeWeeklySession } from '../lib/schedule-editing';
import { getAll, STORES } from '../lib/db';
import { pushScheduleToNative } from '../lib/native-bridge';

export function useScheduleEditing({ onScheduleChanged, blocked }: {
  onScheduleChanged: (rows: WorkoutRow[]) => void | Promise<void>;
  blocked: boolean;
}) {
  const inFlight = useRef(false);
  const pending = useRef(false);
  const [busy, setBusy] = useState(false);
  const [retryPending, setRetryPending] = useState(false);
  const [result, setResult] = useState<{ error: boolean; message: string } | null>(null);
  function markPending(value: boolean) { pending.current = value; setRetryPending(value); }

  async function publish(schedule: WorkoutRow[], success: string, saved: string): Promise<boolean> {
    let viewUpdated = false;
    try {
      await onScheduleChanged(schedule);
      viewUpdated = true;
      if (!await pushScheduleToNative(schedule)) throw new Error('Schedule cache update failed');
      markPending(false);
      setResult({ error: false, message: success });
      return true;
    } catch {
      markPending(true);
      setResult({ error: true, message: `${saved} ${viewUpdated ? 'Could not update the schedule for watch sync.' : 'Could not refresh the schedule view.'} Retry the schedule update.` });
      return false;
    }
  }

  async function edit(rows: WorkoutRow[], destination: { day: Weekday; time: string } | null): Promise<boolean> {
    if (inFlight.current) return false;
    inFlight.current = true; setBusy(true); setResult(null);
    try {
      if (blocked) throw new Error('Finish or end the current workout before changing a session.');
      const schedule = destination ? await moveWeeklySession(rows, destination.day, destination.time) : await removeWeeklySession(rows);
      await publish(schedule,
        destination ? `Weekly session moved to ${destination.day} at ${destination.time}.` : 'Weekly session removed. Workout history kept.',
        `Session ${destination ? 'moved' : 'removed'} on this phone.`);
      // The edit committed. A later view/cache failure must not offer to repeat it.
      return true;
    } catch (error) {
      setResult({ error: true, message: error instanceof Error ? error.message : 'Could not change the session.' });
      return false;
    } finally { inFlight.current = false; setBusy(false); }
  }

  async function retry(): Promise<boolean> {
    if (inFlight.current || !pending.current) return false;
    inFlight.current = true; setBusy(true);
    try {
      // Always use current durable data; another edit/restore may have superseded
      // the failed update. This operation never repeats a move or removal.
      const schedule = await getAll<WorkoutRow>(STORES.workouts);
      return await publish(schedule, 'Saved schedule updated. Workout history kept.', 'Schedule is saved on this phone.');
    } catch {
      setResult({ error: true, message: 'Could not read the saved schedule. Try the schedule update again.' });
      return false;
    } finally { inFlight.current = false; setBusy(false); }
  }

  return { move: (rows: WorkoutRow[], day: Weekday, time: string) => edit(rows, { day, time }),
    remove: (rows: WorkoutRow[]) => edit(rows, null), retry, retryPending, busy, result,
    clearResult: () => { if (!pending.current) setResult(null); } };
}
