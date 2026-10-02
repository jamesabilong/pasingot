import { Capacitor, registerPlugin } from '@capacitor/core';

interface BackupExportPlugin {
  saveBackup(options: { fileName: string; content: string }): Promise<{ saved: boolean }>;
}

const nativeExport = registerPlugin<BackupExportPlugin>('BackupExport');

/** Returns false when the Android document picker is canceled. */
export async function saveBackupFile(fileName: string, content: string): Promise<boolean> {
  if (Capacitor.getPlatform() === 'android') {
    if (!Capacitor.isPluginAvailable('BackupExport')) {
      throw new Error('Update the Pasingot Android app to save backup files.');
    }
    return (await nativeExport.saveBackup({ fileName, content })).saved;
  }
  const url = URL.createObjectURL(new Blob([content], { type: 'application/json' }));
  const link = document.createElement('a');
  try {
    link.href = url;
    link.download = fileName;
    document.body.append(link);
    link.click();
    return true;
  } finally {
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
