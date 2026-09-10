import { Capacitor } from '@capacitor/core';
import { Watch, RefreshCw } from 'lucide-react';
import { useWatchSync } from '../hooks/useWatchSync';

export function WatchSyncPanel({ hasWorkout }: { hasWorkout: boolean }) {
  const { busy, result, sync } = useWatchSync();
  if (!Capacitor.isNativePlatform()) return null;

  return <section className="watch-sync-panel" aria-label="Watch sync">
    <div className="section-heading">
      <div><p className="section-kicker">Wear OS</p><h3><Watch size={18} aria-hidden="true" /> Send to your watch</h3></div>
    </div>
    <p>Send today’s scheduled exercises to your paired watch.</p>
    <button type="button" className="secondary-action" disabled={busy || !hasWorkout} onClick={() => void sync()}>
      <RefreshCw size={16} aria-hidden="true" /> {busy ? 'Sending…' : 'Send today to watch'}
    </button>
    {!hasWorkout && <p>Add exercises to today’s schedule first.</p>}
    {result && <p role={result.error ? 'alert' : 'status'}>{result.message}</p>}
    <details className="watch-sync-panel__hint">
      <summary>Connection and saved workouts</summary>
      <p>Install Pasingot on both devices and connect the watch to this phone. Already downloaded today? Existing watch progress is kept. To fetch changes, delete that download from Options on the watch, then sync again.</p>
    </details>
  </section>;
}
