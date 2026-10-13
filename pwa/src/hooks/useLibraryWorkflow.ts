import { useCallback, useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { customExerciseFromDraft, draftFromCustomExercise, initialCustomExerciseDraft, mergeCatalogWithCustomExercises, type CustomExerciseDraft } from '../lib/custom-exercises';
import { describeCustomExerciseReferences, findCustomExerciseReferences } from '../lib/custom-quests';
import { deleteRecord, getAll, putRecord, STORES } from '../lib/db';
import { buildCatalogFilterOptions, filterCatalog, normalizeCatalogFilterValue } from '../lib/library-filters';
import { pushScheduleToNative } from '../lib/native-bridge';
import { savePlaylistSchedule } from '../lib/playlist-persistence';
import { circuitRange, circuitValueSignature, validateCircuitGroups } from '../lib/workout-circuits';
import { defaultPrescriptionFor, estimateWorkoutDurationSeconds, formatEstimatedDuration, LEVELS, levelEligible, MAX_PLAYLIST_ITEMS } from '../lib/workout-planning';
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
  scheduleEditingBlocked?: boolean;
}

export function useLibraryWorkflow({
  catalog, setCatalog, customExercises, setCustomExercises, draft, setDraft,
  workouts, logs, setLogEntries, sessionEvents, customQuestDefinitions,
  refreshWorkouts, addToast, scheduleEditingBlocked = false,
}: LibraryWorkflowOptions) {
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('all');
  const [featuredOnly, setFeaturedOnly] = useState(false);
  const [muscle, setMuscleValue] = useState('');
  const [equipment, setEquipmentValue] = useState('');
  const setMuscle = useCallback((value: string) => setMuscleValue(normalizeCatalogFilterValue(value)), []);
  const setEquipment = useCallback((value: string) => setEquipmentValue(normalizeCatalogFilterValue(value)), []);
  const clearFilters = useCallback(() => {
    setSearch('');
    setCategory('all');
    setFeaturedOnly(false);
    setMuscleValue('');
    setEquipmentValue('');
  }, []);
  const [customExerciseDraft, setCustomExerciseDraft] = useState<CustomExerciseDraft>(initialCustomExerciseDraft);
  const [customExerciseResult, setCustomExerciseResult] = useState<{ message: string; error: boolean } | null>(null);
  const [playlistResult, setPlaylistResult] = useState<{ message: string; error: boolean } | null>(null);
  const savingPlaylist = useRef(false);
  const blocked = useRef(scheduleEditingBlocked);
  blocked.current = scheduleEditingBlocked;
  const [playlistBusy, setPlaylistBusy] = useState(false);
  const currentDraft = useRef(draft);
  currentDraft.current = draft;
  const draftWrites = useRef<Promise<unknown>>(Promise.resolve());
  const changeDraft = useCallback((change: (current: PlaylistDraft) => PlaylistDraft | null) => {
    const operation = draftWrites.current.then(async () => {
      const next = change(currentDraft.current);
      if (!next) return false;
      const error = validateCircuitGroups(next.items);
      if (error) { setPlaylistResult({ error: true, message: error }); return false; }
      await putRecord(STORES.appState, { key: PLAYLIST_DRAFT_KEY, schemaVersion: SCHEMA_VERSION, ...next });
      currentDraft.current = next;
      setDraft(next);
      return true;
    });
    draftWrites.current = operation.catch(() => undefined);
    return operation;
  }, [setDraft]);
  const saveDraft = useCallback((next: PlaylistDraft) => {
    const expected = draft;
    return changeDraft(current => {
      if (circuitValueSignature(current) === circuitValueSignature(expected)) return next;
      if (circuitValueSignature(next.items) === circuitValueSignature(expected.items)) {
        return { ...current,
          ...(next.day !== expected.day ? { day: next.day } : {}),
          ...(next.time !== expected.time ? { time: next.time } : {}),
          ...(next.level !== expected.level ? { level: next.level } : {}),
        };
      }
      setPlaylistResult({ error: true, message: 'The playlist changed while saving. Review its current exercises and try again.' });
      return null;
    });
  }, [draft, changeDraft]);

  const draftEstimate = useMemo(() => (
    formatEstimatedDuration(estimateWorkoutDurationSeconds(draft.items, draft.level))
  ), [draft.items, draft.level]);

  const categories = useMemo(() => [...new Set(catalog.map((item) => item.category))].sort(), [catalog]);
  const muscleOptions = useMemo(() => buildCatalogFilterOptions(catalog, 'primaryMuscles'), [catalog]);
  const equipmentOptions = useMemo(() => buildCatalogFilterOptions(catalog, 'equipment'), [catalog]);
  useEffect(() => {
    if (muscle && !muscleOptions.some((option) => option.value === muscle)) setMuscleValue('');
  }, [muscle, muscleOptions]);
  useEffect(() => {
    if (equipment && !equipmentOptions.some((option) => option.value === equipment)) setEquipmentValue('');
  }, [equipment, equipmentOptions]);
  const levelCounts = useMemo(() => LEVELS.reduce((result, level) => ({
    ...result,
    [level]: catalog.filter((item) => levelEligible(item, level)).length,
  }), {} as Record<ExerciseLevel, number>), [catalog]);
  const filteredCatalog = useMemo(() => filterCatalog(catalog, {
    search, category, featuredOnly, level: draft.level, muscle, equipment,
  }), [catalog, search, category, featuredOnly, draft.level, muscle, equipment]);
  async function addCatalogExercise(sourceId: number) {
    const exercise = catalog.find((item) => item.sourceId === sourceId);
    if (!exercise) return;
    await changeDraft(current => {
      if (current.items.length >= MAX_PLAYLIST_ITEMS) { addToast(`A playlist can contain up to ${MAX_PLAYLIST_ITEMS} exercises.`); return null; }
      if (current.items.some(item => item.sourceId === sourceId)) return null;
      return { ...current, items: [...current.items, { sourceId, name: exercise.custom ? exercise.displayName : exercise.name, ...defaultPrescriptionFor(exercise, current.level) }] };
    });
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
    await changeDraft(current => current.items.some(item => item.sourceId === exercise.sourceId)
      ? { ...current, items: current.items.map(item => item.sourceId === exercise.sourceId ? { ...item, name: exercise.displayName } : item) }
      : null);
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
    const selected = draft.items[index];
    if (!selected) return;
    await changeDraft(current => {
      const target = current.items.find(item => item.sourceId === selected.sourceId);
      if (!target) return null;
      return { ...current, items: current.items.map(item => {
        if (item.sourceId === target.sourceId) return { ...item, ...updates };
        return target.circuit && item.circuit?.id === target.circuit.id && updates.sets !== undefined
          ? { ...item, sets: updates.sets } : item;
      }) };
    });
  }

  async function reorderDraftItem(index: number, direction: -1 | 1) {
    const sourceId = draft.items[index]?.sourceId;
    if (sourceId == null || ![-1, 1].includes(direction)) return;
    await changeDraft(current => {
      const currentIndex = current.items.findIndex(item => item.sourceId === sourceId);
      if (currentIndex < 0) return null;
      const selected = circuitRange(current.items, currentIndex) ?? { start: currentIndex, end: currentIndex };
      const targetIndex = direction === -1 ? selected.start - 1 : selected.end + 1;
      if (targetIndex < 0 || targetIndex >= current.items.length) return null;
      const target = circuitRange(current.items, targetIndex) ?? { start: targetIndex, end: targetIndex };
      const left = direction === -1 ? target : selected;
      const right = direction === -1 ? selected : target;
      return { ...current, items: [
        ...current.items.slice(0, left.start), ...current.items.slice(right.start, right.end + 1),
        ...current.items.slice(left.start, left.end + 1), ...current.items.slice(right.end + 1),
      ] };
    });
  }

  async function createCircuit(sourceIds: number[], rounds: number, restSeconds: number) {
    if (!Number.isSafeInteger(rounds) || rounds < 1 || rounds > 100
      || !Number.isSafeInteger(restSeconds) || restSeconds < 0 || restSeconds > 3600) {
      setPlaylistResult({ error: true, message: 'Choose 1–100 rounds and 0–3600 seconds of round rest.' }); return;
    }
    if (await changeDraft(current => {
      const selection = new Set(sourceIds);
      const indexes = current.items.flatMap((item, index) => selection.has(item.sourceId) ? [index] : []);
      if (sourceIds.length < 2 || sourceIds.length > MAX_PLAYLIST_ITEMS || selection.size !== sourceIds.length
        || indexes.length !== sourceIds.length || indexes.at(-1)! - indexes[0] + 1 !== indexes.length) {
        setPlaylistResult({ error: true, message: 'Select 2–24 adjacent exercises to create a superset or circuit.' }); return null;
      }
      if (indexes.some(index => current.items[index].circuit)) {
        setPlaylistResult({ error: true, message: 'Ungroup the selected exercises before creating a new circuit.' }); return null;
      }
      const circuit = { id: crypto.randomUUID(), restSeconds };
      return { ...current, items: current.items.map(item => selection.has(item.sourceId) ? { ...item, sets: rounds, circuit: { ...circuit } } : item) };
    })) setPlaylistResult({ error: false, message: `${sourceIds.length === 2 ? 'Superset' : 'Circuit'} created for phone workouts.` });
  }

  async function updateCircuit(circuitId: string, updates: { rounds?: number; restSeconds?: number }) {
    await changeDraft(current => ({ ...current, items: current.items.map(item => item.circuit?.id === circuitId ? {
      ...item, ...(updates.rounds !== undefined ? { sets: updates.rounds } : {}),
      circuit: { ...item.circuit, ...(updates.restSeconds !== undefined ? { restSeconds: updates.restSeconds } : {}) },
    } : item) }));
  }

  function ungroup(item: PlaylistItem): PlaylistItem {
    const { circuit: _circuit, ...individual } = item;
    return individual;
  }

  async function removeCircuit(circuitId: string) {
    await changeDraft(current => ({ ...current, items: current.items.map(item => item.circuit?.id === circuitId ? ungroup(item) : item) }));
  }

  async function removeDraftItem(index: number) {
    const sourceId = draft.items[index]?.sourceId;
    if (sourceId == null) return;
    await changeDraft(current => {
      const removed = current.items.find(item => item.sourceId === sourceId);
      if (!removed) return null;
      let items = current.items.filter(item => item.sourceId !== sourceId);
      if (removed.circuit && items.filter(item => item.circuit?.id === removed.circuit!.id).length < 2) {
        items = items.map(item => item.circuit?.id === removed.circuit!.id ? ungroup(item) : item);
      }
      return { ...current, items };
    });
  }

  async function savePlaylistToSchedule() {
    if (savingPlaylist.current) return;
    savingPlaylist.current = true; setPlaylistBusy(true); setPlaylistResult(null);
    try {
      await draftWrites.current;
      const savedDraft = structuredClone(currentDraft.current);
      const { schedule, addedCount } = await savePlaylistSchedule(savedDraft, {
        blocked: blocked.current,
        beforeWrite: async () => {
          if (blocked.current) throw new Error('Finish or end the current workout before adding a session.');
        },
      });
      const savedEstimate = formatEstimatedDuration(estimateWorkoutDurationSeconds(savedDraft.items, savedDraft.level));
      const saved = addedCount ? `Added ${addedCount} exercise${addedCount === 1 ? '' : 's'} to ${savedDraft.day} at ${savedDraft.time}. ${savedEstimate}.`
        : 'This playlist is already on the schedule at that day and time.';
      try {
        await refreshWorkouts();
        if (!await pushScheduleToNative(schedule)) throw new Error('Schedule cache update failed.');
        setPlaylistResult({ error: false, message: saved });
      } catch {
        setPlaylistResult({ error: true, message: `${saved} The schedule is saved on this phone. Reopen Today to refresh its view and watch cache.` });
      }
    } catch (error) {
      setPlaylistResult({ error: true, message: error instanceof Error ? error.message : 'Could not save the playlist.' });
    } finally { savingPlaylist.current = false; setPlaylistBusy(false); }
  }

  return {
    search, setSearch, category, setCategory, featuredOnly, setFeaturedOnly,
    muscle, setMuscle, equipment, setEquipment, muscleOptions, equipmentOptions, clearFilters,
    customExerciseDraft, setCustomExerciseDraft, customExerciseResult,
    playlistResult, setPlaylistResult, playlistBusy, categories, levelCounts, filteredCatalog,
    draftEstimate, saveDraft, addCatalogExercise, saveCustomExercise,
    editCustomExercise, deleteCustomExercise, updateDraftItem, reorderDraftItem,
    createCircuit, updateCircuit, removeCircuit, removeDraftItem,
    savePlaylistToSchedule,
  };
}
