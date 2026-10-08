import { useEffect, useMemo, useState } from 'react';
import type { WorkoutSetLog } from '../types';
import { formatHistoryDate, formatHistoryTime } from '../lib/history-stats';
import { filterSetHistory } from '../lib/set-history';

const PAGE_SIZE = 20;

export function SetHistory({ logs }: { logs: WorkoutSetLog[] }) {
  const [search, setSearch] = useState('');
  const [ratedOnly, setRatedOnly] = useState(false);
  const [page, setPage] = useState(0);
  const filtered = useMemo(() => filterSetHistory(logs, search, ratedOnly), [logs, search, ratedOnly]);
  useEffect(() => { setPage(0); }, [logs]);
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pages - 1);
  const start = currentPage * PAGE_SIZE;
  const visible = filtered.slice(start, start + PAGE_SIZE);
  return <details className="space-y-3">
    <summary className="cursor-pointer text-sm font-semibold text-slate-300">Set history · {logs.length} {logs.length === 1 ? 'set' : 'sets'}</summary>
    {logs.length === 0 ? <p className="text-sm text-slate-500">No set logs in this range yet.</p> : <>
      <label className="block text-xs text-slate-400">Search logged exercises
        <input type="search" maxLength={80} value={search} onChange={event => { setSearch(event.target.value); setPage(0); }} placeholder="Exercise name" className="mt-1 w-full rounded border border-slate-700 bg-slate-950 p-2 text-sm text-slate-200" />
      </label>
      <label className="inline-flex items-center gap-2 text-xs text-slate-400"><input type="checkbox" checked={ratedOnly} onChange={event => { setRatedOnly(event.target.checked); setPage(0); }} />Rated sets only</label>
      <p role="status" aria-label="Set history results" className="text-xs text-slate-500">{filtered.length ? `${start + 1}–${start + visible.length} of ${filtered.length} matching sets` : 'No sets match these filters.'}</p>
      <div className="space-y-2" aria-label="Logged sets">
        {visible.map((log, index) => <article key={log.id ?? `${log.date}-${log.exercise}-${log.setNumber}-${start + index}`} className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm" aria-label={`${log.exercise}, set ${log.setNumber}`}>
          <p className="break-words font-medium text-slate-200">{log.exercise} · set {log.setNumber}</p>
          <p className="text-xs text-slate-500">{formatHistoryDate(log.date)} · {formatHistoryTime(log.date)}</p>
          <p className="mt-1 break-words text-slate-300">Actual: {log.actualReps} · {log.loadWeight != null ? `${log.loadWeight} ${log.loadUnit ?? 'kg'}` : 'Load not recorded'}</p>
          <p className="break-words text-xs text-slate-500">Planned: {log.plannedReps}</p>
          {(log.rpe != null || log.rir != null) && <p className="mt-1 text-emerald-300">{[log.rpe != null ? `RPE ${log.rpe}` : '', log.rir != null ? `${log.rir} reps in reserve` : ''].filter(Boolean).join(' · ')}</p>}
        </article>)}
      </div>
      {pages > 1 && <nav aria-label="Set history pages" className="flex flex-wrap items-center gap-2">
        <button type="button" className="secondary-action" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>Previous sets</button>
        <span className="text-xs text-slate-500">Page {currentPage + 1} of {pages}</span>
        <button type="button" className="secondary-action" disabled={currentPage + 1 >= pages} onClick={() => setPage(currentPage + 1)}>Next sets</button>
      </nav>}
    </>}
  </details>;
}
