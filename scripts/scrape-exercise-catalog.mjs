import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

const API_URL = 'https://wger.de/api/v2/exerciseinfo/?language=2&limit=100';
const OUTPUT_PATH = path.resolve('pwa/public/data/exercises.csv');
const QUESTS_OUTPUT_PATH = path.resolve('pwa/public/data/quest-templates.csv');
const QUEST_WORKOUTS_OUTPUT_PATH = path.resolve('pwa/public/data/quest-workouts.csv');
const SCHEMA_VERSION = 1;
const ENGLISH_LANGUAGE_ID = 2;
const MAX_PAGES = 20;

// Reviewed source records covering cardio and the major movement/muscle groups.
// IDs are stable wger exercise IDs; additions should be reviewed in the generated
// CSV diff before this allowlist changes.
const CURATED_SOURCE_IDS = new Set([
  // Cardio and conditioning
  1104, 319, 1204, 320, 132, 996,
  // Chest
  1551, 73, 75, 538, 537, 129, 238, 713, 1111, 1112,
  // Back
  475, 1737, 355, 1117, 81, 83, 1198,
  // Shoulders
  687, 20, 1968, 348, 256, 822,
  // Legs and hips
  615, 1312, 1801, 257, 984, 981, 507, 184, 265, 294, 365, 71, 622, 1706,
  // Arms
  91, 92, 272, 1185, 211, 197,
  // Core
  458, 580, 167, 1412, 1193, 283, 1572, 636,
  // Full-body strength
  960,
]);

// "minimum_level" is an app editorial suitability tag, not a clinical rating.
// Intermediate users can still select beginner movements, and advanced users
// can select every level. Exercise technique, load, and individual ability
// matter more than a universal label.
const BEGINNER_SOURCE_IDS = new Set([
  1104, 1204, 320, 1737, 355, 1117, 129, 713, 1111, 1968, 348, 822,
  1312, 981, 265, 365, 71, 622, 91, 92, 211, 458, 167, 1572, 636,
]);

const ADVANCED_SOURCE_IDS = new Set([
  1801, 257, 1112, 475, 184, 1706, 197, 283,
]);

const DISPLAY_NAME_OVERRIDES = new Map([
  [75, 'Dumbbell Bench Press'],
  [83, 'Barbell Bent-Over Row'],
  [365, 'Lying Leg Curl'],
  [713, 'Wall Push-Up'],
  [1312, 'Bodyweight Squat'],
  [1412, 'Bicycle Crunches'],
]);

const PROGRESSION_METADATA = new Map([
  [1104, { group: 'cardio', level: 'beginner' }],
  [319, { group: 'cardio', level: 'intermediate' }],
  [713, { group: 'push', level: 'beginner' }],
  [1551, { group: 'push', level: 'intermediate' }],
  [1112, { group: 'push', level: 'advanced' }],
  [1312, { group: 'squat', level: 'beginner' }],
  [984, { group: 'squat', level: 'intermediate' }],
  [1706, { group: 'squat', level: 'advanced' }],
  [636, { group: 'pull', level: 'beginner' }],
  [81, { group: 'pull', level: 'intermediate' }],
  [475, { group: 'pull', level: 'advanced' }],
  [265, { group: 'hinge', level: 'beginner' }],
  [507, { group: 'hinge', level: 'intermediate' }],
  [184, { group: 'hinge', level: 'advanced' }],
  [1572, { group: 'core', level: 'beginner' }],
  [458, { group: 'core', level: 'intermediate' }],
  [283, { group: 'core', level: 'advanced' }],
]);

