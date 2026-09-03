import { type WeightUnit, type WorkoutSetLog } from '../types';
import { formatHistoryDate, localDateKey } from './history-stats';

export interface StrengthRecord {
  exercise: string;
  label: string;
  value: string;
  date: string;
}

export interface StrengthTrendPoint {
  key: string;
  label: string;
  maxLoad: number;
  bestEstimatedOneRepMax: number;
  volume: number;
  unit: WeightUnit;
}

export interface StrengthExerciseAnalytics {
  exercise: string;
  latestDate: string;
  unit: WeightUnit;
  setCount: number;
  records: StrengthRecord[];
  trend: StrengthTrendPoint[];
}

export interface StrengthPersonalRecord {
  exercise: string;
  type: 'maxLoad' | 'maxRepsAtLoad' | 'estimatedOneRepMax';
  label: string;
  value: string;
  date: string;
}

interface NormalizedStrengthSet {
  exercise: string;
  date: string;
  key: string;
  setNumber: number;
  reps: number;
  loadWeight: number;
  loadUnit: WeightUnit;
}

function parseRepCount(value: string): number | null {
  const normalized = value.toLowerCase();
  if (normalized.includes('min') || normalized.includes('sec') || normalized.includes(':')) return null;
  const values = [...normalized.matchAll(/\d+(?:\.\d+)?/g)].map((match) => Number(match[0])).filter(Number.isFinite);
  if (!values.length) return null;
  return Math.max(...values);
}

function estimatedOneRepMax(weight: number, reps: number): number {
  return reps <= 1 ? weight : weight * (1 + reps / 30);
}

function formatWeight(value: number, unit: WeightUnit): string {
  const rounded = Math.round(value * 10) / 10;
  return `${rounded % 1 === 0 ? rounded.toFixed(0) : rounded.toFixed(1)} ${unit}`;
}

function normalizeSetLogs(setLogs: WorkoutSetLog[]): NormalizedStrengthSet[] {
  return setLogs
    .map((log) => {
      const reps = parseRepCount(log.actualReps);
      if (reps == null || log.loadWeight == null || !log.loadUnit) return null;
      return {
        exercise: log.exercise,
        date: log.date,
        key: localDateKey(log.date),
        setNumber: log.setNumber,
        reps,
        loadWeight: log.loadWeight,
        loadUnit: log.loadUnit,
      } satisfies NormalizedStrengthSet;
    })
    .filter((log): log is NormalizedStrengthSet => log !== null)
    .sort((left, right) => left.date.localeCompare(right.date) || left.setNumber - right.setNumber);
}

function byLatestDate(left: StrengthExerciseAnalytics, right: StrengthExerciseAnalytics): number {
  return right.latestDate.localeCompare(left.latestDate) || right.setCount - left.setCount;
}

