import type { ExerciseCatalogItem, HistoryRange, WorkoutLog, WorkoutSessionEvent } from '../types';
import { formatDuration } from './format';

export interface HistoryDateGroup {
  key: string;
  label: string;
  logs: WorkoutLog[];
  done: number;
  skipped: number;
}

export interface ActivityDay {
  key: string;
  label: string;
  done: number;
  skipped: number;
}

export interface PeriodStats {
  label: string;
  days: number;
  done: number;
  skipped: number;
  sessions: number;
}

export interface TrendBucket extends PeriodStats {
  key: string;
}

export interface BalanceStat {
  label: string;
  done: number;
  skipped: number;
}

export function dateKeyFromDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function todayDateKey(): string {
  return dateKeyFromDate(new Date());
}

export function localDateKey(value: string): string {
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? dateFromKey(value) : new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 10);
  return dateKeyFromDate(date);
}

export function dateFromKey(key: string): Date {
  const [year, month, day] = key.split('-').map(Number);
  return new Date(year, (month || 1) - 1, day || 1);
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function startOfWeek(date: Date): Date {
  return addDays(date, -date.getDay());
}

function sameOrBefore(left: Date, right: Date): boolean {
  return dateKeyFromDate(left) <= dateKeyFromDate(right);
}

function datesBetween(start: Date, end: Date): Date[] {
  const result: Date[] = [];
  for (let current = new Date(start); sameOrBefore(current, end); current = addDays(current, 1)) result.push(new Date(current));
  return result;
}

export function formatHistoryDate(value: string): string {
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? dateFromKey(value) : new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
}

export function formatHistoryTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}

