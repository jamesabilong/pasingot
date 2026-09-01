// FUTURE-PHASE(web-push): background delivery needs a push subscription
// endpoint on the VPS + VAPID keys, and a `push` event handler in the service
// worker. Foreground matching remains isolated in checkScheduleAgainstNow so a
// future push handler can reuse it without changing the schedule contract.

import { useCallback, useEffect, useMemo, useState } from 'react';
import Papa from 'papaparse';
import { parseCatalogCsv } from './lib/catalog';
import { addRecord, clearAndBulkInsert, deleteRecord, getAll, getRecord, putRecord, STORES } from './lib/db';
import { drainPendingWatchLogs, pushScheduleToNative } from './lib/native-bridge';
import { parseQuestTemplatesCsv, parseQuestWorkoutsCsv } from './lib/quests';
import {
  SCHEMA_VERSION,
  WEEKDAYS,
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
  type Weekday,
  type WorkoutLog,
  type WorkoutRow,
} from './types';

const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;
const PLAYLIST_DRAFT_KEY = 'playlistDraft';
const QUEST_STATE_KEY = 'questState';
const MAX_PLAYLIST_ITEMS = 24;
const POLL_INTERVAL_MS = 30_000;
const NOTIFICATION_TOLERANCE_MINUTES = 1;
const LEVELS: ExerciseLevel[] = ['beginner', 'intermediate', 'advanced'];
const LEVEL_RANK: Record<ExerciseLevel, number> = { beginner: 0, intermediate: 1, advanced: 2 };
const LEVEL_LABELS: Record<ExerciseLevel, string> = {
  beginner: 'Beginner',
  intermediate: 'Intermediate',
  advanced: 'Advanced',
};

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

interface ImportResult {
  imported: number;
  skipped: number;
}

interface Toast {
  id: number;
  message: string;
}

interface HistoryDateGroup {
  key: string;
  label: string;
  logs: WorkoutLog[];
  done: number;
  skipped: number;
}

function todayName(): Weekday {
  return new Date().toLocaleDateString('en-US', { weekday: 'long' }) as Weekday;
}

function todayDateKey(): string {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function currentHHMM(): string {
  const date = new Date();
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function localDateKey(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 10);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function formatHistoryDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
}

function formatHistoryTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}

function withinTolerance(scheduled: string, current: string): boolean {
  const [scheduledHour, scheduledMinute] = scheduled.split(':').map(Number);
  const [currentHour, currentMinute] = current.split(':').map(Number);
  return Math.abs((scheduledHour * 60 + scheduledMinute) - (currentHour * 60 + currentMinute)) <= NOTIFICATION_TOLERANCE_MINUTES;
}

function validateWorkoutRow(raw: Record<string, string>): WorkoutRow | null {
  const dayRaw = String(raw.day ?? '').trim();
  const day = WEEKDAYS.find((item) => item.toLowerCase() === dayRaw.toLowerCase());
  const time = String(raw.time ?? '').trim();
  const exercise = String(raw.exercise ?? '').trim();
  const reps = String(raw.reps ?? '').trim();
  const sets = Number.parseInt(String(raw.sets ?? '').trim(), 10);
  const rest = Number.parseInt(String(raw.rest ?? '').trim(), 10);
  if (!day || !TIME_RE.test(time) || !exercise || !reps || !Number.isInteger(sets) || sets <= 0 || !Number.isInteger(rest) || rest < 0) return null;
  return { schemaVersion: SCHEMA_VERSION, day, time, exercise, sets, reps, rest };
}

function isExerciseLevel(value: unknown): value is ExerciseLevel {
  return typeof value === 'string' && LEVELS.includes(value as ExerciseLevel);
}

function defaultPrescriptionFor(exercise: ExerciseCatalogItem, level: ExerciseLevel): Omit<PlaylistItem, 'sourceId' | 'name'> {
  return exercise.category.toLowerCase() === 'cardio' ? DEFAULT_PRESCRIPTIONS[level].cardio : DEFAULT_PRESCRIPTIONS[level].strength;
}

function levelEligible(exercise: ExerciseCatalogItem, selectedLevel: ExerciseLevel): boolean {
  return LEVEL_RANK[exercise.minimumLevel] <= LEVEL_RANK[selectedLevel];
}

function questTotalDays(template: QuestTemplate): number {
  return template.durationWeeks * template.daysPerWeek;
}

function questWeekNumber(state: QuestState, template: QuestTemplate): number {
  return Math.floor((state.nextDayIndex - 1) / template.daysPerWeek) + 1;
}

function questTemplateDayNumber(state: QuestState, template: QuestTemplate): number {
  return ((state.nextDayIndex - 1) % template.daysPerWeek) + 1;
}

