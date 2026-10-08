import { useMemo, useState } from 'react';
import { calculatePlates } from '../lib/plate-calculator';
import type { WeightUnit } from '../types';

const defaults = {
  kg: { target: '60', bar: '20', sizes: '25, 20, 15, 10, 5, 2.5, 1.25' },
  lb: { target: '135', bar: '45', sizes: '45, 35, 25, 10, 5, 2.5' },
};
const inputClass = 'mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none';

export function PlateCalculator() {
  const [unit, setUnit] = useState<WeightUnit>('kg');
  const [draft, setDraft] = useState(defaults.kg);
  const result = useMemo(() => calculatePlates(
    draft.target.trim() ? Number(draft.target) : NaN,
    draft.bar.trim() ? Number(draft.bar) : NaN,
    draft.sizes.split(',').map((size) => size.trim() ? Number(size) : NaN),
  ), [draft]);
  return <details className="rounded-lg border border-slate-800 bg-slate-900 p-4">
    <summary className="cursor-pointer text-base font-semibold text-slate-200">Plate calculator</summary>
    <div className="mt-3 space-y-3">
      <label className="block text-xs text-slate-400">Weight unit
        <select className={inputClass} value={unit} onChange={(event) => {
          const next = event.target.value as WeightUnit;
          setUnit(next);
          setDraft(defaults[next]);
        }}><option value="kg">kg</option><option value="lb">lb</option></select>
      </label>
      <p className="text-xs text-slate-500">Changing units resets the calculator to example weights.</p>
      <div className="grid grid-cols-2 gap-3">
        <label className="text-xs text-slate-400">Target total ({unit})
          <input type="number" inputMode="decimal" min="0" max="1000" step="0.01" className={inputClass} value={draft.target} onChange={(event) => setDraft({ ...draft, target: event.target.value })} />
        </label>
        <label className="text-xs text-slate-400">Bar weight ({unit})
          <input type="number" inputMode="decimal" min="0" max="1000" step="0.01" className={inputClass} value={draft.bar} onChange={(event) => setDraft({ ...draft, bar: event.target.value })} />
        </label>
      </div>
      <label className="block text-xs text-slate-400">Available plate sizes ({unit}), separated by commas
        <input type="text" className={inputClass} maxLength={200} value={draft.sizes} onChange={(event) => setDraft({ ...draft, sizes: event.target.value })} />
      </label>
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
