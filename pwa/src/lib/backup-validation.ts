import { SCHEMA_VERSION, WEEKDAYS } from '../types';
import { isCustomQuestDefinition } from './custom-quests';
import { isExerciseLevel, isWeightUnit, TIME_RE, validLoadWeight } from './workout-planning';

type RecordValue = Record<string, unknown>;
const object = (value: unknown): value is RecordValue => value !== null && typeof value === 'object' && !Array.isArray(value);
const text = (value: unknown): value is string => typeof value === 'string' && Boolean(value.trim());
const integer = (value: unknown, min = 0): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value >= min;
const timestamp = (value: unknown): value is string => text(value) && Number.isFinite(Date.parse(value));
const strings = (value: unknown): value is string[] => Array.isArray(value) && value.every((item) => typeof item === 'string');
const finite = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && value >= 0;
const identity = (value: unknown) => value == null || (typeof value === 'number' && Number.isSafeInteger(value) && value !== 0);
const load = (row: RecordValue) => row.loadWeight == null || (typeof row.loadWeight === 'number' && validLoadWeight(row.loadWeight) != null && isWeightUnit(row.loadUnit));
const prescription = (row: RecordValue) => integer(row.sets, 1) && text(row.reps) && integer(row.rest) && load(row);

function questState(row: RecordValue): boolean {
  return text(row.questId) && (row.runId == null || text(row.runId)) && isExerciseLevel(row.level)
    && integer(row.nextDayIndex, 1) && typeof row.scheduledTime === 'string' && (row.scheduledTime === '' || TIME_RE.test(row.scheduledTime))
    && timestamp(row.startedAt) && ['active', 'completed'].includes(String(row.status))
    && Array.isArray(row.completedDays) && row.completedDays.every((day) => object(day)
      && integer(day.dayIndex, 1) && integer(day.dayNumber, 1) && text(day.dayLabel)
      && isExerciseLevel(day.level) && timestamp(day.completedAt));
}

function questTemplate(row: unknown): boolean {
  return object(row) && row.schemaVersion === SCHEMA_VERSION && text(row.questId) && text(row.title)
    && text(row.description) && text(row.safetyNote) && integer(row.durationWeeks, 1)
    && integer(row.daysPerWeek, 1) && strings(row.evidenceBasis);
}

function appState(row: RecordValue): boolean {
  if (!text(row.key)) return false;
  switch (row.key) {
    case 'customQuests':
      return Array.isArray(row.quests) && row.quests.every(isCustomQuestDefinition)
        && new Set(row.quests.map((quest) => quest.questId)).size === row.quests.length;
    case 'questState': return questState(row);
    case 'questHistory':
      return Array.isArray(row.entries) && row.entries.every((entry) => object(entry)
        && object(entry.state) && questState(entry.state) && questTemplate(entry.template) && timestamp(entry.archivedAt));
    case 'playlistDraft':
      // Drafts can be exported halfway through editing. Check their shape;
      // normalizeDraft supplies defaults when the app restores the form.
      return WEEKDAYS.includes(row.day as typeof WEEKDAYS[number]) && typeof row.time === 'string'
        && isExerciseLevel(row.level) && Array.isArray(row.items) && row.items.every((item) => object(item)
          && identity(item.sourceId) && item.sourceId != null && typeof item.name === 'string'
          && typeof item.sets === 'number' && Number.isFinite(item.sets) && typeof item.reps === 'string'
          && typeof item.rest === 'number' && Number.isFinite(item.rest)
          && (item.loadWeight == null || (typeof item.loadWeight === 'number' && Number.isFinite(item.loadWeight)))
          && (item.loadUnit == null || isWeightUnit(item.loadUnit)));
    case 'activeWorkoutSession':
      return timestamp(row.planDate) && Array.isArray(row.rowIds) && row.rowIds.every((id) => integer(id, 1))
        && ['active', 'resting', 'paused', 'completed', 'ended'].includes(String(row.status))
        && integer(row.exerciseIndex) && integer(row.currentSet, 1) && finite(row.accumulatedElapsedMillis)
        && (row.elapsedStartedAtEpochMillis == null || finite(row.elapsedStartedAtEpochMillis))
        && (row.restUntilEpochMillis == null || finite(row.restUntilEpochMillis))
        && (row.pausedRestRemainingSeconds == null || finite(row.pausedRestRemainingSeconds))
        && (row.setInputs == null || (object(row.setInputs) && Object.values(row.setInputs).every((input) => object(input)
          && typeof input.actualReps === 'string' && typeof input.loadWeight === 'string' && isWeightUnit(input.loadUnit))));
    case 'healthConnectSettings': return typeof row.enabled === 'boolean';
    case 'healthConnectPendingWrites': {
      const validPayload = (payload: unknown, kind: unknown): boolean => {
        if (!object(payload) || !text(payload.clientRecordId)) return false;
        if (kind === 'body-weight-delete') return true;
        if (!finite(payload.clientRecordVersion)) return false;
        if (kind === 'body-weight-write') return timestamp(payload.time) && finite(payload.kilograms) && payload.kilograms > 0;
        return kind === 'workout-write' && text(payload.title) && timestamp(payload.startTime) && timestamp(payload.endTime);
      };
      return row.operations != null
        ? Array.isArray(row.operations) && row.operations.every((operation) => object(operation) && validPayload(operation.payload, operation.kind))
        : Array.isArray(row.writes) && row.writes.every((payload) => validPayload(payload, 'workout-write'));
    }
    default: return true;
  }
}

