import { useCallback, useEffect } from 'react';
import { repairLegacyCustomExerciseNames } from '../lib/custom-exercises';
import { getAll, getRecord, STORES } from '../lib/db';
import { drainPendingHealthConnectWrites, pushScheduleToNative } from '../lib/native-bridge';
import { WORKOUT_CUE_SETTINGS_KEY, type WorkoutCueSettings } from '../lib/workout-cues';
import type { WorkoutRow } from '../types';
import type { useWorkoutData } from './useWorkoutData';

interface WorkoutDataHydrationOptions {
  data: ReturnType<typeof useWorkoutData>;
  refreshBodyMetrics: () => Promise<void>;
  refreshQuestData: (isCurrent?: () => boolean) => Promise<void>;
  refreshHealthConnectEnabled: () => Promise<void>;
  loadWorkoutCueSettings: (settings: WorkoutCueSettings | undefined) => void;
  restoreWorkoutSession: () => Promise<void>;
  refreshWatchData: () => Promise<void>;
  retryPendingSyncs: () => Promise<void>;
  addToast: (message: string) => void;
}

export function useWorkoutDataHydration({
  data, refreshBodyMetrics, refreshQuestData, refreshHealthConnectEnabled,
  loadWorkoutCueSettings, restoreWorkoutSession, refreshWatchData,
  retryPendingSyncs, addToast,
}: WorkoutDataHydrationOptions) {
  const {
    refreshWorkouts, refreshLogs, refreshSessionEvents, refreshSetLogs,
    refreshCustomExercises, loadDefinitions, restoreLibrary,
  } = data;

  const refreshUserData = useCallback(async () => {
    await Promise.all([refreshWorkouts(), refreshLogs(), refreshSessionEvents(),
      refreshSetLogs(), refreshBodyMetrics(), refreshCustomExercises()]);
    await refreshQuestData();
  }, [refreshWorkouts, refreshLogs, refreshSessionEvents, refreshSetLogs,
    refreshBodyMetrics, refreshCustomExercises, refreshQuestData]);

  const rehydrateBackup = useCallback(async () => {
    await refreshUserData();
    await refreshHealthConnectEnabled();
    await restoreLibrary();
    loadWorkoutCueSettings(await getRecord<WorkoutCueSettings>(STORES.appState, WORKOUT_CUE_SETTINGS_KEY));
    await restoreWorkoutSession();
  }, [refreshUserData, refreshHealthConnectEnabled, restoreLibrary,
    loadWorkoutCueSettings, restoreWorkoutSession]);

  useEffect(() => {
    let disposed = false;
    async function initialize() {
      await repairLegacyCustomExerciseNames();
      await Promise.all([refreshWorkouts(), refreshLogs(), refreshSessionEvents(), refreshSetLogs(), refreshBodyMetrics(), refreshCustomExercises()]);
      // Refresh the native cache after an app upgrade as well as after an
      // explicit schedule edit, so existing quest rows gain new bridge fields.
      await pushScheduleToNative(await getAll<WorkoutRow>(STORES.workouts));
      await loadDefinitions(() => !disposed);
      if (disposed) return;
      await refreshQuestData(() => !disposed);
      const storedCueSettings = await getRecord<WorkoutCueSettings>(STORES.appState, WORKOUT_CUE_SETTINGS_KEY);
      if (!disposed) loadWorkoutCueSettings(storedCueSettings);
      if (!disposed) await restoreWorkoutSession();
      if (!disposed) await refreshWatchData();
      const healthConnectDrained = await drainPendingHealthConnectWrites();
      if (healthConnectDrained && !disposed) addToast(healthConnectDrained === 1 ? 'A queued Health Connect update synced.' : `${healthConnectDrained} queued Health Connect updates synced.`);
    }
    void initialize();
    const onVisible = () => {
      if (document.visibilityState === 'visible') void retryPendingSyncs();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => { disposed = true; document.removeEventListener('visibilitychange', onVisible); };
  }, [loadWorkoutCueSettings, refreshBodyMetrics, refreshCustomExercises, refreshLogs, refreshSessionEvents, refreshSetLogs, refreshWorkouts, refreshWatchData, restoreWorkoutSession, retryPendingSyncs, refreshQuestData, addToast, loadDefinitions]);

  return { refreshUserData, rehydrateBackup };
}
