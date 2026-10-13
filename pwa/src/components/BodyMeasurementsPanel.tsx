import { useMemo, useState } from 'react';
import type { BodyMeasurementEntry, BodyMeasurementSite, HistoryRange, MeasurementUnit } from '../types';
import type { useBodyMeasurements } from '../hooks/useBodyMeasurements';
import { MEASUREMENT_SITES, measurementChange, sortedMeasurements } from '../lib/body-measurements';
import { formatShortDateKey, inHistoryRange, todayDateKey } from '../lib/history-stats';

const inputClass = 'mt-1 min-w-0 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200';
const buttonClass = 'rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 disabled:opacity-50';
const sites = Object.entries(MEASUREMENT_SITES);

export function BodyMeasurementsPanel({ workflow, range }: { workflow: ReturnType<typeof useBodyMeasurements>; range: HistoryRange }) {
  const { entries, draft, setDraft, editing, busy, result, save, edit, remove, cancel, reload } = workflow;
  const [filter, setFilter] = useState<BodyMeasurementSite | 'all'>('all');
  const [limit, setLimit] = useState(20);
  const [deleting, setDeleting] = useState<BodyMeasurementEntry>();
  const sorted = useMemo(() => sortedMeasurements(entries), [entries]);
  const filtered = sorted.filter(entry => inHistoryRange(entry.date, range) && (filter === 'all' || entry.site === filter));
  const siteEntries = sorted.filter(entry => entry.site === draft.site);
  const latest = siteEntries[0];
  const change = latest ? measurementChange(latest, siteEntries[1]) : null;
  const visibleLimit = range === 'month' ? Math.min(limit, filtered.length) : limit;
  return <section aria-label="Body measurements" className="space-y-3 rounded-lg border border-slate-800 bg-slate-900 p-4">
    <div className="flex flex-wrap items-start justify-between gap-2">
      <div><h3 className="text-sm font-semibold text-slate-200">Body measurements</h3>
        <p className="text-xs text-slate-400">{latest ? `${MEASUREMENT_SITES[latest.site]}: ${latest.value} ${latest.unit} · ${formatShortDateKey(latest.date)}` : `No ${MEASUREMENT_SITES[draft.site].toLowerCase()} entries yet`}</p>
        {change !== null && <p className="text-xs text-slate-400">{change > 0 ? '+' : ''}{change} {latest.unit} since previous entry</p>}
      </div>
      <button type="button" className={buttonClass} disabled={busy} onClick={() => { setDeleting(undefined); void reload(); }}>Reload measurements</button>
    </div>
    <form onSubmit={event => { event.preventDefault(); void save(); }}>
      <fieldset disabled={busy} className="space-y-3">
        <legend className="mb-2 text-sm text-slate-300">{editing ? 'Edit measurement' : 'Log measurement'}</legend>
        <div className="grid grid-cols-2 gap-2">
          <label className="min-w-0 text-xs text-slate-400">Measurement
            <select className={inputClass} value={draft.site} onChange={event => setDraft({ ...draft, site: event.target.value as BodyMeasurementSite })}>
              {sites.map(([key, label]) => <option key={key} value={key}>{label}</option>)}
            </select>
          </label>
          <label className="min-w-0 text-xs text-slate-400">Measurement date
            <input className={inputClass} type="date" required min="1900-01-01" max={todayDateKey()} value={draft.date} onChange={event => setDraft({ ...draft, date: event.target.value })} />
          </label>
          <label className="min-w-0 text-xs text-slate-400">Measurement value
            <input className={inputClass} type="number" inputMode="decimal" required min="0.1" max="1000" step="0.1" value={draft.value} onChange={event => setDraft({ ...draft, value: event.target.value })} />
          </label>
          <label className="min-w-0 text-xs text-slate-400">Measurement unit
            <select className={inputClass} value={draft.unit} onChange={event => setDraft({ ...draft, unit: event.target.value as MeasurementUnit })}><option value="cm">cm</option><option value="in">in</option></select>
          </label>
        </div>
        <label className="block text-xs text-slate-400">Measurement note (optional)
          <input className={inputClass} maxLength={160} value={draft.note} onChange={event => setDraft({ ...draft, note: event.target.value })} />
        </label>
        <div className="flex gap-2"><button type="submit" className={buttonClass}>{busy ? 'Saving…' : editing ? 'Update measurement' : 'Save measurement'}</button>
          {editing && <button type="button" className={buttonClass} onClick={cancel}>Cancel edit</button>}
        </div>
      </fieldset>
    </form>
    {result && <p role="status" className={`text-sm ${result.error ? 'text-rose-300' : 'text-emerald-300'}`}>{result.message}</p>}
    <label className="block text-xs text-slate-400">Show measurements
      <select className={inputClass} value={filter} onChange={event => { setFilter(event.target.value as typeof filter); setLimit(20); }}>
        <option value="all">All measurements</option>{sites.map(([key, label]) => <option key={key} value={key}>{label}</option>)}
      </select>
    </label>
    {!filtered.length && <p className="text-sm text-slate-500">No measurements in this range.</p>}
    <ul className="divide-y divide-slate-800">
      {filtered.slice(0, visibleLimit).map(entry => <li key={entry.id} className="space-y-2 py-3">
        <div className="flex flex-wrap justify-between gap-2 text-sm text-slate-300"><span>{MEASUREMENT_SITES[entry.site]} · {entry.value} {entry.unit}</span><span>{formatShortDateKey(entry.date)}</span></div>
        {entry.note && <p className="break-words text-xs text-slate-400">{entry.note}</p>}
        <div className="flex gap-2">
          <button type="button" className={buttonClass} disabled={busy} onClick={() => { setDeleting(undefined); edit(entry); }}>Edit measurement</button>
          <button type="button" className={buttonClass} disabled={busy || !!editing} onClick={() => setDeleting({ ...entry })}>Delete measurement</button>
        </div>
        {deleting && deleting.id === entry.id && <div className="space-y-2" role="group" aria-label="Confirm measurement deletion">
          <p className="text-sm text-slate-300">Delete {MEASUREMENT_SITES[deleting.site].toLowerCase()} · {deleting.value} {deleting.unit} from {formatShortDateKey(deleting.date)}?</p>
          <div className="flex gap-2"><button type="button" className={buttonClass} disabled={busy} onClick={() => { void remove(deleting).then(() => setDeleting(undefined)); }}>Confirm delete measurement</button>
            <button type="button" className={buttonClass} disabled={busy} onClick={() => setDeleting(undefined)}>Keep measurement</button></div>
        </div>}
      </li>)}
    </ul>
    {filtered.length > visibleLimit && <button type="button" className={buttonClass} onClick={() => setLimit(limit + 20)}>Show more measurements</button>}
  </section>;
}
