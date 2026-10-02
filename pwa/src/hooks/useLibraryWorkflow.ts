import { useCallback, useMemo, useState, type Dispatch, type SetStateAction } from 'react';
import { customExerciseFromDraft, draftFromCustomExercise, initialCustomExerciseDraft, mergeCatalogWithCustomExercises, type CustomExerciseDraft } from '../lib/custom-exercises';
import { describeCustomExerciseReferences, findCustomExerciseReferences } from '../lib/custom-quests';
import { addRecord, deleteRecord, getAll, putRecord, STORES } from '../lib/db';
import { pushScheduleToNative } from '../lib/native-bridge';
import { defaultPrescriptionFor, estimateWorkoutDurationSeconds, formatEstimatedDuration, isWeightUnit, LEVELS, levelEligible, MAX_PLAYLIST_ITEMS, TIME_RE, validLoadWeight } from '../lib/workout-planning';
import { SCHEMA_VERSION, type CustomExercise, type CustomQuestDefinition, type ExerciseCatalogItem, type ExerciseLevel, type PlaylistDraft, type PlaylistItem, type WorkoutLog, type WorkoutRow, type WorkoutSetLog, type WorkoutSessionEvent } from '../types';

const PLAYLIST_DRAFT_KEY = 'playlistDraft';

interface LibraryWorkflowOptions {
  catalog: ExerciseCatalogItem[];
  setCatalog: Dispatch<SetStateAction<ExerciseCatalogItem[]>>;
  customExercises: CustomExercise[];
  setCustomExercises: Dispatch<SetStateAction<CustomExercise[]>>;
  draft: PlaylistDraft;
  setDraft: Dispatch<SetStateAction<PlaylistDraft>>;
  workouts: WorkoutRow[];
  logs: WorkoutLog[];
  setLogEntries: WorkoutSetLog[];
  sessionEvents: WorkoutSessionEvent[];
  customQuestDefinitions: CustomQuestDefinition[];
  refreshWorkouts: () => Promise<void>;
  addToast: (message: string) => void;
}

