import { useCallback, useRef, useState } from 'react';
import type { BodyMeasurementEntry } from '../types';
import { initialMeasurementDraft, measurementDraftFromEntry, parseMeasurementDraft } from '../lib/body-measurements';
import { saveMeasurement } from '../lib/body-measurement-persistence';
import { getAll, STORES } from '../lib/db';

export function useBodyMeasurements() {
  const [entries, setEntries] = useState<BodyMeasurementEntry[]>([]);
  const [draft, setDraft] = useState(initialMeasurementDraft);
  const [editing, setEditing] = useState<BodyMeasurementEntry>();
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [result, setResult] = useState<{ message: string; error: boolean } | null>(null);
  const refresh = useCallback(async () => setEntries(await getAll<BodyMeasurementEntry>(STORES.bodyMeasurements)), []);
  const cancel = useCallback(() => {
    if (pending.current) return;
    setEditing(undefined); setDraft(current => ({ ...initialMeasurementDraft(), site: current.site, unit: current.unit })); setResult(null);
  }, []);
  const edit = useCallback((entry: BodyMeasurementEntry) => {
    if (pending.current) return;
    setEditing({ ...entry }); setDraft(measurementDraftFromEntry(entry)); setResult(null);
  }, []);
  const mutate = useCallback(async (next: BodyMeasurementEntry | null, expected?: BodyMeasurementEntry) => {
    if (pending.current) return;
    pending.current = true; setBusy(true);
    try {
      await saveMeasurement(next, expected);
      setEditing(undefined);
      setDraft(current => ({ ...initialMeasurementDraft(), site: current.site, unit: current.unit }));
      setResult({ error: false, message: next ? expected ? 'Measurement updated.' : 'Measurement saved.' : 'Measurement deleted.' });
      try { await refresh(); }
      catch { setResult({ error: true, message: 'Change saved. Reload measurements to update the list.' }); }
    } catch (error) {
      setResult({ error: true, message: error instanceof Error ? error.message : 'Could not save measurement. Try again.' });
    } finally { pending.current = false; setBusy(false); }
  }, [refresh]);
  const save = useCallback(async () => {
    const entry = parseMeasurementDraft(draft);
    if (!entry) { setResult({ error: true, message: 'Enter a valid date through today and a measurement from 0.1 to 1000.' }); return; }
    await mutate(entry, editing);
  }, [draft, editing, mutate]);
  const remove = useCallback(async (entry: BodyMeasurementEntry) => mutate(null, entry), [mutate]);
  const reload = useCallback(async () => {
    if (pending.current) return;
    pending.current = true; setBusy(true);
    try { await refresh(); setEditing(undefined); setDraft(initialMeasurementDraft()); setResult(null); }
    catch { setResult({ error: true, message: 'Could not reload measurements. Try again.' }); }
    finally { pending.current = false; setBusy(false); }
  }, [refresh]);
  return { entries, draft, setDraft, editing, busy, result, refresh, reload, save, edit, remove, cancel };
}
