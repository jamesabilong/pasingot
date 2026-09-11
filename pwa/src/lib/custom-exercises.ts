import { SCHEMA_VERSION, type CustomExercise, type ExerciseCatalogItem, type ExerciseLevel } from '../types';
import { getAll, getRecord, putRecord, STORES } from './db';

export interface CustomExerciseDraft {
  sourceId?: number;
  name: string;
  category: string;
  primaryMuscles: string;
  equipment: string;
  minimumLevel: ExerciseLevel;
  imageUrl: string;
  videoUrl: string;
}

export const CUSTOM_EXERCISE_CATEGORIES = ['Strength', 'Cardio', 'Mobility', 'Core', 'Custom'] as const;

export function initialCustomExerciseDraft(): CustomExerciseDraft {
  return {
    name: '',
    category: 'Strength',
    primaryMuscles: '',
    equipment: '',
    minimumLevel: 'beginner',
    imageUrl: '',
    videoUrl: '',
  };
}

function slugName(value: string): string {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || 'custom-exercise';
}

function listFromCsv(value: string): string[] {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function cleanUrl(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  try {
    const url = new URL(trimmed);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null;
  } catch {
    return null;
  }
}

export function draftFromCustomExercise(exercise: CustomExercise): CustomExerciseDraft {
  return {
    sourceId: exercise.sourceId,
    name: exercise.displayName,
    category: exercise.category,
    primaryMuscles: exercise.primaryMuscles.join(', '),
    equipment: exercise.equipment.join(', '),
    minimumLevel: exercise.minimumLevel,
    imageUrl: exercise.imageUrl ?? '',
    videoUrl: exercise.videoUrl ?? '',
  };
}

export function customExerciseFromDraft(draft: CustomExerciseDraft, existing?: CustomExercise): CustomExercise | null {
  const displayName = draft.name.trim().slice(0, 80);
  if (!displayName) return null;
  const now = new Date().toISOString();
  const sourceId = existing?.sourceId ?? draft.sourceId ?? -Date.now();
  const category = draft.category.trim().slice(0, 40) || 'Custom';
  const primaryMuscles = listFromCsv(draft.primaryMuscles).slice(0, 8);
  const equipment = listFromCsv(draft.equipment).slice(0, 8);
  const aliases = existing
    ? [...new Set([...(existing.aliases ?? []), existing.name, existing.displayName])].filter((name) => name !== displayName)
    : [];
  return {
    schemaVersion: SCHEMA_VERSION,
    sourceId,
    name: existing?.name ?? `custom:${Math.abs(sourceId)}:${slugName(displayName)}`,
    displayName,
    category,
    primaryMuscles,
    secondaryMuscles: [],
    equipment,
    featured: false,
    minimumLevel: draft.minimumLevel,
    progressionGroup: 'custom',
    progressionLevel: draft.minimumLevel,
    license: 'User-provided',
    licenseUrl: '',
    author: 'Custom',
    sourceUrl: '',
    custom: true,
    aliases,
    imageUrl: cleanUrl(draft.imageUrl),
    videoUrl: cleanUrl(draft.videoUrl),
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  };
}

export function mergeCatalogWithCustomExercises(catalog: ExerciseCatalogItem[], customExercises: CustomExercise[]): ExerciseCatalogItem[] {
  return [...catalog, ...customExercises].sort((left, right) => left.displayName.localeCompare(right.displayName));
}

/** Resolve keys saved by older releases without exposing their timestamp to users. */
export function customExerciseDisplayName(name: string, catalog: ExerciseCatalogItem[] = []): string {
  const legacy = /^custom:(\d+):(.+)$/i.exec(name);
  if (!legacy) return name;
  const exercise = catalog.find((item) => item.custom && (item.name === name || Math.abs(item.sourceId) === Number(legacy[1])));
  if (exercise?.displayName.trim()) return exercise.displayName;
  // Deleted exercises can still be present in history or a watch's offline queue.
  const fallback = legacy[2].replace(/-/g, ' ').trim();
  return fallback ? fallback[0].toUpperCase() + fallback.slice(1) : 'Custom exercise';
}

export function customExerciseNames(exercise: CustomExercise): Set<string> {
  return new Set([exercise.name, exercise.displayName, ...(exercise.aliases ?? [])].map((name) => name.trim().toLowerCase()).filter(Boolean));
}

/** Repair persisted labels while retaining row IDs, log IDs, and catalog keys. */
export async function repairLegacyCustomExerciseNames(): Promise<void> {
  const catalog = await getAll<CustomExercise>(STORES.customExercises);
  const stores = [STORES.workouts, STORES.logs, STORES.setLogs, STORES.sessionEvents] as const;
  const storedRecords = await Promise.all(stores.map((store) => getAll<Record<string, unknown>>(store)));
  for (const [index, records] of storedRecords.entries()) {
    for (const record of records) {
      const repaired = { ...record };
      for (const field of ['exercise', 'currentExercise'] as const) {
        const value = record[field];
        if (typeof value === 'string') repaired[field] = customExerciseDisplayName(value, catalog);
      }
      if (typeof record.exercise === 'string') {
        const normalizedName = record.exercise.trim().toLowerCase();
        const customExercise = catalog.find((item) => customExerciseNames(item).has(normalizedName));
        if (customExercise && record.exerciseSourceId == null) {
          repaired.exerciseSourceId = customExercise.sourceId;
        }
      }
      if (repaired.exercise !== record.exercise || repaired.currentExercise !== record.currentExercise || repaired.exerciseSourceId !== record.exerciseSourceId) {
        await putRecord(stores[index], repaired);
      }
    }
  }
  const draft = await getRecord<{ key: string; items?: Array<{ name: string }> }>(STORES.appState, 'playlistDraft');
  if (draft?.items?.some((item) => customExerciseDisplayName(item.name, catalog) !== item.name)) {
    await putRecord(STORES.appState, { ...draft, items: draft.items.map((item) => ({ ...item, name: customExerciseDisplayName(item.name, catalog) })) });
  }
}
