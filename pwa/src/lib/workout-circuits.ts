import type { ScheduleDateOverrides, WorkoutCircuit } from '../types';

export interface CircuitItem {
  sets: number;
  rest?: number;
  circuit?: WorkoutCircuit;
  day?: string;
  time?: string;
  dateOverrides?: ScheduleDateOverrides;
  questId?: string;
}

export interface WorkoutSessionStep {
  exerciseIndex: number;
  setNumber: number;
  restSeconds: number;
}

/** Optional metadata remains absent on every legacy straight-set prescription. */
export function validCircuitMetadata(value: unknown): boolean {
  if (value === undefined) return true;
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const circuit = value as Record<string, unknown>;
  return typeof circuit.id === 'string' && circuit.id.length >= 1 && circuit.id.length <= 100
    && /^[A-Za-z0-9_-]+$/.test(circuit.id)
    && typeof circuit.restSeconds === 'number' && Number.isSafeInteger(circuit.restSeconds)
    && circuit.restSeconds >= 0 && circuit.restSeconds <= 3600;
}

function slot(item: CircuitItem): string {
  return JSON.stringify([item.day ?? '', item.time ?? '']);
}

/** Property order in imported JSON is not part of a prescription's identity. */
export function circuitValueSignature(value: unknown): string {
  const canonical = (input: unknown): unknown => Array.isArray(input) ? input.map(canonical)
    : input && typeof input === 'object' ? Object.fromEntries(Object.entries(input)
      .filter(([, entry]) => entry !== undefined).sort(([left], [right]) => left.localeCompare(right))
      .map(([key, entry]) => [key, canonical(entry)])) : input;
  return JSON.stringify(canonical(value));
}

export function circuitRange(items: readonly CircuitItem[], index: number): { start: number; end: number } | null {
  const item = items[index];
  if (!item?.circuit) return null;
  const same = (candidate: CircuitItem) => candidate.circuit?.id === item.circuit!.id && slot(candidate) === slot(item);
  let start = index;
  let end = index;
  while (start > 0 && same(items[start - 1])) start--;
  while (end + 1 < items.length && same(items[end + 1])) end++;
  return { start, end };
}

/** The same ID may be reused in another slot, but can never join separate slots. */
export function validateCircuitGroups(items: readonly CircuitItem[]): string | null {
  const slots = new Map<string, CircuitItem[]>();
  for (const item of items) {
    if (!validCircuitMetadata(item.circuit)) return 'A circuit has invalid group details.';
    const key = slot(item);
    const group = slots.get(key) ?? [];
    group.push(item);
    slots.set(key, group);
  }
  for (const members of slots.values()) {
    const seen = new Set<string>();
    for (let index = 0; index < members.length; index++) {
      const first = members[index];
      if (!first.circuit) continue;
      const id = first.circuit.id;
      if (seen.has(id)) return 'Keep each circuit together in the playlist.';
      seen.add(id);
      const range = circuitRange(members, index)!;
      const group = members.slice(range.start, range.end + 1);
      if (group.length < 2 || group.length > 24) return 'A circuit needs 2–24 exercises in the same session.';
      if (!Number.isSafeInteger(first.sets) || first.sets < 1 || first.sets > 100
        || group.some(item => item.sets !== first.sets)) return 'Circuit exercises must use the same 1–100 rounds.';
      if (group.some(item => item.questId != null)) return 'Quest exercises cannot be grouped into circuits.';
      if (group.some(item => item.circuit!.restSeconds !== first.circuit!.restSeconds)) return 'Circuit exercises must share one round rest.';
      const overrides = circuitValueSignature(first.dateOverrides ?? {});
      if (group.some(item => circuitValueSignature(item.dateOverrides ?? {}) !== overrides)) return 'Circuit exercises must share the same date changes.';
      index = range.end;
    }
  }
  return null;
}

/** Snapshot this sequence when starting a circuit workout so recovery keeps its order and rests. */
export function compileCircuitSteps(items: readonly CircuitItem[]): WorkoutSessionStep[] {
  const error = validateCircuitGroups(items);
  if (error) throw new Error(error);
  const steps: WorkoutSessionStep[] = [];
  for (let index = 0; index < items.length; index++) {
    const item = items[index];
    if (!Number.isSafeInteger(item.sets) || item.sets < 1 || item.sets > 100) {
      throw new Error('Workout plans containing circuits support 1–100 sets per exercise.');
    }
    const range = circuitRange(items, index);
    if (range) {
      for (let round = 1; round <= item.sets; round++) {
        for (let member = range.start; member <= range.end; member++) {
          steps.push({ exerciseIndex: member, setNumber: round, restSeconds: member === range.end ? item.circuit!.restSeconds : 0 });
        }
      }
      index = range.end;
    } else {
      for (let setNumber = 1; setNumber <= item.sets; setNumber++) {
        steps.push({ exerciseIndex: index, setNumber, restSeconds: Math.max(0, item.rest ?? 0) });
      }
    }
  }
  if (steps.length) steps[steps.length - 1].restSeconds = 0;
  return steps;
}
