// =============================================================================
// Workout Tracker — app.js
// CSV import -> IndexedDB -> Today's Workout checklist -> logging -> history.
// No framework beyond PapaParse (CSV) + vanilla DOM. Tailwind is compiled CSS
// only (vendor/tailwind.css), no runtime JIT dependency.
// =============================================================================

const SCHEMA_VERSION = 1;
const DB_NAME = 'workoutAppDB';
const DB_VERSION = 1;
const STORE_WORKOUTS = 'workouts';
const STORE_LOGS = 'logs';

const WEEKDAYS = [
  'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday',
];

// -----------------------------------------------------------------------------
// IndexedDB helpers
// -----------------------------------------------------------------------------

/** @returns {Promise<IDBDatabase>} */
function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_WORKOUTS)) {
        db.createObjectStore(STORE_WORKOUTS, { keyPath: 'id', autoIncrement: true });
      }
      if (!db.objectStoreNames.contains(STORE_LOGS)) {
        db.createObjectStore(STORE_LOGS, { keyPath: 'id', autoIncrement: true });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function idbRequestToPromise(req) {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function withStore(storeName, mode, fn) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, mode);
    const store = tx.objectStore(storeName);
    let result;
    Promise.resolve(fn(store))
      .then((r) => { result = r; })
      .catch(reject);
    tx.oncomplete = () => resolve(result);
    tx.onerror = () => reject(tx.error);
  });
}

async function clearAndBulkInsert(storeName, rows) {
  return withStore(storeName, 'readwrite', async (store) => {
    await idbRequestToPromise(store.clear());
    for (const row of rows) {
      store.put(row);
    }
  });
}

async function getAll(storeName) {
  return withStore(storeName, 'readonly', (store) => idbRequestToPromise(store.getAll()));
}

async function addRecord(storeName, record) {
  return withStore(storeName, 'readwrite', (store) => idbRequestToPromise(store.add(record)));
}

// -----------------------------------------------------------------------------
// CSV import + validation (Prompt 1, req 1-2)
// -----------------------------------------------------------------------------

const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;

/**
 * Validate + normalize one raw CSV row into the shared JSON contract.
 * Returns { ok: true, row } or { ok: false, reason }.
 */
function validateRow(raw) {
  const day = (raw.day || '').trim();
  const time = (raw.time || '').trim();
  const exercise = (raw.exercise || '').trim();
  const setsStr = (raw.sets || '').toString().trim();
  const reps = (raw.reps || '').toString().trim();
  const restStr = (raw.rest || '').toString().trim();

  if (!WEEKDAYS.some((d) => d.toLowerCase() === day.toLowerCase())) {
    return { ok: false, reason: `invalid day "${raw.day}"` };
  }
  if (!TIME_RE.test(time)) {
    return { ok: false, reason: `invalid time "${raw.time}"` };
  }
  if (!exercise) {
    return { ok: false, reason: 'missing exercise' };
  }
  const sets = parseInt(setsStr, 10);
  if (!Number.isInteger(sets) || sets <= 0) {
    return { ok: false, reason: `invalid sets "${raw.sets}"` };
  }
  if (!reps) {
    return { ok: false, reason: 'missing reps' };
  }
  const rest = parseInt(restStr, 10);
  if (!Number.isInteger(rest) || rest < 0) {
    return { ok: false, reason: `invalid rest "${raw.rest}"` };
  }

  // Normalize `day` to the canonical full weekday name; keep `reps` as a string
  // as-is (may be a range like "8-12") — never coerce it to a number.
  const canonicalDay = WEEKDAYS.find((d) => d.toLowerCase() === day.toLowerCase());
  return {
    ok: true,
    row: { day: canonicalDay, time, exercise, sets, reps, rest, schemaVersion: SCHEMA_VERSION },
  };
}

function parseCsvFile(file) {
  return new Promise((resolve) => {
    Papa.parse(file, {
      header: true,
      skipEmptyLines: true,
      complete: (results) => {
        const valid = [];
        const skipped = [];
        for (const raw of results.data) {
          const v = validateRow(raw);
          if (v.ok) valid.push(v.row);
          else skipped.push({ raw, reason: v.reason });
        }
        resolve({ valid, skipped, fieldErrors: results.errors || [] });
      },
    });
  });
}

async function handleCsvImport(file) {
  const { valid, skipped } = await parseCsvFile(file);
  await clearAndBulkInsert(STORE_WORKOUTS, valid);
  renderImportResult(valid.length, skipped);
  await renderScheduleSummary();
  await renderTodayList();
  await pushScheduleToNative(valid);
}

/**
 * Prompt 6 schedule bridge: hand the freshly-parsed schedule to phone-native
 * code (ScheduleSyncPlugin) so the background /request-workout listener has
 * something to read without reaching into IndexedDB, which it can't do from
 * a Kotlin service. No-ops outside the Capacitor-wrapped app (e.g. a plain
 * browser tab), where `window.Capacitor` doesn't exist.
 */
