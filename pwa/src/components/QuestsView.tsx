import { useState } from 'react';
import { Award, BookPlus, CalendarPlus, Check, Flag, LogOut, Play, Trash2 } from 'lucide-react';
import { EstimateSummary, LevelPicker, PlanProgressSummary, type PlanProgress } from './SummaryCards';
import { initialCustomQuestDraft, type CustomQuestDraft } from '../lib/custom-quests';
import { type ExerciseCatalogItem, type ExerciseLevel, type PlaylistDraft, type QuestCompletion, type QuestState, type QuestTemplate, type QuestWorkoutRow } from '../types';

export type CurrentQuestRow = {
  row: QuestWorkoutRow;
  exercise: ExerciseCatalogItem;
};

export function QuestsView({
  questState,
  activeQuestTemplate,
  questTemplates,
  playlistDraft,
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
  onCreateCustomQuest,
  onDeleteCustomQuest,
  onLeaveQuest,
}: {
  questState: QuestState | null;
  activeQuestTemplate: QuestTemplate | undefined;
  questTemplates: QuestTemplate[];
  playlistDraft: PlaylistDraft;
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
  onCreateCustomQuest: (draft: CustomQuestDraft) => void;
  onDeleteCustomQuest: (questId: string) => void;
  onLeaveQuest: () => void;
}) {
  return (
    <section className="quests-view space-y-5">
      <div className="section-heading">
        <div><p className="section-kicker">Program</p><h2 className="text-base font-semibold text-slate-200">Daily quest</h2></div>
        {questState && activeQuestTemplate && totalDays != null && <span className="text-xs text-slate-500">{questState.completedDays.length}/{totalDays}</span>}
      </div>

      {!questState ? <div className="space-y-6">
        {questTemplates.length ? <div className="quest-catalog">{questTemplates.map((template) => <QuestEnrollment
          key={template.questId}
          template={template}
          draftLevel={draftLevel}
          levels={template.availableLevels?.length ? template.availableLevels : levels}
          levelLabels={levelLabels}
          onDraftLevelChange={onDraftLevelChange}
          onStartQuest={onStartQuest}
          onDeleteCustomQuest={onDeleteCustomQuest}
        />)}</div> : <p className="py-10 text-center text-sm text-slate-500">No quest definitions are available.</p>}
        <CustomQuestBuilder playlistDraft={playlistDraft} levelLabels={levelLabels} onCreate={onCreateCustomQuest} />
        {questResult && <p className={`rounded-md border p-3 text-sm ${questResult.error ? 'border-rose-900 bg-rose-950/40 text-rose-300' : 'border-emerald-900 bg-emerald-950/40 text-emerald-300'}`}>{questResult.message}</p>}
      </div> : !activeQuestTemplate ? <div className="space-y-3 rounded-lg border border-rose-900 bg-rose-950/30 p-4 text-sm text-rose-300">
        <p>The active quest template is missing from local storage.</p>
        <button type="button" onClick={onLeaveQuest} className="secondary-action">Clear missing quest</button>
      </div> : <QuestProgress
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
        onLeaveQuest={onLeaveQuest}
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
  onDeleteCustomQuest,
}: {
  template: QuestTemplate;
  draftLevel: ExerciseLevel;
  levels: ExerciseLevel[];
  levelLabels: Record<ExerciseLevel, string>;
  onDraftLevelChange: (level: ExerciseLevel) => void;
  onStartQuest: (template: QuestTemplate) => void;
  onDeleteCustomQuest: (questId: string) => void;
}) {
  const selectedLevel = levels.includes(draftLevel) ? draftLevel : levels[0];
  return (
    <div className="space-y-4">
      <div className="quest-program-card">
        <div className="mb-3 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="text-lg font-semibold">{template.title}</h3>
            <p className="mt-1 text-sm leading-relaxed text-slate-400">{template.description}</p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            {template.custom && <span className="rounded border border-indigo-700 px-2 py-1 text-xs text-indigo-300">Custom</span>}
            <span className="rounded border border-emerald-700 px-2 py-1 text-xs text-emerald-300">{template.durationWeeks}w</span>
          </div>
        </div>
        <LevelPicker levels={levels} levelLabels={levelLabels} selected={selectedLevel} onSelect={onDraftLevelChange} variant="compact" />
        <div className="grid grid-cols-[1fr_auto] gap-2">
          <button type="button" onClick={() => onStartQuest(template)} className="primary-action mt-0"><Play size={18} fill="currentColor" aria-hidden="true" /> Start quest</button>
          {template.custom && <button type="button" onClick={() => onDeleteCustomQuest(template.questId)} className="playlist-icon-action playlist-icon-action--danger" title="Delete custom quest" aria-label={`Delete ${template.title}`}><Trash2 size={18} aria-hidden="true" /></button>}
        </div>
      </div>
      <p className="text-xs leading-relaxed text-slate-500">{template.safetyNote}</p>
      <div className="flex flex-wrap gap-2 text-xs">
        {template.evidenceBasis.map((url) => <a key={url} href={url} target="_blank" rel="noreferrer" className="rounded border border-slate-800 px-2 py-1 text-emerald-400 hover:border-emerald-700">Evidence</a>)}
      </div>
    </div>
  );
}

function CustomQuestBuilder({
  playlistDraft,
  levelLabels,
  onCreate,
}: {
  playlistDraft: PlaylistDraft;
  levelLabels: Record<ExerciseLevel, string>;
  onCreate: (draft: CustomQuestDraft) => void;
}) {
  const [draft, setDraft] = useState<CustomQuestDraft>(initialCustomQuestDraft);
  const playlistSummary = playlistDraft.items.length
    ? `${playlistDraft.items.length} exercise${playlistDraft.items.length === 1 ? '' : 's'} · ${levelLabels[playlistDraft.level]} · ${playlistDraft.time}`
    : 'No Library playlist is ready';
  return (
    <div className="custom-quest-builder space-y-3">
      <div>
        <p className="section-kicker">Your program</p>
        <h3 className="text-base font-semibold text-slate-200">Create quest from Library playlist</h3>
        <p className="mt-1 text-xs leading-relaxed text-slate-500">Each quest day repeats the current playlist and keeps its exercise IDs, set prescriptions, rest, and planned loads. Edit the playlist in Library before creating the quest.</p>
      </div>
      <p className={`rounded-md border p-2 text-xs ${playlistDraft.items.length ? 'border-emerald-900 bg-emerald-950/30 text-emerald-300' : 'border-slate-800 bg-slate-950 text-slate-500'}`}>{playlistSummary}</p>
      <label className="block text-xs text-slate-500">Title<input type="text" maxLength={80} value={draft.title} onChange={(event) => setDraft({ ...draft, title: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" /></label>
      <label className="block text-xs text-slate-500">Goal<textarea maxLength={240} rows={2} value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" /></label>
      <div className="grid grid-cols-2 gap-2">
        <label className="text-xs text-slate-500">Weeks<input type="number" min="1" max="52" value={draft.durationWeeks} onChange={(event) => setDraft({ ...draft, durationWeeks: Number(event.target.value) })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" /></label>
        <label className="text-xs text-slate-500">Days per week<input type="number" min="1" max="7" value={draft.daysPerWeek} onChange={(event) => setDraft({ ...draft, daysPerWeek: Number(event.target.value) })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" /></label>
      </div>
      <label className="block text-xs text-slate-500">Safety note<textarea maxLength={320} rows={2} value={draft.safetyNote} onChange={(event) => setDraft({ ...draft, safetyNote: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" /></label>
      <button type="button" disabled={!playlistDraft.items.length} onClick={() => onCreate(draft)} className="primary-action mt-0 disabled:cursor-not-allowed disabled:opacity-40"><BookPlus size={18} aria-hidden="true" /> Create custom quest</button>
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
  onLeaveQuest,
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
  onLeaveQuest: () => void;
}) {
  const progressPercent = totalDays ? Math.round((questState.completedDays.length / totalDays) * 100) : 0;
  return (
    <div className="space-y-4">
      <div className="quest-program-card quest-program-card--active">
        <div className="quest-program-card__summary">
          <div className="min-w-0">
            <p className="section-kicker">Active quest</p>
            <h3 className="text-lg font-semibold">{template.title}</h3>
            <p className="mt-1 text-sm text-slate-400">{questState.status === 'completed' ? 'Quest complete' : `Week ${weekNumber} · Day ${dayNumber}`}</p>
            <span className="quest-level">{levelLabels[questState.level]}</span>
          </div>
          <div className="quest-progress-ring" aria-label={`${progressPercent}% complete`}>
            <svg viewBox="0 0 100 100" aria-hidden="true"><circle className="quest-progress-ring__track" cx="50" cy="50" r="42" /><circle className="quest-progress-ring__value" cx="50" cy="50" r="42" style={{ strokeDashoffset: 264 * (1 - progressPercent / 100) }} /></svg>
            <strong>{progressPercent}%</strong>
          </div>
        </div>
      </div>

      {questState.status === 'active' && <>
        <LevelPicker levels={levels} levelLabels={levelLabels} selected={questState.level} onSelect={(level) => onQuestStateChange({ ...questState, level })} />

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
          {currentQuestRows.length === 0 ? <p className="rounded-lg border border-rose-900 bg-rose-950/30 p-3 text-sm text-rose-300">This quest day could not be resolved from the local catalog.</p> : <div className="quest-day-path">{currentQuestRows.map(({ row, exercise }, index) => (
            <div key={`${row.dayNumber}-${row.sequence}`} className="quest-day-step">
              <span>{index === currentQuestRows.length - 1 ? <Flag size={15} aria-hidden="true" /> : row.sequence}</span>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{exercise.displayName}</p>
                <p className="text-xs text-slate-500">{row.progressionGroup} · {row.sets} x {row.reps}{row.loadWeight != null ? ` @ ${row.loadWeight} ${row.loadUnit ?? 'kg'}` : ''} · rest {row.rest}s</p>
              </div>
            </div>
          ))}</div>}
        </div>

        <button type="button" disabled={!currentQuestRows.length} onClick={onSaveQuestDayToSchedule} className="primary-action mt-0 disabled:cursor-not-allowed disabled:opacity-40"><CalendarPlus size={18} aria-hidden="true" /> Add today's quest</button>
        {questResult && <p className={`rounded-md border p-3 text-sm ${questResult.error ? 'border-rose-900 bg-rose-950/40 text-rose-300' : 'border-emerald-900 bg-emerald-950/40 text-emerald-300'}`}>{questResult.message}</p>}
      </>}

      {questState.completedDays.length > 0 && <CompletedQuestDays completedDays={questState.completedDays} levelLabels={levelLabels} />}

      <p className="border-t border-slate-800 pt-4 text-xs leading-relaxed text-slate-500">{template.safetyNote}</p>
      <button type="button" onClick={onLeaveQuest} className="secondary-action w-full"><LogOut size={16} aria-hidden="true" />{questState.status === 'completed' ? 'Choose another quest' : 'Leave quest'}</button>
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
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500"><Award className="mr-2 inline" size={15} aria-hidden="true" />Completed quest days</h3>
      <div className="quest-completion-list">{completedDays.slice().reverse().map((day) => (
        <div key={day.dayIndex} className="flex items-center justify-between p-3 text-sm">
          <Check size={16} className="mr-2 shrink-0 text-emerald-400" aria-hidden="true" />
          <span className="min-w-0 truncate">{day.dayLabel}</span>
          <span className="shrink-0 text-xs text-slate-500">{levelLabels[day.level]}</span>
        </div>
      ))}</div>
    </div>
  );
}
