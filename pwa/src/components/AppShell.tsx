import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  Activity,
  Bell,
  ChartNoAxesCombined,
  ChevronRight,
  CircleUserRound,
  Download,
  Dumbbell,
  FileUp,
  Library,
  Map,
  Play,
  Wifi,
  WifiOff,
  type LucideIcon,
} from 'lucide-react';
import { type Tab } from '../types';

interface Toast {
  id: number;
  message: string;
}

interface ActiveWorkoutSummary {
  exercise: string;
  status: 'active' | 'resting' | 'paused';
}

interface InstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const NAV_ITEMS: Array<{ value: Tab; label: string; icon: LucideIcon }> = [
  { value: 'today', label: 'Today', icon: Dumbbell },
  { value: 'quests', label: 'Quests', icon: Map },
  { value: 'library', label: 'Library', icon: Library },
  { value: 'import', label: 'Import', icon: FileUp },
  { value: 'history', label: 'History', icon: ChartNoAxesCombined },
];

function navButtonClass(active: boolean): string {
  return `app-nav__button ${active ? 'app-nav__button--active' : ''}`;
}

export function AppShell({
  tab,
  toasts,
  notificationPermission,
  activeWorkout,
  todayPendingCount,
  questReady,
  healthSyncState,
  children,
  onTabChange,
  onWorkoutAction,
  onOnline,
  onRequestNotificationPermission,
}: {
  tab: Tab;
  toasts: Toast[];
  notificationPermission: NotificationPermission | 'unsupported';
  activeWorkout: ActiveWorkoutSummary | null;
  todayPendingCount: number;
  questReady: boolean;
  healthSyncState: 'off' | 'ready' | 'needs-permission';
  children: ReactNode;
  onTabChange: (tab: Tab) => void;
  onWorkoutAction: () => void;
  onOnline?: () => void;
  onRequestNotificationPermission: () => void;
}) {
  const [online, setOnline] = useState(() => navigator.onLine);
  const [installPrompt, setInstallPrompt] = useState<InstallPromptEvent | null>(null);
  const canOpenWorkout = activeWorkout != null || todayPendingCount > 0;
  const onOnlineRef = useRef(onOnline);
  useEffect(() => { onOnlineRef.current = onOnline; }, [onOnline]);

  useEffect(() => {
    const onOnline = () => { setOnline(true); onOnlineRef.current?.(); };
    const onOffline = () => setOnline(false);
    const onInstallPrompt = (event: Event) => {
      event.preventDefault();
      setInstallPrompt(event as InstallPromptEvent);
    };
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    window.addEventListener('beforeinstallprompt', onInstallPrompt);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
      window.removeEventListener('beforeinstallprompt', onInstallPrompt);
    };
  }, []);

  async function installApp() {
    if (!installPrompt) return;
    await installPrompt.prompt();
    await installPrompt.userChoice;
    setInstallPrompt(null);
  }

  const healthLabel = healthSyncState === 'ready'
    ? 'Health Connect ready'
    : healthSyncState === 'needs-permission' ? 'Health Connect needs permission' : 'Health Connect off';

  return (
    <div className="app-shell min-h-screen pb-24">
      <div className="app-toast-layer pointer-events-none fixed inset-x-0 top-0 z-50 flex flex-col items-center gap-2 p-3">
        {toasts.map((toast) => <div key={toast.id} className="app-toast max-w-sm px-4 py-3 text-sm shadow-lg">{toast.message}</div>)}
      </div>

      <header className="app-header sticky top-0 z-40 border-b backdrop-blur">
        <div className="app-header__inner mx-auto flex items-center gap-3 px-4 py-3">
          <div className="flex min-w-0 flex-1 items-center gap-2.5">
            <span className="app-brand-mark grid size-9 shrink-0 place-items-center" aria-hidden="true"><Activity size={20} strokeWidth={2.2} /></span>
            <div className="app-brand-copy min-w-0">
              <h1 className="truncate text-base font-bold">Pasingot</h1>
              <p className="hidden text-xs sm:block">Workout tracker</p>
            </div>
          </div>

          <div className="hidden items-center gap-2 md:flex">
            <span className={`app-status ${online ? '' : 'app-status--warning'}`} title={online ? 'Online' : 'Offline'}>
              {online ? <Wifi size={14} /> : <WifiOff size={14} />}{online ? 'Online' : 'Offline'}
            </span>
            <span className={`app-status ${healthSyncState === 'needs-permission' ? 'app-status--warning' : ''}`} title={healthLabel}>
              <CircleUserRound size={14} />{healthLabel}
            </span>
          </div>

          {!online && <span className="app-icon-button app-icon-button--warning md:hidden" role="status" aria-label="Offline" title="Offline"><WifiOff size={18} /></span>}
          {healthSyncState === 'needs-permission' && <span className="app-icon-button app-icon-button--warning md:hidden" role="status" aria-label={healthLabel} title={healthLabel}><CircleUserRound size={18} /></span>}
          {installPrompt && <button type="button" onClick={() => void installApp()} className="app-icon-button" aria-label="Install Pasingot" title="Install Pasingot"><Download size={18} /></button>}
          {notificationPermission === 'default' && <button type="button" onClick={onRequestNotificationPermission} className="app-icon-button" aria-label="Enable workout reminders" title="Enable workout reminders"><Bell size={18} /></button>}
          <button type="button" onClick={onWorkoutAction} disabled={!canOpenWorkout} className="app-workout-action" aria-label={activeWorkout ? 'Continue workout' : 'Start workout'}>
            <Play size={17} fill="currentColor" />
            <span>{activeWorkout ? 'Continue' : 'Start'}</span>
          </button>
        </div>
      </header>

      <main className="app-main mx-auto px-4 pt-4">
        <nav className="app-nav" aria-label="Workout views">
          {NAV_ITEMS.map(({ value, label, icon: Icon }) => {
            const showBadge = value === 'today' ? todayPendingCount > 0 : value === 'quests' && questReady;
            return (
              <button key={value} type="button" onClick={() => onTabChange(value)} className={navButtonClass(tab === value)} aria-current={tab === value ? 'page' : undefined}>
                <span className="relative"><Icon size={19} strokeWidth={2} />{showBadge && <span className="app-nav__badge" aria-label="Ready" />}</span>
                <span>{label}</span>
              </button>
            );
          })}
        </nav>

        {activeWorkout && tab !== 'today' && (
          <button type="button" onClick={onWorkoutAction} className="app-session-banner">
            <span className="app-session-banner__icon"><Activity size={18} /></span>
            <span className="min-w-0 flex-1 text-left">
              <span className="block text-xs">{activeWorkout.status === 'resting' ? 'Rest in progress' : activeWorkout.status === 'paused' ? 'Workout paused' : 'Workout in progress'}</span>
              <span className="block truncate text-sm font-semibold">{activeWorkout.exercise}</span>
            </span>
            <ChevronRight size={19} />
          </button>
        )}

        <div className="app-content">{children}</div>
      </main>
    </div>
  );
}
