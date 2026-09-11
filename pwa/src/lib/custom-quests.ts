import {
  SCHEMA_VERSION,
  type CustomExercise,
  type CustomQuestCollection,
  type CustomQuestDefinition,
  type ExerciseCatalogItem,
  type PlaylistDraft,
  type QuestWorkoutRow,
  type WorkoutLog,
  type WorkoutRow,
  type WorkoutSessionEvent,
  type WorkoutSetLog,
} from '../types';
import { customExerciseNames } from './custom-exercises';
import { isExerciseLevel, isWeightUnit, MAX_PLAYLIST_ITEMS, TIME_RE, validLoadWeight } from './workout-planning';

export const CUSTOM_QUESTS_KEY = 'customQuests' as const;

export interface CustomQuestDraft {
  title: string;
  description: string;
  durationWeeks: number;
  daysPerWeek: number;
  safetyNote: string;
}

export interface CustomExerciseReferenceSnapshot {
  draft: PlaylistDraft;
  workouts: WorkoutRow[];
  logs: WorkoutLog[];
  setLogs: WorkoutSetLog[];
  sessionEvents: WorkoutSessionEvent[];
  customQuests: CustomQuestDefinition[];
}

export interface CustomExerciseReferences {
  draft: number;
  schedule: number;
  history: number;
  quests: number;
  total: number;
}

export function initialCustomQuestDraft(): CustomQuestDraft {
  return {
    title: '',
    description: '',
    durationWeeks: 4,
    daysPerWeek: 1,
    safetyNote: 'Use a load and range of motion you can control. Stop if you feel pain, dizziness, or unusual shortness of breath.',
  };
}

function slug(value: string): string {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || 'quest';
}

function validPlaylistItem(item: PlaylistDraft['items'][number], catalogBySourceId: Map<number, ExerciseCatalogItem>): boolean {
  return catalogBySourceId.has(item.sourceId)
    && Number.isInteger(item.sets)
    && item.sets > 0
    && Boolean(item.reps.trim())
    && Number.isInteger(item.rest)
    && item.rest >= 0
    && (item.loadWeight == null || (validLoadWeight(item.loadWeight) != null && isWeightUnit(item.loadUnit)));
}

export function createCustomQuestDefinition(
  questDraft: CustomQuestDraft,
  playlist: PlaylistDraft,
  catalog: ExerciseCatalogItem[],
  now = new Date(),
): { definition: CustomQuestDefinition | null; error: string | null } {
  const title = questDraft.title.trim().slice(0, 80);
  const description = questDraft.description.trim().slice(0, 240);
  const safetyNote = questDraft.safetyNote.trim().slice(0, 320);
  const durationWeeks = Number(questDraft.durationWeeks);
  const daysPerWeek = Number(questDraft.daysPerWeek);
  if (!title) return { definition: null, error: 'Enter a quest title.' };
  if (!description) return { definition: null, error: 'Describe the goal of this quest.' };
  if (!safetyNote) return { definition: null, error: 'Add a short safety note.' };
  if (!Number.isInteger(durationWeeks) || durationWeeks < 1 || durationWeeks > 52) {
    return { definition: null, error: 'Duration must be between 1 and 52 weeks.' };
  }
  if (!Number.isInteger(daysPerWeek) || daysPerWeek < 1 || daysPerWeek > 7) {
    return { definition: null, error: 'Days per week must be between 1 and 7.' };
  }
  if (!isExerciseLevel(playlist.level) || !TIME_RE.test(playlist.time) || !playlist.items.length || playlist.items.length > MAX_PLAYLIST_ITEMS) {
    return { definition: null, error: 'Build a valid playlist in Library before creating a quest.' };
  }
  const catalogBySourceId = new Map(catalog.map((exercise) => [exercise.sourceId, exercise]));
  if (playlist.items.some((item) => !validPlaylistItem(item, catalogBySourceId))) {
    return { definition: null, error: 'The Library playlist contains a missing exercise or invalid prescription.' };
  }

  const stamp = now.toISOString();
  const questId = `custom:${now.getTime()}:${slug(title)}`;
  const rows: QuestWorkoutRow[] = [];
  for (let dayNumber = 1; dayNumber <= daysPerWeek; dayNumber += 1) {
    playlist.items.forEach((item, index) => {
      const exercise = catalogBySourceId.get(item.sourceId)!;
      rows.push({
        schemaVersion: SCHEMA_VERSION,
        questId,
        level: playlist.level,
        dayNumber,
        dayLabel: daysPerWeek === 1 ? title : `${title} · Day ${dayNumber}`,
        sequence: index + 1,
        progressionGroup: exercise.progressionGroup || (exercise.custom ? 'custom' : exercise.category.toLowerCase()),
        exerciseSourceId: exercise.sourceId,
        sets: item.sets,
        reps: item.reps.trim(),
        rest: item.rest,
        loadWeight: item.loadWeight ?? null,
        loadUnit: item.loadWeight != null ? item.loadUnit ?? 'kg' : null,
      });
    });
  }
  const template = {
    schemaVersion: SCHEMA_VERSION,
    questId,
    title,
    description,
    durationWeeks,
    daysPerWeek,
    evidenceBasis: [],
    safetyNote,
    custom: true,
    availableLevels: [playlist.level],
  } satisfies import('../types').QuestTemplate;
  return {
    definition: { schemaVersion: SCHEMA_VERSION, questId, template, rows, createdAt: stamp, updatedAt: stamp },
    error: null,
  };
}

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value: unknown): value is string {
  return typeof value === 'string' && Boolean(value.trim());
}

