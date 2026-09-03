export const SCHEMA_VERSION = 1;

export const WEEKDAYS = [
  'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday',
] as const;

export type Weekday = (typeof WEEKDAYS)[number];
export type LogStatus = 'done' | 'skipped';
export type SessionEventType = 'completed' | 'ended';
export type HistoryRange = 'month' | 'all';
export type Tab = 'today' | 'quests' | 'library' | 'import' | 'history';
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
  questId?: string;
  questDayIndex?: number;
  questDayLabel?: string;
  questLevel?: ExerciseLevel;
}

export interface WorkoutLog {
  id?: number;
  schemaVersion: number;
  date: string;
  exercise: string;
  status: LogStatus;
  workoutRowId: number | null;
}

export interface WorkoutSessionEvent {
  id?: number;
  schemaVersion: number;
  workoutEntryId: string;
  workoutDate: string;
  eventType: SessionEventType;
  stopReason: string;
  timestamp: string;
  elapsedSeconds: number;
  estimatedDurationSeconds?: number | null;
  exerciseIndex: number;
  currentSet: number;
  totalExercises: number;
  currentExercise?: string | null;
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

export interface QuestTemplate {
  schemaVersion: number;
  questId: string;
  title: string;
  description: string;
  durationWeeks: number;
  daysPerWeek: number;
  evidenceBasis: string[];
  safetyNote: string;
}

export interface QuestWorkoutRow {
  schemaVersion: number;
  questId: string;
  level: ExerciseLevel;
  dayNumber: number;
  dayLabel: string;
  sequence: number;
  progressionGroup: string;
  exerciseSourceId: number;
  sets: number;
  reps: string;
  rest: number;
}

export interface QuestCompletion {
  dayIndex: number;
  dayNumber: number;
  dayLabel: string;
  level: ExerciseLevel;
  completedAt: string;
}

export interface QuestState {
  key: string;
  schemaVersion: number;
  questId: string;
  level: ExerciseLevel;
  nextDayIndex: number;
  scheduledTime: string;
  startedAt: string;
  completedDays: QuestCompletion[];
  status: 'active' | 'completed';
}
