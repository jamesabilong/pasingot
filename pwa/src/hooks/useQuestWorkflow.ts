import { useCallback, useEffect, useMemo, useState } from 'react';
import { createCustomQuestDefinition, CUSTOM_QUESTS_KEY, normalizeCustomQuestCollection, type CustomQuestDraft } from '../lib/custom-quests';
import { getAll, getRecord, putRecord, updateRecord, STORES } from '../lib/db';
import { saveQuestSchedule } from '../lib/quest-scheduling';
import { pushScheduleToNative } from '../lib/native-bridge';
import { archiveQuest, belongsToQuestRun, QUEST_HISTORY_KEY, QUEST_STATE_KEY, reconcileQuestDay, resolveQuestDay, questTotalDays, questWeekNumber, questTemplateDayNumber } from '../lib/quest-progress';
import { calculatePlanProgress, estimateWorkoutDurationSeconds, formatEstimatedDuration, LEVELS, TIME_RE, todayName, workoutStatusesOnDate } from '../lib/workout-planning';
import type { ActiveWorkoutSession } from '../lib/workout-session';
import { SCHEMA_VERSION, type CustomQuestCollection, type CustomQuestDefinition, type ExerciseCatalogItem, type PlaylistDraft, type QuestHistory, type QuestState, type QuestTemplate, type QuestWorkoutRow, type WorkoutLog, type WorkoutRow } from '../types';

interface QuestWorkflowOptions {
  builtInQuestTemplates: QuestTemplate[];
  builtInQuestRows: QuestWorkoutRow[];
  catalog: ExerciseCatalogItem[];
  draft: PlaylistDraft;
  getPlaylistSnapshot?: () => Promise<PlaylistDraft>;
  workouts: WorkoutRow[];
  logs: WorkoutLog[];
  localToday: string;
  activeWorkoutSession: ActiveWorkoutSession | null;
  clearActiveWorkoutSession: () => Promise<void>;
  refreshWorkouts: () => Promise<void>;
  refreshUserData: () => Promise<void>;
}