const QUEST_TEMPLATES = [{
  schema_version: SCHEMA_VERSION,
  quest_id: 'balanced-foundations-4w',
  title: 'Balanced Foundations',
  description: 'A four-week, three-day routine that resolves each movement slot to the selected level.',
  duration_weeks: 4,
  days_per_week: 3,
  evidence_basis: 'https://www.cdc.gov/physical-activity-basics/guidelines/adults.html|https://odphp.health.gov/sites/default/files/2019-09/PAG_Advisory_Committee_Report.pdf',
  safety_note: 'For generally healthy adults. This starter quest does not by itself guarantee the full weekly aerobic guideline. Start gradually and stop for pain, dizziness, or unusual shortness of breath. Seek professional advice when medical conditions or exercise restrictions apply.',
}];

const QUEST_DAY_GROUPS = [
  { day_number: 1, day_label: 'Foundation A', groups: ['cardio', 'squat', 'push', 'pull', 'core'] },
  { day_number: 2, day_label: 'Foundation B', groups: ['cardio', 'hinge', 'push', 'pull', 'core'] },
  { day_number: 3, day_label: 'Foundation C', groups: ['cardio', 'squat', 'hinge', 'pull', 'core'] },
];

const QUEST_PRESCRIPTIONS = new Map([
  ['beginner:cardio', { sets: 1, reps: '15 min', rest: 0 }],
  ['beginner:squat', { sets: 2, reps: '8-10', rest: 75 }],
  ['beginner:push', { sets: 2, reps: '8-10', rest: 75 }],
  ['beginner:pull', { sets: 2, reps: '8-10', rest: 60 }],
  ['beginner:hinge', { sets: 2, reps: '8-10', rest: 75 }],
  ['beginner:core', { sets: 2, reps: '6/side', rest: 45 }],
  ['intermediate:cardio', { sets: 1, reps: '20 min', rest: 0 }],
  ['intermediate:squat', { sets: 3, reps: '8-12', rest: 75 }],
  ['intermediate:push', { sets: 3, reps: '8-12', rest: 75 }],
  ['intermediate:pull', { sets: 3, reps: '8-12', rest: 75 }],
  ['intermediate:hinge', { sets: 3, reps: '8-12', rest: 90 }],
  ['intermediate:core', { sets: 3, reps: '30 sec', rest: 45 }],
  ['advanced:cardio', { sets: 1, reps: '30 min', rest: 0 }],
  ['advanced:squat', { sets: 4, reps: '6-10', rest: 90 }],
  ['advanced:push', { sets: 4, reps: '8-12', rest: 75 }],
  ['advanced:pull', { sets: 4, reps: '5-8', rest: 90 }],
  ['advanced:hinge', { sets: 4, reps: '5-8', rest: 120 }],
  ['advanced:core', { sets: 3, reps: '8-12', rest: 60 }],
]);

const QUEST_EXERCISE_SOURCE_IDS = new Map([
  ['beginner:cardio', 1104],
  ['beginner:squat', 1312],
  ['beginner:push', 713],
  ['beginner:pull', 636],
  ['beginner:hinge', 265],
  ['beginner:core', 1572],
  ['intermediate:cardio', 319],
  ['intermediate:squat', 984],
  ['intermediate:push', 1551],
  ['intermediate:pull', 81],
  ['intermediate:hinge', 507],
  ['intermediate:core', 458],
  ['advanced:cardio', 319],
  ['advanced:squat', 1706],
  ['advanced:push', 1112],
  ['advanced:pull', 475],
  ['advanced:hinge', 184],
  ['advanced:core', 283],
]);

const FEATURED_NAMES = new Set([
  'air squats',
  'barbell bench press',
  'barbell deadlift',
  'barbell row',
  'barbell squat',
  'bench press',
  'bent over row',
  'bodyweight squat',
  'burpees',
  'chin ups',
  'deadlift',
  'front squat',
  'goblet squat',
  'jumping jacks',
  'kettlebell swing',
  'lat pulldown',
  'lunges',
  'overhead press',
  'plank',
  'pull ups',
  'push ups',
  'push up',
  'romanian deadlift',
  'squats',
  'seated cable row',
  'shoulder press',
  'walking',
]);

