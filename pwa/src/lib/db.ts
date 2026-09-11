const DB_NAME = 'workoutAppDB';
const DB_VERSION = 6;

export const STORES = {
  workouts: 'workouts',
  logs: 'logs',
  sessionEvents: 'sessionEvents',
  setLogs: 'setLogs',
  bodyMetrics: 'bodyMetrics',
  customExercises: 'customExercises',
  exercises: 'exercises',
  appState: 'appState',
} as const;

export type StoreName = (typeof STORES)[keyof typeof STORES];
function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORES.workouts)) db.createObjectStore(STORES.workouts, { keyPath: 'id', autoIncrement: true });
      if (!db.objectStoreNames.contains(STORES.logs)) db.createObjectStore(STORES.logs, { keyPath: 'id', autoIncrement: true });
      if (!db.objectStoreNames.contains(STORES.sessionEvents)) db.createObjectStore(STORES.sessionEvents, { keyPath: 'id', autoIncrement: true });
      if (!db.objectStoreNames.contains(STORES.setLogs)) db.createObjectStore(STORES.setLogs, { keyPath: 'id', autoIncrement: true });
      if (!db.objectStoreNames.contains(STORES.bodyMetrics)) db.createObjectStore(STORES.bodyMetrics, { keyPath: 'id', autoIncrement: true });
      if (!db.objectStoreNames.contains(STORES.customExercises)) db.createObjectStore(STORES.customExercises, { keyPath: 'sourceId' });
      if (!db.objectStoreNames.contains(STORES.exercises)) db.createObjectStore(STORES.exercises, { keyPath: 'sourceId' });
      if (!db.objectStoreNames.contains(STORES.appState)) db.createObjectStore(STORES.appState, { keyPath: 'key' });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function getAll<T>(storeName: StoreName): Promise<T[]> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(storeName, 'readonly');
    const request = transaction.objectStore(storeName).getAll() as IDBRequest<T[]>;
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    transaction.oncomplete = () => db.close();
    transaction.onerror = () => reject(transaction.error);
  });
}

export async function getRecord<T>(storeName: StoreName, key: IDBValidKey): Promise<T | undefined> {
  const db = await openDb();
  try {
    return await requestResult(db.transaction(storeName, 'readonly').objectStore(storeName).get(key) as IDBRequest<T | undefined>);
  } finally {
    db.close();
  }
}

async function write<T>(storeName: StoreName, operation: (store: IDBObjectStore) => IDBRequest<T> | void): Promise<T | undefined> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(storeName, 'readwrite');
    const request = operation(transaction.objectStore(storeName));
    let result: T | undefined;
    if (request) {
      request.onsuccess = () => { result = request.result; };
      request.onerror = () => reject(request.error);
    }
    transaction.oncomplete = () => { db.close(); resolve(result); };
    transaction.onerror = () => { db.close(); reject(transaction.error); };
    transaction.onabort = () => { db.close(); reject(transaction.error); };
  });
}

export function addRecord<T>(storeName: StoreName, record: T): Promise<IDBValidKey | undefined> {
  return write(storeName, (store) => store.add(record));
}

export function putRecord<T>(storeName: StoreName, record: T): Promise<IDBValidKey | undefined> {
  return write(storeName, (store) => store.put(record));
}

// Commit the received record and its receipt together. If the native ACK fails
// or the app closes before it is sent, replaying the same watch ID is harmless.
export async function addWatchRecord<T>(storeName: typeof STORES.logs | typeof STORES.sessionEvents, watchId: string, record: T): Promise<boolean> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction([storeName, STORES.appState], 'readwrite');
    const receipts = transaction.objectStore(STORES.appState);
    const key = `watchImport:${storeName}:${watchId}`;
    let inserted = false;
    const existing = receipts.get(key);
    existing.onsuccess = () => {
      if (existing.result) return;
      transaction.objectStore(storeName).add(record);
      receipts.put({ key, schemaVersion: 1 });
      inserted = true;
    };
    transaction.oncomplete = () => { db.close(); resolve(inserted); };
    transaction.onerror = () => { db.close(); reject(transaction.error); };
    transaction.onabort = () => { db.close(); reject(transaction.error); };
  });
}

export function deleteRecord(storeName: StoreName, key: IDBValidKey): Promise<void> {
  return write(storeName, (store) => store.delete(key)).then(() => undefined);
}

export async function clearAndBulkInsert<T>(storeName: StoreName, records: T[]): Promise<void> {
  await replaceStores({ [storeName]: records });
}

// A restore must either commit every user store or leave all of them intact.
// Synchronous request errors also abort: otherwise an earlier clear can commit.
export async function transact(storeNames: StoreName[], operation: (transaction: IDBTransaction) => void): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(storeNames, 'readwrite');
    let failure: unknown;
    transaction.oncomplete = () => { db.close(); resolve(); };
    transaction.onabort = () => { db.close(); reject(failure ?? transaction.error ?? new Error('Database transaction aborted.')); };
    try { operation(transaction); }
    catch (error) { failure = error; transaction.abort(); }
  });
}

export function replaceStores(stores: Partial<Record<StoreName, unknown[]>>): Promise<void> {
  return transact(Object.keys(stores) as StoreName[], (transaction) => {
    for (const [name, records] of Object.entries(stores)) {
      const store = transaction.objectStore(name);
      store.clear();
      records.forEach((record) => store.put(record));
    }
  });
}

export async function updateRecord<T>(storeName: StoreName, key: IDBValidKey, update: (current: T | undefined) => T | undefined): Promise<T | undefined> {
  let next: T | undefined;
  await transact([storeName], (transaction) => {
    const store = transaction.objectStore(storeName);
    const request = store.get(key);
    request.onsuccess = () => {
      try {
        next = update(request.result as T | undefined);
        if (next === undefined) store.delete(key);
        else store.put(next);
      } catch { transaction.abort(); }
    };
  });
  return next;
}
