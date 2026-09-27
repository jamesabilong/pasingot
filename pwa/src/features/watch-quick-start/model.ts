import type { ExerciseCatalogItem, PlaylistItem, WorkoutRow } from '../../types';

export type QuickStartSource = 'single' | 'library_playlist' | 'library_selection' | 'today_row';
export type QuickStartStatus = 'ready' | 'started' | 'dismissed' | 'cancelled' | 'rejected' | 'expired';
export interface QuickStartItem extends PlaylistItem { itemId: string; sourceDate?: string; sourceWorkoutRowId?: number }
export interface QuickStartRequest {
  requestId: string; schemaVersion: 1; revision: 1; createdAtMillis: number;
  expiresAtMillis: number; targetNodeId: string; title: string;
  source: QuickStartSource; exercises: Array<{
    itemId: string; exerciseId: string; exerciseName: string; sets: number;
    prescription: string; restSeconds: number; loadWeight?: number | null;
    loadUnit?: 'kg' | 'lb' | null; sourceDate?: string; sourceWorkoutRowId?: number;
  }>;
}
export interface QuickStartAck { requestId: string; revision: number; targetNodeId: string; status: QuickStartStatus; reason?: string; watchUpdatedAtMillis: number }
export interface QuickStartReceipt { requestId: string; transportAcceptedAtMillis: number | null; acknowledgement: QuickStartAck | null; expiresAtMillis?: number }
export interface PhoneQuickStartRecord { request: QuickStartRequest; transportAcceptedAtMillis: number | null; acknowledgement: QuickStartAck | null }

export function receiptFromRecord(record: PhoneQuickStartRecord): QuickStartReceipt {
  return { requestId: record.request.requestId, transportAcceptedAtMillis: record.transportAcceptedAtMillis,
    acknowledgement: record.acknowledgement, expiresAtMillis: record.request.expiresAtMillis };
}
export function itemsFromRequest(request: QuickStartRequest): QuickStartItem[] {
  return request.exercises.map((exercise) => ({ itemId: exercise.itemId,
    sourceId: Number(exercise.exerciseId) || 0, name: exercise.exerciseName,
    sets: exercise.sets, reps: exercise.prescription, rest: exercise.restSeconds,
    loadWeight: exercise.loadWeight, loadUnit: exercise.loadUnit,
    sourceDate: exercise.sourceDate, sourceWorkoutRowId: exercise.sourceWorkoutRowId }));
}
export function activeOffer(record: PhoneQuickStartRecord, now = Date.now()): boolean {
  if (now > record.request.expiresAtMillis + 30_000) return false;
  return record.acknowledgement?.status === 'ready' || record.acknowledgement == null;
}

export function fromCatalog(item: ExerciseCatalogItem, prescription: Omit<PlaylistItem, 'sourceId' | 'name'>): QuickStartItem {
  return { itemId: crypto.randomUUID(), sourceId: item.sourceId, name: item.displayName, ...prescription };
}
export function fromPlaylist(items: PlaylistItem[]): QuickStartItem[] {
  return items.map((item) => ({ ...item, itemId: crypto.randomUUID() }));
}
export function fromToday(row: WorkoutRow, date: string): QuickStartItem {
  return { itemId: crypto.randomUUID(), sourceId: row.exerciseSourceId ?? 0, name: row.exercise,
    sets: row.sets, reps: row.reps, rest: row.rest, loadWeight: row.loadWeight, loadUnit: row.loadUnit,
    sourceDate: date, sourceWorkoutRowId: row.id };
}
export function validateItems(items: QuickStartItem[]): string | null {
  if (items.length < 1 || items.length > 24) return 'Choose 1 to 24 exercises.';
  const ids = new Set<string>();
  for (const [index, item] of items.entries()) {
    const label = `Exercise ${index + 1}`;
    if (ids.has(item.itemId)) return `${label}: duplicate item.`;
    ids.add(item.itemId);
    if (!item.name.trim() || item.name.length > 120) return `${label}: enter a name up to 120 characters.`;
    if (!Number.isInteger(item.sets) || item.sets < 1 || item.sets > 99) return `${label}: sets must be 1–99.`;
    if (!item.reps.trim() || item.reps.length > 64) return `${label}: enter a reps or duration target.`;
    if (!Number.isInteger(item.rest) || item.rest < 0 || item.rest > 3600) return `${label}: rest must be 0–3600 seconds.`;
    if (item.loadWeight != null && (!Number.isFinite(item.loadWeight) || item.loadWeight <= 0 || item.loadWeight > 2000 || !['kg', 'lb'].includes(item.loadUnit ?? ''))) return `${label}: enter a valid load and unit.`;
    if (item.loadWeight == null && item.loadUnit != null) return `${label}: load unit needs a weight.`;
    if (item.sourceDate && (!item.sourceWorkoutRowId || item.sourceWorkoutRowId <= 0)) return `${label}: scheduled row is missing its ID.`;
  }
  return null;
}
export function buildRequest(items: QuickStartItem[], source: QuickStartSource, targetNodeId: string, now = Date.now()): QuickStartRequest {
  const error = validateItems(items);
  if (error) throw new Error(error);
  if (!targetNodeId.trim()) throw new Error('Choose a connected watch.');
  return { requestId: crypto.randomUUID(), schemaVersion: 1, revision: 1,
    createdAtMillis: now, expiresAtMillis: now + 300_000, targetNodeId,
    title: items.length === 1 ? items[0].name.trim().slice(0, 80) : `Quick Start · ${items.length} exercises`, source,
    exercises: items.map((item) => ({ itemId: item.itemId,
      exerciseId: String(item.sourceId || item.name), exerciseName: item.name.trim(), sets: item.sets,
      prescription: item.reps.trim(), restSeconds: item.rest, loadWeight: item.loadWeight ?? null,
      loadUnit: item.loadWeight == null ? null : item.loadUnit ?? null,
      ...(item.sourceDate ? { sourceDate: item.sourceDate, sourceWorkoutRowId: item.sourceWorkoutRowId } : {}),
    })) };
}
export function statusText(receipt: QuickStartReceipt | null, sending = false, now = Date.now()): string {
  if (sending) return 'Sending to watch…';
  if (!receipt) return 'Confirm the exercises before sending.';
  const ack = receipt.acknowledgement;
  if (receipt.expiresAtMillis && now > receipt.expiresAtMillis + 30_000 && (!ack || ack.status === 'ready')) return 'Request expired. Send a new one.';
  if (!ack) return receipt.transportAcceptedAtMillis ? 'Sent. Waiting for watch confirmation…' : 'Delivery is unconfirmed. Waiting for watch confirmation…';
  switch (ack.status) {
    case 'ready': return 'Ready on watch. Tap Start there.';
    case 'started': return 'Workout started on watch.';
    case 'dismissed': return 'Dismissed on watch.';
    case 'cancelled': return 'Cancelled on watch.';
    case 'expired': return 'Request expired. Send a new one.';
    case 'rejected': return ({ active_session: 'Watch already has an active workout.', pending_request: 'Watch already has a pending request.', unsupported_schema: 'Update the watch app.', invalid_payload: 'Watch rejected invalid exercise details.', storage_error: 'Watch could not save the request.' } as Record<string, string>)[ack.reason ?? ''] ?? 'Watch rejected the request.';
  }
}
