import { useCallback, useRef, useState } from 'react';
import { putRecord, STORES } from '../lib/db';
import {
  initialWorkoutCueSettings,
  normalizeWorkoutCueSettings,
  playWorkoutCue,
  type WorkoutCueSettings,
} from '../lib/workout-cues';
import { type WorkoutRow } from '../types';

export function useWorkoutCueSettings() {
  const initialSettings = initialWorkoutCueSettings();
  const settingsRef = useRef<WorkoutCueSettings>(initialSettings);
  const [settings, setSettings] = useState<WorkoutCueSettings>(initialSettings);

  const loadSettings = useCallback((storedSettings?: Partial<WorkoutCueSettings> | null) => {
    const next = normalizeWorkoutCueSettings(storedSettings);
    settingsRef.current = next;
    setSettings(next);
  }, []);

  const saveSettings = useCallback(async (updates: Partial<Omit<WorkoutCueSettings, 'key' | 'schemaVersion'>>) => {
    const next = normalizeWorkoutCueSettings({ ...settingsRef.current, ...updates });
    settingsRef.current = next;
    setSettings(next);
    await putRecord(STORES.appState, next);
  }, []);

  const playCue = useCallback((row?: WorkoutRow) => {
    playWorkoutCue(settingsRef.current, row);
  }, []);

  return {
    settings,
    loadSettings,
    saveSettings,
    playCue,
  };
}