async function pushScheduleToNative(rows) {
  const scheduleSync = window.Capacitor?.Plugins?.ScheduleSync;
  if (!scheduleSync) return; // running in a plain browser tab, not the Android wrapper
  try {
    await scheduleSync.syncSchedule({ rows });
  } catch (err) {
    console.error('Failed to sync schedule to native:', err);
  }
}

/**
 * Prompt 8 IndexedDB bridge: a background Kotlin service (LogListenerService)
 * can't write into IndexedDB directly, so watch logs land in a native
 * staging store first (PendingLogsStore) and get exposed here via
 * WorkoutLogBridgePlugin. Drained on resume/visibilitychange, written into
 * the `logs` store, and only acked (removed from native staging) after the
 * IndexedDB write commits — so a failed write can't lose an entry.
 * No-ops outside the Capacitor-wrapped app.
 */
async function drainPendingWatchLogs() {
  const bridge = window.Capacitor?.Plugins?.WorkoutLogBridge;
  if (!bridge) return;
  try {
    const { logs } = await bridge.getPendingLogs();
    if (!logs || !logs.length) return;

    for (const log of logs) {
      await addRecord(STORE_LOGS, {
        schemaVersion: log.schemaVersion ?? SCHEMA_VERSION,
        date: log.timestamp,
        exercise: log.exercise,
        status: log.status, // "done" | "skipped" — same schema as the PWA's own log writes
        workoutRowId: null, // no corresponding PWA schedule row; today's checklist ignores null ids
      });
    }

    // Ack only after every write above has committed successfully.
    await bridge.ackLogs({ ids: logs.map((l) => l.id) });
    await renderHistory(currentHistoryRange);
  } catch (err) {
    console.error('Failed to drain pending watch logs:', err);
  }
}

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') drainPendingWatchLogs();
});

function renderImportResult(importedCount, skipped) {
  const el = document.getElementById('import-result');
  el.classList.remove('hidden');
  const skippedLine = skipped.length
    ? `<p class="text-amber-400">Skipped ${skipped.length} malformed row${skipped.length === 1 ? '' : 's'}.</p>`
    : '';
  el.innerHTML = `
    <p class="text-emerald-400">Imported ${importedCount} exercise row${importedCount === 1 ? '' : 's'}.</p>
    ${skippedLine}
  `;
}

async function renderScheduleSummary() {
  const rows = await getAll(STORE_WORKOUTS);
  const el = document.getElementById('schedule-summary');
  if (!rows.length) {
    el.textContent = 'No schedule imported yet.';
    return;
  }
  const byDay = {};
  for (const r of rows) {
    byDay[r.day] = (byDay[r.day] || 0) + 1;
  }
  el.innerHTML = WEEKDAYS
    .filter((d) => byDay[d])
    .map((d) => `<div class="flex justify-between py-0.5"><span>${d}</span><span class="text-slate-500">${byDay[d]} exercise${byDay[d] === 1 ? '' : 's'}</span></div>`)
    .join('');
}

// -----------------------------------------------------------------------------
// Today's Workout (Prompt 1, req 3-4, 6)
// -----------------------------------------------------------------------------

function todayName() {
  return new Date().toLocaleDateString('en-US', { weekday: 'long' });
}

function todayDateKey() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

async function getTodayLogStatuses() {
  const logs = await getAll(STORE_LOGS);
  const map = new Map(); // key: `${workoutRowId}` -> status
  for (const log of logs) {
    if (log.date.startsWith(todayDateKey()) && log.workoutRowId != null) {
      map.set(log.workoutRowId, log.status);
    }
  }
  return map;
}

