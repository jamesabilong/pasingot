import { EstimateSummary, PlanProgressSummary, type PlanProgress } from './SummaryCards';
import { type ExerciseCatalogItem, type ExerciseLevel, type QuestCompletion, type QuestState, type QuestTemplate, type QuestWorkoutRow } from '../types';

export type CurrentQuestRow = {
  row: QuestWorkoutRow;
  exercise: ExerciseCatalogItem;
};

export function QuestsView({
  questState,
  activeQuestTemplate,
  draftLevel,
  levels,
  levelLabels,
  currentQuestRows,
  scheduledCurrentQuestRows,
  currentQuestEstimate,
  currentQuestProgress,
  questResult,
  totalDays,
  weekNumber,
  dayNumber,
  onDraftLevelChange,
  onQuestStateChange,
  onStartQuest,
  onSaveQuestDayToSchedule,
}: {
  questState: QuestState | null;
  activeQuestTemplate: QuestTemplate | undefined;
  draftLevel: ExerciseLevel;
  levels: ExerciseLevel[];
  levelLabels: Record<ExerciseLevel, string>;
  currentQuestRows: CurrentQuestRow[];
  scheduledCurrentQuestRows: unknown[];
  currentQuestEstimate: string;
  currentQuestProgress: PlanProgress;
  questResult: { message: string; error: boolean } | null;
  totalDays: number | null;
  weekNumber: number | null;
  dayNumber: number | null;
  onDraftLevelChange: (level: ExerciseLevel) => void;
  onQuestStateChange: (state: QuestState) => void;
  onStartQuest: (template: QuestTemplate) => void;
  onSaveQuestDayToSchedule: () => void;
}) {
  return (
    <section className="space-y-5">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-base font-semibold text-slate-200">Daily Quest</h2>
        {questState && activeQuestTemplate && totalDays != null && <span className="text-xs text-slate-500">{questState.completedDays.length}/{totalDays}</span>}
      </div>

      {!activeQuestTemplate ? <p className="py-10 text-center text-sm text-slate-500">No quest definitions are available.</p> : !questState ? <QuestEnrollment
        template={activeQuestTemplate}
        draftLevel={draftLevel}
        levels={levels}
        levelLabels={levelLabels}
        onDraftLevelChange={onDraftLevelChange}
        onStartQuest={onStartQuest}
      /> : <QuestProgress
        questState={questState}
        template={activeQuestTemplate}
        levels={levels}
        levelLabels={levelLabels}
        currentQuestRows={currentQuestRows}
        scheduledCurrentQuestRows={scheduledCurrentQuestRows}
        currentQuestEstimate={currentQuestEstimate}
        currentQuestProgress={currentQuestProgress}
        questResult={questResult}
        totalDays={totalDays ?? 0}
        weekNumber={weekNumber ?? 1}
        dayNumber={dayNumber ?? 1}
        onQuestStateChange={onQuestStateChange}
        onSaveQuestDayToSchedule={onSaveQuestDayToSchedule}
      />}
    </section>
  );
}

function QuestEnrollment({
  template,
  draftLevel,
  levels,
  levelLabels,
  onDraftLevelChange,
  onStartQuest,
}: {
  template: QuestTemplate;
  draftLevel: ExerciseLevel;
  levels: ExerciseLevel[];
  levelLabels: Record<ExerciseLevel, string>;
  onDraftLevelChange: (level: ExerciseLevel) => void;
  onStartQuest: (template: QuestTemplate) => void;
}) {
  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
        <div className="mb-3 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="text-lg font-semibold">{template.title}</h3>
            <p className="mt-1 text-sm leading-relaxed text-slate-400">{template.description}</p>
          </div>
          <span className="shrink-0 rounded border border-emerald-700 px-2 py-1 text-xs text-emerald-300">{template.durationWeeks}w</span>
        </div>
        <div className="grid grid-cols-3 gap-1 rounded-md border border-slate-800 bg-slate-950 p-1 text-xs font-medium">
          {levels.map((level) => (
            <button
              key={level}
              type="button"
              onClick={() => onDraftLevelChange(level)}
              className={`rounded px-2 py-2 ${draftLevel === level ? 'bg-emerald-500 text-slate-950' : 'text-slate-400 hover:bg-slate-800'}`}
            >
              {levelLabels[level]}
            </button>
          ))}
        </div>
        <button type="button" onClick={() => onStartQuest(template)} className="mt-4 w-full rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400">Start quest</button>
      </div>
      <p className="text-xs leading-relaxed text-slate-500">{template.safetyNote}</p>
      <div className="flex flex-wrap gap-2 text-xs">
        {template.evidenceBasis.map((url) => <a key={url} href={url} target="_blank" rel="noreferrer" className="rounded border border-slate-800 px-2 py-1 text-emerald-400 hover:border-emerald-700">Evidence</a>)}
      </div>
    </div>
  );
}

