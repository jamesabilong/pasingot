import { useCallback, useEffect, useState } from 'react';
import type { ExerciseCatalogItem, PlaylistItem, WorkoutRow } from '../../types';
import { availabilityText, quickStartBridge, quickStartSupported, type QuickStartAvailability } from './bridge';
import { buildRequest, fromCatalog, fromPlaylist, fromToday, type QuickStartItem, type QuickStartReceipt, type QuickStartSource, validateItems } from './model';

export interface QuickStartDraft { source: QuickStartSource; items: QuickStartItem[] }
export function useWatchQuickStart() {
  const supported = quickStartSupported();
  const [draft, setDraft] = useState<QuickStartDraft | null>(null);
  const [availability, setAvailability] = useState<QuickStartAvailability | null>(null);
  const [checking, setChecking] = useState(false);
  const [sending, setSending] = useState(false);
  const [receipt, setReceipt] = useState<QuickStartReceipt | null>(null);
  const [error, setError] = useState<string | null>(null);

  const open = useCallback((source: QuickStartSource, items: QuickStartItem[]) => {
    setDraft({ source, items }); setAvailability(null); setReceipt(null); setError(null);
    const bridge = quickStartBridge();
    if (!bridge) return;
    setChecking(true);
    void bridge.getAvailability().then(setAvailability).catch(() => setError('Could not check your watch connection.')).finally(() => setChecking(false));
  }, []);
  const openSingle = useCallback((item: ExerciseCatalogItem, prescription: Omit<PlaylistItem, 'sourceId' | 'name'>) => open('single', [fromCatalog(item, prescription)]), [open]);
  const openPlaylist = useCallback((items: PlaylistItem[]) => open('library_playlist', fromPlaylist(items)), [open]);
  const openSelection = useCallback((items: PlaylistItem[]) => open('library_selection', fromPlaylist(items)), [open]);
  const openToday = useCallback((row: WorkoutRow, date: string) => open('today_row', [fromToday(row, date)]), [open]);
  const close = useCallback(() => { setDraft(null); setError(null); }, []);

  useEffect(() => {
    const bridge = quickStartBridge();
    if (!bridge || !receipt?.requestId) return;
    const requestId = receipt.requestId;
    let mounted = true;
    const reconcile = () => { if (document.visibilityState === 'visible') {
      void bridge.getQuickStartStatus({ requestId }).then((next) => { if (mounted) setReceipt(next); }).catch(() => {});
    } };
    const handle = bridge.addListener('quickStartStatus', (ack) => {
      if (ack.requestId === requestId) reconcile();
    });
    document.addEventListener('visibilitychange', reconcile);
    reconcile();
    return () => { mounted = false; document.removeEventListener('visibilitychange', reconcile); void handle.then((listener) => listener.remove()); };
  }, [receipt?.requestId]);

  const send = useCallback(async () => {
    const bridge = quickStartBridge();
    if (!draft || !bridge || !availability?.available || !availability.watchNodeId || sending || receipt) return;
    const validation = validateItems(draft.items);
    if (validation) { setError(validation); return; }
    setSending(true); setError(null);
    try {
      const request = buildRequest(draft.items, draft.source, availability.watchNodeId);
      const accepted = await bridge.sendQuickStart({ request });
      setReceipt({ ...accepted, acknowledgement: null });
      const persisted = await bridge.getQuickStartStatus({ requestId: request.requestId });
      setReceipt(persisted);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not send to watch. Try again.');
    } finally { setSending(false); }
  }, [availability, draft, receipt, sending]);

  return { supported, draft, availability, checking, sending, receipt, error,
    availabilityMessage: availability && !availability.available ? availabilityText(availability.reason) : null,
    openSingle, openPlaylist, openSelection, openToday, close, setDraft, send };
}
