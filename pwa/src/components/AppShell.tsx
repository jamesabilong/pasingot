import { type ReactNode } from 'react';
import { type Tab } from '../types';

interface Toast {
  id: number;
  message: string;
}

function panelButtonClass(active: boolean): string {
  return `min-w-0 rounded-md py-2 ${active ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:bg-slate-800 hover:text-slate-100'}`;
}

export function AppShell({
  tab,
  toasts,
  notificationPermission,
  children,
  onTabChange,
  onRequestNotificationPermission,
}: {
  tab: Tab;
  toasts: Toast[];
  notificationPermission: NotificationPermission | 'unsupported';
  children: ReactNode;
  onTabChange: (tab: Tab) => void;
  onRequestNotificationPermission: () => void;
}) {
  return (
    <div className="app-shell min-h-screen bg-slate-950 pb-24 text-slate-100">
      <div className="app-toast-layer pointer-events-none fixed inset-x-0 top-0 z-50 flex flex-col items-center gap-2 p-3">
        {toasts.map((toast) => <div key={toast.id} className="max-w-sm rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-sm shadow-lg">{toast.message}</div>)}
      </div>
      <header className="app-header sticky top-0 z-40 flex items-center justify-between border-b border-slate-800 bg-slate-950/90 px-4 py-3 backdrop-blur">
        <h1 className="text-lg font-bold">Workout Tracker</h1>
        {notificationPermission === 'default' && <button type="button" onClick={onRequestNotificationPermission} className="rounded-md bg-slate-800 px-2 py-1 text-xs text-slate-300 hover:bg-slate-700">Enable reminders</button>}
      </header>
      <main className="mx-auto max-w-md space-y-6 px-4 pt-4">
        <nav className="grid grid-cols-5 gap-1 rounded-lg bg-slate-900 p-1 text-xs font-medium" aria-label="Workout views">
          {([['today', 'Today'], ['quests', 'Quests'], ['library', 'Library'], ['import', 'Import'], ['history', 'History']] as const).map(([value, label]) => (
            <button key={value} type="button" onClick={() => onTabChange(value)} className={panelButtonClass(tab === value)}>{label}</button>
          ))}
        </nav>
        {children}
      </main>
    </div>
  );
}
