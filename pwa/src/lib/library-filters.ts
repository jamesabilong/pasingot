import type { ExerciseCatalogItem, ExerciseLevel } from '../types';
import { levelEligible } from './workout-planning';

export interface CatalogFilterOption {
  value: string;
  label: string;
}

export interface CatalogFilters {
  search: string;
  category: string;
  featuredOnly: boolean;
  level: ExerciseLevel;
  muscle: string;
  equipment: string;
}

export function normalizeCatalogFilterValue(value: string): string {
  return value.trim().toLowerCase();
}

export function buildCatalogFilterOptions(
  catalog: ExerciseCatalogItem[],
  field: 'primaryMuscles' | 'equipment',
): CatalogFilterOption[] {
  const labels = new Map<string, string>();
  for (const item of catalog) {
    for (const rawLabel of item[field]) {
      const label = rawLabel.trim();
      const value = normalizeCatalogFilterValue(label);
      if (!value) continue;
      const existing = labels.get(value);
      // Pick the same label even if custom exercise merging changes row order.
      if (existing == null || label < existing) labels.set(value, label);
    }
  }
  return [...labels].sort(([left], [right]) => left.localeCompare(right))
    .map(([value, label]) => ({ value, label }));
}

export function filterCatalog(catalog: ExerciseCatalogItem[], filters: CatalogFilters): ExerciseCatalogItem[] {
  const search = filters.search.toLowerCase();
  const muscle = normalizeCatalogFilterValue(filters.muscle);
  const equipment = normalizeCatalogFilterValue(filters.equipment);
  return catalog.filter((item) => {
    const searchable = `${item.displayName} ${item.name} ${item.category} ${item.primaryMuscles.join(' ')} ${item.equipment.join(' ')}`.toLowerCase();
    return levelEligible(item, filters.level)
      && (!search || searchable.includes(search))
      && (filters.category === 'all' || item.category === filters.category)
      && (!filters.featuredOnly || item.featured)
      && (!muscle || item.primaryMuscles.some((value) => normalizeCatalogFilterValue(value) === muscle))
      && (!equipment || item.equipment.some((value) => normalizeCatalogFilterValue(value) === equipment));
  });
}