const validators: Record<string, (row: RecordValue) => boolean> = {
  workouts: (row) => WEEKDAYS.includes(row.day as typeof WEEKDAYS[number]) && text(row.time) && TIME_RE.test(row.time)
    && text(row.exercise) && identity(row.exerciseSourceId) && prescription(row)
    && (row.questId == null || (text(row.questId) && integer(row.questDayIndex, 1)))
    && (row.questRunId == null || text(row.questRunId)),
  logs: (row) => timestamp(row.date) && text(row.exercise) && identity(row.exerciseSourceId)
    && ['done', 'skipped'].includes(String(row.status)) && (row.workoutRowId == null || integer(row.workoutRowId, 1)),
  sessionEvents: (row) => text(row.workoutEntryId) && timestamp(row.workoutDate) && timestamp(row.timestamp)
    && ['completed', 'ended'].includes(String(row.eventType)) && typeof row.stopReason === 'string'
    && finite(row.elapsedSeconds) && integer(row.exerciseIndex) && integer(row.currentSet, 1)
    && integer(row.totalExercises) && (row.currentExercise == null || typeof row.currentExercise === 'string'),
  setLogs: (row) => timestamp(row.date) && text(row.exercise) && identity(row.exerciseSourceId)
    && integer(row.setNumber, 1) && text(row.plannedReps) && text(row.actualReps) && load(row)
    && (row.workoutRowId == null || integer(row.workoutRowId, 1)),
  bodyMetrics: (row) => timestamp(row.date) && finite(row.weight) && row.weight > 0 && isWeightUnit(row.unit)
    && (row.note == null || typeof row.note === 'string'),
  customExercises: (row) => typeof row.sourceId === 'number' && Number.isSafeInteger(row.sourceId) && row.sourceId < 0
    && row.custom === true && text(row.name) && text(row.displayName) && text(row.category)
    && strings(row.primaryMuscles) && strings(row.secondaryMuscles) && strings(row.equipment)
    && (row.aliases == null || strings(row.aliases)) && isExerciseLevel(row.minimumLevel)
    && typeof row.progressionGroup === 'string' && typeof row.progressionLevel === 'string'
    && timestamp(row.createdAt) && timestamp(row.updatedAt),
  appState,
};

export function validateBackupStores(stores: Record<string, unknown>): void {
  for (const [name, validate] of Object.entries(validators)) {
    const records = stores[name];
    if (!Array.isArray(records)) throw new Error(`Backup file is missing ${name}.`);
    const keys = new Set<unknown>();
    for (const [index, row] of records.entries()) {
      if (!object(row) || row.schemaVersion !== SCHEMA_VERSION || !validate(row)
        || (row.id != null && !integer(row.id, 1))) {
        throw new Error(`Backup contains an invalid ${name} record at row ${index + 1}. Nothing was restored.`);
      }
      const key = name === 'appState' ? row.key : name === 'customExercises' ? row.sourceId : row.id;
      if (key != null && keys.has(key)) throw new Error(`Backup contains duplicate ${name} IDs. Nothing was restored.`);
      if (key != null) keys.add(key);
    }
  }
  const customIds = new Set((stores.customExercises as RecordValue[]).map((exercise) => exercise.sourceId));
  const state = stores.appState as RecordValue[];
  const collection = state.find((row) => row.key === 'customQuests');
  if (collection) {
    for (const definition of collection.quests as Array<{ rows: Array<{ exerciseSourceId: number }> }>) {
      if (definition.rows.some((row) => row.exerciseSourceId < 0 && !customIds.has(row.exerciseSourceId))) {
        throw new Error('Backup has a custom quest referencing a missing custom exercise. Nothing was restored.');
      }
    }
  }
  const session = state.find((row) => row.key === 'activeWorkoutSession');
  const scheduleIds = new Set((stores.workouts as RecordValue[]).map((row) => row.id));
  if (session && ['active', 'resting', 'paused'].includes(String(session.status)) && (session.rowIds as number[]).some((id) => !scheduleIds.has(id))) {
    throw new Error('Backup has an active session referencing a missing schedule row. Nothing was restored.');
  }
}
