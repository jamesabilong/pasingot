import { Watch, RefreshCw } from 'lucide-react';
import { useWatchSync } from '../hooks/useWatchSync';
import { getWatchSyncAvailability, WATCH_SYNC_UNAVAILABLE_MESSAGES, type WatchSessionSnapshot } from '../lib/native-bridge';
import { formatDuration } from '../lib/format';

const SESSION_LABELS = { active: 'In progress', resting: 'Resting', paused: 'Paused', completed: 'Completed', ended: 'Ended' };

export function WatchSyncPanel({ hasWorkout, session }: { hasWorkout: boolean; session: WatchSessionSnapshot | null }) {
  const { busy, result, sync } = useWatchSync();
  const availability = getWatchSyncAvailability();

  return <section className="watch-sync-panel" aria-label="Watch sync">
    <div className="section-heading">
      <div><p className="section-kicker">Wear OS</p><h3><Watch size={18} aria-hidden="true" /> Watch sync</h3></div>
    </div>
    {availability !== 'available' ? <>
      <p>{WATCH_SYNC_UNAVAILABLE_MESSAGES[availability]}</p>
      {availability === 'browser' && <details className="watch-sync-panel__hint">
        <summary>Move a browser plan to the Android app</summary>
        <p>In this browser, use Import → Export full backup. Move the file to your phone, then use Import → Restore backup in the Pasingot Android app.</p>
        <p>Restore replaces the Android app’s local data; export a backup there first if you need to keep it. Browser and Android app data do not sync automatically.</p>
        <p>After restoring, use Today → Send today to watch in the Android app. Only today’s scheduled exercises are sent.</p>
      </details>}
    </> : <>
      {session && <div role="status" aria-label="Latest watch workout">
        <p><strong>Watch workout · {SESSION_LABELS[session.status]}</strong></p>
        {session.currentExercise && <p>{session.currentExercise} · Set {session.currentSet} · Exercise {Math.min(session.exerciseIndex + 1, session.totalExercises)} of {session.totalExercises}</p>}
        <p>{formatDuration(session.elapsedSeconds)} elapsed at last update · {session.workoutDate}</p>
        <p>Last received update: {new Date(session.timestamp).toLocaleString()}. {session.status === 'active' || session.status === 'resting' || session.status === 'paused' ? 'Continue this workout on your watch.' : 'See History for the session summary.'}</p>
      </div>}
      <p>Send today’s scheduled exercises to your paired watch.</p>
      <button type="button" className="secondary-action" disabled={busy || !hasWorkout} onClick={() => void sync()}>
        <RefreshCw size={16} aria-hidden="true" /> {busy ? 'Sending…' : 'Send today to watch'}
      </button>
      {!hasWorkout && <p>Add exercises to today’s schedule first.</p>}
      {result && <p role={result.error ? 'alert' : 'status'}>{result.message}</p>}
      <details className="watch-sync-panel__hint">
        <summary>Connection and saved workouts</summary>
        <p>Install Pasingot on both devices and connect the watch to this phone. Already downloaded today? Existing watch progress is kept. To fetch changes, delete that download from Options on the watch, then sync again.</p>
        <p>Starting or changing a workout on the watch updates its status here when connected. Offline updates arrive after reconnection. Workout controls stay on the watch.</p>
      </details>
    </>}
  </section>;
}