export function buildStrengthAnalytics(setLogs: WorkoutSetLog[]): StrengthExerciseAnalytics[] {
  const grouped = new Map<string, NormalizedStrengthSet[]>();
  normalizeSetLogs(setLogs).forEach((log) => {
    const key = `${log.exercise.toLowerCase()}|${log.loadUnit}`;
    grouped.set(key, [...(grouped.get(key) ?? []), log]);
  });

  return [...grouped.values()].map((logs) => {
    const latest = logs.at(-1)!;
    const maxLoad = logs.reduce((best, log) => (log.loadWeight > best.loadWeight ? log : best), logs[0]);
    const maxRepsAtLoad = logs.reduce((best, log) => {
      if (log.reps > best.reps) return log;
      if (log.reps === best.reps && log.loadWeight > best.loadWeight) return log;
      return best;
    }, logs[0]);
    const bestEstimatedOneRepMax = logs.reduce((best, log) => (
      estimatedOneRepMax(log.loadWeight, log.reps) > estimatedOneRepMax(best.loadWeight, best.reps) ? log : best
    ), logs[0]);
    const byDay = new Map<string, StrengthTrendPoint>();
    logs.forEach((log) => {
      const current = byDay.get(log.key) ?? {
        key: log.key,
        label: formatHistoryDate(log.key),
        maxLoad: 0,
        bestEstimatedOneRepMax: 0,
        volume: 0,
        unit: log.loadUnit,
      };
      current.maxLoad = Math.max(current.maxLoad, log.loadWeight);
      current.bestEstimatedOneRepMax = Math.max(current.bestEstimatedOneRepMax, estimatedOneRepMax(log.loadWeight, log.reps));
      current.volume += log.loadWeight * log.reps;
      byDay.set(log.key, current);
    });

    return {
      exercise: latest.exercise,
      latestDate: latest.date,
      unit: latest.loadUnit,
      setCount: logs.length,
      records: [
        { exercise: latest.exercise, label: 'Max load', value: formatWeight(maxLoad.loadWeight, maxLoad.loadUnit), date: maxLoad.date },
        { exercise: latest.exercise, label: 'Max reps at load', value: `${maxRepsAtLoad.reps} reps @ ${formatWeight(maxRepsAtLoad.loadWeight, maxRepsAtLoad.loadUnit)}`, date: maxRepsAtLoad.date },
        { exercise: latest.exercise, label: 'Estimated 1RM', value: formatWeight(estimatedOneRepMax(bestEstimatedOneRepMax.loadWeight, bestEstimatedOneRepMax.reps), bestEstimatedOneRepMax.loadUnit), date: bestEstimatedOneRepMax.date },
      ],
      trend: [...byDay.values()].sort((left, right) => left.key.localeCompare(right.key)).slice(-6),
    };
  }).sort(byLatestDate);
}

export function buildStrengthPersonalRecords(setLogs: WorkoutSetLog[]): StrengthPersonalRecord[] {
  const previousMaxLoad = new Map<string, number>();
  const previousEstimatedOneRepMax = new Map<string, number>();
  const previousRepsAtLoad = new Map<string, number>();
  const records: StrengthPersonalRecord[] = [];

  normalizeSetLogs(setLogs).forEach((log) => {
    const exerciseKey = `${log.exercise.toLowerCase()}|${log.loadUnit}`;
    const loadKey = `${exerciseKey}|${log.loadWeight}`;
    const oneRepMax = estimatedOneRepMax(log.loadWeight, log.reps);

    if (previousMaxLoad.has(exerciseKey) && log.loadWeight > previousMaxLoad.get(exerciseKey)!) {
      records.push({ exercise: log.exercise, type: 'maxLoad', label: 'New max load', value: formatWeight(log.loadWeight, log.loadUnit), date: log.date });
    }
    if (previousRepsAtLoad.has(loadKey) && log.reps > previousRepsAtLoad.get(loadKey)!) {
      records.push({ exercise: log.exercise, type: 'maxRepsAtLoad', label: 'New reps at load', value: `${log.reps} reps @ ${formatWeight(log.loadWeight, log.loadUnit)}`, date: log.date });
    }
    if (previousEstimatedOneRepMax.has(exerciseKey) && oneRepMax > previousEstimatedOneRepMax.get(exerciseKey)!) {
      records.push({ exercise: log.exercise, type: 'estimatedOneRepMax', label: 'New estimated 1RM', value: formatWeight(oneRepMax, log.loadUnit), date: log.date });
    }

    previousMaxLoad.set(exerciseKey, Math.max(previousMaxLoad.get(exerciseKey) ?? 0, log.loadWeight));
    previousRepsAtLoad.set(loadKey, Math.max(previousRepsAtLoad.get(loadKey) ?? 0, log.reps));
    previousEstimatedOneRepMax.set(exerciseKey, Math.max(previousEstimatedOneRepMax.get(exerciseKey) ?? 0, oneRepMax));
  });

  return records.sort((left, right) => right.date.localeCompare(left.date)).slice(0, 8);
}
