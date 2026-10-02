import { useCallback, useEffect, useState } from 'react';
import { drainPendingHealthConnectWrites, drainPendingWatchLogs, getLatestWatchSession, subscribeToWatchChanges, type WatchSessionSnapshot } from '../lib/native-bridge';

const nativeBridge = { drainPendingHealthConnectWrites, drainPendingWatchLogs, getLatestWatchSession, subscribeToWatchChanges };

type WatchUpdatesBridge = typeof nativeBridge;

interface WatchUpdatesOptions {
  refreshLogs: () => Promise<void>;
  refreshSessionEvents: () => Promise<void>;
  addToast: (message: string) => void;
}

/** Keeps inbound watch status and queued native writes separate from manual schedule sends. */
export function useWatchUpdates({ refreshLogs, refreshSessionEvents, addToast }: WatchUpdatesOptions, bridge: WatchUpdatesBridge = nativeBridge) {
  const [watchSession, setWatchSession] = useState<WatchSessionSnapshot | null>(null);

  const refreshWatchData = useCallback(async () => {
    const drained = await bridge.drainPendingWatchLogs();
    if (drained) await Promise.all([refreshLogs(), refreshSessionEvents()]);
    try {
      const latest = await bridge.getLatestWatchSession();
      setWatchSession((current) => current && latest && Date.parse(current.timestamp) > Date.parse(latest.timestamp) ? current : latest);
    } catch (error) {
      console.error('Failed to read watch session:', error);
    }
  }, [bridge, refreshLogs, refreshSessionEvents]);

  const retryPendingSyncs = useCallback(async () => {
    await refreshWatchData();
    const healthConnectDrained = await bridge.drainPendingHealthConnectWrites();
    if (healthConnectDrained) addToast(healthConnectDrained === 1 ? 'A queued Health Connect update synced.' : `${healthConnectDrained} queued Health Connect updates synced.`);
  }, [addToast, bridge, refreshWatchData]);

  useEffect(() => {
    let disposed = false;
    let removeListener = () => {};
    void bridge.subscribeToWatchChanges(() => {
      if (!disposed) void refreshWatchData();
    }).then((remove) => {
      if (disposed) remove();
      else { removeListener = remove; void refreshWatchData(); }
    }).catch((error) => console.error('Could not listen for watch updates:', error));
    return () => { disposed = true; removeListener(); };
  }, [bridge, refreshWatchData]);

  return { watchSession, refreshWatchData, retryPendingSyncs };
}