function QuestProgress({
  questState,
  template,
  levels,
  levelLabels,
  currentQuestRows,
  scheduledCurrentQuestRows,
  currentQuestEstimate,
  currentQuestProgress,
  questResult,
  totalDays,
  weekNumber,
  dayNumber,
  onQuestStateChange,
  onSaveQuestDayToSchedule,
}: {
  questState: QuestState;
  template: QuestTemplate;
  levels: ExerciseLevel[];
  levelLabels: Record<ExerciseLevel, string>;
  currentQuestRows: CurrentQuestRow[];
  scheduledCurrentQuestRows: unknown[];
  currentQuestEstimate: string;
  currentQuestProgress: PlanProgress;
  questResult: { message: string; error: boolean } | null;
  totalDays: number;
  weekNumber: number;
  dayNumber: number;
  onQuestStateChange: (state: QuestState) => void;
  onSaveQuestDayToSchedule: () => void;
}) {
  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="text-lg font-semibold">{template.title}</h3>
            <p className="mt-1 text-sm text-slate-400">{questState.status === 'completed' ? 'Quest complete' : `Week ${weekNumber} · Day ${dayNumber}`}</p>
          </div>
          <span className="shrink-0 rounded border border-slate-700 px-2 py-1 text-xs text-slate-300">{levelLabels[questState.level]}</span>
        </div>
        <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-800">
          <div className="h-full bg-emerald-500" style={{ width: `${Math.round((questState.completedDays.length / totalDays) * 100)}%` }} />
        </div>
      </div>

      {questState.status === 'active' && <>
        <div className="grid grid-cols-3 gap-1 rounded-lg border border-slate-800 bg-slate-900 p-1 text-xs font-medium">
          {levels.map((level) => (
            <button
              key={level}
              type="button"
              onClick={() => onQuestStateChange({ ...questState, level })}
              className={`rounded-md px-2 py-2 text-center ${questState.level === level ? 'bg-emerald-500 text-slate-950' : 'text-slate-400 hover:bg-slate-800'}`}
            >
              {levelLabels[level]}
            </button>
          ))}
        </div>

        <label className="block text-xs text-slate-500">
          Start time
          <input
            type="time"
            value={questState.scheduledTime}
            onChange={(event) => onQuestStateChange({ ...questState, scheduledTime: event.target.value })}
            className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none"
          />
        </label>

        <div className="space-y-2">
          <div className="flex items-baseline justify-between">
            <h3 className="text-sm font-semibold text-slate-200">{currentQuestRows[0]?.row.dayLabel ?? 'Current Day'}</h3>
            <span className="text-xs text-slate-500">{currentQuestRows.length ? `${scheduledCurrentQuestRows.length ? 'Scheduled' : 'Not scheduled'} · ${currentQuestEstimate}` : scheduledCurrentQuestRows.length ? 'Scheduled' : 'Not scheduled'}</span>
          </div>
          {currentQuestRows.length > 0 && <EstimateSummary value={currentQuestEstimate} />}
          {currentQuestRows.length > 0 && <PlanProgressSummary progress={currentQuestProgress} />}
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

        <button type="button" disabled={!currentQuestRows.length} onClick={onSaveQuestDayToSchedule} className="w-full rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400 disabled:cursor-not-allowed disabled:opacity-40">Add today's quest to schedule</button>
        {questResult && <p className={`rounded-md border p-3 text-sm ${questResult.error ? 'border-rose-900 bg-rose-950/40 text-rose-300' : 'border-emerald-900 bg-emerald-950/40 text-emerald-300'}`}>{questResult.message}</p>}
      </>}

      {questState.completedDays.length > 0 && <CompletedQuestDays completedDays={questState.completedDays} levelLabels={levelLabels} />}

      <p className="border-t border-slate-800 pt-4 text-xs leading-relaxed text-slate-500">{template.safetyNote}</p>
    </div>
  );
}

function CompletedQuestDays({
  completedDays,
  levelLabels,
}: {
  completedDays: QuestCompletion[];
  levelLabels: Record<ExerciseLevel, string>;
}) {
  return (
    <div className="space-y-2">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Completed Quest Days</h3>
      {completedDays.slice().reverse().map((day) => (
        <div key={day.dayIndex} className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm">
          <span className="min-w-0 truncate">{day.dayLabel}</span>
          <span className="shrink-0 text-xs text-slate-500">{levelLabels[day.level]}</span>
        </div>
      ))}
    </div>
  );
}