async function renderTodayList() {
  const label = document.getElementById('today-label');
  label.textContent = todayName();

  const all = await getAll(STORE_WORKOUTS);
  const todays = all
    .filter((r) => r.day.toLowerCase() === todayName().toLowerCase())
    .sort((a, b) => a.time.localeCompare(b.time));

  const listEl = document.getElementById('today-list');
  const emptyEl = document.getElementById('today-empty');

  if (!todays.length) {
    listEl.innerHTML = '';
    emptyEl.classList.remove('hidden');
    return;
  }
  emptyEl.classList.add('hidden');

  const statusMap = await getTodayLogStatuses();

  listEl.innerHTML = todays.map((item) => {
    const status = statusMap.get(item.id);
    const isDone = status === 'done';
    const isSkipped = status === 'skipped';
    return `
      <div class="rounded-xl border border-slate-800 bg-slate-900 p-3 flex items-center gap-3 ${isDone ? 'opacity-60' : ''}">
        <div class="flex-1 min-w-0">
          <p class="font-medium truncate ${isDone ? 'line-through text-slate-500' : 'text-slate-100'}">${escapeHtml(item.exercise)}</p>
          <p class="text-xs text-slate-500">${item.time} · ${item.sets} × ${escapeHtml(item.reps)} · rest ${item.rest}s</p>
        </div>
        <div class="flex gap-1.5 shrink-0">
          <button data-action="done" data-id="${item.id}" data-exercise="${escapeAttr(item.exercise)}"
            class="text-xs font-medium px-2.5 py-1.5 rounded-lg ${isDone ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-300 hover:bg-emerald-600 hover:text-white'}">
            ${isDone ? '✓ Done' : 'Mark as Done'}
          </button>
          <button data-action="skip" data-id="${item.id}" data-exercise="${escapeAttr(item.exercise)}"
            class="text-xs font-medium px-2.5 py-1.5 rounded-lg ${isSkipped ? 'bg-slate-700 text-white' : 'bg-slate-800 text-slate-400 hover:bg-slate-700'}">
            Skip
          </button>
        </div>
      </div>
    `;
  }).join('');
}

async function logExerciseStatus(workoutRowId, exercise, status) {
  await addRecord(STORE_LOGS, {
    schemaVersion: SCHEMA_VERSION,
    date: new Date().toISOString(),
    exercise,
    status, // "done" | "skipped"
    workoutRowId,
  });
  await renderTodayList();
}

document.getElementById('today-list').addEventListener('click', (e) => {
  const btn = e.target.closest('button[data-action]');
  if (!btn) return;
  const id = Number(btn.dataset.id);
  const exercise = btn.dataset.exercise;
  const status = btn.dataset.action === 'done' ? 'done' : 'skipped';
  logExerciseStatus(id, exercise, status);
});

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}
function escapeAttr(s) {
  return escapeHtml(s);
}

// -----------------------------------------------------------------------------
// Tabs
// -----------------------------------------------------------------------------

function setupTabs() {
  const buttons = document.querySelectorAll('.tab-btn');
  buttons.forEach((btn) => {
    btn.addEventListener('click', () => {
      buttons.forEach((b) => b.classList.remove('bg-indigo-600', 'text-white'));
      buttons.forEach((b) => b.classList.add('text-slate-400'));
      btn.classList.add('bg-indigo-600', 'text-white');
      btn.classList.remove('text-slate-400');

      document.querySelectorAll('.tab-panel').forEach((p) => p.classList.add('hidden'));
      document.getElementById(`panel-${btn.dataset.tab}`).classList.remove('hidden');

      if (btn.dataset.tab === 'history') renderHistory(currentHistoryRange);
    });
  });
}

// -----------------------------------------------------------------------------
// CSV input wiring
// -----------------------------------------------------------------------------

document.getElementById('csv-input').addEventListener('change', (e) => {
  const file = e.target.files[0];
  if (file) handleCsvImport(file);
});

// =============================================================================
// FUTURE-PHASE(web-push): background delivery needs a push subscription
// endpoint on the VPS + VAPID keys, and a `push` event handler in the service
// worker. Not implemented. The "does this workout time match now" check
// (isWorkoutTimeNow / checkScheduleAgainstNow below) is kept as its own
// function so a future `push` event handler can call it without a rewrite —
// both the foreground poller and a future push handler funnel through it.
// =============================================================================

const NOTIF_TOLERANCE_MIN = 1;
const POLL_INTERVAL_MS = 30_000;
let notifiedToday = new Set(); // `${workoutRowId}` notified already today
let notifiedForDate = todayDateKey();

function currentHHMM() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function withinTolerance(scheduledHHMM, nowHHMM, toleranceMin) {
  const [sh, sm] = scheduledHHMM.split(':').map(Number);
  const [nh, nm] = nowHHMM.split(':').map(Number);
  const scheduledTotal = sh * 60 + sm;
  const nowTotal = nh * 60 + nm;
  return Math.abs(scheduledTotal - nowTotal) <= toleranceMin;
}

/** Core "does this workout time match now" check — reusable by a future push handler. */
async function checkScheduleAgainstNow() {
  if (todayDateKey() !== notifiedForDate) {
    notifiedForDate = todayDateKey();
    notifiedToday = new Set();
  }
  const all = await getAll(STORE_WORKOUTS);
  const now = currentHHMM();
  const todays = all.filter((r) => r.day.toLowerCase() === todayName().toLowerCase());

  for (const item of todays) {
    if (notifiedToday.has(item.id)) continue;
    if (withinTolerance(item.time, now, NOTIF_TOLERANCE_MIN)) {
      notifiedToday.add(item.id);
      fireWorkoutNotification(item.exercise);
    }
  }
}

