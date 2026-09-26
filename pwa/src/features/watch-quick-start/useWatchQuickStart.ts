import { useCallback, useEffect, useState } from 'react';
import type { ExerciseCatalogItem, PlaylistItem, WorkoutRow } from '../../types';
import { availabilityText, quickStartBridge, quickStartSupported, type QuickStartAvailability } from './bridge';
import { activeOffer, buildRequest, fromCatalog, fromPlaylist, fromToday, itemsFromRequest, receiptFromRecord, type PhoneQuickStartRecord, type QuickStartItem, type QuickStartReceipt, type QuickStartSource, validateItems } from './model';

export interface QuickStartDraft { source: QuickStartSource; items: QuickStartItem[] }
export function useWatchQuickStart() {
  const nativeSupported = quickStartSupported();
  const [hydrated, setHydrated] = useState(false);
  const supported = nativeSupported && hydrated;
  const [draft, setDraft] = useState<QuickStartDraft | null>(null);
  const [availability, setAvailability] = useState<QuickStartAvailability | null>(null);
  const [checking, setChecking] = useState(false);
  const [sending, setSending] = useState(false);
  const [receipt, setReceipt] = useState<QuickStartReceipt | null>(null);
  const [latestRecord, setLatestRecord] = useState<PhoneQuickStartRecord | null>(null);
  const [, refreshExpiry] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!receipt?.expiresAtMillis || (receipt.acknowledgement && receipt.acknowledgement.status !== 'ready')) return;
    const delay = receipt.expiresAtMillis + 30_001 - Date.now();
    if (delay <= 0) return;
    const timer = window.setTimeout(() => refreshExpiry((value) => value + 1), delay);
    return () => window.clearTimeout(timer);
  }, [receipt?.expiresAtMillis, receipt?.acknowledgement?.status]);

  useEffect(() => {
    const bridge = quickStartBridge();
    if (!bridge) { setHydrated(true); return; }
    let mounted = true;
    void bridge.getLatestQuickStart().then(({ record }) => {
      if (!mounted || !record) return;
      setLatestRecord(record);
      setReceipt(receiptFromRecord(record));
    }).catch(() => {}).finally(() => { if (mounted) setHydrated(true); });
    return () => { mounted = false; };
  }, []);

  const openLatest = useCallback(() => {
    if (!latestRecord) return;
    setDraft({ source: latestRecord.request.source, items: itemsFromRequest(latestRecord.request) });
    setReceipt(receiptFromRecord(latestRecord));
    setError(null);
  }, [latestRecord]);

  const open = useCallback((source: QuickStartSource, items: QuickStartItem[]) => {
    if (latestRecord && activeOffer(latestRecord)) { openLatest(); return; }
    setDraft({ source, items }); setAvailability(null); setReceipt(null); setError(null);
    const bridge = quickStartBridge();
    if (!bridge) return;
    setChecking(true);
    void bridge.getAvailability().then(setAvailability).catch(() => setError('Could not check your watch connection.')).finally(() => setChecking(false));
  }, [latestRecord, openLatest]);
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
      void bridge.getQuickStartStatus({ requestId }).then((next) => { if (mounted) {
        setReceipt((current) => ({ ...next, expiresAtMillis: current?.expiresAtMillis }));
        setLatestRecord((current) => current?.request.requestId === requestId ? { ...current,
          transportAcceptedAtMillis: next.transportAcceptedAtMillis, acknowledgement: next.acknowledgement } : current);
      } }).catch(() => {});
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
      setLatestRecord({ request, transportAcceptedAtMillis: accepted.transportAcceptedAtMillis, acknowledgement: null });
      setReceipt({ ...accepted, acknowledgement: null, expiresAtMillis: request.expiresAtMillis });
      const persisted = await bridge.getQuickStartStatus({ requestId: request.requestId });
      setReceipt({ ...persisted, expiresAtMillis: request.expiresAtMillis });
      setLatestRecord({ request, transportAcceptedAtMillis: persisted.transportAcceptedAtMillis, acknowledgement: persisted.acknowledgement });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not send to watch. Try again.');
    } finally { setSending(false); }
  }, [availability, draft, receipt, sending]);

  return { supported, draft, availability, checking, sending, receipt, error,
    availabilityMessage: availability && !availability.available ? availabilityText(availability.reason) : null,
    openSingle, openPlaylist, openSelection, openToday, openLatest, close, setDraft, send };
}
