import { useMemo } from 'react';
import { usePlateEquipment } from '../hooks/usePlateEquipment';
import { calculatePlates } from '../lib/plate-calculator';
import type { WeightUnit } from '../types';

const inputClass = 'mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none';

export function PlateCalculator() {
  const { unit, draft, setDraft, changeUnit, save, reset, ready, busy, message } = usePlateEquipment();
  const result = useMemo(() => calculatePlates(
    draft.target.trim() ? Number(draft.target) : NaN,
    draft.bar.trim() ? Number(draft.bar) : NaN,
    draft.sizes.split(',').map((size) => size.trim() ? Number(size) : NaN),
  ), [draft]);
  return <details className="rounded-lg border border-slate-800 bg-slate-900 p-4">
    <summary className="cursor-pointer text-base font-semibold text-slate-200">Plate calculator</summary>
    <div className="mt-3 space-y-3">
      <label className="block text-xs text-slate-400">Weight unit
        <select className={inputClass} disabled={!ready || busy} value={unit} onChange={event => changeUnit(event.target.value as WeightUnit)}><option value="kg">kg</option><option value="lb">lb</option></select>
      </label>
      <p className="text-xs text-slate-500">Changing units loads that unit’s saved equipment and resets the target to an example weight.</p>
      <div className="grid grid-cols-2 gap-3">
        <label className="text-xs text-slate-400">Target total ({unit})
          <input type="number" inputMode="decimal" min="0" max="1000" step="0.01" disabled={!ready || busy} className={inputClass} value={draft.target} onChange={(event) => setDraft({ ...draft, target: event.target.value })} />
        </label>
        <label className="text-xs text-slate-400">Bar weight ({unit})
          <input type="number" inputMode="decimal" min="0" max="1000" step="0.01" disabled={!ready || busy} className={inputClass} value={draft.bar} onChange={(event) => setDraft({ ...draft, bar: event.target.value })} />
        </label>
      </div>
      <label className="block text-xs text-slate-400">Available plate sizes ({unit}), separated by commas
        <input type="text" disabled={!ready || busy} className={inputClass} maxLength={200} value={draft.sizes} onChange={(event) => setDraft({ ...draft, sizes: event.target.value })} />
      </label>
      <div className="flex flex-wrap gap-2">
        <button type="button" className="secondary-action" disabled={!ready || busy} onClick={() => void save()}>Save equipment</button>
        <button type="button" className="secondary-action" disabled={!ready || busy} onClick={reset}>Use default equipment</button>
      </div>
      {message && <p role={message.error ? 'alert' : 'status'} aria-label="Equipment settings" className={message.error ? 'text-sm text-amber-300' : 'text-sm text-emerald-300'}>{message.text}</p>}
      <p className="text-xs text-slate-500">Assumes enough pairs of each size. Load the same plates on both sides. Total includes the bar.</p>
      <div role="status" aria-label="Plate calculation" aria-live="polite" className="space-y-1 text-sm">
        {'error' in result ? <p className="text-amber-300">{result.error}</p> : <>
          <p className="font-medium text-emerald-300">Each side: {result.plates.length ? result.plates.map((plate) => `${plate.count} × ${plate.weight} ${unit}`).join(' + ') : 'No plates'}</p>
          <p>Total: {result.achieved} {unit}</p>
          {result.remaining > 0 && <p className="text-amber-300">{result.remaining} {unit} below target. These sizes cannot make the exact weight.</p>}
        </>}
      </div>
    </div>
  </details>;
}
