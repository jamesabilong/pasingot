export type PlateCalculation =
  | { error: string }
  | { plates: { weight: number; count: number }[]; achieved: number; remaining: number };

// Integer hundredths avoid fractional-weight drift. Each denomination represents
// an unlimited supply of pairs; choose the closest lower load, then fewest plates.
export function calculatePlates(target: number, bar: number, sizes: number[]): PlateCalculation {
  const validWeight = (value: number) => Number.isFinite(value) && value >= 0 && value <= 1000
    && Math.abs(value * 100 - Math.round(value * 100)) < 1e-7;
  if (!validWeight(target) || !validWeight(bar)) return { error: 'Enter weights from 0 to 1000 with up to two decimal places.' };
  if (target < bar) return { error: 'Target weight must be at least the bar weight.' };
  if (!sizes.length || sizes.length > 20 || sizes.some((size) => !validWeight(size) || size <= 0)) {
    return { error: 'Enter 1 to 20 positive plate sizes, up to 1000 each, with up to two decimal places.' };
  }
  const denominations = [...new Set(sizes.map((size) => Math.round(size * 100)))].sort((a, b) => b - a);
  const load = Math.floor((Math.round(target * 100) - Math.round(bar * 100)) / 2);
  const counts = new Float64Array(load + 1).fill(Infinity);
  const choice = new Int32Array(load + 1);
  counts[0] = 0;
  for (let amount = 1; amount <= load; amount++) {
    for (const size of denominations) {
      if (size <= amount && counts[amount - size] + 1 < counts[amount]) {
        counts[amount] = counts[amount - size] + 1;
        choice[amount] = size;
      }
    }
  }
  let achievable = load;
  while (!Number.isFinite(counts[achievable])) achievable--;
  const used = new Map<number, number>();
  for (let amount = achievable; amount > 0; amount -= choice[amount]) {
    const size = choice[amount];
    used.set(size, (used.get(size) ?? 0) + 1);
  }
  const achievedCents = Math.round(bar * 100) + achievable * 2;
  return {
    plates: [...used].map(([size, count]) => ({ weight: size / 100, count })),
    achieved: achievedCents / 100,
    remaining: (Math.round(target * 100) - achievedCents) / 100,
  };
}