function fireWorkoutNotification(exerciseName) {
  const title = `Workout Time – ${exerciseName}`;
  if ('Notification' in window && Notification.permission === 'granted') {
    new Notification(title);
  } else {
    showToast(title);
  }
}

function showToast(message) {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = 'pointer-events-auto max-w-sm w-full bg-slate-800 border border-slate-700 text-slate-100 text-sm rounded-lg shadow-lg px-4 py-3';
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => toast.remove(), 4500);
}

function setupNotifications() {
  const btn = document.getElementById('notif-permission-btn');
  const supported = 'Notification' in window;

  if (supported && Notification.permission === 'default') {
    btn.classList.remove('hidden');
    btn.addEventListener('click', async () => {
      // Requested in response to a user gesture (this click), per API requirement.
      const perm = await Notification.requestPermission();
      if (perm === 'granted') btn.classList.add('hidden');
    });
  }

  setInterval(checkScheduleAgainstNow, POLL_INTERVAL_MS);
  checkScheduleAgainstNow();
}

// -----------------------------------------------------------------------------
// History (Prompt 9)
// -----------------------------------------------------------------------------

let currentHistoryRange = 'month';

document.querySelectorAll('.range-btn').forEach((btn) => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.range-btn').forEach((b) => {
      b.classList.remove('bg-indigo-600', 'text-white');
      b.classList.add('text-slate-400');
    });
    btn.classList.add('bg-indigo-600', 'text-white');
    btn.classList.remove('text-slate-400');
    currentHistoryRange = btn.dataset.range;
    renderHistory(currentHistoryRange);
  });
});

function filterLogsByRange(logs, range) {
  if (range === 'all') return logs;
  const now = new Date();
  return logs.filter((l) => {
    const d = new Date(l.date);
    return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth();
  });
}

async function renderHistory(range) {
  const all = await getAll(STORE_LOGS);
  const logs = filterLogsByRange(all, range);

  const summaryEl = document.getElementById('history-summary');
  const breakdownEl = document.getElementById('history-breakdown');
  const emptyEl = document.getElementById('history-empty');

  if (!logs.length) {
    summaryEl.innerHTML = '';
    breakdownEl.innerHTML = '';
    emptyEl.classList.remove('hidden');
    return;
  }
  emptyEl.classList.add('hidden');

  const doneCount = logs.filter((l) => l.status === 'done').length;
  const skippedCount = logs.filter((l) => l.status === 'skipped').length;

  summaryEl.innerHTML = `
    <div class="rounded-lg bg-slate-900 border border-slate-800 py-3">
      <p class="text-xl font-bold">${logs.length}</p>
      <p class="text-xs text-slate-500">Total</p>
    </div>
    <div class="rounded-lg bg-slate-900 border border-slate-800 py-3">
      <p class="text-xl font-bold text-emerald-400">${doneCount}</p>
      <p class="text-xs text-slate-500">Done</p>
    </div>
    <div class="rounded-lg bg-slate-900 border border-slate-800 py-3">
      <p class="text-xl font-bold text-amber-400">${skippedCount}</p>
      <p class="text-xs text-slate-500">Skipped</p>
    </div>
  `;

  const byExercise = new Map();
  for (const l of logs) {
    if (!byExercise.has(l.exercise)) byExercise.set(l.exercise, { done: 0, skipped: 0 });
    byExercise.get(l.exercise)[l.status === 'done' ? 'done' : 'skipped'] += 1;
  }

  const rows = [...byExercise.entries()].sort((a, b) => (b[1].done + b[1].skipped) - (a[1].done + a[1].skipped));

  breakdownEl.innerHTML = rows.map(([exercise, stats]) => {
    const total = stats.done + stats.skipped;
    const donePct = total ? Math.round((stats.done / total) * 100) : 0;
    return `
      <div class="rounded-lg bg-slate-900 border border-slate-800 p-3">
        <div class="flex justify-between text-sm mb-1.5">
          <span class="font-medium truncate">${escapeHtml(exercise)}</span>
          <span class="text-slate-500 shrink-0">${stats.done} done · ${stats.skipped} skipped</span>
        </div>
        <div class="h-1.5 rounded-full bg-slate-800 overflow-hidden">
          <div class="h-full bg-emerald-500" style="width: ${donePct}%"></div>
        </div>
      </div>
    `;
  }).join('');
}

// -----------------------------------------------------------------------------
// Service worker registration (Prompt 1, req 7)
// -----------------------------------------------------------------------------

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('service-worker.js').catch((err) => {
      console.error('Service worker registration failed:', err);
    });
  });
}

// -----------------------------------------------------------------------------
// Init
// -----------------------------------------------------------------------------

async function init() {
  setupTabs();
  await renderScheduleSummary();
  await renderTodayList();
  setupNotifications();
  await drainPendingWatchLogs(); // covers cold start, in addition to the visibilitychange hook
}

init();
