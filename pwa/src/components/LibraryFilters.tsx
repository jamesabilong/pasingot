import { Search } from 'lucide-react';
import type { CatalogFilterOption } from '../lib/library-filters';

export function LibraryFilters({
  search, category, categories, muscle, muscleOptions, equipment,
  equipmentOptions, featuredOnly, onSearchChange, onCategoryChange,
  onMuscleChange, onEquipmentChange, onFeaturedOnlyChange, onClearFilters,
}: {
  search: string;
  category: string;
  categories: string[];
  muscle: string;
  muscleOptions: CatalogFilterOption[];
  equipment: string;
  equipmentOptions: CatalogFilterOption[];
  featuredOnly: boolean;
  onSearchChange: (value: string) => void;
  onCategoryChange: (value: string) => void;
  onMuscleChange: (value: string) => void;
  onEquipmentChange: (value: string) => void;
  onFeaturedOnlyChange: (value: boolean) => void;
  onClearFilters: () => void;
}) {
  const hasFilters = Boolean(search || category !== 'all' || muscle || equipment || featuredOnly);
  return (
    <div className="library-filters space-y-2" role="group" aria-label="Exercise filters">
      <div className="library-filter-grid">
        <label className="library-search min-w-0">
          <span className="sr-only">Search exercises</span>
          <Search size={17} aria-hidden="true" />
          <input type="search" value={search} onChange={(event) => onSearchChange(event.target.value)} placeholder="Search exercises" className="library-filter-control library-filter-search" />
        </label>
        <label className="min-w-0">
          <span className="sr-only">Filter by category</span>
          <select value={category} onChange={(event) => onCategoryChange(event.target.value)} className="library-filter-control">
            <option value="all">All groups</option>
            {categories.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </label>
      </div>
      <div className="library-filter-grid">
        <label className="library-filter-label">
          Primary muscle
          <select aria-label="Filter by primary muscle" value={muscle} onChange={(event) => onMuscleChange(event.target.value)} className="library-filter-control">
            <option value="">All muscles</option>
            {muscleOptions.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
          </select>
        </label>
        <label className="library-filter-label">
          Equipment
          <select aria-label="Filter by equipment" value={equipment} onChange={(event) => onEquipmentChange(event.target.value)} className="library-filter-control">
            <option value="">All equipment</option>
            {equipmentOptions.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
          </select>
        </label>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-x-3">
        <label className="inline-flex min-h-11 items-center gap-2 text-xs text-slate-400">
          <input type="checkbox" checked={featuredOnly} onChange={(event) => onFeaturedOnlyChange(event.target.checked)} className="size-4 accent-emerald-500" />
          Common movements only
        </label>
        <button type="button" disabled={!hasFilters} onClick={onClearFilters} className="library-clear-filters">Clear filters</button>
      </div>
    </div>
  );
}
