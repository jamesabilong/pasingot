import { useCallback, useState } from 'react';

type NotificationApi = Pick<typeof Notification, 'permission' | 'requestPermission'>;

export function useNotificationPermission(api: NotificationApi | null = window.Notification ?? null) {
  const [permission, setPermission] = useState<NotificationPermission | 'unsupported'>(() => api?.permission ?? 'unsupported');
  const requestPermission = useCallback(async () => {
    if (!api) return;
    setPermission(await api.requestPermission());
  }, [api]);
  return { permission, requestPermission };
}
