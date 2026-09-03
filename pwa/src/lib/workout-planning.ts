import { type PlanProgress } from '../components/SummaryCards';
import {
  SCHEMA_VERSION,
  WEEKDAYS,
  type ExerciseCatalogItem,
  type ExerciseLevel,
  type PlaylistDraft,
  type PlaylistItem,
  type WeightUnit,
  type Weekday,
  type WorkoutLog,
  type WorkoutRow,
} from '../types';

export const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;
export const MAX_PLAYLIST_ITEMS = 24;
export const LEVELS: ExerciseLevel[] = ['beginner', 'intermediate', 'advanced'];
export const LEVEL_LABELS: Record<ExerciseLevel, string> = {
  beginner: 'Beginner',
  intermediate: 'Intermediate',
  advanced: 'Advanced',
};

const LEVEL_RANK: Record<ExerciseLevel, number> = { beginner: 0, intermediate: 1, advanced: 2 };
const ESTIMATE_SECONDS_PER_REP = 4;
const ESTIMATE_SET_SETUP_SECONDS = 10;
const ESTIMATE_BETWEEN_EXERCISE_TRANSITION_SECONDS = 15;
const ESTIMATE_DEFAULT_REP_COUNT = 10;

const DEFAULT_PRESCRIPTIONS: Record<ExerciseLevel, { strength: Omit<PlaylistItem, 'sourceId' | 'name'>; cardio: Omit<PlaylistItem, 'sourceId' | 'name'> }> = {
  beginner: {
    strength: { sets: 2, reps: '8-10', rest: 75 },
    cardio: { sets: 1, reps: '10 min', rest: 60 },
  },
  intermediate: {
    strength: { sets: 3, reps: '8-12', rest: 60 },
    cardio: { sets: 1, reps: '15-20 min', rest: 60 },
  },
  advanced: {
    strength: { sets: 4, reps: '6-10', rest: 90 },
    cardio: { sets: 1, reps: '20-30 min', rest: 60 },
  },
};

type EstimableExercise = Pick<PlaylistItem, 'sets' | 'reps' | 'rest'> & { questLevel?: ExerciseLevel };

export function isWeightUnit(value: unknown): value is WeightUnit {
  return value === 'kg' || value === 'lb';
}

export function validLoadWeight(value: unknown): number | null {
  if (value === '' || value == null) return null;
  const weight = Number(value);
  if (!Number.isFinite(weight) || weight <= 0 || weight > 2000) return null;
  return Math.round(weight * 10) / 10;
}

export function todayName(): Weekday {
  return new Date().toLocaleDateString('en-US', { weekday: 'long' }) as Weekday;
}

export function calculatePlanProgress(rows: Array<{ id?: number }>, statuses: Map<number, WorkoutLog['status']>): PlanProgress {
  const progress = rows.reduce((result, row) => {
    const status = row.id == null ? undefined : statuses.get(row.id);
    if (status === 'done') return { ...result, completed: result.completed + 1 };
    if (status === 'skipped') return { ...result, skipped: result.skipped + 1 };
    return { ...result, pending: result.pending + 1 };
  }, { total: rows.length, completed: 0, pending: 0, skipped: 0, resolvedPercent: 0 });
  const resolved = progress.completed + progress.skipped;
  return { ...progress, resolvedPercent: progress.total ? Math.round((resolved / progress.total) * 100) : 0 };
}

export function validateWorkoutRow(raw: Record<string, string>): WorkoutRow | null {
  const dayRaw = String(raw.day ?? '').trim();
  const day = WEEKDAYS.find((item) => item.toLowerCase() === dayRaw.toLowerCase());
  const time = String(raw.time ?? '').trim();
  const exercise = String(raw.exercise ?? '').trim();
  const reps = String(raw.reps ?? '').trim();
  const sets = Number.parseInt(String(raw.sets ?? '').trim(), 10);
  const rest = Number.parseInt(String(raw.rest ?? '').trim(), 10);
  const rawLoadWeight = raw.load_weight ?? raw.loadWeight ?? raw.weight;
  const loadWeight = validLoadWeight(rawLoadWeight);
  const loadUnitRaw = String(raw.load_unit ?? raw.loadUnit ?? raw.unit ?? '').trim().toLowerCase();
  const loadUnit = loadWeight != null ? (isWeightUnit(loadUnitRaw) ? loadUnitRaw : 'kg') : null;
  if (
    !day
    || !TIME_RE.test(time)
    || !exercise
    || !reps
    || !Number.isInteger(sets)
    || sets <= 0
    || !Number.isInteger(rest)
    || rest < 0
    || (String(rawLoadWeight ?? '').trim() !== '' && loadWeight == null)
  ) return null;
  return { schemaVersion: SCHEMA_VERSION, day, time, exercise, sets, reps, rest, loadWeight, loadUnit };
}

export function isExerciseLevel(value: unknown): value is ExerciseLevel {
  return typeof value === 'string' && LEVELS.includes(value as ExerciseLevel);
}

