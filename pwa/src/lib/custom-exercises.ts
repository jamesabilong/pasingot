import { SCHEMA_VERSION, type CustomExercise, type ExerciseCatalogItem, type ExerciseLevel } from '../types';

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
    imageUrl: cleanUrl(draft.imageUrl),
    videoUrl: cleanUrl(draft.videoUrl),
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  };
}

export function mergeCatalogWithCustomExercises(catalog: ExerciseCatalogItem[], customExercises: CustomExercise[]): ExerciseCatalogItem[] {
  return [...catalog, ...customExercises].sort((left, right) => left.displayName.localeCompare(right.displayName));
}
