import type { WeightUnit } from '../types';

export type WarmupPrescription = { loadWeight?: number | null; loadUnit?: WeightUnit | null; reps: string };
export type WarmupDraft = { minimum: string; increment: string; stages: { percent: string; reps: string }[] };
export type WarmupSet = { percent: number; reps: number; load: number };

export function supportsWarmups(prescription: WarmupPrescription): boolean {
  const weight = prescription.loadWeight;
  const reps = prescription.reps.trim().match(/^(\d+)(?:\s*[-–]\s*(\d+))?$/);
  return typeof weight === 'number' && Number.isFinite(weight) && weight > 0 && weight <= 2000
    && (prescription.loadUnit === 'kg' || prescription.loadUnit === 'lb')
    && !!reps && Number(reps[1]) > 0 && (!reps[2] || Number(reps[2]) >= Number(reps[1]));
}

export function initialWarmupDraft(unit: WeightUnit): WarmupDraft {
  return { minimum: '0', increment: unit === 'kg' ? '2.5' : '5', stages: [
    { percent: '40', reps: '8' }, { percent: '60', reps: '5' }, { percent: '80', reps: '3' },
  ] };
}

const number = (value: string) => value.trim() ? Number(value) : NaN;
const hundredths = (value: number) => Number.isFinite(value) && Math.abs(value * 100 - Math.round(value * 100)) < 1e-7;

/** Preview only: rounds down on a load grid starting at the equipment minimum. */
export function suggestWarmupSets(prescription: WarmupPrescription, draft: WarmupDraft): { error: string } | { sets: WarmupSet[]; omitted: number } {
  if (!supportsWarmups(prescription)) return { error: 'Use a positive planned kg/lb load and a rep count or range.' };
  const working = prescription.loadWeight!;
  const minimum = number(draft.minimum), increment = number(draft.increment);
  if (!hundredths(minimum) || minimum < 0 || minimum >= working
    || !hundredths(increment) || increment <= 0 || increment > 2000) {
    return { error: 'Enter a minimum below the planned load and a positive increment, with up to two decimals.' };
  }
  if (draft.stages.length !== 3) return { error: 'Enter three increasing percentages from 1 to 99 and whole reps from 1 to 30.' };
  const stages = draft.stages.map(stage => ({ percent: number(stage.percent), reps: number(stage.reps) }));
  if (stages.some((stage, index) => !Number.isInteger(stage.percent) || stage.percent < 1 || stage.percent > 99
    || (index > 0 && stage.percent <= stages[index - 1].percent)
    || !Number.isInteger(stage.reps) || stage.reps < 1 || stage.reps > 30)) {
    return { error: 'Enter three increasing percentages from 1 to 99 and whole reps from 1 to 30.' };
  }
  const floor = Math.round(minimum * 100), step = Math.round(increment * 100);
  const sets: WarmupSet[] = [];
  for (const stage of stages) {
    // Percentage * weight is target hundredths; avoid rounding up to the target.
    const target = Math.floor(working * stage.percent + 1e-7);
    if (target < floor) continue;
    const cents = floor + Math.floor((target - floor) / step) * step;
    const load = cents / 100;
    if (load <= 0 || load >= working || sets.some(set => set.load === load)) continue;
    sets.push({ ...stage, load });
  }
  return { sets, omitted: stages.length - sets.length };
}
