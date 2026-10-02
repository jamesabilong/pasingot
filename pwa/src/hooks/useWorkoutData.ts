import { useCallback, useState } from 'react';
import { parseCatalogCsv } from '../lib/catalog';
import { mergeCatalogWithCustomExercises } from '../lib/custom-exercises';
import { addRecord, clearAndBulkInsert, getAll, getRecord, STORES } from '../lib/db';
import { parseQuestTemplatesCsv, parseQuestWorkoutsCsv } from '../lib/quests';
import { initialDraft, normalizeDraft } from '../lib/workout-planning';
import { SCHEMA_VERSION, type CustomExercise, type ExerciseCatalogItem, type PlaylistDraft, type QuestTemplate, type QuestWorkoutRow, type WorkoutLog, type WorkoutRow, type WorkoutSetLog, type WorkoutSessionEvent } from '../types';

const PLAYLIST_DRAFT_KEY = 'playlistDraft';

export function useWorkoutData() {
  const [workouts, setWorkouts] = useState<WorkoutRow[]>([]);
  const [logs, setLogs] = useState<WorkoutLog[]>([]);
  const [sessionEvents, setSessionEvents] = useState<WorkoutSessionEvent[]>([]);
  const [setLogEntries, setSetLogEntries] = useState<WorkoutSetLog[]>([]);
  const [catalog, setCatalog] = useState<ExerciseCatalogItem[]>([]);
  const [customExercises, setCustomExercises] = useState<CustomExercise[]>([]);
  const [builtInQuestTemplates, setBuiltInQuestTemplates] = useState<QuestTemplate[]>([]);
  const [builtInQuestRows, setBuiltInQuestRows] = useState<QuestWorkoutRow[]>([]);
  const [draft, setDraft] = useState<PlaylistDraft>(initialDraft);
  const refreshWorkouts = useCallback(async () => setWorkouts(await getAll<WorkoutRow>(STORES.workouts)), []);
  const refreshLogs = useCallback(async () => setLogs(await getAll<WorkoutLog>(STORES.logs)), []);
  const refreshSessionEvents = useCallback(async () => setSessionEvents(await getAll<WorkoutSessionEvent>(STORES.sessionEvents)), []);
  const refreshSetLogs = useCallback(async () => setSetLogEntries(await getAll<WorkoutSetLog>(STORES.setLogs)), []);
  const refreshCustomExercises = useCallback(async () => setCustomExercises(await getAll<CustomExercise>(STORES.customExercises)), []);
  const refreshWorkoutHistory = useCallback(async () => {
    await Promise.all([refreshLogs(), refreshSessionEvents(), refreshSetLogs()]);
  }, [refreshLogs, refreshSessionEvents, refreshSetLogs]);
  async function logExercise(row: WorkoutRow, status: WorkoutLog['status']) {
    if (row.id == null) return;
    await addRecord(STORES.logs, { schemaVersion: SCHEMA_VERSION, date: new Date().toISOString(), exercise: row.exercise, exerciseSourceId: row.exerciseSourceId ?? null, status, workoutRowId: row.id } satisfies WorkoutLog);
    const latestLogs = await getAll<WorkoutLog>(STORES.logs);
    setLogs(latestLogs);
  }

  const loadDefinitions = useCallback(async (isCurrent: () => boolean = () => true) => {
    let freshCatalog: ExerciseCatalogItem[] = [];
    try {
      const response = await fetch('/data/exercises.csv');
      if (!response.ok) throw new Error(`Catalog request failed with HTTP ${response.status}.`);
      freshCatalog = parseCatalogCsv(await response.text());
      await clearAndBulkInsert(STORES.exercises, freshCatalog);
    } catch (error) {
      console.warn('Could not refresh the local exercise catalog:', error);
    }
    const storedBaseCatalog = (freshCatalog.length ? freshCatalog : await getAll<ExerciseCatalogItem>(STORES.exercises))
      .sort((left, right) => left.displayName.localeCompare(right.displayName));
    const storedCustomExercises = await getAll<CustomExercise>(STORES.customExercises);
    if (!isCurrent()) return;
    setCustomExercises(storedCustomExercises);
    setCatalog(mergeCatalogWithCustomExercises(storedBaseCatalog, storedCustomExercises));
    const storedDraft = await getRecord<PlaylistDraft & { schemaVersion: number }>(STORES.appState, PLAYLIST_DRAFT_KEY);
    if (isCurrent() && storedDraft?.schemaVersion === SCHEMA_VERSION) setDraft(normalizeDraft(storedDraft, mergeCatalogWithCustomExercises(storedBaseCatalog, storedCustomExercises)));
    try {
      const [templateResponse, workoutResponse] = await Promise.all([
        fetch('/data/quest-templates.csv'),
        fetch('/data/quest-workouts.csv'),
      ]);
      if (!templateResponse.ok || !workoutResponse.ok) throw new Error('Quest CSV request failed.');
      const [freshTemplates, freshQuestRows] = [
        parseQuestTemplatesCsv(await templateResponse.text()),
        parseQuestWorkoutsCsv(await workoutResponse.text()),
      ];
      if (isCurrent()) {
        setBuiltInQuestTemplates(freshTemplates);
        setBuiltInQuestRows(freshQuestRows);
      }
    } catch (error) {
      console.warn('Could not load quest definitions:', error);
    }
  }, []);

  const restoreLibrary = useCallback(async () => {
    const [baseCatalog, storedCustomExercises, storedDraft] = await Promise.all([
      getAll<ExerciseCatalogItem>(STORES.exercises),
      getAll<CustomExercise>(STORES.customExercises),
      getRecord<PlaylistDraft & { schemaVersion: number }>(STORES.appState, PLAYLIST_DRAFT_KEY),
    ]);
    const merged = mergeCatalogWithCustomExercises(baseCatalog.sort((left, right) => left.displayName.localeCompare(right.displayName)), storedCustomExercises);
    setCustomExercises(storedCustomExercises);
    setCatalog(merged);
    setDraft(storedDraft?.schemaVersion === SCHEMA_VERSION ? normalizeDraft(storedDraft, merged) : initialDraft());
  }, []);

  return {
    workouts, setWorkouts, logs, sessionEvents, setLogEntries, catalog, setCatalog,
    customExercises, setCustomExercises, builtInQuestTemplates, builtInQuestRows,
    draft, setDraft, refreshWorkouts, refreshLogs, refreshSessionEvents,
    refreshSetLogs, refreshCustomExercises, refreshWorkoutHistory, logExercise,
    loadDefinitions, restoreLibrary,
  };
}