export function formatShortDateKey(key: string): string {
  const date = dateFromKey(key);
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

export function inHistoryRange(value: string, range: HistoryRange): boolean {
  if (range === 'all') return true;
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? dateFromKey(value) : new Date(value);
  const now = new Date();
  return !Number.isNaN(date.getTime()) && date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth();
}

export function calculateStreaks(logs: WorkoutLog[]): { current: number; longest: number } {
  const completedKeys = [...new Set(logs.filter((log) => log.status === 'done').map((log) => localDateKey(log.date)))].sort();
  if (!completedKeys.length) return { current: 0, longest: 0 };

  let longest = 1;
  let run = 1;
  for (let index = 1; index < completedKeys.length; index += 1) {
    const previous = dateFromKey(completedKeys[index - 1]);
    const current = dateFromKey(completedKeys[index]);
    run = dateKeyFromDate(addDays(previous, 1)) === dateKeyFromDate(current) ? run + 1 : 1;
    longest = Math.max(longest, run);
  }

  const completedSet = new Set(completedKeys);
  let cursor = dateFromKey(todayDateKey());
  if (!completedSet.has(dateKeyFromDate(cursor))) cursor = addDays(cursor, -1);
  let current = 0;
  while (completedSet.has(dateKeyFromDate(cursor))) {
    current += 1;
    cursor = addDays(cursor, -1);
  }
  return { current, longest };
}

export function countLogsForPeriod(label: string, logs: WorkoutLog[], events: WorkoutSessionEvent[], start: Date, end: Date): PeriodStats {
  const startKey = dateKeyFromDate(start);
  const endKey = dateKeyFromDate(end);
  const periodLogs = logs.filter((log) => {
    const key = localDateKey(log.date);
    return key >= startKey && key <= endKey;
  });
  const periodEvents = events.filter((event) => {
    const key = localDateKey(event.timestamp);
    return key >= startKey && key <= endKey;
  });
  return {
    label,
    days: new Set(periodLogs.filter((log) => log.status === 'done').map((log) => localDateKey(log.date))).size,
    done: periodLogs.filter((log) => log.status === 'done').length,
    skipped: periodLogs.filter((log) => log.status === 'skipped').length,
    sessions: periodEvents.length,
  };
}

export function deltaLabel(current: number, previous: number): string {
  const delta = current - previous;
  if (delta === 0) return 'same';
  return delta > 0 ? `+${delta}` : String(delta);
}

export function buildActivityDays(logs: WorkoutLog[], range: HistoryRange): ActivityDay[] {
  const today = dateFromKey(todayDateKey());
  const start = range === 'month' ? new Date(today.getFullYear(), today.getMonth(), 1) : addDays(today, -34);
  const byDay = new Map<string, { done: number; skipped: number }>();
  logs.forEach((log) => {
    const key = localDateKey(log.date);
    const current = byDay.get(key) ?? { done: 0, skipped: 0 };
    current[log.status] += 1;
    byDay.set(key, current);
  });
  return datesBetween(start, today).map((date) => {
    const key = dateKeyFromDate(date);
    const current = byDay.get(key) ?? { done: 0, skipped: 0 };
    return { key, label: date.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric' }), ...current };
  });
}

export function buildWeeklyTrend(logs: WorkoutLog[], events: WorkoutSessionEvent[]): TrendBucket[] {
  const thisWeek = startOfWeek(dateFromKey(todayDateKey()));
  return Array.from({ length: 6 }, (_, offset) => {
    const start = addDays(thisWeek, (offset - 5) * 7);
    const end = addDays(start, 6);
    return { key: dateKeyFromDate(start), ...countLogsForPeriod(formatShortDateKey(dateKeyFromDate(start)), logs, events, start, end) };
  });
}

export function buildMonthlyTrend(logs: WorkoutLog[], events: WorkoutSessionEvent[]): TrendBucket[] {
  const today = dateFromKey(todayDateKey());
  return Array.from({ length: 6 }, (_, offset) => {
    const start = new Date(today.getFullYear(), today.getMonth() - 5 + offset, 1);
    const end = new Date(start.getFullYear(), start.getMonth() + 1, 0);
    const label = start.toLocaleDateString('en-US', { month: 'short' });
    return { key: dateKeyFromDate(start), ...countLogsForPeriod(label, logs, events, start, end) };
  });
}

export function buildHistoryDateGroups(logs: WorkoutLog[]): HistoryDateGroup[] {
  const groups = new Map<string, HistoryDateGroup>();
  logs.forEach((log) => {
    const key = localDateKey(log.date);
    const current = groups.get(key) ?? { key, label: formatHistoryDate(log.date), logs: [], done: 0, skipped: 0 };
    current.logs.push(log);
    current[log.status] += 1;
    groups.set(key, current);
  });
  return [...groups.values()]
    .map((group) => ({ ...group, logs: group.logs.slice().sort((left, right) => right.date.localeCompare(left.date)) }))
    .sort((left, right) => right.key.localeCompare(left.key));
}

export function buildHistoryByExercise(logs: WorkoutLog[]): Array<[string, { done: number; skipped: number }]> {
  const result = new Map<string, { done: number; skipped: number }>();
  logs.forEach((log) => {
    const entry = result.get(log.exercise) ?? { done: 0, skipped: 0 };
    entry[log.status] += 1;
    result.set(log.exercise, entry);
  });
  return [...result.entries()].sort((left, right) => (right[1].done + right[1].skipped) - (left[1].done + left[1].skipped));
}

export function buildBalanceStats(logs: WorkoutLog[], catalog: ExerciseCatalogItem[]): BalanceStat[] {
  const catalogByName = new Map<string, ExerciseCatalogItem>();
  catalog.forEach((item) => {
    catalogByName.set(item.name.toLowerCase(), item);
    catalogByName.set(item.displayName.toLowerCase(), item);
  });

  const result = new Map<string, { done: number; skipped: number }>();
  logs.forEach((log) => {
    const exercise = catalogByName.get(log.exercise.toLowerCase());
    const labels = exercise?.primaryMuscles.length ? exercise.primaryMuscles : [exercise?.category ?? 'Unmapped'];
    labels.forEach((label) => {
      const current = result.get(label) ?? { done: 0, skipped: 0 };
      current[log.status] += 1;
      result.set(label, current);
    });
  });
  return [...result.entries()]
    .map(([label, stats]) => ({ label, ...stats }))
    .sort((left, right) => (right.done + right.skipped) - (left.done + left.skipped))
    .slice(0, 6);
}

export function buildPeriodComparisons(logs: WorkoutLog[], sessionEvents: WorkoutSessionEvent[]): Array<{ current: PeriodStats; previous: PeriodStats }> {
  const today = dateFromKey(todayDateKey());
  const thisWeekStart = startOfWeek(today);
  const lastWeekStart = addDays(thisWeekStart, -7);
  const thisMonthStart = new Date(today.getFullYear(), today.getMonth(), 1);
  const lastMonthStart = new Date(today.getFullYear(), today.getMonth() - 1, 1);
  const lastMonthEnd = addDays(thisMonthStart, -1);
  return [
    {
      current: countLogsForPeriod('This week', logs, sessionEvents, thisWeekStart, today),
      previous: countLogsForPeriod('Last week', logs, sessionEvents, lastWeekStart, addDays(thisWeekStart, -1)),
    },
    {
      current: countLogsForPeriod('This month', logs, sessionEvents, thisMonthStart, today),
      previous: countLogsForPeriod('Last month', logs, sessionEvents, lastMonthStart, lastMonthEnd),
    },
  ];
}

export function formatSessionEventType(event: WorkoutSessionEvent): string {
  return event.eventType === 'completed' ? 'Completed' : 'Ended';
}

export function formatSessionStopReason(reason: string): string {
  switch (reason) {
    case 'completed':
      return 'Completed';
    case 'ended_by_user':
      return 'Ended by user';
    case 'stale_next_day':
      return 'Closed after day changed';
    case 'app_closed':
      return 'Closed';
    case 'paused_by_user':
      return 'Paused';
    case 'inactive_timeout':
      return 'Paused after inactivity';
    case 'unexpected_interruption':
      return 'Interrupted';
    default:
      return 'Session event';
  }
}

export function formatActualVsEstimated(event: WorkoutSessionEvent): string | null {
  if (event.estimatedDurationSeconds == null || event.estimatedDurationSeconds <= 0) return null;
  const deltaSeconds = event.elapsedSeconds - event.estimatedDurationSeconds;
  if (Math.abs(deltaSeconds) < 30) return 'On estimate';
  return deltaSeconds > 0
    ? `${formatDuration(deltaSeconds)} over estimate`
    : `${formatDuration(Math.abs(deltaSeconds))} under estimate`;
}
