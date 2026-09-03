// FUTURE-PHASE(web-push): background delivery needs a push subscription
// endpoint on the VPS + VAPID keys, and a `push` event handler in the service
// worker. Foreground matching remains isolated in checkScheduleAgainstNow so a
// future push handler can reuse it without changing the schedule contract.

import { useCallback, useEffect, useMemo, useState } from 'react';
import Papa from 'papaparse';
import { AppShell } from './components/AppShell';
import { HistoryView } from './components/HistoryView';
import { ImportView, type ImportResult } from './components/ImportView';
import { LibraryView } from './components/LibraryView';
import { QuestsView } from './components/QuestsView';
import { TodayView } from './components/TodayView';
import { type WorkoutSetInput } from './components/WorkoutPlayer';
import { useBodyMetrics } from './hooks/useBodyMetrics';
import { useScheduleNotifications } from './hooks/useScheduleNotifications';
import { useToasts } from './hooks/useToasts';
import { useWorkoutCueSettings } from './hooks/useWorkoutCueSettings';
import { parseCatalogCsv } from './lib/catalog';
import { addRecord, clearAndBulkInsert, deleteRecord, getAll, getRecord, putRecord, STORES } from './lib/db';
import { localDateKey, todayDateKey } from './lib/history-stats';
import { drainPendingWatchLogs, pushScheduleToNative } from './lib/native-bridge';
import { parseQuestTemplatesCsv, parseQuestWorkoutsCsv } from './lib/quests';
import { WORKOUT_CUE_SETTINGS_KEY, type WorkoutCueSettings } from './lib/workout-cues';
import {
  calculatePlanProgress,
  defaultPrescriptionFor,
  estimateLevelFor,
  estimateWorkoutDurationSeconds,
  formatEstimatedDuration,
  initialDraft,
  isWeightUnit,
  LEVEL_LABELS,
  LEVELS,
  levelEligible,
  MAX_PLAYLIST_ITEMS,
  normalizeDraft,
  TIME_RE,
  todayName,
  validLoadWeight,
  validateWorkoutRow,
} from './lib/workout-planning';
import {
  ACTIVE_SESSION_IDLE_TIMEOUT_MS,
  ACTIVE_WORKOUT_SESSION_KEY,
  currentSetInput,
  defaultSetInput,
  elapsedSecondsForSession,
  newPwaSession,
  normalizeActiveWorkoutSession,
  restCueKey,
  restOrActive,
  restSecondsForSession,
  setInputKey,
  startElapsedSession,
  stopElapsedSession,
  touchSession,
  type ActiveWorkoutSession,
} from './lib/workout-session';
import {
  SCHEMA_VERSION,
  type ExerciseLevel,
  type ExerciseCatalogItem,
  type HistoryRange,
  type PlaylistDraft,
  type PlaylistItem,
  type QuestCompletion,
  type QuestState,
  type QuestTemplate,
  type QuestWorkoutRow,
  type Tab,
  type WorkoutLog,
  type WorkoutRow,
  type WorkoutSetLog,
  type WorkoutSessionEvent,
} from './types';

const PLAYLIST_DRAFT_KEY = 'playlistDraft';
const QUEST_STATE_KEY = 'questState';

function questTotalDays(template: QuestTemplate): number {
  return template.durationWeeks * template.daysPerWeek;
}

function questWeekNumber(state: QuestState, template: QuestTemplate): number {
  return Math.floor((state.nextDayIndex - 1) / template.daysPerWeek) + 1;
}

function questTemplateDayNumber(state: QuestState, template: QuestTemplate): number {
  return ((state.nextDayIndex - 1) % template.daysPerWeek) + 1;
}