export function useQuestWorkflow({
  builtInQuestTemplates, builtInQuestRows, catalog, draft, getPlaylistSnapshot, workouts, logs,
  localToday, activeWorkoutSession, clearActiveWorkoutSession,
  refreshWorkouts, refreshUserData,
}: QuestWorkflowOptions) {
  const [customQuestDefinitions, setCustomQuestDefinitions] = useState<CustomQuestDefinition[]>([]);
  const [questState, setQuestState] = useState<QuestState | null>(null);
  const [questHistory, setQuestHistory] = useState<QuestHistory['entries']>([]);
  const [questResult, setQuestResult] = useState<{ message: string; error: boolean } | null>(null);

  const refresh = useCallback(async (isCurrent: () => boolean = () => true) => {
    const definitions = normalizeCustomQuestCollection(await getRecord<CustomQuestCollection>(STORES.appState, CUSTOM_QUESTS_KEY));
    const state = await getRecord<QuestState>(STORES.appState, QUEST_STATE_KEY);
    const history = await getRecord<QuestHistory>(STORES.appState, QUEST_HISTORY_KEY);
    if (!isCurrent()) return;
    setCustomQuestDefinitions(definitions.quests);
    setQuestState(state?.schemaVersion === SCHEMA_VERSION ? state : null);
    setQuestHistory(history?.entries ?? []);
  }, []);

  const saveQuestState = useCallback(async (next: QuestState) => {
    setQuestState(next);
    await putRecord(STORES.appState, next);
  }, []);

  const catalogBySourceId = useMemo(() => new Map(catalog.map((item) => [item.sourceId, item])), [catalog]);
  const questTemplates = useMemo(() => [
    ...builtInQuestTemplates,
    ...customQuestDefinitions.map((definition) => definition.template),
  ], [builtInQuestTemplates, customQuestDefinitions]);
  const questRows = useMemo(() => [
    ...builtInQuestRows,
    ...customQuestDefinitions.flatMap((definition) => definition.rows),
  ], [builtInQuestRows, customQuestDefinitions]);
  const activeQuestTemplate = useMemo(() => (
    questState ? questTemplates.find((template) => template.questId === questState.questId) : questTemplates[0]
  ), [questState, questTemplates]);
  const activeQuestLevels = activeQuestTemplate?.availableLevels?.length ? activeQuestTemplate.availableLevels : LEVELS;
  const currentQuestDayNumber = questState && activeQuestTemplate && questState.status === 'active'
    ? questTemplateDayNumber(questState, activeQuestTemplate)
    : null;
  const currentQuestRows = useMemo(() => {
    if (!questState || !activeQuestTemplate || currentQuestDayNumber == null || questState.status !== 'active') return [];
    return resolveQuestDay(questRows, questState, activeQuestTemplate, catalogBySourceId);
  }, [activeQuestTemplate, catalogBySourceId, currentQuestDayNumber, questRows, questState]);
  const currentQuestEstimate = useMemo(() => (
    formatEstimatedDuration(estimateWorkoutDurationSeconds(currentQuestRows.map(({ row }) => row), questState?.level ?? 'beginner'))
  ), [currentQuestRows, questState?.level]);
  const scheduledCurrentQuestRows = useMemo(() => {
    if (!questState) return [];
    return workouts.filter((row) => belongsToQuestRun(row, questState) && row.questDayIndex === questState.nextDayIndex);
  }, [questState, workouts]);
  const currentQuestProgressRows = useMemo(() => (
    scheduledCurrentQuestRows.length
      ? scheduledCurrentQuestRows
      : currentQuestRows.map(({ row }, index) => ({ id: undefined, sequence: row.sequence, fallbackIndex: index }))
  ), [currentQuestRows, scheduledCurrentQuestRows]);
  const todayStatuses = useMemo(() => workoutStatusesOnDate(logs, localToday), [logs, localToday]);
  const currentQuestProgress = useMemo(() => (
    calculatePlanProgress(currentQuestProgressRows, todayStatuses)
  ), [currentQuestProgressRows, todayStatuses]);

  useEffect(() => {
    // Watch logs arrive through the native bridge while the app resumes.
    // Reconcile one representative row per quest day after those logs enter
    // IndexedDB; phone-button logs use this same path through the logs state.
    const candidates = new Map<string, WorkoutRow>();
    workouts.forEach((row) => {
      if (!questState || !belongsToQuestRun(row, questState) || row.questDayIndex !== questState.nextDayIndex || row.id == null) return;
      const hasLinkedLog = logs.some((log) => log.workoutRowId === row.id);
      if (hasLinkedLog) candidates.set(`${row.questId}:${row.questDayIndex}`, row);
    });
    candidates.forEach((row) => { void maybeCompleteQuestDay(row, logs); });
  }, [logs, questTemplates, workouts, questState, localToday]);

  async function startQuest(template: QuestTemplate) {
    const availableLevels = template.availableLevels?.length ? template.availableLevels : LEVELS;
    const next: QuestState = {
      key: QUEST_STATE_KEY,
      schemaVersion: SCHEMA_VERSION,
      questId: template.questId,
      runId: crypto.randomUUID(),
      level: availableLevels.includes(draft.level) ? draft.level : availableLevels[0],
      nextDayIndex: 1,
      scheduledTime: draft.time,
      startedAt: new Date().toISOString(),
      completedDays: [],
      status: 'active',
    };
    setQuestResult(null);
    await saveQuestState(next);
  }

  async function createCustomQuest(questDraft: CustomQuestDraft) {
    const playlist = getPlaylistSnapshot ? await getPlaylistSnapshot() : structuredClone(draft);
    const { definition, error } = createCustomQuestDefinition(questDraft, playlist, catalog);
    if (!definition || error) {
      setQuestResult({ error: true, message: error ?? 'Could not create the custom quest.' });
      return;
    }
    const saved = await updateRecord<CustomQuestCollection>(STORES.appState, CUSTOM_QUESTS_KEY, current => ({
      key: CUSTOM_QUESTS_KEY, schemaVersion: SCHEMA_VERSION,
      quests: [...normalizeCustomQuestCollection(current).quests, definition],
    }));
    setCustomQuestDefinitions(saved!.quests);
    setQuestResult({ error: false, message: `${definition.template.title} created from the ${playlist.items.length}-exercise Library playlist.` });
  }

  async function deleteCustomQuest(questId: string) {
    const definition = customQuestDefinitions.find((item) => item.questId === questId);
    if (!definition) return;
    if (questState?.questId === questId) {
      setQuestResult({ error: true, message: 'Leave this quest before deleting its template.' });
      return;
    }
    const scheduledRows = workouts.filter((row) => row.questId === questId).length;
    if (scheduledRows) {
      setQuestResult({ error: true, message: `This quest still has ${scheduledRows} saved schedule row${scheduledRows === 1 ? '' : 's'} and cannot be deleted.` });
      return;
    }
    if (!window.confirm(`Delete the ${definition.template.title} quest template?`)) return;
    const saved = await updateRecord<CustomQuestCollection>(STORES.appState, CUSTOM_QUESTS_KEY, current => ({
      key: CUSTOM_QUESTS_KEY, schemaVersion: SCHEMA_VERSION,
      quests: normalizeCustomQuestCollection(current).quests.filter(item => item.questId !== questId),
    }));
    setCustomQuestDefinitions(saved!.quests);
    setQuestResult({ error: false, message: 'Custom quest deleted.' });
  }

  async function leaveQuest() {
    if (!questState) return;
    const questRowIds = new Set(workouts.filter((row) => belongsToQuestRun(row, questState)).map((row) => row.id));
    if (activeWorkoutSession && ['active', 'resting', 'paused'].includes(activeWorkoutSession.status)
      && activeWorkoutSession.rowIds.some((id) => questRowIds.has(id))) {
      setQuestResult({ error: true, message: 'Finish or end the current workout before leaving this quest.' });
      return;
    }
    const action = 'Leave this quest and remove its scheduled exercises? Completed days and workout history will be kept in History.';
    if (!window.confirm(action)) return;
    await archiveQuest(questState, activeQuestTemplate);
    setQuestState(null);
    if (activeWorkoutSession?.rowIds.some((id) => questRowIds.has(id))) await clearActiveWorkoutSession();
    await refreshUserData();
    await pushScheduleToNative(await getAll<WorkoutRow>(STORES.workouts));
    setQuestResult(null);
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
      exercise: exercise.custom ? exercise.displayName : exercise.name,
      exerciseSourceId: exercise.sourceId,
      sets: row.sets,
      reps: row.reps,
      rest: row.rest,
      ...(row.circuit ? { circuit: { ...row.circuit } } : {}),
      loadWeight: row.loadWeight ?? null,
      loadUnit: row.loadWeight != null ? row.loadUnit ?? 'kg' : null,
      questId: questState.questId,
      questRunId: questState.runId,
      questDayIndex: questState.nextDayIndex,
      questDayLabel: dayLabel,
      questLevel: questState.level,
    } satisfies WorkoutRow));
    let saved;
    try {
      saved = await saveQuestSchedule(questState, proposedRows,
        Boolean(activeWorkoutSession && ['active', 'resting', 'paused'].includes(activeWorkoutSession.status)));
    } catch (error) {
      setQuestResult({ error: true, message: error instanceof Error ? error.message : 'Could not schedule the quest day.' });
      return;
    }
    let refreshFailed = false;
    try {
      refreshFailed = !(await pushScheduleToNative(saved.schedule));
      await refreshWorkouts();
    } catch { refreshFailed = true; }
    setQuestResult({ error: false, message: refreshFailed
      ? 'Quest day saved. Reopen Today to refresh the schedule and phone cache.'
      : saved.addedCount ? `Scheduled ${dayLabel} for ${todayName()} at ${questState.scheduledTime}. ${currentQuestEstimate}.` : `${dayLabel} is already scheduled.` });
  }

  async function maybeCompleteQuestDay(row: WorkoutRow, latestLogs: WorkoutLog[]) {
    if (!row.questId || row.questDayIndex == null) return;
    const template = questTemplates.find((item) => item.questId === row.questId);
    if (!template) return;
    const nextState = await reconcileQuestDay(template, row, workouts, latestLogs);
    if (!nextState) return;
    setQuestState(nextState);
    const completion = nextState.completedDays.at(-1)!;
    setQuestResult({ error: false, message: nextState.status === 'completed' ? `${template.title} completed.` : `${completion.dayLabel} completed. Next quest day is ready.` });
  }

  return {
    questState, questHistory, customQuestDefinitions, questTemplates,
    activeQuestTemplate, activeQuestLevels, currentQuestRows,
    currentQuestEstimate, scheduledCurrentQuestRows, currentQuestProgress,
    questResult,
    totalDays: activeQuestTemplate ? questTotalDays(activeQuestTemplate) : null,
    weekNumber: questState && activeQuestTemplate ? questWeekNumber(questState, activeQuestTemplate) : null,
    dayNumber: questState && activeQuestTemplate ? questTemplateDayNumber(questState, activeQuestTemplate) : null,
    refresh, saveQuestState, startQuest, createCustomQuest, deleteCustomQuest,
    leaveQuest, saveQuestDayToSchedule,
  };
}
