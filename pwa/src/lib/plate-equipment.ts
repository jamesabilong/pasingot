import { SCHEMA_VERSION, type PlateEquipmentSettings, type WeightUnit } from '../types';
import { calculatePlates } from './plate-calculator';

export const PLATE_EQUIPMENT_KEY = 'plateEquipment';
export function defaultPlateEquipment(): PlateEquipmentSettings {
  return { key: PLATE_EQUIPMENT_KEY, schemaVersion: SCHEMA_VERSION, unit: 'kg', presets: {
    kg: { bar: 20, sizes: [25, 20, 15, 10, 5, 2.5, 1.25] },
    lb: { bar: 45, sizes: [45, 35, 25, 10, 5, 2.5] },
  } };
}
export function equipmentDraft(settings: PlateEquipmentSettings, unit: WeightUnit) {
  return { target: unit === 'kg' ? '60' : '135', bar: String(settings.presets[unit].bar), sizes: settings.presets[unit].sizes.join(', ') };
}
export function parsePlateEquipment(barText: string, sizesText: string): { error: string } | { bar: number; sizes: number[] } {
  const bar = barText.trim() ? Number(barText) : NaN;
  const sizes = sizesText.split(',').map(value => value.trim() ? Number(value) : NaN);
  const validation = calculatePlates(bar, bar, sizes);
  if ('error' in validation) return { error: validation.error };
  return { bar, sizes: [...new Set(sizes)].sort((a, b) => b - a) };
}
export function isPlateEquipmentSettings(value: unknown): value is PlateEquipmentSettings {
  if (!value || typeof value !== 'object') return false;
  const settings = value as PlateEquipmentSettings;
  if (settings.key !== PLATE_EQUIPMENT_KEY || settings.schemaVersion !== SCHEMA_VERSION
    || !['kg', 'lb'].includes(settings.unit) || !settings.presets || typeof settings.presets !== 'object') return false;
  return (['kg', 'lb'] as const).every(unit => {
    const preset = settings.presets[unit];
    return preset && typeof preset.bar === 'number' && Array.isArray(preset.sizes)
      && preset.sizes.every(size => typeof size === 'number')
      && !('error' in calculatePlates(preset.bar, preset.bar, preset.sizes));
  });
}