function initialDraft(): PlaylistDraft {
  return { day: todayName(), time: '07:00', level: 'beginner', items: [] };
}

function normalizeDraft(input: Partial<PlaylistDraft>, catalog: ExerciseCatalogItem[]): PlaylistDraft {
  const day = WEEKDAYS.includes(input.day as Weekday) ? input.day as Weekday : todayName();
  const time = TIME_RE.test(String(input.time ?? '')) ? String(input.time) : '07:00';
  const level = isExerciseLevel(input.level) ? input.level : 'beginner';
  const items = (input.items ?? []).map((item) => {
    const sourceId = Number(item.sourceId);
    const exercise = catalog.find((candidate) => candidate.sourceId === sourceId);
    if (!exercise) return null;
    const sets = Number(item.sets);
    const rest = Number(item.rest);
    const reps = String(item.reps ?? '').trim().slice(0, 30);
    return {
      sourceId,
      name: exercise.name,
      sets: Number.isInteger(sets) && sets > 0 ? sets : 3,
      reps: reps || '8-12',
      rest: Number.isInteger(rest) && rest >= 0 ? rest : 60,
    } satisfies PlaylistItem;
  }).filter((item): item is PlaylistItem => item !== null).slice(0, MAX_PLAYLIST_ITEMS);
  return { day, time, level, items };
}

function panelButtonClass(active: boolean): string {
  return `min-w-0 rounded-md py-2 ${active ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:bg-slate-800 hover:text-slate-100'}`;
}

