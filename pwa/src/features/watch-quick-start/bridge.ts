import { Capacitor, registerPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';
import type { PhoneQuickStartRecord, QuickStartAck, QuickStartCancellation, QuickStartReceipt, QuickStartRequest } from './model';

export interface QuickStartAvailability { available: boolean; watchNodeId?: string; reason?: string }
export interface QuickStartBridge {
  getAvailability(): Promise<QuickStartAvailability>;
  sendQuickStart(input: { request: QuickStartRequest }): Promise<{ requestId: string; transportAcceptedAtMillis: number }>;
  cancelQuickStart(input: { requestId: string }): Promise<QuickStartCancellation>;
  getQuickStartStatus(input: { requestId: string }): Promise<QuickStartReceipt>;
  getLatestQuickStart(): Promise<{ record: PhoneQuickStartRecord | null }>;
  addListener(event: 'quickStartStatus', callback: (ack: QuickStartAck) => void): Promise<PluginListenerHandle>;
}

const plugin = registerPlugin<QuickStartBridge>('WatchQuickStart');
export function quickStartSupported(): boolean {
  return Capacitor.getPlatform() === 'android' && Capacitor.isNativePlatform() && Capacitor.isPluginAvailable('WatchQuickStart');
}
export function quickStartBridge(): QuickStartBridge | null { return quickStartSupported() ? plugin : null; }
export function availabilityText(reason?: string): string {
  switch (reason) {
    case 'disconnected': return 'Connect your watch to send a workout.';
    case 'multiple_watches': return 'Connect one watch at a time.';
    case 'unsupported_schema': case 'incompatible_version': return 'Update the phone and watch apps.';
    default: return 'A compatible watch is not available yet.';
  }
}
