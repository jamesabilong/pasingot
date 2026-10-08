import { useRef, useState } from 'react';
import type { Weekday, WorkoutRow } from '../types';
import { moveWeeklySession, removeWeeklySession } from '../lib/schedule-editing';
import { pushScheduleToNative } from '../lib/native-bridge';

export function useScheduleEditing({ onScheduleChanged, blocked }: {
  onScheduleChanged: (rows: WorkoutRow[]) => void | Promise<void>;
  blocked: boolean;
}) {
  const inFlight = useRef(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ error: boolean; message: string } | null>(null);
  async function edit(rows: WorkoutRow[], destination: { day: Weekday; time: string } | null): Promise<boolean> {
    if (inFlight.current) return false;
    inFlight.current = true;
    setBusy(true);
    setResult(null);
    try {
      if (blocked) throw new Error('Finish or end the current workout before changing a session.');
      const schedule = destination ? await moveWeeklySession(rows, destination.day, destination.time) : await removeWeeklySession(rows);
      await onScheduleChanged(schedule);
      try {
        if (!await pushScheduleToNative(schedule)) throw new Error('Native schedule update failed');
        setResult({ error: false, message: destination ? `Weekly session moved to ${destination.day} at ${destination.time}.` : 'Weekly session removed. Workout history kept.' });
      } catch {
        setResult({ error: true, message: `Session ${destination ? 'moved' : 'removed'} on this phone. Could not update the schedule for watch sync; reopen the app to retry before syncing your watch.` });
      }
      return true;
    } catch (error) {
      setResult({ error: true, message: error instanceof Error ? error.message : 'Could not change the session.' });
      return false;
    } finally { inFlight.current = false; setBusy(false); }
  }
  return { move: (rows: WorkoutRow[], day: Weekday, time: string) => edit(rows, { day, time }),
    remove: (rows: WorkoutRow[]) => edit(rows, null), busy, result, clearResult: () => setResult(null) };
}
