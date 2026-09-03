import { useMemo } from 'react';
import type {
  BodyMetricEntry,
  BodyWeightUnit,
  ExerciseCatalogItem,
  ExerciseLevel,
  HistoryRange,
  QuestState,
  QuestTemplate,
  WorkoutLog,
  WorkoutSetLog,
  WorkoutSessionEvent,
} from '../types';
import type { BodyMetricDraft } from '../lib/body-metrics';
import { initialBodyMetricDraft } from '../lib/body-metrics';
import { formatDuration } from '../lib/format';
import {
  buildActivityDays,
  buildBalanceStats,
  buildHistoryByExercise,
  buildHistoryDateGroups,
  buildMonthlyTrend,
  buildPeriodComparisons,
  buildWeeklyTrend,
  calculateStreaks,
  deltaLabel,
  formatActualVsEstimated,
  formatHistoryDate,
  formatHistoryTime,
  formatSessionEventType,
  formatSessionStopReason,
  formatShortDateKey,
  inHistoryRange,
  localDateKey,
  type ActivityDay,
  type BalanceStat,
  type PeriodStats,
  type TrendBucket,
} from '../lib/history-stats';
import {
  buildStrengthAnalytics,
  buildStrengthPersonalRecords,
  type StrengthExerciseAnalytics,
  type StrengthPersonalRecord,
} from '../lib/strength-analytics';

interface HistoryViewProps {
  range: HistoryRange;
  logs: WorkoutLog[];
  sessionEvents: WorkoutSessionEvent[];
  setLogs: WorkoutSetLog[];
  bodyMetrics: BodyMetricEntry[];
  catalog: ExerciseCatalogItem[];
  questState: QuestState | null;
  activeQuestTemplate?: QuestTemplate;
  levelLabels: Record<ExerciseLevel, string>;
  bodyMetricDraft: BodyMetricDraft;
  bodyMetricResult: { message: string; error: boolean } | null;
  onRangeChange: (range: HistoryRange) => void;
  onBodyMetricDraftChange: (draft: BodyMetricDraft) => void;
  onBodyMetricSave: () => void;
  onBodyMetricEdit: (entry: BodyMetricEntry) => void;
  onBodyMetricDelete: (entry: BodyMetricEntry) => void;
}

