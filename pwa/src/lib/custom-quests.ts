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
import { isExerciseLevel, isWeightUnit, TIME_RE, validLoadWeight } from './workout-planning';

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
  if (!TIME_RE.test(playlist.time) || !playlist.items.length) {
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

export function normalizeCustomQuestCollection(value: unknown): CustomQuestCollection {
  const candidate = value as Partial<CustomQuestCollection> | null;
  const quests = Array.isArray(candidate?.quests) ? candidate.quests.filter((definition): definition is CustomQuestDefinition => {
    if (!definition || definition.schemaVersion !== SCHEMA_VERSION || typeof definition.questId !== 'string') return false;
    const template = definition.template;
    if (!template || template.schemaVersion !== SCHEMA_VERSION || template.questId !== definition.questId || template.custom !== true) return false;
    if (!template.title?.trim() || !template.description?.trim() || !template.safetyNote?.trim()) return false;
    if (!Number.isInteger(template.durationWeeks) || template.durationWeeks < 1 || !Number.isInteger(template.daysPerWeek) || template.daysPerWeek < 1) return false;
    if (!Array.isArray(template.evidenceBasis) || !template.evidenceBasis.every((url) => typeof url === 'string')) return false;
    if (!Array.isArray(template.availableLevels) || !template.availableLevels.length || !template.availableLevels.every(isExerciseLevel)) return false;
    if (!Array.isArray(definition.rows) || !definition.rows.length) return false;
    return definition.rows.every((row) => (
      row?.schemaVersion === SCHEMA_VERSION
      && row.questId === definition.questId
      && isExerciseLevel(row.level)
      && template.availableLevels!.includes(row.level)
      && Number.isInteger(row.dayNumber)
      && row.dayNumber >= 1
      && row.dayNumber <= template.daysPerWeek
      && Number.isInteger(row.sequence)
      && row.sequence >= 1
      && Number.isInteger(row.exerciseSourceId)
      && row.exerciseSourceId !== 0
      && Number.isInteger(row.sets)
      && row.sets >= 1
      && Boolean(row.reps?.trim())
      && Number.isInteger(row.rest)
      && row.rest >= 0
      && (row.loadWeight == null || (validLoadWeight(row.loadWeight) != null && isWeightUnit(row.loadUnit)))
    ));
  }) : [];
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
    sourceId === exercise.sourceId || referencesName(name, names)
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