export function defaultPrescriptionFor(exercise: ExerciseCatalogItem, level: ExerciseLevel): Omit<PlaylistItem, 'sourceId' | 'name'> {
  return exercise.category.toLowerCase() === 'cardio' ? DEFAULT_PRESCRIPTIONS[level].cardio : DEFAULT_PRESCRIPTIONS[level].strength;
}

export function levelEligible(exercise: ExerciseCatalogItem, selectedLevel: ExerciseLevel): boolean {
  return LEVEL_RANK[exercise.minimumLevel] <= LEVEL_RANK[selectedLevel];
}

function estimateSetWorkSeconds(reps: string): number {
  const normalized = reps.toLowerCase();
  const values = [...normalized.matchAll(/\d+(?:\.\d+)?/g)].map((match) => Number(match[0])).filter(Number.isFinite);
  const maxValue = Math.max(...(values.length ? values : [ESTIMATE_DEFAULT_REP_COUNT]));
  if (normalized.includes('min')) return Math.round(maxValue * 60);
  if (normalized.includes('sec') || normalized.includes('second')) return Math.round(maxValue);
  if (normalized.includes('side') || normalized.includes('/leg') || normalized.includes('each')) return Math.round(maxValue * 2 * ESTIMATE_SECONDS_PER_REP);
  return Math.round(maxValue * ESTIMATE_SECONDS_PER_REP);
}

function roundUpToMinute(seconds: number): number {
  return Math.ceil(Math.max(0, seconds) / 60) * 60;
}

export function estimateWorkoutDurationSeconds(exercises: EstimableExercise[], level: ExerciseLevel = 'beginner'): number {
  if (!exercises.length) return 0;
  const baseSeconds = exercises.reduce((total, exercise, index) => {
    const parsedSets = Math.trunc(Number(exercise.sets));
    const parsedRest = Math.trunc(Number(exercise.rest));
    const sets = Number.isFinite(parsedSets) ? Math.max(1, parsedSets) : 1;
    const rest = Number.isFinite(parsedRest) ? Math.max(0, parsedRest) : 0;
    const restCount = (sets - 1) + (index < exercises.length - 1 ? 1 : 0);
    const workSeconds = (Math.max(ESTIMATE_SECONDS_PER_REP, estimateSetWorkSeconds(exercise.reps)) + ESTIMATE_SET_SETUP_SECONDS) * sets;
    const transitionSeconds = index < exercises.length - 1 ? ESTIMATE_BETWEEN_EXERCISE_TRANSITION_SECONDS : 0;
    return total + workSeconds + rest * restCount + transitionSeconds;
  }, 0);
  const multiplier = level === 'advanced' ? 1.12 : level === 'intermediate' ? 1.18 : 1.3;
  const minimumBuffer = level === 'advanced' ? 60 : level === 'intermediate' ? 90 : 150;
  return roundUpToMinute(Math.max(baseSeconds * multiplier, baseSeconds + minimumBuffer));
}

export function formatEstimatedDuration(seconds: number): string {
  const minutes = Math.ceil(Math.max(0, seconds) / 60);
  if (minutes <= 0) return 'Est. 0 min';
  if (minutes < 60) return `Est. ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes === 0 ? `Est. ${hours}h` : `Est. ${hours}h ${remainingMinutes}m`;
}

export function estimateLevelFor(exercises: EstimableExercise[], fallback: ExerciseLevel = 'beginner'): ExerciseLevel {
  return exercises.find((exercise) => exercise.questLevel)?.questLevel ?? fallback;
}

export function initialDraft(): PlaylistDraft {
  return { day: todayName(), time: '07:00', level: 'beginner', items: [] };
}

export function normalizeDraft(input: Partial<PlaylistDraft>, catalog: ExerciseCatalogItem[]): PlaylistDraft {
  const day = WEEKDAYS.includes(input.day as Weekday) ? input.day as Weekday : todayName();
  const time = TIME_RE.test(String(input.time ?? '')) ? String(input.time) : '07:00';
  const level = isExerciseLevel(input.level) ? input.level : 'beginner';
  const items: PlaylistItem[] = (input.items ?? []).map((item): PlaylistItem | null => {
    const sourceId = Number(item.sourceId);
    const exercise = catalog.find((candidate) => candidate.sourceId === sourceId);
    if (!exercise) return null;
    const sets = Number(item.sets);
    const rest = Number(item.rest);
    const reps = String(item.reps ?? '').trim().slice(0, 30);
    const loadWeight = validLoadWeight(item.loadWeight);
    const loadUnit = loadWeight != null ? item.loadUnit ?? 'kg' : null;
    return {
      sourceId,
      name: exercise.name,
      sets: Number.isInteger(sets) && sets > 0 ? sets : 3,
      reps: reps || '8-12',
      rest: Number.isInteger(rest) && rest >= 0 ? rest : 60,
      loadWeight,
      loadUnit,
    } satisfies PlaylistItem;
  }).filter((item): item is PlaylistItem => item !== null).slice(0, MAX_PLAYLIST_ITEMS);
  return { day, time, level, items };
}
