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
    navigator.serviceWorker.register('/service-worker.js').catch((error) => {
      console.error('Service worker registration failed:', error);
    });
  });
}

createRoot(document.getElementById('root')!).render(<App />);
