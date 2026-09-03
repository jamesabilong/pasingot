# React Web App Instructions

Use these guidelines for future PWA work in `pwa/src`.

## Component Boundaries

- Keep `App.tsx` focused on app composition: choosing the active tab, calling
  feature hooks, passing focused props/callbacks to screens, and rendering
  `AppShell`.
- Put feature screens in `pwa/src/components/`, for example `TodayView.tsx`,
  `QuestsView.tsx`, `LibraryView.tsx`, `ImportView.tsx`, and
  `HistoryView.tsx`.
- Do not put tab-level display markup in `App.tsx`. `App.tsx` may choose which
  screen component to render and pass props/callbacks, but the screen owns its
  JSX.
- Keep shell UI such as headers, navigation, and toast presentation in
  component files such as `AppShell.tsx`.
- Put pure calculations, formatting, parsing, and normalization in
  `pwa/src/lib/`.
- Do not add large feature-specific JSX sections directly to `App.tsx`.
  If a tab needs more than a small wrapper, extract it.
- Prefer passing explicit props into feature components over reaching through
  globals or duplicating persistence logic.

## Logic Boundaries

- Prefer feature hooks in `pwa/src/hooks/` when a workflow owns React state,
  effects, timers, IndexedDB refreshes, native bridge coordination, or several
  related action handlers.
- Prefer pure modules in `pwa/src/lib/` for domain calculations,
  normalization, parsing, validation, state transitions, and browser/native
  side-effect adapters that do not need React state.
- Do not let `App.tsx` own feature-specific state machines such as workout
  session progression, rest timer recovery, cue settings, quest reconciliation,
  import/save workflows, or watch sync. `App.tsx` should wire those workflows
  together through hooks.
- Local display-only state may remain inside components when it only affects
  that component, such as confirmation toggles, temporary expanded rows, and
  form field drafts.
- Hooks should be named after concrete product workflows, for example
  `useWorkoutSession` or `useQuestWorkflow`. Avoid vague hooks like
  `useAppLogic` or `useLifecycle`.

## State And Data

- Keep durable data types in `pwa/src/types.ts`.
- Keep IndexedDB store names and migrations in `pwa/src/lib/db.ts`.
- Make schema changes additive where possible so existing local data continues
  to load.
- Store user-entered drafts as strings in React form state, then normalize to
  typed records before writing to IndexedDB.
- Keep pure derived stats in `useMemo` inside the feature component or in a
  dedicated hook/module, not interleaved with unrelated app flows.

## UI Implementation

- Build small named components for repeated UI patterns, panels, lists, metric
  cards, charts, and forms.
- For UI overhaul work, start from component inventory and design tokens before
  changing feature behavior. Web, installed PWA/mobile, and watch screens should
  share the same interaction language even when their layouts differ.
- Avoid nested cards; use cards for individual records, controls, and compact
  panels only.
- Keep mobile layouts stable with explicit grid tracks, fixed control heights,
  and truncation where text can grow.
- Keep active workout, rest, pause/resume, recovery, and cue controls highly
  visible on phone-sized layouts because they are used under fatigue and time
  pressure.
- Keep visible text focused on the workflow. Do not add explanatory in-app
  prose describing how the app works unless the user needs it to complete a
  task.

## Validation

Run these checks for React/PWA changes:

```sh
npx tsc --noEmit
npm run build
git diff --check
```

For visible UI changes, also do a browser smoke test of the changed tab or
workflow.
