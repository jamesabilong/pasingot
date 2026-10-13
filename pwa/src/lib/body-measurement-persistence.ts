import type { BodyMeasurementEntry } from '../types';
import { validBodyMeasurement } from './body-measurements';
import { STORES, transact } from './db';

function sameEntry(a: BodyMeasurementEntry | undefined, b: BodyMeasurementEntry): boolean {
  return !!a && a.id === b.id && a.schemaVersion === b.schemaVersion && a.date === b.date
    && a.site === b.site && a.value === b.value && a.unit === b.unit && a.note === b.note;
}

/** Compare the opened record inside the write transaction; never recreate a deleted edit. */
export async function saveMeasurement(next: BodyMeasurementEntry | null, expected?: BodyMeasurementEntry): Promise<void> {
  if (next && !validBodyMeasurement(next)) throw new Error('Enter a valid measurement.');
  if (!next && !expected) throw new Error('Choose a measurement to delete.');
  if (expected && expected.id == null) throw new Error('Reload measurements before editing.');
  let conflict = false;
  try {
    await transact([STORES.bodyMeasurements], transaction => {
      const store = transaction.objectStore(STORES.bodyMeasurements);
      if (!expected) {
        const record = { ...next! };
        delete record.id;
        store.add(record);
        return;
      }
      const request = store.get(expected.id!);
      request.onsuccess = () => {
        if (!sameEntry(request.result, expected)) { conflict = true; transaction.abort(); return; }
        if (next) store.put({ ...next, id: expected.id });
        else store.delete(expected.id!);
      };
    });
  } catch (error) {
    if (conflict) throw new Error('This measurement changed or was removed. Reload measurements before editing.');
    throw error;
  }
}
