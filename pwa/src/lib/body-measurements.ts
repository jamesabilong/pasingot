import { SCHEMA_VERSION, type BodyMeasurementEntry, type BodyMeasurementSite, type MeasurementUnit } from '../types';
import { todayDateKey } from './history-stats';
import { isScheduleDate } from './schedule-dates';

export const MEASUREMENT_SITES = {
  waist: 'Waist', chest: 'Chest', hips: 'Hips',
  leftArm: 'Left arm', rightArm: 'Right arm', leftThigh: 'Left thigh', rightThigh: 'Right thigh',
} satisfies Record<BodyMeasurementSite, string>;

export interface BodyMeasurementDraft {
  date: string;
  site: BodyMeasurementSite;
  value: string;
  unit: MeasurementUnit;
  note: string;
}

export function initialMeasurementDraft(): BodyMeasurementDraft {
  return { date: todayDateKey(), site: 'waist', value: '', unit: 'cm', note: '' };
}

export function validBodyMeasurement(value: unknown): value is BodyMeasurementEntry {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return false;
  const row = value as Record<string, unknown>;
  return row.schemaVersion === SCHEMA_VERSION && isScheduleDate(row.date)
    && typeof row.site === 'string' && Object.hasOwn(MEASUREMENT_SITES, row.site)
    && (row.unit === 'cm' || row.unit === 'in')
    && typeof row.value === 'number' && Number.isFinite(row.value) && row.value >= 0.1 && row.value <= 1000
    && (row.note === undefined || (typeof row.note === 'string' && row.note.length <= 160))
    && (row.id === undefined || (typeof row.id === 'number' && Number.isSafeInteger(row.id) && row.id > 0));
}

export function parseMeasurementDraft(draft: BodyMeasurementDraft): BodyMeasurementEntry | null {
  const value = Number(draft.value);
  if (!draft.value.trim() || value < 0.1 || value > 1000 || draft.date > todayDateKey()) return null;
  const entry = { schemaVersion: SCHEMA_VERSION, date: draft.date, site: draft.site,
    value: Math.round(value * 10) / 10, unit: draft.unit, note: draft.note.trim().slice(0, 160) || undefined };
  return validBodyMeasurement(entry) ? entry : null;
}

export function measurementDraftFromEntry(entry: BodyMeasurementEntry): BodyMeasurementDraft {
  return { date: entry.date, site: entry.site, value: String(entry.value), unit: entry.unit, note: entry.note ?? '' };
}

export function measurementValueIn(entry: BodyMeasurementEntry, unit: MeasurementUnit): number {
  return entry.unit === unit ? entry.value : unit === 'cm' ? entry.value * 2.54 : entry.value / 2.54;
}

export function measurementChange(latest: BodyMeasurementEntry, previous?: BodyMeasurementEntry): number | null {
  if (!previous || latest.site !== previous.site) return null;
  return Math.round((latest.value - measurementValueIn(previous, latest.unit)) * 10) / 10;
}

export function sortedMeasurements(entries: BodyMeasurementEntry[]): BodyMeasurementEntry[] {
  return entries.slice().sort((a, b) => b.date.localeCompare(a.date) || (b.id ?? 0) - (a.id ?? 0));
}