export default function App() {
  const [tab, setTab] = useState<Tab>('today');
  const [workouts, setWorkouts] = useState<WorkoutRow[]>([]);
  const [logs, setLogs] = useState<WorkoutLog[]>([]);
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
  const [toasts, setToasts] = useState<Toast[]>([]);

  const refreshWorkouts = useCallback(async () => setWorkouts(await getAll<WorkoutRow>(STORES.workouts)), []);
  const refreshLogs = useCallback(async () => setLogs(await getAll<WorkoutLog>(STORES.logs)), []);
  const addToast = useCallback((message: string) => {
    const id = Date.now() + Math.random();
    setToasts((current) => [...current, { id, message }]);
    window.setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), 4500);
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
      await Promise.all([refreshWorkouts(), refreshLogs()]);
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
      const drained = await drainPendingWatchLogs();
      if (drained && !disposed) await refreshLogs();
    }
    void initialize();
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        void drainPendingWatchLogs().then((drained) => { if (drained) return refreshLogs(); });
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => { disposed = true; document.removeEventListener('visibilitychange', onVisible); };
  }, [refreshLogs, refreshWorkouts]);

  useEffect(() => {
    const notified = new Set<number>();
    let notifiedDate = todayDateKey();
    const checkScheduleAgainstNow = () => {
      if (todayDateKey() !== notifiedDate) { notifiedDate = todayDateKey(); notified.clear(); }
      const day = todayName().toLowerCase();
      workouts.filter((row) => row.day.toLowerCase() === day).forEach((row) => {
        if (row.id == null || notified.has(row.id) || !withinTolerance(row.time, currentHHMM())) return;
        notified.add(row.id);
        const title = `Workout Time – ${row.exercise}`;
        if ('Notification' in window && Notification.permission === 'granted') new Notification(title);
        else addToast(title);
      });
    };
    checkScheduleAgainstNow();
    const timer = window.setInterval(checkScheduleAgainstNow, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [addToast, workouts]);

  const todayWorkouts = useMemo(() => workouts
    .filter((row) => row.day.toLowerCase() === todayName().toLowerCase())
    .sort((left, right) => left.time.localeCompare(right.time)), [workouts]);

  const todayStatuses = useMemo(() => {
    const statuses = new Map<number, WorkoutLog['status']>();
    logs.forEach((log) => {
      if (log.date.startsWith(todayDateKey()) && log.workoutRowId != null) statuses.set(log.workoutRowId, log.status);
    });
    return statuses;
  }, [logs]);

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
  const scheduledCurrentQuestRows = useMemo(() => {
    if (!questState) return [];
    return workouts.filter((row) => row.questId === questState.questId && row.questDayIndex === questState.nextDayIndex);
  }, [questState, workouts]);

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

  const inHistoryRange = useCallback((value: string) => {
    if (historyRange === 'all') return true;
    const date = new Date(value);
    const now = new Date();
    return !Number.isNaN(date.getTime()) && date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth();
  }, [historyRange]);

  const historyLogs = useMemo(() => logs.filter((log) => inHistoryRange(log.date)), [inHistoryRange, logs]);
  const historyWorkoutDays = useMemo(() => new Set(historyLogs.map((log) => localDateKey(log.date))).size, [historyLogs]);
  const historyDoneCount = useMemo(() => historyLogs.filter((item) => item.status === 'done').length, [historyLogs]);
  const historySkippedCount = useMemo(() => historyLogs.filter((item) => item.status === 'skipped').length, [historyLogs]);
  const historyCompletionPercent = historyLogs.length ? Math.round((historyDoneCount / historyLogs.length) * 100) : 0;
  const historyDateGroups = useMemo(() => {
    const groups = new Map<string, HistoryDateGroup>();
    historyLogs.forEach((log) => {
      const key = localDateKey(log.date);
      const current = groups.get(key) ?? { key, label: formatHistoryDate(log.date), logs: [], done: 0, skipped: 0 };
      current.logs.push(log);
      current[log.status] += 1;
      groups.set(key, current);
    });
    return [...groups.values()]
      .map((group) => ({ ...group, logs: group.logs.slice().sort((left, right) => right.date.localeCompare(left.date)) }))
      .sort((left, right) => right.key.localeCompare(left.key));
  }, [historyLogs]);
  const historyQuestCompletions = useMemo(() => (questState?.completedDays ?? [])
    .filter((day) => inHistoryRange(day.completedAt))
    .slice()
    .sort((left, right) => right.completedAt.localeCompare(left.completedAt)), [inHistoryRange, questState]);
  const historyQuestPercent = questState && activeQuestTemplate
    ? Math.round((questState.completedDays.length / questTotalDays(activeQuestTemplate)) * 100)
    : 0;

  const historyByExercise = useMemo(() => {
    const result = new Map<string, { done: number; skipped: number }>();
    historyLogs.forEach((log) => {
      const entry = result.get(log.exercise) ?? { done: 0, skipped: 0 };
      entry[log.status] += 1;
      result.set(log.exercise, entry);
    });
    return [...result.entries()].sort((left, right) => (right[1].done + right[1].skipped) - (left[1].done + left[1].skipped));
  }, [historyLogs]);

  async function logExercise(row: WorkoutRow, status: WorkoutLog['status']) {
    if (row.id == null) return;
    await addRecord(STORES.logs, { schemaVersion: SCHEMA_VERSION, date: new Date().toISOString(), exercise: row.exercise, status, workoutRowId: row.id } satisfies WorkoutLog);
    const latestLogs = await getAll<WorkoutLog>(STORES.logs);
    setLogs(latestLogs);
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
    const invalid = draft.items.some((item) => !Number.isInteger(item.sets) || item.sets <= 0 || !item.reps.trim() || !Number.isInteger(item.rest) || item.rest < 0);
    if (!TIME_RE.test(draft.time) || invalid) return setPlaylistResult({ error: true, message: 'Fix invalid day, time, sets, reps, or rest values before saving.' });
    const existingKeys = new Set(workouts.map((row) => `${row.day}|${row.time}|${row.exercise.toLowerCase()}`));
    const additions = draft.items.filter((item) => !existingKeys.has(`${draft.day}|${draft.time}|${item.name.toLowerCase()}`)).map((item) => ({
      schemaVersion: SCHEMA_VERSION, day: draft.day, time: draft.time, exercise: item.name, sets: item.sets, reps: item.reps.trim(), rest: item.rest,
    } satisfies WorkoutRow));
    for (const row of additions) await addRecord(STORES.workouts, row);
    const schedule = await getAll<WorkoutRow>(STORES.workouts);
    await pushScheduleToNative(schedule);
    await refreshWorkouts();
    setPlaylistResult({ error: false, message: additions.length ? `Added ${additions.length} exercise${additions.length === 1 ? '' : 's'} to ${draft.day} at ${draft.time}.` : 'This playlist is already on the schedule at that day and time.' });
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
    setQuestResult({ error: false, message: additions.length ? `Scheduled ${dayLabel} for ${todayName()} at ${questState.scheduledTime}.` : `${dayLabel} is already scheduled.` });
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
    <div className="app-shell min-h-screen bg-slate-950 pb-24 text-slate-100">
      <div className="app-toast-layer pointer-events-none fixed inset-x-0 top-0 z-50 flex flex-col items-center gap-2 p-3">
        {toasts.map((toast) => <div key={toast.id} className="max-w-sm rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-sm shadow-lg">{toast.message}</div>)}
      </div>
      <header className="app-header sticky top-0 z-40 flex items-center justify-between border-b border-slate-800 bg-slate-950/90 px-4 py-3 backdrop-blur">
        <h1 className="text-lg font-bold">Workout Tracker</h1>
        {notificationPermission === 'default' && <button type="button" onClick={() => void requestNotificationPermission()} className="rounded-md bg-slate-800 px-2 py-1 text-xs text-slate-300 hover:bg-slate-700">Enable reminders</button>}
      </header>
      <main className="mx-auto max-w-md space-y-6 px-4 pt-4">
        <nav className="grid grid-cols-5 gap-1 rounded-lg bg-slate-900 p-1 text-xs font-medium" aria-label="Workout views">
          {([['today', 'Today'], ['quests', 'Quests'], ['library', 'Library'], ['import', 'Import'], ['history', 'History']] as const).map(([value, label]) => (
            <button key={value} type="button" onClick={() => setTab(value)} className={panelButtonClass(tab === value)}>{label}</button>
          ))}
        </nav>

        {tab === 'today' && <section className="space-y-3">
          <div className="flex items-baseline justify-between"><h2 className="text-base font-semibold text-slate-200">Today's Workout</h2><span className="text-xs text-slate-500">{todayName()}</span></div>
          {todayWorkouts.length === 0 ? <p className="py-10 text-center text-sm text-slate-500">No exercises scheduled for today. Import a CSV to get started.</p> : <div className="space-y-2">
            {todayWorkouts.map((row) => {
              const status = row.id == null ? undefined : todayStatuses.get(row.id);
              return <div key={row.id} className={`flex items-center gap-3 rounded-lg border border-slate-800 bg-slate-900 p-3 ${status === 'done' ? 'opacity-60' : ''}`}>
                <div className="min-w-0 flex-1"><p className={`truncate font-medium ${status === 'done' ? 'text-slate-500 line-through' : 'text-slate-100'}`}>{row.exercise}</p><p className="text-xs text-slate-500">{row.time} · {row.sets} × {row.reps} · rest {row.rest}s</p></div>
                <div className="flex shrink-0 gap-1.5"><button type="button" onClick={() => void logExercise(row, 'done')} className={`rounded-md px-2.5 py-1.5 text-xs font-medium ${status === 'done' ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-300 hover:bg-emerald-600 hover:text-white'}`}>{status === 'done' ? 'Done' : 'Mark as Done'}</button><button type="button" onClick={() => void logExercise(row, 'skipped')} className={`rounded-md px-2.5 py-1.5 text-xs font-medium ${status === 'skipped' ? 'bg-slate-700 text-white' : 'bg-slate-800 text-slate-400 hover:bg-slate-700'}`}>Skip</button></div>
              </div>;
            })}
          </div>}
        </section>}

        {tab === 'quests' && <section className="space-y-5">
          <div className="flex items-baseline justify-between gap-3">
            <h2 className="text-base font-semibold text-slate-200">Daily Quest</h2>
            {questState && activeQuestTemplate && <span className="text-xs text-slate-500">{questState.completedDays.length}/{questTotalDays(activeQuestTemplate)}</span>}
          </div>

          {!activeQuestTemplate ? <p className="py-10 text-center text-sm text-slate-500">No quest definitions are available.</p> : !questState ? <div className="space-y-4">
            <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
              <div className="mb-3 flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="text-lg font-semibold">{activeQuestTemplate.title}</h3>
                  <p className="mt-1 text-sm leading-relaxed text-slate-400">{activeQuestTemplate.description}</p>
                </div>
                <span className="shrink-0 rounded border border-emerald-700 px-2 py-1 text-xs text-emerald-300">{activeQuestTemplate.durationWeeks}w</span>
              </div>
              <div className="grid grid-cols-3 gap-1 rounded-md border border-slate-800 bg-slate-950 p-1 text-xs font-medium">
                {LEVELS.map((level) => (
                  <button
                    key={level}
                    type="button"
                    onClick={() => void saveDraft({ ...draft, level })}
                    className={`rounded px-2 py-2 ${draft.level === level ? 'bg-emerald-500 text-slate-950' : 'text-slate-400 hover:bg-slate-800'}`}
                  >
                    {LEVEL_LABELS[level]}
                  </button>
                ))}
              </div>
              <button type="button" onClick={() => void startQuest(activeQuestTemplate)} className="mt-4 w-full rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400">Start quest</button>
            </div>
            <p className="text-xs leading-relaxed text-slate-500">{activeQuestTemplate.safetyNote}</p>
            <div className="flex flex-wrap gap-2 text-xs">
              {activeQuestTemplate.evidenceBasis.map((url) => <a key={url} href={url} target="_blank" rel="noreferrer" className="rounded border border-slate-800 px-2 py-1 text-emerald-400 hover:border-emerald-700">Evidence</a>)}
            </div>
          </div> : <div className="space-y-4">
            <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="text-lg font-semibold">{activeQuestTemplate.title}</h3>
                  <p className="mt-1 text-sm text-slate-400">{questState.status === 'completed' ? 'Quest complete' : `Week ${questWeekNumber(questState, activeQuestTemplate)} · Day ${questTemplateDayNumber(questState, activeQuestTemplate)}`}</p>
                </div>
                <span className="shrink-0 rounded border border-slate-700 px-2 py-1 text-xs text-slate-300">{LEVEL_LABELS[questState.level]}</span>
              </div>
              <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-800">
                <div className="h-full bg-emerald-500" style={{ width: `${Math.round((questState.completedDays.length / questTotalDays(activeQuestTemplate)) * 100)}%` }} />
              </div>
            </div>

            {questState.status === 'active' && <>
              <div className="grid grid-cols-3 gap-1 rounded-lg border border-slate-800 bg-slate-900 p-1 text-xs font-medium">
                {LEVELS.map((level) => (
                  <button
                    key={level}
                    type="button"
                    onClick={() => void saveQuestState({ ...questState, level })}
                    className={`rounded-md px-2 py-2 text-center ${questState.level === level ? 'bg-emerald-500 text-slate-950' : 'text-slate-400 hover:bg-slate-800'}`}
                  >
                    {LEVEL_LABELS[level]}
                  </button>
                ))}
              </div>

              <label className="block text-xs text-slate-500">
                Start time
                <input
                  type="time"
                  value={questState.scheduledTime}
                  onChange={(event) => void saveQuestState({ ...questState, scheduledTime: event.target.value })}
                  className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </label>

              <div className="space-y-2">
                <div className="flex items-baseline justify-between">
                  <h3 className="text-sm font-semibold text-slate-200">{currentQuestRows[0]?.row.dayLabel ?? 'Current Day'}</h3>
                  <span className="text-xs text-slate-500">{scheduledCurrentQuestRows.length ? 'Scheduled' : 'Not scheduled'}</span>
                </div>
                {currentQuestRows.length === 0 ? <p className="rounded-lg border border-rose-900 bg-rose-950/30 p-3 text-sm text-rose-300">This quest day could not be resolved from the local catalog.</p> : currentQuestRows.map(({ row, exercise }) => (
                  <div key={`${row.dayNumber}-${row.sequence}`} className="grid grid-cols-[1.5rem_1fr] gap-3 rounded-lg border border-slate-800 bg-slate-900 p-3">
                    <span className="grid size-6 place-items-center rounded bg-slate-800 text-xs text-slate-400">{row.sequence}</span>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{exercise.displayName}</p>
                      <p className="text-xs text-slate-500">{row.progressionGroup} · {row.sets} x {row.reps} · rest {row.rest}s</p>
                    </div>
                  </div>
                ))}
              </div>

              <button type="button" disabled={!currentQuestRows.length} onClick={() => void saveQuestDayToSchedule()} className="w-full rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400 disabled:cursor-not-allowed disabled:opacity-40">Add today's quest to schedule</button>
              {questResult && <p className={`rounded-md border p-3 text-sm ${questResult.error ? 'border-rose-900 bg-rose-950/40 text-rose-300' : 'border-emerald-900 bg-emerald-950/40 text-emerald-300'}`}>{questResult.message}</p>}
            </>}

            {questState.completedDays.length > 0 && <div className="space-y-2">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Completed Quest Days</h3>
              {questState.completedDays.slice().reverse().map((day) => (
                <div key={day.dayIndex} className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm">
                  <span className="min-w-0 truncate">{day.dayLabel}</span>
                  <span className="shrink-0 text-xs text-slate-500">{LEVEL_LABELS[day.level]}</span>
                </div>
              ))}
            </div>}

            <p className="border-t border-slate-800 pt-4 text-xs leading-relaxed text-slate-500">{activeQuestTemplate.safetyNote}</p>
          </div>}
        </section>}

        {tab === 'library' && <section className="space-y-5">
          <div className="space-y-3">
            <div className="flex items-baseline justify-between gap-3">
              <h2 className="text-base font-semibold text-slate-200">Exercise Library</h2>
              <span className="text-xs text-slate-500">{filteredCatalog.length} shown</span>
            </div>

            <div className="grid grid-cols-3 gap-1 rounded-lg border border-slate-800 bg-slate-900 p-1 text-xs font-medium">
              {LEVELS.map((level) => (
                <button
                  key={level}
                  type="button"
                  onClick={() => void saveDraft({ ...draft, level })}
                  className={`rounded-md px-2 py-2 text-center transition ${draft.level === level ? 'bg-emerald-500 text-slate-950' : 'text-slate-400 hover:bg-slate-800 hover:text-slate-100'}`}
                >
                  <span className="block truncate">{LEVEL_LABELS[level]}</span>
                  <span className="block text-[10px] opacity-70">{levelCounts[level] ?? 0}</span>
                </button>
              ))}
            </div>

            <div className="grid grid-cols-[1fr_auto] gap-2">
              <label className="min-w-0">
                <span className="sr-only">Search exercises</span>
                <input
                  type="search"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Search exercises"
                  className="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm placeholder:text-slate-600 focus:border-emerald-500 focus:outline-none"
                />
              </label>
              <label>
                <span className="sr-only">Filter by category</span>
                <select
                  value={category}
                  onChange={(event) => setCategory(event.target.value)}
                  className="h-full rounded-md border border-slate-700 bg-slate-900 px-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none"
                >
                  <option value="all">All groups</option>
                  {categories.map((item) => <option key={item} value={item}>{item}</option>)}
                </select>
              </label>
            </div>

            <label className="inline-flex items-center gap-2 text-xs text-slate-400">
              <input
                type="checkbox"
                checked={featuredOnly}
                onChange={(event) => setFeaturedOnly(event.target.checked)}
                className="size-4 accent-emerald-500"
              />
              Common movements only
            </label>

            {filteredCatalog.length === 0 ? <p className="py-8 text-center text-sm text-slate-500">No exercises match these filters.</p> : <div className="max-h-[48vh] space-y-2 overflow-y-auto pr-1">
              {filteredCatalog.map((item) => {
                const addedIndex = draft.items.findIndex((entry) => entry.sourceId === item.sourceId);
                const added = addedIndex >= 0;
                const prescription = defaultPrescriptionFor(item, draft.level);
                return (
                  <div key={item.sourceId} className={`grid grid-cols-[1fr_auto] gap-3 rounded-lg border p-3 transition ${added ? 'border-emerald-700 bg-emerald-950/20' : 'border-slate-800 bg-slate-900 hover:border-slate-700'}`}>
                    <div className="min-w-0 space-y-1">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <p className="min-w-0 truncate text-sm font-medium">{item.displayName}</p>
                        <span className="rounded border border-slate-700 px-1.5 py-0.5 text-[10px] uppercase text-slate-400">{LEVEL_LABELS[item.minimumLevel]}</span>
                        {item.featured && <span className="rounded border border-emerald-700 px-1.5 py-0.5 text-[10px] uppercase text-emerald-300">Common</span>}
                      </div>
                      <p className="truncate text-xs text-slate-500">{item.category} · {item.primaryMuscles.length ? item.primaryMuscles.join(', ') : item.category}</p>
                      <p className="truncate text-xs text-slate-600">{item.equipment.join(', ') || 'No equipment listed'} · {prescription.sets} x {prescription.reps} · rest {prescription.rest}s</p>
                      <a href={item.sourceUrl} target="_blank" rel="noreferrer" className="inline-flex text-xs text-emerald-400 hover:text-emerald-300">Source</a>
                    </div>
                    <button
                      type="button"
                      disabled={added}
                      onClick={() => void addCatalogExercise(item.sourceId)}
                      className={`h-9 min-w-12 shrink-0 rounded-md px-3 text-xs font-semibold ${added ? 'bg-emerald-500 text-slate-950' : 'bg-indigo-600 text-white hover:bg-indigo-500'}`}
                    >
                      {added ? `#${addedIndex + 1}` : 'Add'}
                    </button>
                  </div>
                );
              })}
            </div>}
          </div>

          <div className="space-y-3 border-t border-slate-800 pt-5">
            <div className="flex items-baseline justify-between gap-3">
              <h2 className="text-base font-semibold text-slate-200">Workout Playlist</h2>
              <span className="text-xs text-slate-500">{draft.items.length}/{MAX_PLAYLIST_ITEMS}</span>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <label className="text-xs text-slate-500">
                Day
                <select value={draft.day} onChange={(event) => void saveDraft({ ...draft, day: event.target.value as Weekday })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none">
                  {WEEKDAYS.map((day) => <option key={day}>{day}</option>)}
                </select>
              </label>
              <label className="text-xs text-slate-500">
                Start time
                <input type="time" value={draft.time} onChange={(event) => void saveDraft({ ...draft, time: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" />
              </label>
            </div>
            {draft.items.length === 0 ? <p className="rounded-lg border border-dashed border-slate-800 py-8 text-center text-sm text-slate-500">Add exercises from the library to build a workout.</p> : <div className="space-y-2">
              {draft.items.map((item, index) => (
                <div key={item.sourceId} className="space-y-2 rounded-lg border border-slate-800 bg-slate-900 p-3">
                  <div className="flex items-center gap-2">
                    <span className="grid size-6 shrink-0 place-items-center rounded bg-emerald-500 text-xs font-bold text-slate-950">{index + 1}</span>
                    <p className="min-w-0 flex-1 truncate text-sm font-medium">{item.name}</p>
                    <button type="button" disabled={index === 0} title="Move up" onClick={() => void reorderDraftItem(index, -1)} className="size-8 rounded-md text-slate-400 hover:bg-slate-800 disabled:opacity-30">↑</button>
                    <button type="button" disabled={index === draft.items.length - 1} title="Move down" onClick={() => void reorderDraftItem(index, 1)} className="size-8 rounded-md text-slate-400 hover:bg-slate-800 disabled:opacity-30">↓</button>
                    <button type="button" title="Remove" onClick={() => void saveDraft({ ...draft, items: draft.items.filter((_, itemIndex) => itemIndex !== index) })} className="size-8 rounded-md text-slate-400 hover:bg-rose-950 hover:text-rose-300">×</button>
                  </div>
                  <div className="grid grid-cols-[4.5rem_1fr_5rem] gap-2 pl-8">
                    <label className="text-[10px] uppercase text-slate-600">
                      Sets
                      <input type="number" min="1" max="99" value={item.sets} onChange={(event) => void updateDraftItem(index, { sets: Number(event.target.value) })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm focus:border-emerald-500 focus:outline-none" />
                    </label>
                    <label className="text-[10px] uppercase text-slate-600">
                      Reps / duration
                      <input type="text" maxLength={30} value={item.reps} onChange={(event) => void updateDraftItem(index, { reps: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm focus:border-emerald-500 focus:outline-none" />
                    </label>
                    <label className="text-[10px] uppercase text-slate-600">
                      Rest
                      <input type="number" min="0" max="3600" value={item.rest} onChange={(event) => void updateDraftItem(index, { rest: Number(event.target.value) })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm focus:border-emerald-500 focus:outline-none" />
                    </label>
                  </div>
                </div>
              ))}
            </div>}
            <div className="grid grid-cols-[1fr_auto] gap-2">
              <button type="button" disabled={!draft.items.length} onClick={() => void savePlaylistToSchedule()} className="rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400 disabled:cursor-not-allowed disabled:opacity-40">Add to weekly schedule</button>
              <button type="button" disabled={!draft.items.length} onClick={() => { void saveDraft({ ...draft, items: [] }); setPlaylistResult(null); }} className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40">Clear</button>
            </div>
            {playlistResult && <p className={`rounded-md border p-3 text-sm ${playlistResult.error ? 'border-rose-900 bg-rose-950/40 text-rose-300' : 'border-emerald-900 bg-emerald-950/40 text-emerald-300'}`}>{playlistResult.message}</p>}
          </div>
          <p className="border-t border-slate-800 pt-4 text-xs leading-relaxed text-slate-600">Reviewed metadata from <a href="https://wger.de/" target="_blank" rel="noreferrer" className="text-emerald-400 hover:text-emerald-300">wger contributors</a>. License and source attribution are retained per exercise.</p>
        </section>}

        {tab === 'import' && <section className="space-y-4"><h2 className="text-base font-semibold text-slate-200">Import Schedule (CSV)</h2><p className="text-xs leading-relaxed text-slate-500">Columns: <code className="text-indigo-300">day,time,exercise,sets,reps,rest</code>. <code>day</code> must be a full weekday name, <code>time</code> is 24-hour <code>HH:MM</code>, and <code>rest</code> is seconds.</p><label className="block"><span className="sr-only">Choose CSV file</span><input type="file" accept=".csv,text/csv" onChange={(event) => { const file = event.target.files?.[0]; if (file) void importCsv(file); }} className="block w-full cursor-pointer text-sm text-slate-300 file:mr-3 file:rounded-lg file:border-0 file:bg-indigo-600 file:px-3 file:py-2 file:text-sm file:font-medium file:text-white hover:file:bg-indigo-500" /></label>{importResult && <div className="space-y-1 rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm"><p className="text-emerald-400">Imported {importResult.imported} exercise row{importResult.imported === 1 ? '' : 's'}.</p>{importResult.skipped > 0 && <p className="text-amber-400">Skipped {importResult.skipped} malformed row{importResult.skipped === 1 ? '' : 's'}.</p>}</div>}<div><h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Current schedule</h3>{workouts.length === 0 ? <p className="text-sm text-slate-400">No schedule imported yet.</p> : <div className="text-sm text-slate-400">{WEEKDAYS.filter((day) => workouts.some((row) => row.day === day)).map((day) => { const count = workouts.filter((row) => row.day === day).length; return <div key={day} className="flex justify-between py-0.5"><span>{day}</span><span className="text-slate-500">{count} exercise{count === 1 ? '' : 's'}</span></div>; })}</div>}</div></section>}

        {tab === 'history' && <section className="space-y-5">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-slate-200">History</h2>
            <div className="flex gap-1 rounded-lg bg-slate-900 p-1 text-xs font-medium">
              <button type="button" onClick={() => setHistoryRange('month')} className={`rounded-md px-3 py-1 ${historyRange === 'month' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400'}`}>This Month</button>
              <button type="button" onClick={() => setHistoryRange('all')} className={`rounded-md px-3 py-1 ${historyRange === 'all' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400'}`}>All Time</button>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2 text-center">
            <Metric label="Workout days" value={historyWorkoutDays} />
            <Metric label="Logged items" value={historyLogs.length} />
            <Metric label="Done" value={historyDoneCount} color="text-emerald-400" />
            <Metric label="Skipped" value={historySkippedCount} color="text-amber-400" />
          </div>

          <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
            <div className="mb-2 flex items-center justify-between gap-3">
              <h3 className="text-sm font-semibold text-slate-200">Completion</h3>
              <span className="text-xs text-slate-500">{historyCompletionPercent}% done</span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-slate-800">
              <div className="h-full bg-emerald-500" style={{ width: `${historyCompletionPercent}%` }} />
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex items-baseline justify-between">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Quest Progress</h3>
              {questState && activeQuestTemplate && <span className="text-xs text-slate-500">{questState.status}</span>}
            </div>
            {!questState || !activeQuestTemplate ? <p className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm text-slate-500">No quest progress yet.</p> : <div className="space-y-2 rounded-lg border border-slate-800 bg-slate-900 p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold">{activeQuestTemplate.title}</p>
                  <p className="text-xs text-slate-500">{questState.completedDays.length} of {questTotalDays(activeQuestTemplate)} days complete · {LEVEL_LABELS[questState.level]}</p>
                </div>
                <span className="shrink-0 text-sm font-semibold text-emerald-400">{historyQuestPercent}%</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-slate-800">
                <div className="h-full bg-emerald-500" style={{ width: `${historyQuestPercent}%` }} />
              </div>
              {historyQuestCompletions.length === 0 ? <p className="pt-1 text-xs text-slate-500">No completed quest days in this range.</p> : <div className="space-y-1 pt-1">
                {historyQuestCompletions.map((day) => (
                  <div key={day.dayIndex} className="flex items-center justify-between gap-3 text-xs">
                    <span className="min-w-0 truncate text-slate-300">{day.dayLabel}</span>
                    <span className="shrink-0 text-slate-500">{LEVEL_LABELS[day.level]} · {formatHistoryDate(day.completedAt)}</span>
                  </div>
                ))}
              </div>}
            </div>}
          </div>

          <div className="space-y-2">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Recent Activity</h3>
            {historyDateGroups.length === 0 ? <p className="rounded-lg border border-slate-800 bg-slate-900 p-6 text-center text-sm text-slate-500">No logged workouts in this range yet.</p> : historyDateGroups.map((group) => (
              <div key={group.key} className="rounded-lg border border-slate-800 bg-slate-900 p-3">
                <div className="mb-2 flex items-center justify-between gap-3 text-sm">
                  <span className="font-semibold text-slate-200">{group.label}</span>
                  <span className="text-xs text-slate-500">{group.done} done · {group.skipped} skipped</span>
                </div>
                <div className="space-y-1">
                  {group.logs.map((log) => (
                    <div key={log.id ?? `${log.date}-${log.exercise}-${log.status}`} className="grid grid-cols-[auto_1fr_auto] items-center gap-2 text-xs">
                      <span className={`size-2 rounded-full ${log.status === 'done' ? 'bg-emerald-400' : 'bg-amber-400'}`} />
                      <span className="min-w-0 truncate text-slate-300">{log.exercise}</span>
                      <span className="shrink-0 text-slate-600">{formatHistoryTime(log.date)}</span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>

          <div className="space-y-2">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Per-exercise Breakdown</h3>
            {historyByExercise.length === 0 ? <p className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm text-slate-500">No exercise breakdown available yet.</p> : historyByExercise.map(([exercise, stats]) => {
              const total = stats.done + stats.skipped;
              const donePercent = total ? Math.round((stats.done / total) * 100) : 0;
              return <div key={exercise} className="rounded-lg border border-slate-800 bg-slate-900 p-3">
                <div className="mb-1.5 flex justify-between gap-2 text-sm">
                  <span className="truncate font-medium">{exercise}</span>
                  <span className="shrink-0 text-slate-500">{stats.done} done · {stats.skipped} skipped</span>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-slate-800">
                  <div className="h-full bg-emerald-500" style={{ width: `${donePercent}%` }} />
                </div>
              </div>;
            })}
          </div>
        </section>}
      </main>
    </div>
  );
}

function Metric({ label, value, color = '' }: { label: string; value: number; color?: string }) {
  return <div className="rounded-lg border border-slate-800 bg-slate-900 py-3"><p className={`text-xl font-bold ${color}`}>{value}</p><p className="text-xs text-slate-500">{label}</p></div>;
}
