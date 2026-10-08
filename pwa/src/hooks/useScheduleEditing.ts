import { useRef, useState } from 'react';
import type { Weekday, WorkoutRow } from '../types';
import { moveWeeklySession } from '../lib/schedule-editing';
import { pushScheduleToNative } from '../lib/native-bridge';

export function useScheduleEditing({ onScheduleChanged, blocked }: {
  onScheduleChanged: (rows: WorkoutRow[]) => void;
  blocked: boolean;
}) {
  const inFlight = useRef(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ error: boolean; message: string } | null>(null);
  async function move(rows: WorkoutRow[], day: Weekday, time: string): Promise<boolean> {
    if (inFlight.current) return false;
    inFlight.current = true;
    setBusy(true);
    setResult(null);
    try {
      if (blocked) throw new Error('Finish or end the current workout before moving a session.');
      const schedule = await moveWeeklySession(rows, day, time);
      onScheduleChanged(schedule);
      try {
        if (!await pushScheduleToNative(schedule)) throw new Error('Native schedule update failed');
        setResult({ error: false, message: `Weekly session moved to ${day} at ${time}.` });
      } catch {
        setResult({ error: true, message: 'Session moved on this phone. Native schedule update failed; reopen the app to retry before syncing your watch.' });
      }
      return true;
    } catch (error) {
      setResult({ error: true, message: error instanceof Error ? error.message : 'Could not move the session.' });
      return false;
    } finally { inFlight.current = false; setBusy(false); }
  }
  return { move, busy, result, clearResult: () => setResult(null) };
}
