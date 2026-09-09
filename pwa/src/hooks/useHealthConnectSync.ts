import { useCallback, useEffect, useState } from 'react';
import { SCHEMA_VERSION, type BodyMetricEntry, type WorkoutRow, type WorkoutSessionEvent } from '../types';
import { getRecord, putRecord, STORES } from '../lib/db';
import {
  getHealthConnectStatus,
  deleteBodyMetricFromHealthConnect,
  discardPendingBodyMetricSync,
  HEALTH_CONNECT_SETTINGS_KEY,
  requestHealthConnectPermissions,
  writeBodyMetricToHealthConnect,
  writeSessionEventToHealthConnect,
  type HealthConnectStatus,
} from '../lib/native-bridge';

interface HealthConnectSettings {
  key: typeof HEALTH_CONNECT_SETTINGS_KEY;
  schemaVersion: number;
  enabled: boolean;
}

export function useHealthConnectSync(addToast: (message: string) => void) {
  const [enabled, setEnabled] = useState(false);
  const [status, setStatus] = useState<HealthConnectStatus>({ availability: 'unavailable', permissionGranted: false });
  const [result, setResult] = useState<{ message: string; error: boolean } | null>(null);

  const refreshStatus = useCallback(async () => {
    const next = await getHealthConnectStatus();
    setStatus(next);
    return next;
  }, []);

  const refreshEnabled = useCallback(async () => {
    const stored = await getRecord<HealthConnectSettings>(STORES.appState, HEALTH_CONNECT_SETTINGS_KEY);
    if (stored?.schemaVersion === SCHEMA_VERSION) setEnabled(stored.enabled);
  }, []);

  useEffect(() => {
    let disposed = false;
    async function initialize() {
      if (!disposed) await refreshEnabled();
      const next = await getHealthConnectStatus();
      if (!disposed) setStatus(next);
    }
    void initialize();

    const onVisible = () => {
      // Re-reads the persisted setting (not just native status) so a restored
      // backup that changed this value doesn't leave the toggle stale.
      if (document.visibilityState === 'visible') {
        void refreshEnabled();
        void refreshStatus();
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      disposed = true;
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [refreshEnabled, refreshStatus]);

  const setSyncEnabled = useCallback(async (nextEnabled: boolean) => {
    setEnabled(nextEnabled);
    setResult(null);
    await putRecord(STORES.appState, {
      key: HEALTH_CONNECT_SETTINGS_KEY,
      schemaVersion: SCHEMA_VERSION,
      enabled: nextEnabled,
    } satisfies HealthConnectSettings);
    await refreshStatus();
  }, [refreshStatus]);

  const requestPermission = useCallback(async () => {
    setResult(null);
    const next = await requestHealthConnectPermissions();
    setStatus(next);
    const workoutReady = next.workoutPermissionGranted ?? next.permissionGranted;
    const bodyWeightReady = next.bodyWeightPermissionGranted ?? false;
    if (next.availability === 'available' && workoutReady && bodyWeightReady) {
      setResult({ message: 'Health Connect workout and body-weight sync is ready.', error: false });
    } else if (next.availability === 'provider_update_required') {
      setResult({ message: 'Health Connect needs to be installed or updated first.', error: true });
    } else if (next.availability === 'available') {
      const missing = [!workoutReady && 'workout', !bodyWeightReady && 'body-weight'].filter(Boolean).join(' and ');
      setResult({ message: `Return here after granting ${missing} permission${missing.includes(' and ') ? 's' : ''}.`, error: false });
    } else {
      setResult({ message: 'Health Connect is not available on this device.', error: true });
    }
  }, []);

  const writeCompletedSession = useCallback((event: WorkoutSessionEvent, rows: WorkoutRow[]) => {
    if (!enabled || event.eventType !== 'completed') return;
    void writeSessionEventToHealthConnect(event, rows).then((writeResult) => {
      void refreshStatus();
      if (writeResult.written) addToast('Workout written to Health Connect.');
      else if (writeResult.availability === 'available' && !writeResult.permissionGranted) addToast('Health Connect permission is needed before workouts can sync.');
    });
  }, [addToast, enabled, refreshStatus]);

  const writeBodyMetric = useCallback((entry: BodyMetricEntry) => {
    if (!enabled) {
      void discardPendingBodyMetricSync(entry);
      return;
    }
    void writeBodyMetricToHealthConnect(entry).then((writeResult) => {
      void refreshStatus();
      if (writeResult.written) addToast('Body weight written to Health Connect.');
      else if (writeResult.availability === 'available' && !writeResult.permissionGranted) addToast('Health Connect permission is needed before body weight can sync.');
    });
  }, [addToast, enabled, refreshStatus]);

  const deleteBodyMetric = useCallback((entry: BodyMetricEntry) => {
    if (!enabled) {
      void discardPendingBodyMetricSync(entry);
      return;
    }
    void deleteBodyMetricFromHealthConnect(entry).then((deleteResult) => {
      void refreshStatus();
      if (deleteResult.deleted) addToast('Body weight removed from Health Connect.');
      else if (deleteResult.availability === 'available' && !deleteResult.permissionGranted) addToast('Health Connect permission is needed to remove the synced body weight.');
    });
  }, [addToast, enabled, refreshStatus]);

  return {
    enabled,
    status,
    result,
    setSyncEnabled,
    requestPermission,
    writeCompletedSession,
    writeBodyMetric,
    deleteBodyMetric,
  };
}
