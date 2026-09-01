import { createRoot } from 'react-dom/client';
import { Capacitor } from '@capacitor/core';
import App from './App';
import './styles.css';

const isCapacitorShell = Capacitor.isNativePlatform() || window.location.origin === 'https://localhost';

if (isCapacitorShell) {
  document.documentElement.classList.add('capacitor-native');
}

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    if (isCapacitorShell) {
      // Capacitor already serves the bundled files offline. Keeping the PWA
      // service worker active here can make an upgraded APK continue serving
      // an older app shell from Cache Storage.
      Promise.all([
        navigator.serviceWorker.getRegistrations().then((registrations) => (
          Promise.all(registrations.map((registration) => registration.unregister()))
        )),
        'caches' in window
          ? caches.keys().then((keys) => Promise.all(keys.map((key) => caches.delete(key))))
          : Promise.resolve([]),
      ]).catch((error) => {
        console.error('Failed to clear the legacy native app cache:', error);
      });
      return;
    }

    navigator.serviceWorker.register('/service-worker.js').catch((error) => {
      console.error('Service worker registration failed:', error);
    });
  });
}

createRoot(document.getElementById('root')!).render(<App />);
