import { useState } from 'react';
import { initialWarmupDraft, suggestWarmupSets, supportsWarmups, type WarmupPrescription } from '../lib/warmup-sets';

const fieldClass = 'mt-1 min-w-0 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200';

export function WarmupSuggestions({ prescription }: { prescription: WarmupPrescription }) {
  if (!supportsWarmups(prescription)) return null;
  // A changed load/unit/rep target starts a fresh preview, including closed state.
  return <WarmupPreview key={`${prescription.loadWeight}:${prescription.loadUnit}:${prescription.reps}`} prescription={prescription} />;
}

function WarmupPreview({ prescription }: { prescription: WarmupPrescription }) {
  const unit = prescription.loadUnit!;
  const [draft, setDraft] = useState(() => initialWarmupDraft(unit));
  const result = suggestWarmupSets(prescription, draft);
  return <details className="border-t border-slate-800 pt-3" aria-label="Warm-up suggestions">
    <summary className="cursor-pointer text-sm font-medium text-slate-300">Warm-up suggestions</summary>
    <div className="mt-3 space-y-3">
      <p className="text-xs text-slate-400">Based on planned load: {prescription.loadWeight} {unit}. Adjust this example ramp for your session.</p>
      <p className="text-xs text-slate-400">Preview only. Working sets and history stay unchanged.</p>
      <div className="grid grid-cols-2 gap-2">
        <label className="min-w-0 text-xs text-slate-400">Minimum load ({unit})
          <input type="number" inputMode="decimal" min="0" max="2000" step="0.01" className={fieldClass} value={draft.minimum} onChange={event => setDraft({ ...draft, minimum: event.target.value })} />
        </label>
        <label className="min-w-0 text-xs text-slate-400">Load increment ({unit})
          <input type="number" inputMode="decimal" min="0.01" max="2000" step="0.01" className={fieldClass} value={draft.increment} onChange={event => setDraft({ ...draft, increment: event.target.value })} />
        </label>
      </div>
      <p className="text-xs text-slate-500">For a barbell, use the bar weight as the minimum and a pair of your smallest plates as the increment. Use the same load convention as your planned weight.</p>
      <div className="space-y-2">
        {draft.stages.map((stage, index) => <div key={index} className="grid grid-cols-2 gap-2">
          <label className="min-w-0 text-xs text-slate-400">Warm-up {index + 1} percent
            <input type="number" inputMode="numeric" min="1" max="99" step="1" className={fieldClass} value={stage.percent} onChange={event => setDraft({ ...draft, stages: draft.stages.map((row, i) => i === index ? { ...row, percent: event.target.value } : row) })} />
          </label>
          <label className="min-w-0 text-xs text-slate-400">Warm-up {index + 1} reps
            <input type="number" inputMode="numeric" min="1" max="30" step="1" className={fieldClass} value={stage.reps} onChange={event => setDraft({ ...draft, stages: draft.stages.map((row, i) => i === index ? { ...row, reps: event.target.value } : row) })} />
          </label>
        </div>)}
      </div>
      <button type="button" className="secondary-action" onClick={() => setDraft(initialWarmupDraft(unit))}>Reset warm-up suggestions</button>
      <div role="status" aria-label="Suggested warm-up sets" aria-live="polite" className="space-y-2 text-sm">
        {'error' in result ? <p className="text-amber-300">{result.error}</p> : <>
          <ol className="space-y-1">{result.sets.map((set, index) => <li key={set.load}>Warm-up {index + 1}: {set.reps} reps × {set.load} {unit} <span className="text-xs text-slate-500">({set.percent}% target)</span></li>)}</ol>
          {!result.sets.length && <p className="text-slate-400">No lighter sets fit these settings. Adjust the minimum or increment.</p>}
          {result.omitted > 0 && <p className="text-xs text-slate-500">Repeated, zero and below-minimum loads are omitted.</p>}
        </>}
      </div>
    </div>
  </details>;
}
