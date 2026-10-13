import { WEEKDAYS, type ScheduleDateOverrides, type Weekday, type WorkoutRow } from '../types';

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

export function isScheduleDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value) || value < '1900-01-01') return false;
  const date = new Date(`${value}T12:00:00`);
  return Number.isFinite(date.getTime()) && date.getFullYear() === Number(value.slice(0, 4))
    && date.getMonth() + 1 === Number(value.slice(5, 7)) && date.getDate() === Number(value.slice(8, 10));
}

export function scheduleWeekday(date: string): Weekday {
  return WEEKDAYS[new Date(`${date}T12:00:00`).getDay()];
}

export function validDateOverrides(value: unknown): value is ScheduleDateOverrides {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    && Object.entries(value).every(([source, target]) => isScheduleDate(source) && (target === null
      || (typeof target === 'object' && !Array.isArray(target) && isScheduleDate(target.date)
        && typeof target.time === 'string' && TIME.test(target.time))));
}

/** Preserve IDs/order/prescriptions, replacing only a single day's placement. */
export function workoutsOnDate(rows: WorkoutRow[], date: string): WorkoutRow[] {
  if (!isScheduleDate(date)) return [];
  const day = scheduleWeekday(date);
  return rows.flatMap(row => {
    const overrides = row.dateOverrides ?? {};
    const occurrences: WorkoutRow[] = [];
    if (row.day.toLowerCase() === day.toLowerCase() && !Object.hasOwn(overrides, date)) occurrences.push(row);
    for (const target of Object.values(overrides)) {
      if (target?.date === date) occurrences.push({ ...row, day, time: target.time });
    }
    return occurrences;
  }).sort((left, right) => left.time.localeCompare(right.time));
}

export function nextScheduleDate(day: Weekday, from: string): string {
  const date = new Date(`${from}T12:00:00`);
  date.setDate(date.getDate() + (WEEKDAYS.indexOf(day) - date.getDay() + 7) % 7);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
