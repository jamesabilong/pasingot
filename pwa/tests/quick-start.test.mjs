import test from 'node:test';
import assert from 'node:assert/strict';
import { activeOffer, buildRequest, itemsFromRequest, receiptFromRecord, statusText, validateItems } from '../src/features/watch-quick-start/model.ts';

const item = (name, index) => ({ itemId: `item-${index}`, sourceId: index, name, sets: 3, reps: '8-12', rest: 60 });

test('selection preserves order and rejects every invalid prescription atomically', () => {
  const items = [item('Squat', 1), item('Press', 2)];
  assert.deepEqual(buildRequest(items, 'library_selection', 'watch-1', 1000).exercises.map((entry) => entry.exerciseName), ['Squat', 'Press']);
  assert.match(validateItems([items[0], { ...items[1], rest: -1 }]), /Exercise 2: rest/);
  assert.match(validateItems([items[0], { ...items[1], itemId: items[0].itemId }]), /duplicate/);
  assert.match(validateItems(Array.from({ length: 25 }, (_, index) => item(`Item ${index}`, index))), /1 to 24/);
  assert.equal(buildRequest([{ ...items[0], name: 'A'.repeat(100) }], 'single', 'watch-1').title.length, 80);
});

test('mocked bridge restoration distinguishes transport and every watch status', async () => {
  const requestId = '123e4567-e89b-12d3-a456-426614174000';
  const pending = { requestId, transportAcceptedAtMillis: 1100, acknowledgement: null };
  let stored = pending;
  const bridge = { getQuickStartStatus: async () => stored };
  assert.match(statusText(await bridge.getQuickStartStatus()), /Waiting for watch/);
  const cases = [
    ['ready', undefined, /Ready on watch/],
    ['started', undefined, /started on watch/],
    ['dismissed', undefined, /Dismissed on watch/],
    ['cancelled', undefined, /Cancelled on watch/],
    ['expired', undefined, /expired/],
    ['rejected', 'active_session', /active workout/],
    ['rejected', 'pending_request', /pending request/],
    ['rejected', 'unsupported_schema', /Update the watch app/],
    ['rejected', 'invalid_payload', /invalid exercise details/],
    ['rejected', 'storage_error', /could not save/],
  ];
  for (const [status, reason, wording] of cases) {
    stored = { ...pending, acknowledgement: { requestId, revision: 1, targetNodeId: 'watch-1', status, reason, watchUpdatedAtMillis: 1200 } };
    assert.match(statusText(await bridge.getQuickStartStatus()), wording);
  }
});

test('process recovery restores the exact offer and pending state', () => {
  const request = buildRequest([item('Squat', 1), item('Press', 2)], 'library_selection', 'watch-1', 1000);
  const record = { request, transportAcceptedAtMillis: 1100, acknowledgement: null };
  assert.equal(activeOffer(record, 1200), true);
  assert.deepEqual(itemsFromRequest(request).map((entry) => entry.name), ['Squat', 'Press']);
  assert.equal(receiptFromRecord(record).requestId, request.requestId);
  assert.equal(activeOffer(record, request.expiresAtMillis + 30_001), false);
  assert.match(statusText(receiptFromRecord(record), false, request.expiresAtMillis + 30_001), /expired/);
  assert.match(statusText(receiptFromRecord({ ...record, acknowledgement: { requestId: request.requestId,
    targetNodeId: 'watch-1', revision: 1, status: 'ready', watchUpdatedAtMillis: 1200 } }),
    false, request.expiresAtMillis + 30_001), /expired/);
  assert.equal(activeOffer({ ...record, acknowledgement: { requestId: request.requestId,
    targetNodeId: 'watch-1', revision: 2, status: 'started', watchUpdatedAtMillis: 1200 } }, 1200), false);
  assert.match(statusText({ requestId: request.requestId, transportAcceptedAtMillis: null,
    acknowledgement: null }), /interrupted/);
});
