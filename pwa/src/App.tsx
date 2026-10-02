// FUTURE-PHASE(web-push): background delivery needs a push subscription
// endpoint on the VPS + VAPID keys, and a `push` event handler in the service
// worker. Foreground matching remains isolated in checkScheduleAgainstNow so a
// future push handler can reuse it without changing the schedule contract.

import { useMemo, useState } from 'react';
import { AppShell } from './components/AppShell';
import { HistoryView } from './components/HistoryView';
import { ImportView } from './components/ImportView';
import { LibraryView } from './components/LibraryView';
import { QuestsView } from './components/QuestsView';
import { TodayView } from './components/TodayView';
import { WatchQuickStartSheet } from './features/watch-quick-start/WatchQuickStartSheet';
import { useWatchQuickStart } from './features/watch-quick-start/useWatchQuickStart';
import { useWatchUpdates } from './hooks/useWatchUpdates';
import { useWorkoutData } from './hooks/useWorkoutData';
import { useWorkoutDataHydration } from './hooks/useWorkoutDataHydration';
import { useBodyMetrics } from './hooks/useBodyMetrics';
import { useScheduleImport } from './hooks/useScheduleImport';
import { useQuestWorkflow } from './hooks/useQuestWorkflow';
import { useLibraryWorkflow } from './hooks/useLibraryWorkflow';
import { useLocalDate } from './hooks/useLocalDate';
import { useHealthConnectSync } from './hooks/useHealthConnectSync';
import { useScheduleNotifications } from './hooks/useScheduleNotifications';
import { useToasts } from './hooks/useToasts';
import { useWorkoutCueSettings } from './hooks/useWorkoutCueSettings';
import { useWorkoutBackup } from './hooks/useWorkoutBackup';
import { useWorkoutSession } from './hooks/useWorkoutSession';
import { localDateKey } from './lib/history-stats';
import {
  calculatePlanProgress,
  defaultPrescriptionFor,
  estimateLevelFor,
  estimateWorkoutDurationSeconds,
  formatEstimatedDuration,
  LEVEL_LABELS,
  LEVELS,
  MAX_PLAYLIST_ITEMS,
  todayName,
  workoutStatusesOnDate,
} from './lib/workout-planning';
import {
  type HistoryRange,
  type Tab,
} from './types';

