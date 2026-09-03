import { type ExerciseLevel } from '../types';

export interface PlanProgress {
  total: number;
  completed: number;
  pending: number;
  skipped: number;
  resolvedPercent: number;
}

function estimateDisplayValue(value: string): string {
  return value.replace(/^Est\.\s*/, '');
}

export function EstimateSummary({ value }: { value: string }) {
  return (
    <div className="estimate-summary">
      <span className="estimate-summary__label">Estimated Time</span>
      <strong className="estimate-summary__value">{estimateDisplayValue(value)}</strong>
      <span className="estimate-summary__note">with buffer</span>
    </div>
  );
}

export function LevelPicker({
  levels,
  levelLabels,
  selected,
  onSelect,
  counts,
  variant = 'default',
}: {
  levels: ExerciseLevel[];
  levelLabels: Record<ExerciseLevel, string>;
  selected: ExerciseLevel;
  onSelect: (level: ExerciseLevel) => void;
  counts?: Record<ExerciseLevel, number>;
  variant?: 'default' | 'compact';
}) {
  const wrapperClass = variant === 'compact'
    ? 'grid grid-cols-3 gap-1 rounded-md border border-slate-800 bg-slate-950 p-1 text-xs font-medium'
    : 'grid grid-cols-3 gap-1 rounded-lg border border-slate-800 bg-slate-900 p-1 text-xs font-medium';
  const buttonClass = variant === 'compact' ? 'rounded px-2 py-2' : 'rounded-md px-2 py-2 text-center';
  return (
    <div className={wrapperClass}>
      {levels.map((level) => (
        <button
          key={level}
          type="button"
          onClick={() => onSelect(level)}
          className={`${buttonClass} transition ${selected === level ? 'bg-emerald-500 text-slate-950' : 'text-slate-400 hover:bg-slate-800 hover:text-slate-100'}`}
        >
          <span className="block truncate">{levelLabels[level]}</span>
          {counts && <span className="block text-[10px] opacity-70">{counts[level] ?? 0}</span>}
        </button>
      ))}
    </div>
  );
}

export function PlanProgressSummary({ progress }: { progress: PlanProgress }) {
  if (progress.total === 0) return null;
  const completedPercent = (progress.completed / progress.total) * 100;
  const skippedPercent = (progress.skipped / progress.total) * 100;
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-3">
      <div className="mb-2 flex items-center justify-between gap-3">
        <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">Plan Progress</span>
        <span className="text-xs text-slate-500">{progress.resolvedPercent}% handled</span>
      </div>
      <div className="flex h-2 overflow-hidden rounded-full bg-slate-800">
        <div className="h-full bg-emerald-500" style={{ width: `${completedPercent}%` }} />
        <div className="h-full bg-amber-500" style={{ width: `${skippedPercent}%` }} />
      </div>
      <div className="mt-3 grid grid-cols-3 gap-2 text-center text-xs">
        <div><p className="text-base font-semibold text-emerald-300">{progress.completed}</p><p className="text-slate-500">Completed</p></div>
        <div><p className="text-base font-semibold text-slate-300">{progress.pending}</p><p className="text-slate-500">Pending</p></div>
        <div><p className="text-base font-semibold text-amber-300">{progress.skipped}</p><p className="text-slate-500">Skipped</p></div>
      </div>
    </div>
  );
}