export default function App() {
  const [tab, setTab] = useState<Tab>('today');
  const [workouts, setWorkouts] = useState<WorkoutRow[]>([]);
  const [logs, setLogs] = useState<WorkoutLog[]>([]);
  const [sessionEvents, setSessionEvents] = useState<WorkoutSessionEvent[]>([]);
  const [setLogEntries, setSetLogEntries] = useState<WorkoutSetLog[]>([]);
  const [activeWorkoutSession, setActiveWorkoutSession] = useState<ActiveWorkoutSession | null>(null);
  const [workoutElapsedSeconds, setWorkoutElapsedSeconds] = useState(0);
  const [workoutRestRemainingSeconds, setWorkoutRestRemainingSeconds] = useState(0);
  const [catalog, setCatalog] = useState<ExerciseCatalogItem[]>([]);
  const [questTemplates, setQuestTemplates] = useState<QuestTemplate[]>([]);
  const [questRows, setQuestRows] = useState<QuestWorkoutRow[]>([]);
  const [questState, setQuestState] = useState<QuestState | null>(null);
  const [draft, setDraft] = useState<PlaylistDraft>(initialDraft);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('all');
  const [featuredOnly, setFeaturedOnly] = useState(false);
  const [importResult, setImportResult] = useState<ImportResult | null>(null);
  const [playlistResult, setPlaylistResult] = useState<{ message: string; error: boolean } | null>(null);
  const [questResult, setQuestResult] = useState<{ message: string; error: boolean } | null>(null);
  const [historyRange, setHistoryRange] = useState<HistoryRange>('month');
  const [notificationPermission, setNotificationPermission] = useState<NotificationPermission | 'unsupported'>(() => (
    'Notification' in window ? Notification.permission : 'unsupported'
  ));
  const {
    entries: bodyMetricEntries,
    draft: bodyMetricDraft,
    result: bodyMetricResult,
    setDraft: setBodyMetricDraft,
    refresh: refreshBodyMetrics,
    save: saveBodyMetric,
    edit: editBodyMetric,
    remove: deleteBodyMetric,
  } = useBodyMetrics();
  const { toasts, addToast } = useToasts();
  const {
    settings: workoutCueSettings,
    loadSettings: loadWorkoutCueSettings,
    saveSettings: saveWorkoutCueSettings,
    playCue: playWorkoutCue,
  } = useWorkoutCueSettings();

  const refreshWorkouts = useCallback(async () => setWorkouts(await getAll<WorkoutRow>(STORES.workouts)), []);
  const refreshLogs = useCallback(async () => setLogs(await getAll<WorkoutLog>(STORES.logs)), []);
  const refreshSessionEvents = useCallback(async () => setSessionEvents(await getAll<WorkoutSessionEvent>(STORES.sessionEvents)), []);
  const refreshSetLogs = useCallback(async () => setSetLogEntries(await getAll<WorkoutSetLog>(STORES.setLogs)), []);
  const saveActiveWorkoutSession = useCallback(async (next: ActiveWorkoutSession | null) => {
    setActiveWorkoutSession(next);
    if (next) await putRecord(STORES.appState, next);
    else await deleteRecord(STORES.appState, ACTIVE_WORKOUT_SESSION_KEY);
  }, []);
  const saveDraft = useCallback(async (next: PlaylistDraft) => {
    setDraft(next);
    await putRecord(STORES.appState, { key: PLAYLIST_DRAFT_KEY, schemaVersion: SCHEMA_VERSION, ...next });
  }, []);

  const saveQuestState = useCallback(async (next: QuestState) => {
    setQuestState(next);
    await putRecord(STORES.appState, next);
  }, []);

  useEffect(() => {
    let disposed = false;
    async function initialize() {
      await Promise.all([refreshWorkouts(), refreshLogs(), refreshSessionEvents(), refreshSetLogs(), refreshBodyMetrics()]);
      // Refresh the native cache after an app upgrade as well as after an
      // explicit schedule edit, so existing quest rows gain new bridge fields.
      await pushScheduleToNative(await getAll<WorkoutRow>(STORES.workouts));
      let freshCatalog: ExerciseCatalogItem[] = [];
      try {
        const response = await fetch('/data/exercises.csv');
        if (!response.ok) throw new Error(`Catalog request failed with HTTP ${response.status}.`);
        freshCatalog = parseCatalogCsv(await response.text());
        await clearAndBulkInsert(STORES.exercises, freshCatalog);
      } catch (error) {
        console.warn('Could not refresh the local exercise catalog:', error);
      }
      const storedCatalog = (freshCatalog.length ? freshCatalog : await getAll<ExerciseCatalogItem>(STORES.exercises))
        .sort((left, right) => left.displayName.localeCompare(right.displayName));
      if (disposed) return;
      setCatalog(storedCatalog);
      const storedDraft = await getRecord<PlaylistDraft & { schemaVersion: number }>(STORES.appState, PLAYLIST_DRAFT_KEY);
      if (!disposed && storedDraft?.schemaVersion === SCHEMA_VERSION) setDraft(normalizeDraft(storedDraft, storedCatalog));
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
        if (!disposed) {
          setQuestTemplates(freshTemplates);
          setQuestRows(freshQuestRows);
        }
      } catch (error) {
        console.warn('Could not load quest definitions:', error);
      }
      const storedQuestState = await getRecord<QuestState>(STORES.appState, QUEST_STATE_KEY);
      if (!disposed && storedQuestState?.schemaVersion === SCHEMA_VERSION) setQuestState(storedQuestState);
      const storedCueSettings = await getRecord<WorkoutCueSettings>(STORES.appState, WORKOUT_CUE_SETTINGS_KEY);
      if (!disposed) loadWorkoutCueSettings(storedCueSettings);
      const storedWorkoutSession = await getRecord<ActiveWorkoutSession>(STORES.appState, ACTIVE_WORKOUT_SESSION_KEY);
      if (!disposed && storedWorkoutSession?.schemaVersion === SCHEMA_VERSION && storedWorkoutSession.planDate === todayDateKey()) {
        setActiveWorkoutSession(normalizeActiveWorkoutSession(storedWorkoutSession));
      } else if (storedWorkoutSession) {
        await deleteRecord(STORES.appState, ACTIVE_WORKOUT_SESSION_KEY);
      }
      const drained = await drainPendingWatchLogs();
      if (drained && !disposed) await Promise.all([refreshLogs(), refreshSessionEvents()]);
    }
    void initialize();
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        void drainPendingWatchLogs().then((drained) => {
          if (drained) return Promise.all([refreshLogs(), refreshSessionEvents()]);
          return undefined;
        });
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => { disposed = true; document.removeEventListener('visibilitychange', onVisible); };
  }, [loadWorkoutCueSettings, refreshBodyMetrics, refreshLogs, refreshSessionEvents, refreshSetLogs, refreshWorkouts]);

  useScheduleNotifications(workouts, addToast);

  const todayWorkouts = useMemo(() => workouts
    .filter((row) => row.day.toLowerCase() === todayName().toLowerCase())
    .sort((left, right) => left.time.localeCompare(right.time)), [workouts]);
  const todayEstimate = useMemo(() => (
    formatEstimatedDuration(estimateWorkoutDurationSeconds(todayWorkouts, estimateLevelFor(todayWorkouts)))
  ), [todayWorkouts]);
  const draftEstimate = useMemo(() => (
    formatEstimatedDuration(estimateWorkoutDurationSeconds(draft.items, draft.level))
  ), [draft.items, draft.level]);

  const todayStatuses = useMemo(() => {
    const statuses = new Map<number, WorkoutLog['status']>();
    logs.forEach((log) => {
      if (log.date.startsWith(todayDateKey()) && log.workoutRowId != null) statuses.set(log.workoutRowId, log.status);
    });
    return statuses;
  }, [logs]);
  const todayProgress = useMemo(() => calculatePlanProgress(todayWorkouts, todayStatuses), [todayStatuses, todayWorkouts]);
  const activeWorkoutRows = useMemo(() => {
    if (!activeWorkoutSession) return [];
    return activeWorkoutSession.rowIds
      .map((id) => workouts.find((row) => row.id === id))
      .filter((row): row is WorkoutRow => Boolean(row));
  }, [activeWorkoutSession, workouts]);

  useEffect(() => {
    if (!activeWorkoutSession) {
      setWorkoutElapsedSeconds(0);
      setWorkoutRestRemainingSeconds(0);
      return undefined;
    }

    const syncTimers = () => {
      const now = Date.now();
      if (activeWorkoutSession.planDate !== todayDateKey()) {
        const ended = stopElapsedSession({ ...activeWorkoutSession, status: 'ended' }, 'stale_next_day', now);
        void recordLocalSessionEvent(ended, 'ended', 'stale_next_day', activeWorkoutRows)
          .then(() => saveActiveWorkoutSession(null))
          .then(() => addToast('Previous workout was closed because the day changed.'));
        return;
      }
      if (
        (activeWorkoutSession.status === 'active' || activeWorkoutSession.status === 'resting')
        && now - activeWorkoutSession.lastInteractionAtEpochMillis > ACTIVE_SESSION_IDLE_TIMEOUT_MS
      ) {
        const pausedRestSeconds = activeWorkoutSession.status === 'resting' ? Math.max(1, restSecondsForSession(activeWorkoutSession, now)) : null;
        void saveActiveWorkoutSession({
          ...stopElapsedSession(activeWorkoutSession, 'inactive_timeout', now),
          status: 'paused',
          restUntilEpochMillis: null,
          pausedRestRemainingSeconds: pausedRestSeconds,
        }).then(() => addToast('Workout paused after 45 minutes without activity.'));
        return;
      }
      const restSeconds = restSecondsForSession(activeWorkoutSession, now);
      setWorkoutElapsedSeconds(elapsedSecondsForSession(activeWorkoutSession, now));
      setWorkoutRestRemainingSeconds(restSeconds);
      if (activeWorkoutSession.status === 'resting' && restSeconds <= 0) {
        const cueKey = restCueKey(activeWorkoutSession);
        if (activeWorkoutSession.lastRestCueKey !== cueKey) playWorkoutCue(activeWorkoutRows[activeWorkoutSession.exerciseIndex]);
        void saveActiveWorkoutSession(touchSession(startElapsedSession({
          ...activeWorkoutSession,
          status: 'active',
          restUntilEpochMillis: null,
          pausedRestRemainingSeconds: null,
          lastRestCueKey: cueKey,
        }, now), now));
      }
    };

    syncTimers();
    if (activeWorkoutSession.status !== 'active' && activeWorkoutSession.status !== 'resting') return undefined;
    const timer = window.setInterval(syncTimers, 1000);
    return () => window.clearInterval(timer);
  }, [activeWorkoutRows, activeWorkoutSession, addToast, playWorkoutCue, saveActiveWorkoutSession]);

  const activeWorkoutRow = activeWorkoutSession ? activeWorkoutRows[activeWorkoutSession.exerciseIndex] : undefined;
  const activeSetInput = activeWorkoutSession && activeWorkoutRow ? currentSetInput(activeWorkoutSession, activeWorkoutRow) : defaultSetInput({
    schemaVersion: SCHEMA_VERSION,
    day: todayName(),
    time: '00:00',
    exercise: '',
    sets: 1,
    reps: '',
    rest: 0,
  });
  const todaySetLogCount = useMemo(() => setLogEntries.filter((entry) => entry.date.startsWith(todayDateKey())).length, [setLogEntries]);

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
  const catalogBySourceId = useMemo(() => new Map(catalog.map((item) => [item.sourceId, item])), [catalog]);
  const activeQuestTemplate = useMemo(() => (
    questState ? questTemplates.find((template) => template.questId === questState.questId) : questTemplates[0]
  ), [questState, questTemplates]);
  const currentQuestDayNumber = questState && activeQuestTemplate && questState.status === 'active'
    ? questTemplateDayNumber(questState, activeQuestTemplate)
    : null;
  const currentQuestRows = useMemo(() => {
    if (!questState || !activeQuestTemplate || currentQuestDayNumber == null || questState.status !== 'active') return [];
    return questRows
      .filter((row) => row.questId === questState.questId && row.level === questState.level && row.dayNumber === currentQuestDayNumber)
      .map((row) => ({ row, exercise: catalogBySourceId.get(row.exerciseSourceId) }))
      .filter((entry): entry is { row: QuestWorkoutRow; exercise: ExerciseCatalogItem } => Boolean(entry.exercise))
      .sort((left, right) => left.row.sequence - right.row.sequence);
  }, [activeQuestTemplate, catalogBySourceId, currentQuestDayNumber, questRows, questState]);
  const currentQuestEstimate = useMemo(() => (
    formatEstimatedDuration(estimateWorkoutDurationSeconds(currentQuestRows.map(({ row }) => row), questState?.level ?? 'beginner'))
  ), [currentQuestRows, questState?.level]);
  const scheduledCurrentQuestRows = useMemo(() => {
    if (!questState) return [];
    return workouts.filter((row) => row.questId === questState.questId && row.questDayIndex === questState.nextDayIndex);
  }, [questState, workouts]);
  const currentQuestProgressRows = useMemo(() => (
    scheduledCurrentQuestRows.length
      ? scheduledCurrentQuestRows
      : currentQuestRows.map(({ row }, index) => ({ id: undefined, sequence: row.sequence, fallbackIndex: index }))
  ), [currentQuestRows, scheduledCurrentQuestRows]);
  const currentQuestProgress = useMemo(() => (
    calculatePlanProgress(currentQuestProgressRows, todayStatuses)
  ), [currentQuestProgressRows, todayStatuses]);

  useEffect(() => {
    // Watch logs arrive through the native bridge while the app resumes.
    // Reconcile one representative row per quest day after those logs enter
    // IndexedDB; phone-button logs use this same path through the logs state.
    const candidates = new Map<string, WorkoutRow>();
    workouts.forEach((row) => {
      if (!row.questId || row.questDayIndex == null || row.id == null) return;
      const hasLinkedLog = logs.some((log) => log.workoutRowId === row.id && log.date.startsWith(todayDateKey()));
      if (hasLinkedLog) candidates.set(`${row.questId}:${row.questDayIndex}`, row);
    });
    candidates.forEach((row) => { void maybeCompleteQuestDay(row, logs); });
  }, [logs, questTemplates, workouts]);

  async function logExercise(row: WorkoutRow, status: WorkoutLog['status']) {
    if (row.id == null) return;
    await addRecord(STORES.logs, { schemaVersion: SCHEMA_VERSION, date: new Date().toISOString(), exercise: row.exercise, status, workoutRowId: row.id } satisfies WorkoutLog);
    const latestLogs = await getAll<WorkoutLog>(STORES.logs);
    setLogs(latestLogs);
  }

  async function updatePwaSetInput(updates: Partial<WorkoutSetInput>) {
    if (!activeWorkoutSession) return;
    const row = activeWorkoutRows[activeWorkoutSession.exerciseIndex];
    if (!row) return;
    const key = setInputKey(row, activeWorkoutSession.currentSet);
    await saveActiveWorkoutSession({
      ...touchSession(activeWorkoutSession),
      setInputs: {
        ...activeWorkoutSession.setInputs,
        [key]: { ...currentSetInput(activeWorkoutSession, row), ...updates },
      },
    });
  }

  async function recordWorkoutSet(row: WorkoutRow, setNumber: number, input: WorkoutSetInput) {
    const actualReps = input.actualReps.trim() || row.reps;
    const loadWeight = validLoadWeight(input.loadWeight);
    const loadUnit = loadWeight != null ? input.loadUnit : null;
    await addRecord(STORES.setLogs, {
      schemaVersion: SCHEMA_VERSION,
      date: new Date().toISOString(),
      workoutRowId: row.id ?? null,
      exercise: row.exercise,
      setNumber,
      plannedReps: row.reps,
      actualReps,
      loadWeight,
      loadUnit,
    } satisfies WorkoutSetLog);
    await refreshSetLogs();
  }

  async function recordLocalSessionEvent(session: ActiveWorkoutSession, eventType: WorkoutSessionEvent['eventType'], stopReason: string, rows: WorkoutRow[]) {
    const row = rows[session.exerciseIndex];
    const estimatedDurationSeconds = estimateWorkoutDurationSeconds(rows, estimateLevelFor(rows));
    await addRecord(STORES.sessionEvents, {
      schemaVersion: SCHEMA_VERSION,
      workoutEntryId: `pwa:${session.planDate}`,
      workoutDate: session.planDate,
      eventType,
      stopReason,
      timestamp: new Date().toISOString(),
      elapsedSeconds: elapsedSecondsForSession(session),
      estimatedDurationSeconds,
      exerciseIndex: session.exerciseIndex,
      currentSet: session.currentSet,
      totalExercises: rows.length,
      currentExercise: row?.exercise ?? null,
    } satisfies WorkoutSessionEvent);
    await refreshSessionEvents();
  }

  async function startTodayWorkoutPlayer() {
    const playableRows = todayWorkouts.filter((row) => row.id != null);
    if (!playableRows.length) return addToast('Add a workout to today before starting the player.');
    const firstPendingIndex = playableRows.findIndex((row) => row.id != null && !todayStatuses.has(row.id));
    if (firstPendingIndex < 0) return addToast('Today’s plan is already handled.');
    await saveActiveWorkoutSession(newPwaSession(playableRows, firstPendingIndex));
  }

  async function completePwaSet() {
    if (!activeWorkoutSession || activeWorkoutSession.status !== 'active') return;
    const rows = activeWorkoutRows;
    const row = rows[activeWorkoutSession.exerciseIndex];
    if (!row) return;
    const touchedSession = touchSession(activeWorkoutSession);
    await recordWorkoutSet(row, touchedSession.currentSet, currentSetInput(touchedSession, row));

    if (touchedSession.currentSet < row.sets) {
      await saveActiveWorkoutSession(restOrActive({ ...touchedSession, currentSet: touchedSession.currentSet + 1 }, row.rest));
      return;
    }

    await logExercise(row, 'done');
    const nextIndex = touchedSession.exerciseIndex + 1;
    if (nextIndex >= rows.length) {
      const completed = stopElapsedSession({ ...touchedSession, status: 'completed' }, 'completed');
      await saveActiveWorkoutSession(completed);
      await recordLocalSessionEvent(completed, 'completed', 'completed', rows);
      return;
    }
    await saveActiveWorkoutSession(restOrActive({ ...touchedSession, exerciseIndex: nextIndex, currentSet: 1 }, row.rest));
  }

  async function skipPwaExercise() {
    if (!activeWorkoutSession || activeWorkoutSession.status !== 'active') return;
    const rows = activeWorkoutRows;
    const row = rows[activeWorkoutSession.exerciseIndex];
    if (!row) return;
    const touchedSession = touchSession(activeWorkoutSession);

    await logExercise(row, 'skipped');
    const nextIndex = touchedSession.exerciseIndex + 1;
    if (nextIndex >= rows.length) {
      const completed = stopElapsedSession({ ...touchedSession, status: 'completed' }, 'completed');
      await saveActiveWorkoutSession(completed);
      await recordLocalSessionEvent(completed, 'completed', 'completed', rows);
      return;
    }
    await saveActiveWorkoutSession({ ...touchedSession, exerciseIndex: nextIndex, currentSet: 1 });
  }

  async function pausePwaWorkout() {
    if (!activeWorkoutSession || (activeWorkoutSession.status !== 'active' && activeWorkoutSession.status !== 'resting')) return;
    const pausedRestSeconds = activeWorkoutSession.status === 'resting' ? Math.max(1, restSecondsForSession(activeWorkoutSession)) : null;
    await saveActiveWorkoutSession({
      ...stopElapsedSession(activeWorkoutSession, 'paused_by_user'),
      status: 'paused',
      restUntilEpochMillis: null,
      pausedRestRemainingSeconds: pausedRestSeconds,
      lastInteractionAtEpochMillis: Date.now(),
    });
  }

  async function resumePwaWorkout() {
    if (!activeWorkoutSession || activeWorkoutSession.status !== 'paused') return;
    const now = Date.now();
    const resumed = activeWorkoutSession.pausedRestRemainingSeconds != null ? {
      ...activeWorkoutSession,
      status: 'resting' as const,
      restUntilEpochMillis: now + activeWorkoutSession.pausedRestRemainingSeconds * 1000,
      pausedRestRemainingSeconds: null,
      elapsedStartedAtEpochMillis: now,
      lastStopReason: null,
      lastInteractionAtEpochMillis: now,
    } : touchSession(startElapsedSession({ ...activeWorkoutSession, status: 'active' }, now), now);
    await saveActiveWorkoutSession(resumed);
  }

  async function restartPwaWorkout() {
    const rows = activeWorkoutRows.length ? activeWorkoutRows : todayWorkouts.filter((row) => row.id != null);
    if (!rows.length) return;
    await saveActiveWorkoutSession(newPwaSession(rows));
  }

  async function endPwaWorkout() {
    if (!activeWorkoutSession || activeWorkoutSession.status === 'ended' || activeWorkoutSession.status === 'completed') return;
    const rows = activeWorkoutRows;
    const ended = stopElapsedSession({ ...touchSession(activeWorkoutSession), status: 'ended' }, 'ended_by_user');
    await saveActiveWorkoutSession(ended);
    await recordLocalSessionEvent(ended, 'ended', 'ended_by_user', rows);
  }

  async function startPwaRestNow() {
    if (!activeWorkoutSession || activeWorkoutSession.status !== 'resting') return;
    await saveActiveWorkoutSession(touchSession(startElapsedSession({
      ...activeWorkoutSession,
      status: 'active',
      restUntilEpochMillis: null,
      pausedRestRemainingSeconds: null,
    })));
  }

  async function addPwaRestSeconds(seconds: number) {
    if (!activeWorkoutSession || activeWorkoutSession.status !== 'resting' || seconds <= 0) return;
    const now = Date.now();
    await saveActiveWorkoutSession({
      ...touchSession(activeWorkoutSession, now),
      restUntilEpochMillis: Math.max(activeWorkoutSession.restUntilEpochMillis ?? now, now) + seconds * 1000,
    });
  }

  async function importCsv(file: File) {
    const parsed = await new Promise<Papa.ParseResult<Record<string, string>>>((resolve) => {
      Papa.parse<Record<string, string>>(file, { header: true, skipEmptyLines: true, complete: resolve });
    });
    const valid = parsed.data.map(validateWorkoutRow).filter((row): row is WorkoutRow => row !== null);
    await clearAndBulkInsert(STORES.workouts, valid);
    await pushScheduleToNative(valid);
    setImportResult({ imported: valid.length, skipped: parsed.data.length - valid.length });
    await refreshWorkouts();
  }

  async function addCatalogExercise(sourceId: number) {
    if (draft.items.length >= MAX_PLAYLIST_ITEMS) return addToast(`A playlist can contain up to ${MAX_PLAYLIST_ITEMS} exercises.`);
    const exercise = catalog.find((item) => item.sourceId === sourceId);
    if (!exercise || draft.items.some((item) => item.sourceId === sourceId)) return;
    await saveDraft({ ...draft, items: [...draft.items, { sourceId, name: exercise.name, ...defaultPrescriptionFor(exercise, draft.level) }] });
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

  async function startQuest(template: QuestTemplate) {
    const next: QuestState = {
      key: QUEST_STATE_KEY,
      schemaVersion: SCHEMA_VERSION,
      questId: template.questId,
      level: draft.level,
      nextDayIndex: 1,
      scheduledTime: draft.time,
      startedAt: new Date().toISOString(),
      completedDays: [],
      status: 'active',
    };
    setQuestResult(null);
    await saveQuestState(next);
  }

  async function saveQuestDayToSchedule() {
    if (!questState || !activeQuestTemplate || !currentQuestRows.length) return;
    if (!TIME_RE.test(questState.scheduledTime)) {
      setQuestResult({ error: true, message: 'Choose a valid start time before scheduling this quest day.' });
      return;
    }
    const dayNumber = questTemplateDayNumber(questState, activeQuestTemplate);
    const dayLabel = currentQuestRows[0]?.row.dayLabel ?? `Day ${dayNumber}`;
    const proposedRows = currentQuestRows.map(({ row, exercise }) => ({
      schemaVersion: SCHEMA_VERSION,
      day: todayName(),
      time: questState.scheduledTime,
      exercise: exercise.name,
      sets: row.sets,
      reps: row.reps,
      rest: row.rest,
      questId: questState.questId,
      questDayIndex: questState.nextDayIndex,
      questDayLabel: dayLabel,
      questLevel: questState.level,
    } satisfies WorkoutRow));
    const completedQuestDayIndexes = new Set(questState.completedDays.map((day) => day.dayIndex));
    const replacementRows = workouts.filter((existing) => (
      existing.id != null
      && existing.questId === questState.questId
      && existing.questDayIndex != null
      && completedQuestDayIndexes.has(existing.questDayIndex)
      && existing.day === todayName()
      && existing.time === questState.scheduledTime
    ));
    const replacementIds = new Set(replacementRows.map((row) => row.id));
    const activeWorkouts = workouts.filter((row) => row.id == null || !replacementIds.has(row.id));
    const conflicts = proposedRows.filter((proposed) => activeWorkouts.some((existing) => (
      existing.day === proposed.day
      && existing.time === proposed.time
      && existing.exercise.toLowerCase() === proposed.exercise.toLowerCase()
      && !(existing.questId === proposed.questId && existing.questDayIndex === proposed.questDayIndex)
    )));
    if (conflicts.length) {
      setQuestResult({ error: true, message: `Move this quest to a different time; ${conflicts.length} exercise${conflicts.length === 1 ? '' : 's'} already exist at ${questState.scheduledTime}.` });
      return;
    }
    for (const row of replacementRows) {
      if (row.id != null) await deleteRecord(STORES.workouts, row.id);
    }
    const existingQuestKeys = new Set(activeWorkouts
      .filter((row) => row.questId === questState.questId && row.questDayIndex === questState.nextDayIndex)
      .map((row) => row.exercise.toLowerCase()));
    const additions = proposedRows.filter((row) => !existingQuestKeys.has(row.exercise.toLowerCase()));
    for (const row of additions) await addRecord(STORES.workouts, row);
    const schedule = await getAll<WorkoutRow>(STORES.workouts);
    await pushScheduleToNative(schedule);
    await refreshWorkouts();
    setQuestResult({ error: false, message: additions.length ? `Scheduled ${dayLabel} for ${todayName()} at ${questState.scheduledTime}. ${currentQuestEstimate}.` : `${dayLabel} is already scheduled.` });
  }

  async function maybeCompleteQuestDay(row: WorkoutRow, latestLogs: WorkoutLog[]) {
    if (!row.questId || row.questDayIndex == null) return;
    const storedState = await getRecord<QuestState>(STORES.appState, QUEST_STATE_KEY);
    const template = questTemplates.find((item) => item.questId === row.questId);
    if (!storedState || storedState.schemaVersion !== SCHEMA_VERSION || !template) return;
    if (storedState.completedDays.some((day) => day.dayIndex === row.questDayIndex)) return;
    const questDayRows = workouts.filter((item) => item.questId === row.questId && item.questDayIndex === row.questDayIndex);
    if (!questDayRows.length) return;
    const terminal = questDayRows.every((item) => item.id != null && latestLogs.some((log) => (
      log.workoutRowId === item.id && log.date.startsWith(todayDateKey()) && (log.status === 'done' || log.status === 'skipped')
    )));
    if (!terminal) return;
    const totalDays = questTotalDays(template);
    const dayNumber = ((row.questDayIndex - 1) % template.daysPerWeek) + 1;
    const completion: QuestCompletion = {
      dayIndex: row.questDayIndex,
      dayNumber,
      dayLabel: row.questDayLabel ?? `Day ${dayNumber}`,
      level: row.questLevel ?? storedState.level,
      completedAt: new Date().toISOString(),
    };
    const nextDayIndex = storedState.nextDayIndex === row.questDayIndex ? storedState.nextDayIndex + 1 : storedState.nextDayIndex;
    const nextState: QuestState = {
      ...storedState,
      completedDays: [...storedState.completedDays, completion],
      nextDayIndex,
      status: nextDayIndex > totalDays ? 'completed' : 'active',
    };
    await saveQuestState(nextState);
    setQuestResult({ error: false, message: nextState.status === 'completed' ? `${template.title} completed.` : `${completion.dayLabel} completed. Next quest day is ready.` });
  }

  async function requestNotificationPermission() {
    if (!('Notification' in window)) return;
    const permission = await Notification.requestPermission();
    setNotificationPermission(permission);
  }

  return (
    <AppShell
      tab={tab}
      toasts={toasts}
      notificationPermission={notificationPermission}
      onTabChange={setTab}
      onRequestNotificationPermission={() => void requestNotificationPermission()}
    >
      {tab === 'today' && <TodayView
        todayName={todayName()}
        workouts={todayWorkouts}
        estimate={todayEstimate}
        progress={todayProgress}
        statuses={todayStatuses}
        setLogCount={todaySetLogCount}
        activeSession={activeWorkoutSession}
        activeRows={activeWorkoutRows}
        elapsedSeconds={workoutElapsedSeconds}
        restRemainingSeconds={workoutRestRemainingSeconds}
        activeSetInput={activeSetInput}
        cueSettings={workoutCueSettings}
        onStartPlayer={() => void startTodayWorkoutPlayer()}
        onSetInputChange={(updates) => void updatePwaSetInput(updates)}
        onCueSettingsChange={saveWorkoutCueSettings}
        onCompleteSet={() => void completePwaSet()}
        onSkipExercise={() => void skipPwaExercise()}
        onPause={() => void pausePwaWorkout()}
        onResume={() => void resumePwaWorkout()}
        onRestart={() => void restartPwaWorkout()}
        onEnd={() => void endPwaWorkout()}
        onStartNow={() => void startPwaRestNow()}
        onAddRestSeconds={(seconds) => void addPwaRestSeconds(seconds)}
        onClosePlayer={() => void saveActiveWorkoutSession(null)}
        onLogExercise={(row, status) => void logExercise(row, status)}
      />}

      {tab === 'quests' && <QuestsView
        questState={questState}
        activeQuestTemplate={activeQuestTemplate}
        draftLevel={draft.level}
        levels={LEVELS}
        levelLabels={LEVEL_LABELS}
        currentQuestRows={currentQuestRows}
        scheduledCurrentQuestRows={scheduledCurrentQuestRows}
        currentQuestEstimate={currentQuestEstimate}
        currentQuestProgress={currentQuestProgress}
        questResult={questResult}
        totalDays={activeQuestTemplate ? questTotalDays(activeQuestTemplate) : null}
        weekNumber={questState && activeQuestTemplate ? questWeekNumber(questState, activeQuestTemplate) : null}
        dayNumber={questState && activeQuestTemplate ? questTemplateDayNumber(questState, activeQuestTemplate) : null}
        onDraftLevelChange={(level) => void saveDraft({ ...draft, level })}
        onQuestStateChange={(state) => void saveQuestState(state)}
        onStartQuest={(template) => void startQuest(template)}
        onSaveQuestDayToSchedule={() => void saveQuestDayToSchedule()}
      />}

      {tab === 'library' && <LibraryView
        catalog={catalog}
        filteredCatalog={filteredCatalog}
        categories={categories}
        levelCounts={levelCounts}
        levels={LEVELS}
        levelLabels={LEVEL_LABELS}
        maxPlaylistItems={MAX_PLAYLIST_ITEMS}
        draft={draft}
        search={search}
        category={category}
        featuredOnly={featuredOnly}
        draftEstimate={draftEstimate}
        playlistResult={playlistResult}
        defaultPrescriptionFor={defaultPrescriptionFor}
        onSearchChange={setSearch}
        onCategoryChange={setCategory}
        onFeaturedOnlyChange={setFeaturedOnly}
        onDraftChange={(next) => void saveDraft(next)}
        onAddCatalogExercise={(sourceId) => void addCatalogExercise(sourceId)}
        onUpdateDraftItem={(index, updates) => void updateDraftItem(index, updates)}
        onReorderDraftItem={(index, direction) => void reorderDraftItem(index, direction)}
        onSavePlaylistToSchedule={() => void savePlaylistToSchedule()}
        onClearPlaylistResult={() => setPlaylistResult(null)}
      />}

      {tab === 'import' && <ImportView
        result={importResult}
        workouts={workouts}
        onImportFile={(file) => void importCsv(file)}
      />}

      {tab === 'history' && <HistoryView
        range={historyRange}
        logs={logs}
        sessionEvents={sessionEvents}
        setLogs={setLogEntries}
        bodyMetrics={bodyMetricEntries}
        catalog={catalog}
        questState={questState}
        activeQuestTemplate={activeQuestTemplate}
        levelLabels={LEVEL_LABELS}
        bodyMetricDraft={bodyMetricDraft}
        bodyMetricResult={bodyMetricResult}
        onRangeChange={setHistoryRange}
        onBodyMetricDraftChange={setBodyMetricDraft}
        onBodyMetricSave={() => void saveBodyMetric()}
        onBodyMetricEdit={editBodyMetric}
        onBodyMetricDelete={(entry) => void deleteBodyMetric(entry)}
      />}
    </AppShell>
  );
}
