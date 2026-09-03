import { useCallback, useState } from 'react';
import {
  bodyMetricDraftFromEntry,
  initialBodyMetricDraft,
  parseBodyMetricDraft,
  type BodyMetricDraft,
} from '../lib/body-metrics';
import { addRecord, deleteRecord, getAll, putRecord, STORES } from '../lib/db';
import { type BodyMetricEntry } from '../types';

export function useBodyMetrics() {
  const [entries, setEntries] = useState<BodyMetricEntry[]>([]);
  const [draft, setDraft] = useState<BodyMetricDraft>(initialBodyMetricDraft);
  const [result, setResult] = useState<{ message: string; error: boolean } | null>(null);

  const refresh = useCallback(async () => setEntries(await getAll<BodyMetricEntry>(STORES.bodyMetrics)), []);

  const save = useCallback(async () => {
    const entry = parseBodyMetricDraft(draft);
    if (!entry) {
      setResult({ error: true, message: 'Enter a valid date and body weight.' });
      return;
    }
    if (entry.id == null) await addRecord(STORES.bodyMetrics, entry);
    else await putRecord(STORES.bodyMetrics, entry);
    await refresh();
    setDraft({ ...initialBodyMetricDraft(), unit: entry.unit });
    setResult({ error: false, message: entry.id == null ? 'Body weight logged.' : 'Body weight updated.' });
  }, [draft, refresh]);

  const edit = useCallback((entry: BodyMetricEntry) => {
    setDraft(bodyMetricDraftFromEntry(entry));
    setResult(null);
  }, []);

  const remove = useCallback(async (entry: BodyMetricEntry) => {
    if (entry.id == null) return;
    await deleteRecord(STORES.bodyMetrics, entry.id);
    await refresh();
    setDraft((current) => (current.id === entry.id ? initialBodyMetricDraft() : current));
    setResult({ error: false, message: 'Body weight entry deleted.' });
  }, [refresh]);

  return {
    entries,
    draft,
    result,
    setDraft,
    refresh,
    save,
    edit,
    remove,
  };
}