function cleanText(value, maxLength = 160) {
  const text = String(value ?? '')
    .replace(/[\u0000-\u001f\u007f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, maxLength);

  // Prevent spreadsheet programs from treating imported cells as formulas.
  return /^[=+\-@]/.test(text) ? `'${text}` : text;
}

function normalizeName(value) {
  return cleanText(value)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function joinNames(items, field = 'name_en') {
  return [...new Set((items ?? []).map((item) => cleanText(item[field] || item.name)).filter(Boolean))]
    .sort((a, b) => a.localeCompare(b))
    .join('|');
}

function sourceUrl(id) {
  return `https://wger.de/en/exercise/${id}/view`;
}

function minimumLevel(sourceId) {
  if (BEGINNER_SOURCE_IDS.has(sourceId)) return 'beginner';
  if (ADVANCED_SOURCE_IDS.has(sourceId)) return 'advanced';
  return 'intermediate';
}

function toCatalogRow(item) {
  const translation = item.translations?.find(
    (candidate) => candidate.language === ENGLISH_LANGUAGE_ID && cleanText(candidate.name),
  );
  if (!translation) return null;

  const name = cleanText(translation.name);
  const license = item.license || {};
  const licenseName = cleanText(license.short_name || license.full_name, 80);
  const licenseUrl = cleanText(license.url, 240);
  const progression = PROGRESSION_METADATA.get(item.id);

  if (!Number.isInteger(item.id) || !name || !item.category?.name) return null;
  if (!licenseName.startsWith('CC-BY-SA')) return null;
  if (!licenseUrl.startsWith('https://creativecommons.org/licenses/by-sa/')) return null;

  return {
    schema_version: SCHEMA_VERSION,
    source_id: item.id,
    name,
    display_name: DISPLAY_NAME_OVERRIDES.get(item.id) || name,
    category: cleanText(item.category.name, 80),
    primary_muscles: joinNames(item.muscles),
    secondary_muscles: joinNames(item.muscles_secondary),
    equipment: joinNames(item.equipment, 'name'),
    featured: FEATURED_NAMES.has(normalizeName(name)) ? 'true' : 'false',
    minimum_level: minimumLevel(item.id),
    progression_group: progression?.group || '',
    progression_level: progression?.level || '',
    license: licenseName,
    license_url: licenseUrl,
    author: cleanText(translation.license_author || item.license_author || 'wger contributors', 120),
    source_url: sourceUrl(item.id),
  };
}

async function fetchAllExercises() {
  const records = [];
  let next = API_URL;
  let page = 0;

  while (next) {
    page += 1;
    if (page > MAX_PAGES) throw new Error(`Stopped after ${MAX_PAGES} pages; API pagination looks invalid.`);

    const response = await fetch(next, {
      headers: {
        Accept: 'application/json',
        'User-Agent': 'personal-workout-catalog/1.0 (+local reviewed CSV import)',
      },
    });
    if (!response.ok) throw new Error(`wger API returned HTTP ${response.status} for ${next}`);

    const body = await response.json();
    if (!Array.isArray(body.results)) throw new Error('wger API response did not contain a results array.');
    records.push(...body.results);
    next = body.next;
  }

  return records;
}

function csvCell(value) {
  const text = String(value ?? '');
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function toCsv(rows, columns) {
  return `${columns.join(',')}\n${rows.map((row) => columns.map((column) => csvCell(row[column])).join(',')).join('\n')}\n`;
}

const EXERCISE_COLUMNS = [
    'schema_version',
    'source_id',
    'name',
    'display_name',
    'category',
    'primary_muscles',
    'secondary_muscles',
    'equipment',
    'featured',
    'minimum_level',
    'progression_group',
    'progression_level',
    'license',
    'license_url',
    'author',
    'source_url',
];

const QUEST_TEMPLATE_COLUMNS = [
  'schema_version', 'quest_id', 'title', 'description', 'duration_weeks',
  'days_per_week', 'evidence_basis', 'safety_note',
];

const QUEST_WORKOUT_COLUMNS = [
  'schema_version', 'quest_id', 'level', 'day_number', 'day_label', 'sequence',
  'progression_group', 'exercise_source_id', 'sets', 'reps', 'rest',
];

function buildQuestWorkouts(exerciseRows) {
  const bySourceId = new Map(exerciseRows.map((row) => [row.source_id, row]));

  const workouts = [];
  for (const level of ['beginner', 'intermediate', 'advanced']) {
    for (const day of QUEST_DAY_GROUPS) {
      day.groups.forEach((group, index) => {
        const exercise = bySourceId.get(QUEST_EXERCISE_SOURCE_IDS.get(`${level}:${group}`));
        const prescription = QUEST_PRESCRIPTIONS.get(`${level}:${group}`);
        if (!exercise || !prescription) {
          throw new Error(`Missing quest mapping for ${level}:${group}.`);
        }
        workouts.push({
          schema_version: SCHEMA_VERSION,
          quest_id: QUEST_TEMPLATES[0].quest_id,
          level,
          day_number: day.day_number,
          day_label: day.day_label,
          sequence: index + 1,
          progression_group: group,
          exercise_source_id: exercise.source_id,
          ...prescription,
        });
      });
    }
  }
  return workouts;
}

function validateAndDedupe(items) {
  const rows = [];
  const seenNames = new Set();
  let skippedNoEnglishOrInvalid = 0;
  let skippedDuplicate = 0;

  for (const item of items) {
    if (!CURATED_SOURCE_IDS.has(item.id)) continue;
    const row = toCatalogRow(item);
    if (!row) {
      skippedNoEnglishOrInvalid += 1;
      continue;
    }

    const key = normalizeName(row.name);
    if (seenNames.has(key)) {
      skippedDuplicate += 1;
      continue;
    }
    seenNames.add(key);
    rows.push(row);
  }

  rows.sort((a, b) => a.name.localeCompare(b.name));
  return { rows, skippedNoEnglishOrInvalid, skippedDuplicate };
}

async function main() {
  const raw = await fetchAllExercises();
  const { rows, skippedNoEnglishOrInvalid, skippedDuplicate } = validateAndDedupe(raw);
  if (rows.length !== CURATED_SOURCE_IDS.size) {
    throw new Error(
      `Expected ${CURATED_SOURCE_IDS.size} reviewed exercises but produced ${rows.length}; refusing to replace the catalog.`,
    );
  }
  const questWorkouts = buildQuestWorkouts(rows);
  if (questWorkouts.length !== 45) {
    throw new Error(`Expected 45 quest workout rows but produced ${questWorkouts.length}.`);
  }

  await mkdir(path.dirname(OUTPUT_PATH), { recursive: true });
  await Promise.all([
    writeFile(OUTPUT_PATH, toCsv(rows, EXERCISE_COLUMNS), 'utf8'),
    writeFile(QUESTS_OUTPUT_PATH, toCsv(QUEST_TEMPLATES, QUEST_TEMPLATE_COLUMNS), 'utf8'),
    writeFile(QUEST_WORKOUTS_OUTPUT_PATH, toCsv(questWorkouts, QUEST_WORKOUT_COLUMNS), 'utf8'),
  ]);

  console.log(`Fetched ${raw.length} records from wger.`);
  console.log(`Wrote ${rows.length} reviewed English exercises to ${OUTPUT_PATH}.`);
  console.log(`Skipped ${skippedNoEnglishOrInvalid} invalid/non-English and ${skippedDuplicate} duplicate names.`);
  console.log(`Featured ${rows.filter((row) => row.featured === 'true').length} common exercises.`);
  console.log(`Wrote ${questWorkouts.length} level-resolved quest rows across ${QUEST_DAY_GROUPS.length} workout days.`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
