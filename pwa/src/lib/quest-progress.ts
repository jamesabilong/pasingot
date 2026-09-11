import { SCHEMA_VERSION, type ExerciseCatalogItem, type QuestHistory, type QuestState, type QuestTemplate, type QuestWorkoutRow, type WorkoutLog, type WorkoutRow } from '../types';
import { STORES, transact, updateRecord } from './db';
import { localDateKey, todayDateKey } from './history-stats';

export const QUEST_STATE_KEY = 'questState';
export const QUEST_HISTORY_KEY = 'questHistory';

export function belongsToQuestRun(row: WorkoutRow, state: QuestState): boolean {
  return row.questId === state.questId && row.questRunId === state.runId;
}

export function resolveQuestDay(rows: QuestWorkoutRow[], state: QuestState, template: QuestTemplate, catalog: Map<number, ExerciseCatalogItem>) {
  if (state.status !== 'active') return [];
  const dayNumber = ((state.nextDayIndex - 1) % template.daysPerWeek) + 1;
  const dayRows = rows.filter((row) => row.questId === state.questId && row.level === state.level && row.dayNumber === dayNumber);
  // A partial day must not become a smaller, apparently complete workout.
  if (!dayRows.length || dayRows.some((row) => !catalog.has(row.exerciseSourceId))) return [];
  return dayRows.sort((left, right) => left.sequence - right.sequence)
    .map((row) => ({ row, exercise: catalog.get(row.exerciseSourceId)! }));
}

export function completeQuestDay(state: QuestState, template: QuestTemplate, row: WorkoutRow, workouts: WorkoutRow[], logs: WorkoutLog[], date = todayDateKey(), now = new Date()): QuestState {
  if (state.schemaVersion !== SCHEMA_VERSION || state.status !== 'active' || template.questId !== state.questId
    || !belongsToQuestRun(row, state) || row.questDayIndex !== state.nextDayIndex
    || state.completedDays.some((day) => day.dayIndex === row.questDayIndex)) return state;
  const dayRows = workouts.filter((item) => belongsToQuestRun(item, state) && item.questDayIndex === state.nextDayIndex);
  if (!dayRows.length || !dayRows.every((item) => item.id != null && logs.some((log) => (
    log.workoutRowId === item.id && localDateKey(log.date) === date
    && new Date(log.date).getTime() >= new Date(state.startedAt).getTime()
    && (log.status === 'done' || log.status === 'skipped')
  )))) return state;
  const dayNumber = ((state.nextDayIndex - 1) % template.daysPerWeek) + 1;
  const nextDayIndex = state.nextDayIndex + 1;
  return {
    ...state,
    nextDayIndex,
    completedDays: [...state.completedDays, {
      dayIndex: state.nextDayIndex,
      dayNumber,
      dayLabel: row.questDayLabel ?? `Day ${dayNumber}`,
      level: row.questLevel ?? state.level,
      completedAt: now.toISOString(),
    }],
    status: nextDayIndex > template.durationWeeks * template.daysPerWeek ? 'completed' : 'active',
  };
}

export async function reconcileQuestDay(template: QuestTemplate, row: WorkoutRow, workouts: WorkoutRow[], logs: WorkoutLog[]): Promise<QuestState | null> {
  let changed: QuestState | null = null;
  await updateRecord<QuestState>(STORES.appState, QUEST_STATE_KEY, (state) => {
    if (!state) return state;
    const next = completeQuestDay(state, template, row, workouts, logs);
    if (next !== state) changed = next;
    return next;
  });
  return changed;
}

export async function archiveQuest(state: QuestState, template?: QuestTemplate): Promise<void> {
  await transact([STORES.appState, STORES.workouts], (transaction) => {
    const appState = transaction.objectStore(STORES.appState);
    const current = appState.get(QUEST_STATE_KEY);
    current.onsuccess = () => {
      const stored = current.result as QuestState | undefined;
      if (!stored || stored.questId !== state.questId || stored.startedAt !== state.startedAt) return;
      const history = appState.get(QUEST_HISTORY_KEY);
      history.onsuccess = () => {
        const entries = (history.result as QuestHistory | undefined)?.entries ?? [];
        const archivedTemplate = template ?? {
          schemaVersion: SCHEMA_VERSION, questId: stored.questId, title: stored.questId,
          description: 'Archived quest', durationWeeks: Math.max(1, stored.nextDayIndex), daysPerWeek: 1,
          safetyNote: 'Original template unavailable.', evidenceBasis: [],
        };
        appState.put({ key: QUEST_HISTORY_KEY, schemaVersion: SCHEMA_VERSION,
          entries: [...entries, { state: stored, template: archivedTemplate, archivedAt: new Date().toISOString() }],
        } satisfies QuestHistory);
        appState.delete(QUEST_STATE_KEY);
      };
      const cursor = transaction.objectStore(STORES.workouts).openCursor();
      cursor.onsuccess = () => {
        const entry = cursor.result;
        if (!entry) return;
        if (belongsToQuestRun(entry.value as WorkoutRow, stored)) entry.delete();
        entry.continue();
      };
    };
  });
}
