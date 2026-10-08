import test from 'node:test';
import assert from 'node:assert/strict';
import { filterSetHistory } from '../src/lib/set-history.ts';
const logs = [
  { id: 1, date: '2026-10-08T10:00:00+08:00', exercise: 'Squat', rir: 0 },
  { id: 2, date: '2026-10-08T05:00:00Z', exercise: 'Walking' },
  { id: 3, date: '2026-10-08T05:00:00Z', exercise: 'Press', rpe: 8.5 },
];
test('sorts by actual timestamp and stable ID without changing input', () => {
  const before = JSON.stringify(logs);
  assert.deepEqual(filterSetHistory(logs, '', false).map(log => log.id), [3, 2, 1]);
  assert.equal(JSON.stringify(logs), before);
});
test('unrated and duration sets are included by default; zero reserve is rated', () => {
  assert.equal(filterSetHistory(logs, '', false).length, 3);
  assert.deepEqual(filterSetHistory(logs, '', true).map(log => log.id), [3, 1]);
});
test('trimmed case-insensitive exercise search combines with rated filter', () => {
  assert.deepEqual(filterSetHistory(logs, '  WALK  ', false).map(log => log.id), [2]);
  assert.deepEqual(filterSetHistory(logs, 'walk', true), []);
  assert.deepEqual(filterSetHistory(logs, 'absent', false), []);
});
