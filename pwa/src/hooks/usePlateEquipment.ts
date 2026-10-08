import { useEffect, useRef, useState } from 'react';
import { getRecord, putRecord, STORES } from '../lib/db';
import { defaultPlateEquipment, equipmentDraft, isPlateEquipmentSettings, parsePlateEquipment, PLATE_EQUIPMENT_KEY } from '../lib/plate-equipment';
import type { WeightUnit } from '../types';

export function usePlateEquipment() {
  const [settings, setSettings] = useState(defaultPlateEquipment);
  const [unit, setUnit] = useState<WeightUnit>('kg');
  const [draft, setDraft] = useState(() => equipmentDraft(defaultPlateEquipment(), 'kg'));
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [message, setMessage] = useState<{ error: boolean; text: string } | null>(null);
  useEffect(() => {
    let disposed = false;
    async function load() {
      try {
        const stored = await getRecord<unknown>(STORES.appState, PLATE_EQUIPMENT_KEY);
        if (disposed) return;
        if (stored != null && !isPlateEquipmentSettings(stored)) {
          setMessage({ error: true, text: 'Saved equipment could not be read. Default equipment is shown.' });
        }
        const next = isPlateEquipmentSettings(stored) ? stored : defaultPlateEquipment();
        setSettings(next); setUnit(next.unit); setDraft(equipmentDraft(next, next.unit));
      } catch {
        if (!disposed) setMessage({ error: true, text: 'Could not load saved equipment. Default equipment is shown.' });
      } finally { if (!disposed) setReady(true); }
    }
    void load();
    return () => { disposed = true; };
  }, []);
  function updateDraft(next: typeof draft) {
    if (!ready || saving.current) return;
    setDraft(next); setMessage(null);
  }
  function changeUnit(next: WeightUnit) {
    if (!ready || saving.current) return;
    setUnit(next); setDraft(equipmentDraft(settings, next)); setMessage(null);
  }
  async function save() {
    if (!ready || saving.current) return;
    const parsed = parsePlateEquipment(draft.bar, draft.sizes);
    if ('error' in parsed) { setMessage({ error: true, text: parsed.error }); return; }
    saving.current = true; setBusy(true); setMessage(null);
    const next = { ...settings, unit, presets: { ...settings.presets, [unit]: parsed } };
    try {
      await putRecord(STORES.appState, next);
      setSettings(next);
      setMessage({ error: false, text: `Equipment saved for ${unit}.` });
    } catch { setMessage({ error: true, text: 'Could not save equipment. Your previous setup was kept. Try again.' }); }
    finally { saving.current = false; setBusy(false); }
  }
  function reset() {
    if (!ready || saving.current) return;
    const defaults = equipmentDraft(defaultPlateEquipment(), unit);
    setDraft({ ...draft, bar: defaults.bar, sizes: defaults.sizes });
    setMessage({ error: false, text: 'Default equipment loaded. Save equipment to keep it.' });
  }
  return { unit, draft, setDraft: updateDraft, changeUnit, save, reset, ready, busy, message };
}
