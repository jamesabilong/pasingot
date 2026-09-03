import Papa from 'papaparse';
import { SCHEMA_VERSION, type QuestTemplate, type QuestWorkoutRow } from '../types';
import { isExerciseLevel } from './workout-planning';

function parsePositiveInt(value: unknown): number {
  const parsed = Number.parseInt(String(value ?? '').trim(), 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : Number.NaN;
}

function parseNonNegativeInt(value: unknown): number {
  const parsed = Number.parseInt(String(value ?? '').trim(), 10);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : Number.NaN;
}

export function parseQuestTemplatesCsv(csvText: string): QuestTemplate[] {
  const parsed = Papa.parse<Record<string, string>>(csvText, { header: true, skipEmptyLines: true });
  if (parsed.errors.length) throw new Error(`Quest template CSV has ${parsed.errors.length} parse error(s).`);

  const rows = parsed.data.map((raw) => {
    const questId = String(raw.quest_id ?? '').trim();
    const title = String(raw.title ?? '').trim();
    const description = String(raw.description ?? '').trim();
    const durationWeeks = parsePositiveInt(raw.duration_weeks);
    const daysPerWeek = parsePositiveInt(raw.days_per_week);
    const safetyNote = String(raw.safety_note ?? '').trim();
    if (Number(raw.schema_version) !== SCHEMA_VERSION || !questId || !title || !description || !safetyNote) return null;
    if (!Number.isInteger(durationWeeks) || !Number.isInteger(daysPerWeek)) return null;
    return {
      schemaVersion: SCHEMA_VERSION,
      questId,
      title,
      description,
      durationWeeks,
      daysPerWeek,
      evidenceBasis: String(raw.evidence_basis ?? '').split('|').map((part) => part.trim()).filter(Boolean),
      safetyNote,
    } satisfies QuestTemplate;
  }).filter((row): row is QuestTemplate => row !== null);

  if (!rows.length || rows.length !== parsed.data.length) throw new Error('Quest template validation rejected one or more rows.');
  if (new Set(rows.map((row) => row.questId)).size !== rows.length) throw new Error('Quest templates contain duplicate IDs.');
  return rows;
}

export function parseQuestWorkoutsCsv(csvText: string): QuestWorkoutRow[] {
  const parsed = Papa.parse<Record<string, string>>(csvText, { header: true, skipEmptyLines: true });
  if (parsed.errors.length) throw new Error(`Quest workout CSV has ${parsed.errors.length} parse error(s).`);

  const rows = parsed.data.map((raw) => {
    const questId = String(raw.quest_id ?? '').trim();
    const level = String(raw.level ?? '').trim();
    const dayNumber = parsePositiveInt(raw.day_number);
    const dayLabel = String(raw.day_label ?? '').trim();
    const sequence = parsePositiveInt(raw.sequence);
    const progressionGroup = String(raw.progression_group ?? '').trim();
    const exerciseSourceId = parsePositiveInt(raw.exercise_source_id);
    const sets = parsePositiveInt(raw.sets);
    const reps = String(raw.reps ?? '').trim();
    const rest = parseNonNegativeInt(raw.rest);
    if (Number(raw.schema_version) !== SCHEMA_VERSION || !questId || !isExerciseLevel(level) || !dayLabel || !progressionGroup || !reps) return null;
    if (![dayNumber, sequence, exerciseSourceId, sets, rest].every(Number.isInteger)) return null;
    return {
      schemaVersion: SCHEMA_VERSION,
      questId,
      level,
      dayNumber,
      dayLabel,
      sequence,
      progressionGroup,
      exerciseSourceId,
      sets,
      reps,
      rest,
    } satisfies QuestWorkoutRow;
  }).filter((row): row is QuestWorkoutRow => row !== null)
    .sort((left, right) => left.questId.localeCompare(right.questId) || left.level.localeCompare(right.level) || left.dayNumber - right.dayNumber || left.sequence - right.sequence);

  if (!rows.length || rows.length !== parsed.data.length) throw new Error('Quest workout validation rejected one or more rows.');
  return rows;
}