export function useLibraryWorkflow({
  catalog, setCatalog, customExercises, setCustomExercises, draft, setDraft,
  workouts, logs, setLogEntries, sessionEvents, customQuestDefinitions,
  refreshWorkouts, addToast,
}: LibraryWorkflowOptions) {
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('all');
  const [featuredOnly, setFeaturedOnly] = useState(false);
  const [customExerciseDraft, setCustomExerciseDraft] = useState<CustomExerciseDraft>(initialCustomExerciseDraft);
  const [customExerciseResult, setCustomExerciseResult] = useState<{ message: string; error: boolean } | null>(null);
  const [playlistResult, setPlaylistResult] = useState<{ message: string; error: boolean } | null>(null);
  const saveDraft = useCallback(async (next: PlaylistDraft) => {
    setDraft(next);
    await putRecord(STORES.appState, { key: PLAYLIST_DRAFT_KEY, schemaVersion: SCHEMA_VERSION, ...next });
  }, [setDraft]);

  const draftEstimate = useMemo(() => (
    formatEstimatedDuration(estimateWorkoutDurationSeconds(draft.items, draft.level))
  ), [draft.items, draft.level]);

  const categories = useMemo(() => [...new Set(catalog.map((item) => item.category))].sort(), [catalog]);
  const levelCounts = useMemo(() => LEVELS.reduce((result, level) => ({
    ...result,
    [level]: catalog.filter((item) => levelEligible(item, level)).length,
  }), {} as Record<ExerciseLevel, number>), [catalog]);
  const filteredCatalog = useMemo(() => catalog.filter((item) => {
    const searchable = `${item.displayName} ${item.name} ${item.category} ${item.primaryMuscles.join(' ')} ${item.equipment.join(' ')}`.toLowerCase();
    return levelEligible(item, draft.level)
      && (!search || searchable.includes(search.toLowerCase()))
      && (category === 'all' || item.category === category)
      && (!featuredOnly || item.featured);
  }), [catalog, search, category, featuredOnly, draft.level]);
  async function addCatalogExercise(sourceId: number) {
    if (draft.items.length >= MAX_PLAYLIST_ITEMS) return addToast(`A playlist can contain up to ${MAX_PLAYLIST_ITEMS} exercises.`);
    const exercise = catalog.find((item) => item.sourceId === sourceId);
    if (!exercise || draft.items.some((item) => item.sourceId === sourceId)) return;
    await saveDraft({ ...draft, items: [...draft.items, { sourceId, name: exercise.custom ? exercise.displayName : exercise.name, ...defaultPrescriptionFor(exercise, draft.level) }] });
  }

  async function saveCustomExercise() {
    const existing = customExerciseDraft.sourceId == null
      ? undefined
      : customExercises.find((exercise) => exercise.sourceId === customExerciseDraft.sourceId);
    const exercise = customExerciseFromDraft(customExerciseDraft, existing);
    if (!exercise) {
      setCustomExerciseResult({ error: true, message: 'Enter a custom exercise name.' });
      return;
    }
    const duplicate = catalog.find((item) => item.sourceId !== exercise.sourceId && (
      item.name.trim().toLowerCase() === exercise.displayName.toLowerCase()
      || item.displayName.trim().toLowerCase() === exercise.displayName.toLowerCase()
    ));
    if (duplicate) {
      setCustomExerciseResult({ error: true, message: `An exercise named ${duplicate.displayName} already exists.` });
      return;
    }
    await putRecord(STORES.customExercises, exercise);
    const nextCustomExercises = await getAll<CustomExercise>(STORES.customExercises);
    const baseCatalog = catalog.filter((item) => !item.custom);
    setCustomExercises(nextCustomExercises);
    setCatalog(mergeCatalogWithCustomExercises(baseCatalog, nextCustomExercises));
    if (draft.items.some((item) => item.sourceId === exercise.sourceId)) {
      await saveDraft({ ...draft, items: draft.items.map((item) => item.sourceId === exercise.sourceId ? { ...item, name: exercise.displayName } : item) });
    }
    setCustomExerciseDraft(initialCustomExerciseDraft());
    setCustomExerciseResult({ error: false, message: existing ? 'Custom exercise updated.' : 'Custom exercise created.' });
  }

  async function editCustomExercise(sourceId: number) {
    const exercise = customExercises.find((item) => item.sourceId === sourceId);
    if (!exercise) return;
    setCustomExerciseDraft(draftFromCustomExercise(exercise));
    setCustomExerciseResult(null);
  }

  async function deleteCustomExercise(sourceId: number) {
    const exercise = customExercises.find((item) => item.sourceId === sourceId);
    if (!exercise) return;
    const references = findCustomExerciseReferences(exercise, {
      draft,
      workouts,
      logs,
      setLogs: setLogEntries,
      sessionEvents,
      customQuests: customQuestDefinitions,
    });
    if (references.total) {
      setCustomExerciseResult({ error: true, message: `This exercise is still used by ${describeCustomExerciseReferences(references)}. Remove those references before deleting it.` });
      return;
    }
    if (!window.confirm(`Delete ${exercise.displayName}?`)) return;
    await deleteRecord(STORES.customExercises, sourceId);
    const nextCustomExercises = await getAll<CustomExercise>(STORES.customExercises);
    const baseCatalog = catalog.filter((item) => !item.custom);
    setCustomExercises(nextCustomExercises);
    setCatalog(mergeCatalogWithCustomExercises(baseCatalog, nextCustomExercises));
    setCustomExerciseDraft((current) => current.sourceId === sourceId ? initialCustomExerciseDraft() : current);
    setCustomExerciseResult({ error: false, message: 'Custom exercise deleted.' });
  }

  async function updateDraftItem(index: number, updates: Partial<PlaylistItem>) {
    await saveDraft({ ...draft, items: draft.items.map((item, itemIndex) => itemIndex === index ? { ...item, ...updates } : item) });
  }

  async function reorderDraftItem(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= draft.items.length) return;
    const items = [...draft.items];
    [items[index], items[target]] = [items[target], items[index]];
    await saveDraft({ ...draft, items });
  }

  async function savePlaylistToSchedule() {
    const invalid = draft.items.some((item) => (
      !Number.isInteger(item.sets)
      || item.sets <= 0
      || !item.reps.trim()
      || !Number.isInteger(item.rest)
      || item.rest < 0
      || (item.loadWeight != null && validLoadWeight(item.loadWeight) == null)
      || (item.loadWeight != null && !isWeightUnit(item.loadUnit))
    ));
    if (!TIME_RE.test(draft.time) || invalid) return setPlaylistResult({ error: true, message: 'Fix invalid day, time, sets, reps, rest, or load values before saving.' });
    const existingKeys = new Set(workouts.map((row) => `${row.day}|${row.time}|${row.exercise.toLowerCase()}`));
    const additions = draft.items.filter((item) => !existingKeys.has(`${draft.day}|${draft.time}|${item.name.toLowerCase()}`)).map((item) => ({
      schemaVersion: SCHEMA_VERSION,
      day: draft.day,
      time: draft.time,
      exercise: item.name,
      exerciseSourceId: item.sourceId,
      sets: item.sets,
      reps: item.reps.trim(),
      rest: item.rest,
      loadWeight: item.loadWeight ?? null,
      loadUnit: item.loadWeight != null ? item.loadUnit ?? 'kg' : null,
    } satisfies WorkoutRow));
    for (const row of additions) await addRecord(STORES.workouts, row);
    const schedule = await getAll<WorkoutRow>(STORES.workouts);
    await pushScheduleToNative(schedule);
    await refreshWorkouts();
    setPlaylistResult({ error: false, message: additions.length ? `Added ${additions.length} exercise${additions.length === 1 ? '' : 's'} to ${draft.day} at ${draft.time}. ${draftEstimate}.` : 'This playlist is already on the schedule at that day and time.' });
  }

  return {
    search, setSearch, category, setCategory, featuredOnly, setFeaturedOnly,
    customExerciseDraft, setCustomExerciseDraft, customExerciseResult,
    playlistResult, setPlaylistResult, categories, levelCounts, filteredCatalog,
    draftEstimate, saveDraft, addCatalogExercise, saveCustomExercise,
    editCustomExercise, deleteCustomExercise, updateDraftItem, reorderDraftItem,
    savePlaylistToSchedule,
  };
}
