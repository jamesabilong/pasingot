import { SCHEMA_VERSION, type WorkoutRow } from '../types';

export const WORKOUT_CUE_SETTINGS_KEY = 'workoutCueSettings';

export interface WorkoutCueSettings {
  key: typeof WORKOUT_CUE_SETTINGS_KEY;
  schemaVersion: number;
  hapticsEnabled: boolean;
  soundEnabled: boolean;
  voiceEnabled: boolean;
}

export function initialWorkoutCueSettings(): WorkoutCueSettings {
  return {
    key: WORKOUT_CUE_SETTINGS_KEY,
    schemaVersion: SCHEMA_VERSION,
    hapticsEnabled: true,
    soundEnabled: false,
    voiceEnabled: false,
  };
}

export function normalizeWorkoutCueSettings(settings?: Partial<WorkoutCueSettings> | null): WorkoutCueSettings {
  return {
    ...initialWorkoutCueSettings(),
    hapticsEnabled: settings?.hapticsEnabled ?? true,
    soundEnabled: settings?.soundEnabled ?? false,
    voiceEnabled: settings?.voiceEnabled ?? false,
  };
}

export function playWorkoutCue(settings: WorkoutCueSettings, row?: WorkoutRow): void {
  if (settings.hapticsEnabled && 'vibrate' in navigator) navigator.vibrate([90, 45, 90]);
  if (settings.soundEnabled) playBeep();
  if (settings.voiceEnabled && 'speechSynthesis' in window) {
    const utterance = new SpeechSynthesisUtterance(row ? `Rest complete. Next set: ${row.exercise}.` : 'Rest complete.');
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
  }
}

function playBeep(): void {
  const AudioContextClass = window.AudioContext ?? (window as Window & typeof globalThis & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AudioContextClass) return;
  const context = new AudioContextClass();
  const oscillator = context.createOscillator();
  const gain = context.createGain();
  oscillator.type = 'sine';
  oscillator.frequency.setValueAtTime(880, context.currentTime);
  oscillator.connect(gain);
  gain.connect(context.destination);
  gain.gain.setValueAtTime(0.0001, context.currentTime);
  gain.gain.exponentialRampToValueAtTime(0.18, context.currentTime + 0.03);
  gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.35);
  oscillator.start();
  oscillator.stop(context.currentTime + 0.38);
  window.setTimeout(() => void context.close(), 500);
}
