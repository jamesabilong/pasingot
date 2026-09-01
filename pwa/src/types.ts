export const SCHEMA_VERSION = 1;

export const WEEKDAYS = [
  'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday',
] as const;

export type Weekday = (typeof WEEKDAYS)[number];
export type LogStatus = 'done' | 'skipped';
export type HistoryRange = 'month' | 'all';
export type Tab = 'today' | 'library' | 'import' | 'history';
export type ExerciseLevel = 'beginner' | 'intermediate' | 'advanced';

export interface WorkoutRow {
  id?: number;
  schemaVersion: number;
  day: Weekday;
  time: string;
  exercise: string;
  sets: number;
  reps: string;
  rest: number;
}

export interface WorkoutLog {
  id?: number;
  schemaVersion: number;
  date: string;
  exercise: string;
  status: LogStatus;
  workoutRowId: number | null;
}

export interface ExerciseCatalogItem {
  schemaVersion: number;
  sourceId: number;
  name: string;
  displayName: string;
  category: string;
  primaryMuscles: string[];
  secondaryMuscles: string[];
  equipment: string[];
  featured: boolean;
  minimumLevel: ExerciseLevel;
  progressionGroup: string;
  progressionLevel: string;
  license: string;
  licenseUrl: string;
  author: string;
  sourceUrl: string;
}

export interface PlaylistItem {
  sourceId: number;
  name: string;
  sets: number;
  reps: string;
  rest: number;
}

export interface PlaylistDraft {
  day: Weekday;
  time: string;
  level: ExerciseLevel;
  items: PlaylistItem[];
}