export function HistoryView({
  range,
  logs,
  sessionEvents,
  setLogs,
  bodyMetrics,
  catalog,
  questState,
  activeQuestTemplate,
  levelLabels,
  bodyMetricDraft,
  bodyMetricResult,
  onRangeChange,
  onBodyMetricDraftChange,
  onBodyMetricSave,
  onBodyMetricEdit,
  onBodyMetricDelete,
}: HistoryViewProps) {
  const historyLogs = useMemo(() => logs.filter((log) => inHistoryRange(log.date, range)), [logs, range]);
  const historySessionEvents = useMemo(() => sessionEvents
    .filter((event) => inHistoryRange(event.timestamp, range))
    .slice()
    .sort((left, right) => right.timestamp.localeCompare(left.timestamp)), [range, sessionEvents]);
  const historySetLogs = useMemo(() => setLogs.filter((log) => inHistoryRange(log.date, range)), [range, setLogs]);
  const historyBodyMetrics = useMemo(() => bodyMetrics
    .filter((entry) => inHistoryRange(entry.date, range))
    .slice()
    .sort((left, right) => right.date.localeCompare(left.date) || (right.id ?? 0) - (left.id ?? 0)), [bodyMetrics, range]);
  const sortedBodyMetrics = useMemo(() => bodyMetrics
    .slice()
    .sort((left, right) => left.date.localeCompare(right.date) || (left.id ?? 0) - (right.id ?? 0)), [bodyMetrics]);
  const latestBodyMetric = sortedBodyMetrics.at(-1);
  const previousBodyMetric = sortedBodyMetrics.at(-2);
  const bodyMetricChange = latestBodyMetric && previousBodyMetric && latestBodyMetric.unit === previousBodyMetric.unit
    ? Math.round((latestBodyMetric.weight - previousBodyMetric.weight) * 10) / 10
    : null;

  const workoutDays = useMemo(() => new Set(historyLogs.map((log) => localDateKey(log.date))).size, [historyLogs]);
  const doneCount = useMemo(() => historyLogs.filter((item) => item.status === 'done').length, [historyLogs]);
  const skippedCount = useMemo(() => historyLogs.filter((item) => item.status === 'skipped').length, [historyLogs]);
  const completionPercent = historyLogs.length ? Math.round((doneCount / historyLogs.length) * 100) : 0;
  const streaks = useMemo(() => calculateStreaks(logs), [logs]);
  const activityDays = useMemo(() => buildActivityDays(logs, range), [logs, range]);
  const weeklyTrend = useMemo(() => buildWeeklyTrend(logs, sessionEvents), [logs, sessionEvents]);
  const monthlyTrend = useMemo(() => buildMonthlyTrend(logs, sessionEvents), [logs, sessionEvents]);
  const maxWeeklyTrendTotal = useMemo(() => Math.max(1, ...weeklyTrend.map((bucket) => bucket.done + bucket.skipped + bucket.sessions)), [weeklyTrend]);
  const maxMonthlyTrendTotal = useMemo(() => Math.max(1, ...monthlyTrend.map((bucket) => bucket.done + bucket.skipped + bucket.sessions)), [monthlyTrend]);
  const balanceStats = useMemo(() => buildBalanceStats(historyLogs, catalog), [catalog, historyLogs]);
  const maxBalanceTotal = useMemo(() => Math.max(1, ...balanceStats.map((item) => item.done + item.skipped)), [balanceStats]);
  const periodComparisons = useMemo(() => buildPeriodComparisons(logs, sessionEvents), [logs, sessionEvents]);
  const dateGroups = useMemo(() => buildHistoryDateGroups(historyLogs), [historyLogs]);
  const byExercise = useMemo(() => buildHistoryByExercise(historyLogs), [historyLogs]);
  const strengthAnalytics = useMemo(() => buildStrengthAnalytics(historySetLogs), [historySetLogs]);
  const strengthPersonalRecords = useMemo(() => buildStrengthPersonalRecords(setLogs), [setLogs]);
  const questCompletions = useMemo(() => (questState?.completedDays ?? [])
    .filter((day) => inHistoryRange(day.completedAt, range))
    .slice()
    .sort((left, right) => right.completedAt.localeCompare(left.completedAt)), [questState, range]);
  const questPercent = questState && activeQuestTemplate
    ? Math.round((questState.completedDays.length / (activeQuestTemplate.durationWeeks * activeQuestTemplate.daysPerWeek)) * 100)
    : 0;

  return (
    <section className="space-y-5">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-slate-200">History</h2>
        <div className="flex gap-1 rounded-lg bg-slate-900 p-1 text-xs font-medium">
          <button type="button" onClick={() => onRangeChange('month')} className={`rounded-md px-3 py-1 ${range === 'month' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400'}`}>This Month</button>
          <button type="button" onClick={() => onRangeChange('all')} className={`rounded-md px-3 py-1 ${range === 'all' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400'}`}>All Time</button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2 text-center">
        <Metric label="Workout days" value={workoutDays} />
        <Metric label="Logged items" value={historyLogs.length} />
        <Metric label="Done" value={doneCount} color="text-emerald-400" />
        <Metric label="Skipped" value={skippedCount} color="text-amber-400" />
        <Metric label="Current streak" value={streaks.current} color="text-teal-300" />
        <Metric label="Longest streak" value={streaks.longest} color="text-indigo-300" />
      </div>

      <CompletionSummary percent={completionPercent} />

      <BodyMetricsPanel
        draft={bodyMetricDraft}
        entries={historyBodyMetrics}
        latest={latestBodyMetric}
        change={bodyMetricChange}
        result={bodyMetricResult}
        onDraftChange={onBodyMetricDraftChange}
        onSave={onBodyMetricSave}
        onEdit={onBodyMetricEdit}
        onDelete={onBodyMetricDelete}
      />

      <StrengthAnalyticsPanel analytics={strengthAnalytics} personalRecords={strengthPersonalRecords} />
      <ActivityCalendar days={activityDays} range={range} />
      <PeriodComparison comparisons={periodComparisons} />
      <TrendBars title="Weekly Trend" detail="6 weeks" buckets={weeklyTrend} maxTotal={maxWeeklyTrendTotal} />
      <TrendBars title="Monthly Trend" detail="6 months" buckets={monthlyTrend} maxTotal={maxMonthlyTrendTotal} />
      <BalanceBreakdown stats={balanceStats} maxTotal={maxBalanceTotal} />

      <QuestProgress
        questState={questState}
        activeQuestTemplate={activeQuestTemplate}
        completions={questCompletions}
        percent={questPercent}
        levelLabels={levelLabels}
      />
      <WorkoutSessions events={historySessionEvents} />
      <RecentActivity groups={dateGroups} />
      <ExerciseBreakdown items={byExercise} />
    </section>
  );
}

