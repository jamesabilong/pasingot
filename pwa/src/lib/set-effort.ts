export function validRpe(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 1 && value <= 10 && Number.isInteger(value * 2);
}
export function validRir(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 10;
}
export function parseSetEffort(input: { rpe?: string; rir?: string }):
  { rpe: number | null; rir: number | null; error: null } | { error: string } {
  const rpe = input.rpe?.trim() ? Number(input.rpe) : null;
  const rir = input.rir?.trim() ? Number(input.rir) : null;
  if (rpe != null && !validRpe(rpe)) return { error: 'RPE must be 1 to 10 in steps of 0.5, or blank.' };
  if (rir != null && !validRir(rir)) return { error: 'Reps in reserve must be a whole number from 0 to 10, or blank.' };
  return { rpe, rir, error: null };
}