function integer(value: unknown, min: number, max: number): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= min && value <= max;
}

export function isCustomQuestDefinition(value: unknown): value is CustomQuestDefinition {
  if (!record(value) || value.schemaVersion !== SCHEMA_VERSION || !text(value.questId) || !value.questId.startsWith('custom:')) return false;
  const template = value.template;
  if (!record(template) || template.schemaVersion !== SCHEMA_VERSION || template.questId !== value.questId || template.custom !== true) return false;
  if (!text(template.title) || !text(template.description) || !text(template.safetyNote)) return false;
  if (!integer(template.durationWeeks, 1, 52) || !integer(template.daysPerWeek, 1, 7)) return false;
  if (!Array.isArray(template.evidenceBasis) || !template.evidenceBasis.every((url) => typeof url === 'string')) return false;
  const levels = template.availableLevels;
  if (!Array.isArray(levels) || !levels.length || !levels.every(isExerciseLevel) || new Set(levels).size !== levels.length) return false;
  if (!text(value.createdAt) || !Number.isFinite(Date.parse(value.createdAt)) || !text(value.updatedAt) || !Number.isFinite(Date.parse(value.updatedAt))) return false;
  if (!Array.isArray(value.rows) || !value.rows.length || value.rows.length > MAX_PLAYLIST_ITEMS * template.daysPerWeek * levels.length) return false;
  const sequences = new Map<string, Set<number>>();
  for (const row of value.rows) {
    if (!record(row) || row.schemaVersion !== SCHEMA_VERSION || row.questId !== value.questId
      || !isExerciseLevel(row.level) || !levels.includes(row.level)
      || !integer(row.dayNumber, 1, template.daysPerWeek) || !integer(row.sequence, 1, MAX_PLAYLIST_ITEMS)
      || !integer(row.exerciseSourceId, Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER) || row.exerciseSourceId === 0
      || !integer(row.sets, 1, Number.MAX_SAFE_INTEGER) || !text(row.reps) || !text(row.dayLabel) || !text(row.progressionGroup)
      || !integer(row.rest, 0, Number.MAX_SAFE_INTEGER)
      || (row.loadWeight != null && (typeof row.loadWeight !== 'number' || validLoadWeight(row.loadWeight) == null || !isWeightUnit(row.loadUnit)))) return false;
    const key = `${row.level}:${row.dayNumber}`;
    const daySequences = sequences.get(key) ?? new Set<number>();
    if (daySequences.has(row.sequence)) return false;
    daySequences.add(row.sequence);
    sequences.set(key, daySequences);
  }
  for (const level of levels) {
    for (let day = 1; day <= template.daysPerWeek; day += 1) {
      const daySequences = sequences.get(`${level}:${day}`);
      if (!daySequences || [...daySequences].some((sequence) => sequence > daySequences.size)) return false;
    }
  }
  return true;
}

export function normalizeCustomQuestCollection(value: unknown): CustomQuestCollection {
  const quests = record(value) && Array.isArray(value.quests) ? value.quests.filter(isCustomQuestDefinition) : [];
  const seenQuestIds = new Set<string>();
  return {
    key: CUSTOM_QUESTS_KEY,
    schemaVersion: SCHEMA_VERSION,
    quests: quests.filter((definition) => {
      if (seenQuestIds.has(definition.questId)) return false;
      seenQuestIds.add(definition.questId);
      return true;
    }),
  };
}

function referencesName(value: string | null | undefined, names: Set<string>): boolean {
  return typeof value === 'string' && names.has(value.trim().toLowerCase());
}

export function findCustomExerciseReferences(exercise: CustomExercise, snapshot: CustomExerciseReferenceSnapshot): CustomExerciseReferences {
  const names = customExerciseNames(exercise);
  const hasIdentity = (sourceId: number | null | undefined, name: string | null | undefined) => (
    sourceId != null ? sourceId === exercise.sourceId : referencesName(name, names)
  );
  const draft = snapshot.draft.items.filter((item) => hasIdentity(item.sourceId, item.name)).length;
  const schedule = snapshot.workouts.filter((row) => hasIdentity(row.exerciseSourceId, row.exercise)).length;
  const logReferences = snapshot.logs.filter((log) => hasIdentity(log.exerciseSourceId, log.exercise)).length;
  const setLogReferences = snapshot.setLogs.filter((log) => hasIdentity(log.exerciseSourceId, log.exercise)).length;
  const sessionReferences = snapshot.sessionEvents.filter((event) => referencesName(event.currentExercise, names)).length;
  const quests = snapshot.customQuests.reduce((count, definition) => (
    count + definition.rows.filter((row) => row.exerciseSourceId === exercise.sourceId).length
  ), 0);
  const history = logReferences + setLogReferences + sessionReferences;
  return { draft, schedule, history, quests, total: draft + schedule + history + quests };
}

export function describeCustomExerciseReferences(references: CustomExerciseReferences): string {
  const parts = [
    references.draft ? `${references.draft} draft item${references.draft === 1 ? '' : 's'}` : '',
    references.schedule ? `${references.schedule} schedule row${references.schedule === 1 ? '' : 's'}` : '',
    references.history ? `${references.history} history record${references.history === 1 ? '' : 's'}` : '',
    references.quests ? `${references.quests} custom quest row${references.quests === 1 ? '' : 's'}` : '',
  ].filter(Boolean);
  return parts.join(', ');
}
