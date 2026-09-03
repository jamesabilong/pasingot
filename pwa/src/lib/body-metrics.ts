import { SCHEMA_VERSION, type BodyMetricEntry, type BodyWeightUnit } from '../types';
import { todayDateKey } from './history-stats';

export interface BodyMetricDraft {
  id?: number;
  date: string;
  weight: string;
  unit: BodyWeightUnit;
  note: string;
}

export function initialBodyMetricDraft(): BodyMetricDraft {
  return { date: todayDateKey(), weight: '', unit: 'kg', note: '' };
}

export function parseBodyMetricDraft(draft: BodyMetricDraft): BodyMetricEntry | null {
  const weight = Number(draft.weight);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(draft.date) || !Number.isFinite(weight) || weight <= 0 || weight > 700) return null;
  const entry: BodyMetricEntry = {
    schemaVersion: SCHEMA_VERSION,
    date: draft.date,
    weight: Math.round(weight * 10) / 10,
    unit: draft.unit,
    note: draft.note.trim().slice(0, 160) || undefined,
  };
  if (draft.id != null) entry.id = draft.id;
  return entry;
}

export function bodyMetricDraftFromEntry(entry: BodyMetricEntry): BodyMetricDraft {
  return {
    id: entry.id,
    date: entry.date,
    weight: String(entry.weight),
    unit: entry.unit,
    note: entry.note ?? '',
  };
}
