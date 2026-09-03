import Papa from 'papaparse';
import { SCHEMA_VERSION, type ExerciseCatalogItem } from '../types';
import { isExerciseLevel } from './workout-planning';

function splitList(value: unknown): string[] {
  return String(value ?? '').split('|').map((part) => part.trim()).filter(Boolean);
}

export function parseCatalogCsv(csvText: string): ExerciseCatalogItem[] {
  const parsed = Papa.parse<Record<string, string>>(csvText, { header: true, skipEmptyLines: true });
  if (parsed.errors.length) throw new Error(`Catalog CSV has ${parsed.errors.length} parse error(s).`);

  const rows = parsed.data.map((raw) => {
    const sourceId = Number(raw.source_id);
    const name = String(raw.name ?? '').trim();
    const displayName = String(raw.display_name ?? name).trim();
    const category = String(raw.category ?? '').trim();
    const minimumLevel = String(raw.minimum_level ?? '').trim();
    if (Number(raw.schema_version) !== SCHEMA_VERSION || !Number.isInteger(sourceId) || sourceId <= 0) return null;
    if (!name || !displayName || !category || !isExerciseLevel(minimumLevel)) return null;
    if (raw.featured !== 'true' && raw.featured !== 'false') return null;
    if (!raw.license?.startsWith('CC-BY-SA') || !raw.license_url?.startsWith('https://creativecommons.org/licenses/by-sa/') || !raw.source_url?.startsWith('https://wger.de/')) return null;
    return {
      schemaVersion: SCHEMA_VERSION,
      sourceId,
      name,
      displayName,
      category,
      primaryMuscles: splitList(raw.primary_muscles),
      secondaryMuscles: splitList(raw.secondary_muscles),
      equipment: splitList(raw.equipment),
      featured: raw.featured === 'true',
      minimumLevel,
      progressionGroup: String(raw.progression_group ?? '').trim(),
      progressionLevel: String(raw.progression_level ?? '').trim(),
      license: raw.license,
      licenseUrl: raw.license_url,
      author: String(raw.author ?? 'wger contributors').trim().slice(0, 120),
      sourceUrl: raw.source_url,
    };
  }).filter((row): row is ExerciseCatalogItem => row !== null);

  if (!rows.length || rows.length !== parsed.data.length) throw new Error('Catalog validation rejected one or more rows.');
  if (new Set(rows.map((row) => row.sourceId)).size !== rows.length) throw new Error('Catalog contains duplicate source IDs.');
  return rows;
}
