import { ArrowDown, ArrowUp, X } from 'lucide-react';
import type { QuickStartDraft } from './useWatchQuickStart';
import type { QuickStartReceipt } from './model';
import { statusText, validateItems } from './model';
import type { QuickStartAvailability } from './bridge';

export function WatchQuickStartSheet({ draft, availability, availabilityMessage, checking, sending, cancelling, receipt, error, onDraftChange, onSend, onCancel, onClose }: {
  draft: QuickStartDraft | null; availability: QuickStartAvailability | null; availabilityMessage: string | null;
  checking: boolean; sending: boolean; cancelling: boolean; receipt: QuickStartReceipt | null; error: string | null;
  onDraftChange: (draft: QuickStartDraft) => void; onSend: () => void; onCancel: () => void; onClose: () => void;
}) {
  if (!draft) return null;
  const validation = validateItems(draft.items);
  const locked = sending || receipt != null;
  const update = (index: number, changes: Partial<QuickStartDraft['items'][number]>) => onDraftChange({ ...draft,
    items: draft.items.map((item, at) => at === index ? { ...item, ...changes } : item) });
  const move = (index: number, direction: -1 | 1) => {
    const items = [...draft.items]; const target = index + direction;
    if (target < 0 || target >= items.length) return;
    [items[index], items[target]] = [items[target], items[index]];
    onDraftChange({ ...draft, items });
  };
  return <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/85 p-2 sm:items-center" role="presentation">
    <section role="dialog" aria-modal="true" aria-labelledby="quick-start-title" className="max-h-[95vh] w-full max-w-xl overflow-y-auto rounded-xl border border-slate-700 bg-slate-900 p-4 shadow-2xl">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div><p className="section-kicker">Watch</p><h2 id="quick-start-title" className="text-lg font-semibold">Quick Start</h2></div>
        <button type="button" onClick={onClose} className="playlist-icon-action" aria-label="Close Quick Start"><X size={20} /></button>
      </div>
      <p className="mb-3 text-sm text-slate-400">Confirm each target. The watch will ask you to tap Start.</p>
      <div className="space-y-3">
        {draft.items.map((item, index) => <div key={item.itemId} className="rounded-lg border border-slate-700 p-3">
          <div className="mb-2 flex items-center gap-2"><strong className="min-w-0 flex-1 truncate text-sm">{index + 1}. {item.name}</strong>
            <button type="button" disabled={locked || index === 0} onClick={() => move(index, -1)} aria-label={`Move ${item.name} up`} className="playlist-icon-action"><ArrowUp size={16} /></button>
            <button type="button" disabled={locked || index === draft.items.length - 1} onClick={() => move(index, 1)} aria-label={`Move ${item.name} down`} className="playlist-icon-action"><ArrowDown size={16} /></button>
          </div>
          <div className="grid grid-cols-[4.5rem_1fr_5rem] gap-2">
            <label className="text-xs text-slate-400">Sets<input aria-label={`${item.name} sets`} type="number" min="1" max="99" disabled={locked} value={item.sets} onChange={(e) => update(index, { sets: Number(e.target.value) })} className="mt-1 w-full rounded border border-slate-600 bg-slate-950 p-2" /></label>
            <label className="text-xs text-slate-400">Reps / duration<input aria-label={`${item.name} target`} type="text" maxLength={64} disabled={locked} value={item.reps} onChange={(e) => update(index, { reps: e.target.value })} className="mt-1 w-full rounded border border-slate-600 bg-slate-950 p-2" /></label>
            <label className="text-xs text-slate-400">Rest s<input aria-label={`${item.name} rest seconds`} type="number" min="0" max="3600" disabled={locked} value={item.rest} onChange={(e) => update(index, { rest: Number(e.target.value) })} className="mt-1 w-full rounded border border-slate-600 bg-slate-950 p-2" /></label>
          </div>
          <div className="mt-2 grid grid-cols-[1fr_5rem] gap-2">
            <label className="text-xs text-slate-400">Load (optional)<input aria-label={`${item.name} load`} type="number" min="0" max="2000" step="any" disabled={locked} value={item.loadWeight ?? ''} onChange={(e) => update(index, { loadWeight: e.target.value ? Number(e.target.value) : null, loadUnit: e.target.value ? item.loadUnit ?? 'kg' : null })} className="mt-1 w-full rounded border border-slate-600 bg-slate-950 p-2" /></label>
            <label className="text-xs text-slate-400">Unit<select aria-label={`${item.name} load unit`} disabled={locked || item.loadWeight == null} value={item.loadUnit ?? 'kg'} onChange={(e) => update(index, { loadUnit: e.target.value as 'kg' | 'lb' })} className="mt-1 w-full rounded border border-slate-600 bg-slate-950 p-2"><option value="kg">kg</option><option value="lb">lb</option></select></label>
          </div>
        </div>)}
      </div>
      {validation && <p className="mt-3 text-sm text-rose-300" role="alert">{validation}</p>}
      {error && <p className="mt-3 text-sm text-rose-300" role="alert">{error}</p>}
      {availabilityMessage && <p className="mt-3 text-sm text-amber-300" role="status">{availabilityMessage}</p>}
      {checking && <p className="mt-3 text-sm text-slate-400" role="status">Checking watch…</p>}
      {receipt && <p className="mt-3 text-sm text-emerald-300" role="status">{statusText(receipt)}</p>}
      <button type="button" className="primary-action mt-4 disabled:cursor-not-allowed disabled:opacity-50" disabled={!!validation || !availability?.available || checking || locked} onClick={onSend}>
        {sending ? 'Sending…' : receipt ? 'Sent to watch' : `Send ${draft.items.length} to watch`}
      </button>
      {receipt && (!receipt.acknowledgement || receipt.acknowledgement.status === 'ready') &&
        <button type="button" className="secondary-action mt-2 disabled:cursor-not-allowed disabled:opacity-50" disabled={cancelling || !!receipt.cancellation} onClick={onCancel}>
          {cancelling ? 'Cancelling…' : receipt.cancellation ? 'Cancellation sent' : 'Cancel request'}
        </button>}
    </section>
  </div>;
}