export default function App() {
  const localToday = useLocalDate();
  const quickStart = useWatchQuickStart();
  const [tab, setTab] = useState<Tab>('today');
  const data = useWorkoutData();
  const {
    workouts, setWorkouts, logs, sessionEvents, setLogEntries, catalog, setCatalog,
    customExercises, setCustomExercises, builtInQuestTemplates, builtInQuestRows,
    draft, setDraft, refreshWorkouts, refreshLogs, refreshSessionEvents,
    refreshSetLogs, refreshCustomExercises, refreshWorkoutHistory, logExercise,
  } = data;
  const [historyRange, setHistoryRange] = useState<HistoryRange>('month');
  const [notificationPermission, setNotificationPermission] = useState<NotificationPermission | 'unsupported'>(() => (
    'Notification' in window ? Notification.permission : 'unsupported'
  ));
  const {
    entries: bodyMetricEntries,
    draft: bodyMetricDraft,
    result: bodyMetricResult,
    setDraft: setBodyMetricDraft,
    refresh: refreshBodyMetrics,
    save: saveBodyMetric,
    edit: editBodyMetric,
    remove: deleteBodyMetric,
  } = useBodyMetrics();
  const { toasts, addToast } = useToasts();
  const {
    enabled: healthConnectEnabled,
    status: healthConnectStatus,
    result: healthConnectResult,
    setSyncEnabled: setHealthConnectSyncEnabled,
    requestPermission: requestHealthConnectSyncPermission,
    writeCompletedSession: writeHealthConnectSession,
    writeBodyMetric: writeHealthConnectBodyMetric,
    deleteBodyMetric: deleteHealthConnectBodyMetric,
    refreshEnabled: refreshHealthConnectEnabled,
  } = useHealthConnectSync(addToast);
  const {
    settings: workoutCueSettings,
    loadSettings: loadWorkoutCueSettings,
    saveSettings: saveWorkoutCueSettings,
    playCue: playWorkoutCue,
  } = useWorkoutCueSettings();

  const {
    session: activeWorkoutSession, rows: activeWorkoutRows, row: activeWorkoutRow,
    setInput: activeSetInput, elapsedSeconds: workoutElapsedSeconds, restRemainingSeconds: workoutRestRemainingSeconds,
    restore: restoreWorkoutSession, clear: clearActiveWorkoutSession, start: startTodayWorkoutPlayer,
    completeSet: completePwaSet, skip: skipPwaExercise, updateInput: updatePwaSetInput,
    pause: pausePwaWorkout, resume: resumePwaWorkout, restart: restartPwaWorkout, end: endPwaWorkout,
    startRestNow: startPwaRestNow, addRestSeconds: addPwaRestSeconds,
  } = useWorkoutSession({ workouts, logs, onHistoryChanged: refreshWorkoutHistory,
    onCompleted: writeHealthConnectSession, playCue: playWorkoutCue, addToast });
  const {
    questState, questHistory, customQuestDefinitions, questTemplates,
    activeQuestTemplate, activeQuestLevels, currentQuestRows,
    currentQuestEstimate, scheduledCurrentQuestRows, currentQuestProgress,
    questResult, totalDays, weekNumber, dayNumber,
    refresh: refreshQuestData, saveQuestState, startQuest, createCustomQuest,
    deleteCustomQuest, leaveQuest, saveQuestDayToSchedule,
  } = useQuestWorkflow({
    builtInQuestTemplates, builtInQuestRows, catalog, draft, workouts, logs,
    localToday, activeWorkoutSession, clearActiveWorkoutSession,
    refreshWorkouts, refreshUserData: () => hydration.refreshUserData(),
  });
  const {
    search, setSearch, category, setCategory, featuredOnly, setFeaturedOnly,
    customExerciseDraft, setCustomExerciseDraft, customExerciseResult,
    playlistResult, setPlaylistResult, categories, levelCounts, filteredCatalog,
    draftEstimate, saveDraft, addCatalogExercise, saveCustomExercise,
    editCustomExercise, deleteCustomExercise, updateDraftItem, reorderDraftItem,
    savePlaylistToSchedule,
  } = useLibraryWorkflow({
    catalog, setCatalog, customExercises, setCustomExercises, draft, setDraft,
    workouts, logs, setLogEntries, sessionEvents, customQuestDefinitions,
    refreshWorkouts, addToast,
  });
  const { result: importResult, importCsv } = useScheduleImport({
    session: activeWorkoutSession,
    clearSession: clearActiveWorkoutSession,
    onScheduleChanged: setWorkouts,
  });
  const { result: backupResult, exportBackup, importBackup } = useWorkoutBackup({
    onRestored: () => hydration.rehydrateBackup(),
  });
  const { watchSession, refreshWatchData, retryPendingSyncs } = useWatchUpdates({
    refreshLogs, refreshSessionEvents, addToast,
  });

  const hydration = useWorkoutDataHydration({
    data, refreshBodyMetrics, refreshQuestData, refreshHealthConnectEnabled,
    loadWorkoutCueSettings, restoreWorkoutSession, refreshWatchData,
    retryPendingSyncs, addToast,
  });

  useScheduleNotifications(workouts, addToast);

  const todayWorkouts = useMemo(() => workouts
    .filter((row) => row.day.toLowerCase() === todayName().toLowerCase())
    .sort((left, right) => left.time.localeCompare(right.time)), [workouts, localToday]);
  const todayEstimate = useMemo(() => (
    formatEstimatedDuration(estimateWorkoutDurationSeconds(todayWorkouts, estimateLevelFor(todayWorkouts)))
  ), [todayWorkouts]);
  const todayStatuses = useMemo(() => workoutStatusesOnDate(logs, localToday), [logs, localToday]);
  const todayProgress = useMemo(() => calculatePlanProgress(todayWorkouts, todayStatuses), [todayStatuses, todayWorkouts]);
  const todaySetLogCount = useMemo(() => setLogEntries.filter((entry) => localDateKey(entry.date) === localToday).length, [setLogEntries, localToday]);

  async function requestNotificationPermission() {
    if (!('Notification' in window)) return;
    const permission = await Notification.requestPermission();
    setNotificationPermission(permission);
  }

  return (
    <AppShell
      tab={tab}
      toasts={toasts}
      notificationPermission={notificationPermission}
      activeWorkout={activeWorkoutSession && activeWorkoutRow && ['active', 'resting', 'paused'].includes(activeWorkoutSession.status) ? {
        exercise: activeWorkoutRow.exercise,
        status: activeWorkoutSession.status as 'active' | 'resting' | 'paused',
      } : null}
      todayPendingCount={todayProgress.pending}
      questReady={currentQuestRows.length > 0 && currentQuestProgress.pending > 0}
      healthSyncState={!healthConnectEnabled ? 'off' : (healthConnectStatus.workoutPermissionGranted ?? healthConnectStatus.permissionGranted) ? 'ready' : 'needs-permission'}
      onTabChange={setTab}
      onOnline={() => void retryPendingSyncs()}
      onWorkoutAction={() => {
        setTab('today');
        const isLive = activeWorkoutSession && ['active', 'resting', 'paused'].includes(activeWorkoutSession.status);
        if (!isLive) void startTodayWorkoutPlayer();
      }}
      onRequestNotificationPermission={() => void requestNotificationPermission()}
    >
      {tab === 'today' && <TodayView
        todayName={todayName()}
        watchSession={watchSession}
        weeklyWorkouts={workouts}
        onBuildPlan={() => setTab('library')}
        onBrowseQuests={() => setTab('quests')}
        workouts={todayWorkouts}
        estimate={todayEstimate}
        progress={todayProgress}
        statuses={todayStatuses}
        setLogCount={todaySetLogCount}
        activeSession={activeWorkoutSession}
        activeRows={activeWorkoutRows}
        elapsedSeconds={workoutElapsedSeconds}
        restRemainingSeconds={workoutRestRemainingSeconds}
        activeSetInput={activeSetInput}
        cueSettings={workoutCueSettings}
        onStartPlayer={() => void startTodayWorkoutPlayer()}
        onSetInputChange={(updates) => void updatePwaSetInput(updates)}
        onCueSettingsChange={saveWorkoutCueSettings}
        onCompleteSet={() => void completePwaSet()}
        onSkipExercise={() => void skipPwaExercise()}
        onPause={() => void pausePwaWorkout()}
        onResume={() => void resumePwaWorkout()}
        onRestart={() => void restartPwaWorkout()}
        onEnd={() => void endPwaWorkout()}
        onStartNow={() => void startPwaRestNow()}
        onAddRestSeconds={(seconds) => void addPwaRestSeconds(seconds)}
        onClosePlayer={() => void clearActiveWorkoutSession()}
        onLogExercise={(row, status) => void logExercise(row, status)}
        onQuickStartRow={quickStart.supported ? (row) => quickStart.openToday(row, localToday) : undefined}
        quickStartReceipt={quickStart.supported ? quickStart.receipt : null}
        onViewQuickStart={quickStart.supported ? quickStart.openLatest : undefined}
      />}

      {tab === 'quests' && <QuestsView
        questState={questState}
        activeQuestTemplate={activeQuestTemplate}
        questTemplates={questTemplates}
        playlistDraft={draft}
        draftLevel={draft.level}
        levels={activeQuestLevels}
        levelLabels={LEVEL_LABELS}
        currentQuestRows={currentQuestRows}
        scheduledCurrentQuestRows={scheduledCurrentQuestRows}
        currentQuestEstimate={currentQuestEstimate}
        currentQuestProgress={currentQuestProgress}
        questResult={questResult}
        totalDays={totalDays}
        weekNumber={weekNumber}
        dayNumber={dayNumber}
        onDraftLevelChange={(level) => void saveDraft({ ...draft, level })}
        onQuestStateChange={(state) => void saveQuestState(state)}
        onStartQuest={(template) => void startQuest(template)}
        onSaveQuestDayToSchedule={() => void saveQuestDayToSchedule()}
        onCreateCustomQuest={(questDraft) => void createCustomQuest(questDraft)}
        onDeleteCustomQuest={(questId) => void deleteCustomQuest(questId)}
        onLeaveQuest={() => void leaveQuest()}
      />}

      {tab === 'library' && <LibraryView
        catalog={catalog}
        filteredCatalog={filteredCatalog}
        categories={categories}
        levelCounts={levelCounts}
        levels={LEVELS}
        levelLabels={LEVEL_LABELS}
        maxPlaylistItems={MAX_PLAYLIST_ITEMS}
        draft={draft}
        search={search}
        category={category}
        featuredOnly={featuredOnly}
        draftEstimate={draftEstimate}
        playlistResult={playlistResult}
        customExerciseDraft={customExerciseDraft}
        customExerciseResult={customExerciseResult}
        defaultPrescriptionFor={defaultPrescriptionFor}
        onSearchChange={setSearch}
        onCategoryChange={setCategory}
        onFeaturedOnlyChange={setFeaturedOnly}
        onDraftChange={(next) => void saveDraft(next)}
        onAddCatalogExercise={(sourceId) => void addCatalogExercise(sourceId)}
        onUpdateDraftItem={(index, updates) => void updateDraftItem(index, updates)}
        onReorderDraftItem={(index, direction) => void reorderDraftItem(index, direction)}
        onSavePlaylistToSchedule={() => void savePlaylistToSchedule()}
        onClearPlaylistResult={() => setPlaylistResult(null)}
        onCustomExerciseDraftChange={setCustomExerciseDraft}
        onSaveCustomExercise={() => void saveCustomExercise()}
        onEditCustomExercise={(sourceId) => void editCustomExercise(sourceId)}
        onDeleteCustomExercise={(sourceId) => void deleteCustomExercise(sourceId)}
        onQuickStartSingle={quickStart.supported ? quickStart.openSingle : undefined}
        onQuickStartPlaylist={quickStart.supported ? quickStart.openPlaylist : undefined}
        onQuickStartSelection={quickStart.supported ? quickStart.openSelection : undefined}
      />}

      {tab === 'import' && <ImportView
        result={importResult}
        backupResult={backupResult}
        workouts={workouts}
        onImportFile={(file) => void importCsv(file)}
        onExportBackup={() => void exportBackup()}
        onImportBackupFile={(file) => void importBackup(file)}
      />}

      {tab === 'history' && <HistoryView
        key={localToday}
        questHistory={questHistory}
        range={historyRange}
        logs={logs}
        sessionEvents={sessionEvents}
        setLogs={setLogEntries}
        bodyMetrics={bodyMetricEntries}
        catalog={catalog}
        questState={questState}
        activeQuestTemplate={activeQuestTemplate}
        levelLabels={LEVEL_LABELS}
        bodyMetricDraft={bodyMetricDraft}
        bodyMetricResult={bodyMetricResult}
        healthConnectEnabled={healthConnectEnabled}
        healthConnectStatus={healthConnectStatus}
        healthConnectResult={healthConnectResult}
        onRangeChange={setHistoryRange}
        onBodyMetricDraftChange={setBodyMetricDraft}
        onBodyMetricSave={() => void saveBodyMetric().then((entry) => {
          if (entry) writeHealthConnectBodyMetric(entry);
        })}
        onBodyMetricEdit={editBodyMetric}
        onBodyMetricDelete={(entry) => void deleteBodyMetric(entry).then(() => deleteHealthConnectBodyMetric(entry))}
        onHealthConnectEnabledChange={(enabled) => void setHealthConnectSyncEnabled(enabled)}
        onHealthConnectPermissionRequest={() => void requestHealthConnectSyncPermission()}
      />}
      <WatchQuickStartSheet
        draft={quickStart.draft}
        availability={quickStart.availability}
        availabilityMessage={quickStart.availabilityMessage}
        checking={quickStart.checking}
        sending={quickStart.sending}
        cancelling={quickStart.cancelling}
        receipt={quickStart.receipt}
        error={quickStart.error}
        onDraftChange={quickStart.setDraft}
        onSend={() => void quickStart.send()}
        onCancel={() => void quickStart.cancel()}
        onClose={quickStart.close}
      />
    </AppShell>
  );
}
