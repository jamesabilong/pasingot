import { useRef, useState } from 'react';
import { getAll, STORES } from '../lib/db';
import { sendTodayToWatch } from '../lib/native-bridge';
import { type WorkoutRow } from '../types';

export function useWatchSync() {
  const inFlight = useRef(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ error: boolean; message: string } | null>(null);

  async function sync() {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setResult(null);
    try {
      const rows = await getAll<WorkoutRow>(STORES.workouts);
      const sent = await sendTodayToWatch(rows);
      setResult({ error: false, message: `${sent.exerciseCount} exercises queued for your watch. Open Pasingot on the watch to confirm receipt.` });
    } catch (error) {
      setResult({ error: true, message: error instanceof Error ? error.message : 'Could not send workout. Check your watch connection and try again.' });
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  return { busy, result, sync };
}