function Metric({ label, value, color = '' }: { label: string; value: number; color?: string }) {
  return <div className="rounded-lg border border-slate-800 bg-slate-900 py-3"><p className={`text-xl font-bold ${color}`}>{value}</p><p className="text-xs text-slate-500">{label}</p></div>;
}

function CompletionSummary({ percent }: { percent: number }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
      <div className="mb-2 flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-slate-200">Completion</h3>
        <span className="text-xs text-slate-500">{percent}% done</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-slate-800">
        <div className="h-full bg-emerald-500" style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}

function BodyMetricsPanel({
  draft,
  entries,
  latest,
  change,
  result,
  onDraftChange,
  onSave,
  onEdit,
  onDelete,
}: {
  draft: BodyMetricDraft;
  entries: BodyMetricEntry[];
  latest?: BodyMetricEntry;
  change: number | null;
  result: { message: string; error: boolean } | null;
  onDraftChange: (draft: BodyMetricDraft) => void;
  onSave: () => void;
  onEdit: (entry: BodyMetricEntry) => void;
  onDelete: (entry: BodyMetricEntry) => void;
}) {
  const changeLabel = change == null ? 'No prior entry' : `${change > 0 ? '+' : ''}${change} ${latest?.unit ?? ''}`;
  const changeColor = change == null ? 'text-slate-500' : change > 0 ? 'text-amber-300' : change < 0 ? 'text-teal-300' : 'text-slate-300';
  return (
    <div className="space-y-3 rounded-lg border border-slate-800 bg-slate-900 p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-200">Body Weight</h3>
          <p className="text-xs text-slate-500">{latest ? `${latest.weight} ${latest.unit} · ${formatShortDateKey(latest.date)}` : 'No entries yet'}</p>
        </div>
        <span className={`shrink-0 rounded border border-slate-800 bg-slate-950 px-2 py-1 text-xs font-semibold ${changeColor}`}>{changeLabel}</span>
      </div>

      <div className="grid gap-2 sm:grid-cols-[1fr_6rem_5rem]">
        <label className="text-xs text-slate-500">
          Date
          <input type="date" value={draft.date} onChange={(event) => onDraftChange({ ...draft, date: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" />
        </label>
        <label className="text-xs text-slate-500">
          Weight
          <input type="number" min="1" max="700" step="0.1" value={draft.weight} onChange={(event) => onDraftChange({ ...draft, weight: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" />
        </label>
        <label className="text-xs text-slate-500">
          Unit
          <select value={draft.unit} onChange={(event) => onDraftChange({ ...draft, unit: event.target.value as BodyWeightUnit })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none">
            <option value="kg">kg</option>
            <option value="lb">lb</option>
          </select>
        </label>
      </div>
      <label className="block text-xs text-slate-500">
        Note
        <input type="text" maxLength={160} value={draft.note} onChange={(event) => onDraftChange({ ...draft, note: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" />
      </label>
      <div className="grid grid-cols-[1fr_auto] gap-2">
        <button type="button" onClick={onSave} className="rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400">{draft.id == null ? 'Log body weight' : 'Update body weight'}</button>
        {draft.id != null && <button type="button" onClick={() => onDraftChange(initialBodyMetricDraft())} className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-slate-800">Cancel</button>}
      </div>
      {result && <p className={`rounded-md border p-3 text-sm ${result.error ? 'border-rose-900 bg-rose-950/40 text-rose-300' : 'border-emerald-900 bg-emerald-950/40 text-emerald-300'}`}>{result.message}</p>}

      {entries.length === 0 ? <p className="rounded-md border border-slate-800 bg-slate-950 p-3 text-sm text-slate-500">No body-weight entries in this range.</p> : <div className="space-y-2">
        {entries.slice(0, 8).map((entry) => (
          <div key={entry.id ?? `${entry.date}-${entry.weight}-${entry.unit}`} className="grid grid-cols-[1fr_auto_auto] items-center gap-2 rounded-md border border-slate-800 bg-slate-950 p-2 text-sm">
            <div className="min-w-0">
              <p className="font-semibold text-slate-200">{entry.weight} {entry.unit}</p>
              <p className="truncate text-xs text-slate-600">{formatHistoryDate(entry.date)}{entry.note ? ` · ${entry.note}` : ''}</p>
            </div>
            <button type="button" onClick={() => onEdit(entry)} className="rounded-md px-2 py-1 text-xs text-slate-300 hover:bg-slate-800">Edit</button>
            <button type="button" onClick={() => onDelete(entry)} className="rounded-md px-2 py-1 text-xs text-rose-300 hover:bg-rose-950">Delete</button>
          </div>
        ))}
      </div>}
    </div>
  );
}

function StrengthAnalyticsPanel({
  analytics,
  personalRecords,
}: {
  analytics: StrengthExerciseAnalytics[];
  personalRecords: StrengthPersonalRecord[];
}) {
  const visibleAnalytics = analytics.slice(0, 4);
  return (
    <div className="space-y-3">
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Strength Analytics</h3>
        <span className="text-xs text-slate-500">{analytics.length} weighted exercise{analytics.length === 1 ? '' : 's'}</span>
      </div>
      {analytics.length === 0 ? <p className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm text-slate-500">No weighted set logs in this range yet.</p> : <>
        {personalRecords.length > 0 && <div className="rounded-lg border border-emerald-900 bg-emerald-950/20 p-3">
          <div className="mb-2 flex items-center justify-between gap-3">
            <p className="text-sm font-semibold text-emerald-200">Recent PRs</p>
            <span className="text-xs text-emerald-400">{personalRecords.length}</span>
          </div>
          <div className="space-y-1">
            {personalRecords.slice(0, 4).map((record) => (
              <div key={`${record.exercise}-${record.type}-${record.date}-${record.value}`} className="grid grid-cols-[1fr_auto] gap-2 text-xs">
                <span className="min-w-0 truncate text-slate-200">{record.exercise} · {record.label}</span>
                <span className="shrink-0 text-emerald-300">{record.value}</span>
              </div>
            ))}
          </div>
        </div>}
        {visibleAnalytics.map((item) => <StrengthExerciseCard key={`${item.exercise}-${item.unit}`} item={item} />)}
      </>}
    </div>
  );
}

function StrengthExerciseCard({ item }: { item: StrengthExerciseAnalytics }) {
  const maxTrendValue = Math.max(1, ...item.trend.map((point) => point.volume));
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-3">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-slate-200">{item.exercise}</p>
          <p className="text-xs text-slate-500">{item.setCount} weighted set{item.setCount === 1 ? '' : 's'} · {item.unit}</p>
        </div>
        <span className="shrink-0 text-xs text-slate-600">{formatHistoryDate(item.latestDate)}</span>
      </div>
      <div className="mb-3 grid grid-cols-3 gap-2 text-center text-xs">
        {item.records.map((record) => (
          <div key={record.label} className="rounded-md border border-slate-800 bg-slate-950 p-2">
            <p className="truncate font-semibold text-slate-200">{record.value}</p>
            <p className="mt-1 text-[10px] text-slate-600">{record.label}</p>
          </div>
        ))}
      </div>
      <div className="grid grid-cols-6 items-end gap-1">
        {item.trend.map((point) => (
          <div key={point.key} className="grid gap-1 text-center">
            <div className="flex h-16 items-end rounded bg-slate-950 p-1" title={`${point.label}: ${Math.round(point.volume)} ${point.unit} volume`}>
              <div className="w-full rounded bg-indigo-400" style={{ height: `${Math.max(8, (point.volume / maxTrendValue) * 100)}%` }} />
            </div>
            <p className="truncate text-[10px] text-slate-600">{point.label}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function ActivityCalendar({ days, range }: { days: ActivityDay[]; range: HistoryRange }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-slate-200">Activity Calendar</h3>
        <span className="text-xs text-slate-500">{range === 'month' ? 'This month' : 'Last 35 days'}</span>
      </div>
      <div className="grid grid-cols-7 gap-1">
        {days.map((day) => {
          const total = day.done + day.skipped;
          const shade = day.done > 0 ? (day.done >= 3 ? 'bg-emerald-400' : day.done >= 2 ? 'bg-emerald-600' : 'bg-emerald-800') : day.skipped > 0 ? 'bg-amber-700' : 'bg-slate-800';
          return <div key={day.key} title={`${day.label}: ${day.done} done, ${day.skipped} skipped`} className={`aspect-square rounded ${shade} ${total > 0 ? 'ring-1 ring-slate-700' : ''}`} />;
        })}
      </div>
      <div className="mt-3 flex items-center justify-between text-[11px] text-slate-600">
        <span>{days[0] ? formatShortDateKey(days[0].key) : ''}</span>
        <span>{days.at(-1) ? formatShortDateKey(days.at(-1)!.key) : ''}</span>
      </div>
    </div>
  );
}

function PeriodComparison({ comparisons }: { comparisons: Array<{ current: PeriodStats; previous: PeriodStats }> }) {
  return (
    <div className="space-y-2">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Period Comparison</h3>
      <div className="grid gap-2 sm:grid-cols-2">
        {comparisons.map(({ current, previous }) => (
          <div key={current.label} className="rounded-lg border border-slate-800 bg-slate-900 p-3">
            <div className="mb-2 flex items-center justify-between gap-3">
              <p className="text-sm font-semibold text-slate-200">{current.label}</p>
              <span className="text-xs text-slate-500">vs {previous.label.toLowerCase()}</span>
            </div>
            <div className="grid grid-cols-3 gap-2 text-center text-xs">
              <div><p className="font-semibold text-emerald-300">{current.done}</p><p className="text-slate-600">Done {deltaLabel(current.done, previous.done)}</p></div>
              <div><p className="font-semibold text-slate-300">{current.days}</p><p className="text-slate-600">Days {deltaLabel(current.days, previous.days)}</p></div>
              <div><p className="font-semibold text-indigo-300">{current.sessions}</p><p className="text-slate-600">Sessions {deltaLabel(current.sessions, previous.sessions)}</p></div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function TrendBars({ title, detail, buckets, maxTotal }: { title: string; detail: string; buckets: TrendBucket[]; maxTotal: number }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-slate-200">{title}</h3>
        <span className="text-xs text-slate-500">{detail}</span>
      </div>
      <div className="grid grid-cols-6 items-end gap-2">
        {buckets.map((bucket) => {
          const total = bucket.done + bucket.skipped + bucket.sessions;
          return (
            <div key={bucket.key} className="grid gap-1 text-center">
              <div className="flex h-24 items-end rounded-md bg-slate-950 p-1">
                <div className="w-full rounded bg-emerald-500" style={{ height: `${Math.max(6, (total / maxTotal) * 100)}%` }} />
              </div>
              <p className="truncate text-[10px] text-slate-600">{bucket.label}</p>
              <p className="text-xs font-semibold text-slate-300">{total}</p>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function BalanceBreakdown({ stats, maxTotal }: { stats: BalanceStat[]; maxTotal: number }) {
  return (
    <div className="space-y-2">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Muscle Balance</h3>
      {stats.length === 0 ? <p className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm text-slate-500">No muscle/category balance available yet.</p> : stats.map((item) => {
        const total = item.done + item.skipped;
        return (
          <div key={item.label} className="rounded-lg border border-slate-800 bg-slate-900 p-3">
            <div className="mb-1.5 flex justify-between gap-2 text-sm">
              <span className="truncate font-medium">{item.label}</span>
              <span className="shrink-0 text-slate-500">{item.done} done · {item.skipped} skipped</span>
            </div>
            <div className="h-1.5 overflow-hidden rounded-full bg-slate-800">
              <div className="h-full bg-teal-400" style={{ width: `${Math.max(4, (total / maxTotal) * 100)}%` }} />
            </div>
          </div>
        );
      })}
    </div>
  );
}

function QuestProgress({
  questState,
  activeQuestTemplate,
  completions,
  percent,
  levelLabels,
}: {
  questState: QuestState | null;
  activeQuestTemplate?: QuestTemplate;
  completions: QuestState['completedDays'];
  percent: number;
  levelLabels: Record<ExerciseLevel, string>;
}) {
  return (
    <div className="space-y-2">
      <div className="flex items-baseline justify-between">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Quest Progress</h3>
        {questState && activeQuestTemplate && <span className="text-xs text-slate-500">{questState.status}</span>}
      </div>
      {!questState || !activeQuestTemplate ? <p className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm text-slate-500">No quest progress yet.</p> : <div className="space-y-2 rounded-lg border border-slate-800 bg-slate-900 p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">{activeQuestTemplate.title}</p>
            <p className="text-xs text-slate-500">{questState.completedDays.length} of {activeQuestTemplate.durationWeeks * activeQuestTemplate.daysPerWeek} days complete · {levelLabels[questState.level]}</p>
          </div>
          <span className="shrink-0 text-sm font-semibold text-emerald-400">{percent}%</span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-slate-800">
          <div className="h-full bg-emerald-500" style={{ width: `${percent}%` }} />
        </div>
        {completions.length === 0 ? <p className="pt-1 text-xs text-slate-500">No completed quest days in this range.</p> : <div className="space-y-1 pt-1">
          {completions.map((day) => (
            <div key={day.dayIndex} className="flex items-center justify-between gap-3 text-xs">
              <span className="min-w-0 truncate text-slate-300">{day.dayLabel}</span>
              <span className="shrink-0 text-slate-500">{levelLabels[day.level]} · {formatHistoryDate(day.completedAt)}</span>
            </div>
          ))}
        </div>}
      </div>}
    </div>
  );
}

function WorkoutSessions({ events }: { events: WorkoutSessionEvent[] }) {
  return (
    <div className="space-y-2">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Workout Sessions</h3>
      {events.length === 0 ? <p className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm text-slate-500">No workout session summaries in this range yet.</p> : <div className="space-y-2">
        {events.map((event) => (
          <div key={event.id ?? `${event.timestamp}-${event.workoutEntryId}`} className="rounded-lg border border-slate-800 bg-slate-900 p-3">
            <div className="flex items-start justify-between gap-3 text-sm">
              <div className="min-w-0">
                <p className="font-semibold text-slate-200">{formatSessionEventType(event)} workout</p>
                <p className="truncate text-xs text-slate-500">{event.currentExercise ?? 'Workout'} · set {event.currentSet} · exercise {event.exerciseIndex + 1} of {event.totalExercises}</p>
              </div>
              <span className="shrink-0 text-xs text-slate-500">{formatHistoryTime(event.timestamp)}</span>
            </div>
            <div className="mt-2 flex items-center justify-between gap-3 text-xs">
              <span className="text-slate-500">{formatSessionStopReason(event.stopReason)}</span>
              <span className="font-medium text-emerald-300">{formatDuration(event.elapsedSeconds)}</span>
            </div>
            {event.estimatedDurationSeconds != null && <div className="mt-2 grid grid-cols-3 gap-2 rounded-md border border-slate-800 bg-slate-950 p-2 text-center text-xs">
              <div>
                <p className="font-semibold text-slate-200">{formatDuration(event.elapsedSeconds)}</p>
                <p className="text-slate-600">Actual</p>
              </div>
              <div>
                <p className="font-semibold text-slate-200">{formatDuration(event.estimatedDurationSeconds)}</p>
                <p className="text-slate-600">Estimate</p>
              </div>
              <div>
                <p className="font-semibold text-emerald-300">{formatActualVsEstimated(event)}</p>
                <p className="text-slate-600">Pace</p>
              </div>
            </div>}
          </div>
        ))}
      </div>}
    </div>
  );
}

function RecentActivity({ groups }: { groups: ReturnType<typeof buildHistoryDateGroups> }) {
  return (
    <div className="space-y-2">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Recent Activity</h3>
      {groups.length === 0 ? <p className="rounded-lg border border-slate-800 bg-slate-900 p-6 text-center text-sm text-slate-500">No logged workouts in this range yet.</p> : groups.map((group) => (
        <div key={group.key} className="rounded-lg border border-slate-800 bg-slate-900 p-3">
          <div className="mb-2 flex items-center justify-between gap-3 text-sm">
            <span className="font-semibold text-slate-200">{group.label}</span>
            <span className="text-xs text-slate-500">{group.done} done · {group.skipped} skipped</span>
          </div>
          <div className="space-y-1">
            {group.logs.map((log) => (
              <div key={log.id ?? `${log.date}-${log.exercise}-${log.status}`} className="grid grid-cols-[auto_1fr_auto] items-center gap-2 text-xs">
                <span className={`size-2 rounded-full ${log.status === 'done' ? 'bg-emerald-400' : 'bg-amber-400'}`} />
                <span className="min-w-0 truncate text-slate-300">{log.exercise}</span>
                <span className="shrink-0 text-slate-600">{formatHistoryTime(log.date)}</span>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

function ExerciseBreakdown({ items }: { items: Array<[string, { done: number; skipped: number }]> }) {
  return (
    <div className="space-y-2">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Per-exercise Breakdown</h3>
      {items.length === 0 ? <p className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm text-slate-500">No exercise breakdown available yet.</p> : items.map(([exercise, stats]) => {
        const total = stats.done + stats.skipped;
        const donePercent = total ? Math.round((stats.done / total) * 100) : 0;
        return (
          <div key={exercise} className="rounded-lg border border-slate-800 bg-slate-900 p-3">
            <div className="mb-1.5 flex justify-between gap-2 text-sm">
              <span className="truncate font-medium">{exercise}</span>
              <span className="shrink-0 text-slate-500">{stats.done} done · {stats.skipped} skipped</span>
            </div>
            <div className="h-1.5 overflow-hidden rounded-full bg-slate-800">
              <div className="h-full bg-emerald-500" style={{ width: `${donePercent}%` }} />
            </div>
          </div>
        );
      })}
    </div>
  );
}
