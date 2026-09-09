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
      return null;
    }
    const isNew = entry.id == null;
    if (isNew) {
      const key = await addRecord(STORES.bodyMetrics, entry);
      if (typeof key !== 'number') throw new Error('Body weight entry was saved without a numeric ID.');
      entry.id = key;
    } else {
      await putRecord(STORES.bodyMetrics, entry);
    }
    await refresh();
    setDraft({ ...initialBodyMetricDraft(), unit: entry.unit });
    setResult({ error: false, message: isNew ? 'Body weight logged.' : 'Body weight updated.' });
    return entry;
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
