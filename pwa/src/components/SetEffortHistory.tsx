import { useMemo } from 'react';
import type { WorkoutSetLog } from '../types';
import { formatHistoryDate, formatHistoryTime } from '../lib/history-stats';

export function SetEffortHistory({ logs }: { logs: WorkoutSetLog[] }) {
  const rated = useMemo(() => logs.filter((log) => log.rpe != null || log.rir != null)
    .slice().sort((a, b) => b.date.localeCompare(a.date) || (b.id ?? 0) - (a.id ?? 0)), [logs]);
  if (!rated.length) return null;
  return <details className="space-y-2">
    <summary className="cursor-pointer text-sm font-semibold text-slate-300">Set effort · {rated.length} rated {rated.length === 1 ? 'set' : 'sets'}</summary>
    <p className="text-xs text-slate-500">Latest {Math.min(rated.length, 20)} in this range</p>
    {rated.slice(0, 20).map((log) => <div key={log.id ?? `${log.date}-${log.exercise}-${log.setNumber}`} className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm">
      <p className="font-medium text-slate-200">{log.exercise} · set {log.setNumber}</p>
      <p className="text-xs text-slate-500">{formatHistoryDate(log.date)} · {formatHistoryTime(log.date)} · {log.actualReps}{log.loadWeight != null ? ` · ${log.loadWeight} ${log.loadUnit ?? 'kg'}` : ''}</p>
      <p className="mt-1 text-emerald-300">{[log.rpe != null ? `RPE ${log.rpe}` : '', log.rir != null ? `${log.rir} reps in reserve` : ''].filter(Boolean).join(' · ')}</p>
    </div>)}
  </details>;
}
