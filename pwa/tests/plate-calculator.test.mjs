import test from 'node:test';
import assert from 'node:assert/strict';
import { calculatePlates } from '../src/lib/plate-calculator.ts';

test('metric and imperial totals include the bar and equal sides', () => {
  assert.deepEqual(calculatePlates(60, 20, [25, 20, 10]), { plates: [{ weight: 20, count: 1 }], achieved: 60, remaining: 0 });
  assert.deepEqual(calculatePlates(135, 45, [45, 35, 25, 10, 5]), { plates: [{ weight: 45, count: 1 }], achieved: 135, remaining: 0 });
});
test('noncanonical sizes find exact solutions and minimum plate count', () => {
  assert.deepEqual(calculatePlates(32, 20, [5, 3]), { plates: [{ weight: 3, count: 2 }], achieved: 32, remaining: 0 });
  assert.deepEqual(calculatePlates(36, 20, [6, 4, 1]), { plates: [{ weight: 4, count: 2 }], achieved: 36, remaining: 0 });
});
test('unreachable targets never overload and fractional loads remain exact', () => {
  assert.deepEqual(calculatePlates(61, 20, [20, 5]), { plates: [{ weight: 20, count: 1 }], achieved: 60, remaining: 1 });
  assert.deepEqual(calculatePlates(20.31, 20, [0.1, 0.05]), { plates: [{ weight: 0.1, count: 1 }, { weight: 0.05, count: 1 }], achieved: 20.3, remaining: 0.01 });
  assert.deepEqual(calculatePlates(20, 20, [5]), { plates: [], achieved: 20, remaining: 0 });
});
test('invalid, blank, excessive and sub-cent precision values are rejected', () => {
  for (const args of [[19, 20, [5]], [NaN, 20, [5]], [1001, 20, [5]], [60, -1, [5]], [60, 20, []], [60, 20, [0]], [60, 20, [NaN]], [60, 20, [1.001]], [60.001, 20, [5]], [60, 20, Array(21).fill(5)]]) assert.ok('error' in calculatePlates(...args));
});
test('duplicate sizes and input order do not change the allocation', () => {
  assert.deepEqual(calculatePlates(100, 20, [5, 20, 20, 10]), calculatePlates(100, 20, [20, 10, 5]));
});
