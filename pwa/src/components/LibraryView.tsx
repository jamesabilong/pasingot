import { EstimateSummary } from './SummaryCards';
import { CUSTOM_EXERCISE_CATEGORIES, type CustomExerciseDraft } from '../lib/custom-exercises';
import { WEEKDAYS, type ExerciseCatalogItem, type ExerciseLevel, type PlaylistDraft, type PlaylistItem, type Weekday, type WeightUnit } from '../types';

export function LibraryView({
  catalog,
  filteredCatalog,
  categories,
  levelCounts,
  levels,
  levelLabels,
  maxPlaylistItems,
  draft,
  search,
  category,
  featuredOnly,
  draftEstimate,
  playlistResult,
  customExerciseDraft,
  customExerciseResult,
  defaultPrescriptionFor,
  onSearchChange,
  onCategoryChange,
  onFeaturedOnlyChange,
  onDraftChange,
  onAddCatalogExercise,
  onUpdateDraftItem,
  onReorderDraftItem,
  onSavePlaylistToSchedule,
  onClearPlaylistResult,
  onCustomExerciseDraftChange,
  onSaveCustomExercise,
  onEditCustomExercise,
  onDeleteCustomExercise,
}: {
  catalog: ExerciseCatalogItem[];
  filteredCatalog: ExerciseCatalogItem[];
  categories: string[];
  levelCounts: Record<ExerciseLevel, number>;
  levels: ExerciseLevel[];
  levelLabels: Record<ExerciseLevel, string>;
  maxPlaylistItems: number;
  draft: PlaylistDraft;
  search: string;
  category: string;
  featuredOnly: boolean;
  draftEstimate: string;
  playlistResult: { message: string; error: boolean } | null;
  customExerciseDraft: CustomExerciseDraft;
  customExerciseResult: { message: string; error: boolean } | null;
  defaultPrescriptionFor: (exercise: ExerciseCatalogItem, level: ExerciseLevel) => Omit<PlaylistItem, 'sourceId' | 'name'>;
  onSearchChange: (value: string) => void;
  onCategoryChange: (value: string) => void;
  onFeaturedOnlyChange: (value: boolean) => void;
  onDraftChange: (draft: PlaylistDraft) => void;
  onAddCatalogExercise: (sourceId: number) => void;
  onUpdateDraftItem: (index: number, updates: Partial<PlaylistItem>) => void;
  onReorderDraftItem: (index: number, direction: -1 | 1) => void;
  onSavePlaylistToSchedule: () => void;
  onClearPlaylistResult: () => void;
  onCustomExerciseDraftChange: (draft: CustomExerciseDraft) => void;
  onSaveCustomExercise: () => void;
  onEditCustomExercise: (sourceId: number) => void;
  onDeleteCustomExercise: (sourceId: number) => void;
}) {
  return (
    <section className="space-y-5">
      <div className="space-y-3">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="text-base font-semibold text-slate-200">Exercise Library</h2>
          <span className="text-xs text-slate-500">{filteredCatalog.length} shown</span>
        </div>

        <div className="grid grid-cols-3 gap-1 rounded-lg border border-slate-800 bg-slate-900 p-1 text-xs font-medium">
          {levels.map((level) => (
            <button
              key={level}
              type="button"
              onClick={() => onDraftChange({ ...draft, level })}
              className={`rounded-md px-2 py-2 text-center transition ${draft.level === level ? 'bg-emerald-500 text-slate-950' : 'text-slate-400 hover:bg-slate-800 hover:text-slate-100'}`}
            >
              <span className="block truncate">{levelLabels[level]}</span>
              <span className="block text-[10px] opacity-70">{levelCounts[level] ?? 0}</span>
            </button>
          ))}
        </div>

        <div className="grid grid-cols-[1fr_auto] gap-2">
          <label className="min-w-0">
            <span className="sr-only">Search exercises</span>
            <input
              type="search"
              value={search}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder="Search exercises"
              className="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm placeholder:text-slate-600 focus:border-emerald-500 focus:outline-none"
            />
          </label>
          <label>
            <span className="sr-only">Filter by category</span>
            <select
              value={category}
              onChange={(event) => onCategoryChange(event.target.value)}
              className="h-full rounded-md border border-slate-700 bg-slate-900 px-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none"
            >
              <option value="all">All groups</option>
              {categories.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
        </div>

        <label className="inline-flex items-center gap-2 text-xs text-slate-400">
          <input
            type="checkbox"
            checked={featuredOnly}
            onChange={(event) => onFeaturedOnlyChange(event.target.checked)}
            className="size-4 accent-emerald-500"
          />
          Common movements only
        </label>

        {filteredCatalog.length === 0 ? <p className="py-8 text-center text-sm text-slate-500">No exercises match these filters.</p> : <div className="max-h-[48vh] space-y-2 overflow-y-auto pr-1">
          {filteredCatalog.map((item) => {
            const addedIndex = draft.items.findIndex((entry) => entry.sourceId === item.sourceId);
            const added = addedIndex >= 0;
            const prescription = defaultPrescriptionFor(item, draft.level);
            return (
              <div key={item.sourceId} className={`grid grid-cols-[1fr_auto] gap-3 rounded-lg border p-3 transition ${added ? 'border-emerald-700 bg-emerald-950/20' : 'border-slate-800 bg-slate-900 hover:border-slate-700'}`}>
                <div className="min-w-0 space-y-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <p className="min-w-0 truncate text-sm font-medium">{item.displayName}</p>
                    <span className="rounded border border-slate-700 px-1.5 py-0.5 text-[10px] uppercase text-slate-400">{levelLabels[item.minimumLevel]}</span>
                    {item.featured && <span className="rounded border border-emerald-700 px-1.5 py-0.5 text-[10px] uppercase text-emerald-300">Common</span>}
                    {item.custom && <span className="rounded border border-indigo-700 px-1.5 py-0.5 text-[10px] uppercase text-indigo-300">Custom</span>}
                  </div>
                  {item.imageUrl && <img src={item.imageUrl} alt="" className="mt-2 aspect-video w-full max-w-44 rounded-md border border-slate-800 object-cover" loading="lazy" />}
                  <p className="truncate text-xs text-slate-500">{item.category} · {item.primaryMuscles.length ? item.primaryMuscles.join(', ') : item.category}</p>
                  <p className="truncate text-xs text-slate-600">{item.equipment.join(', ') || 'No equipment listed'} · {prescription.sets} x {prescription.reps} · rest after {prescription.rest}s</p>
                  <div className="flex flex-wrap gap-3 text-xs">
                    {item.sourceUrl && <a href={item.sourceUrl} target="_blank" rel="noreferrer" className="inline-flex text-emerald-400 hover:text-emerald-300">Source</a>}
                    {item.videoUrl && <a href={item.videoUrl} target="_blank" rel="noreferrer" className="inline-flex text-indigo-300 hover:text-indigo-200">Video</a>}
                    {item.custom && <>
                      <button type="button" onClick={() => onEditCustomExercise(item.sourceId)} className="text-slate-400 hover:text-slate-200">Edit</button>
                      <button type="button" onClick={() => onDeleteCustomExercise(item.sourceId)} className="text-rose-300 hover:text-rose-200">Delete</button>
                    </>}
                  </div>
                </div>
                <button
                  type="button"
                  disabled={added}
                  onClick={() => onAddCatalogExercise(item.sourceId)}
                  className={`h-9 min-w-12 shrink-0 rounded-md px-3 text-xs font-semibold ${added ? 'bg-emerald-500 text-slate-950' : 'bg-indigo-600 text-white hover:bg-indigo-500'}`}
                >
                  {added ? `#${addedIndex + 1}` : 'Add'}
                </button>
              </div>
            );
          })}
        </div>}
      </div>

      <div className="space-y-3 rounded-lg border border-slate-800 bg-slate-900 p-4">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="text-base font-semibold text-slate-200">{customExerciseDraft.sourceId == null ? 'Create Custom Exercise' : 'Edit Custom Exercise'}</h2>
          {customExerciseDraft.sourceId != null && <button type="button" onClick={() => onCustomExerciseDraftChange({ ...customExerciseDraft, sourceId: undefined, name: '', imageUrl: '', videoUrl: '' })} className="text-xs text-slate-400 hover:text-slate-200">New</button>}
        </div>
        <div className="grid gap-2 sm:grid-cols-[1fr_9rem_8rem]">
          <label className="text-xs text-slate-500">
            Name
            <input type="text" maxLength={80} value={customExerciseDraft.name} onChange={(event) => onCustomExerciseDraftChange({ ...customExerciseDraft, name: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" />
          </label>
          <label className="text-xs text-slate-500">
            Category
            <select value={customExerciseDraft.category} onChange={(event) => onCustomExerciseDraftChange({ ...customExerciseDraft, category: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none">
              {CUSTOM_EXERCISE_CATEGORIES.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <label className="text-xs text-slate-500">
            Level
            <select value={customExerciseDraft.minimumLevel} onChange={(event) => onCustomExerciseDraftChange({ ...customExerciseDraft, minimumLevel: event.target.value as ExerciseLevel })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none">
              {levels.map((level) => <option key={level} value={level}>{levelLabels[level]}</option>)}
            </select>
          </label>
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="text-xs text-slate-500">
            Primary muscles
            <input type="text" value={customExerciseDraft.primaryMuscles} onChange={(event) => onCustomExerciseDraftChange({ ...customExerciseDraft, primaryMuscles: event.target.value })} placeholder="Chest, triceps" className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 placeholder:text-slate-700 focus:border-emerald-500 focus:outline-none" />
          </label>
          <label className="text-xs text-slate-500">
            Equipment
            <input type="text" value={customExerciseDraft.equipment} onChange={(event) => onCustomExerciseDraftChange({ ...customExerciseDraft, equipment: event.target.value })} placeholder="Dumbbell, bench" className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 placeholder:text-slate-700 focus:border-emerald-500 focus:outline-none" />
          </label>
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="text-xs text-slate-500">
            Image URL
            <input type="url" value={customExerciseDraft.imageUrl} onChange={(event) => onCustomExerciseDraftChange({ ...customExerciseDraft, imageUrl: event.target.value })} placeholder="https://..." className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 placeholder:text-slate-700 focus:border-emerald-500 focus:outline-none" />
          </label>
          <label className="text-xs text-slate-500">
            Video URL
            <input type="url" value={customExerciseDraft.videoUrl} onChange={(event) => onCustomExerciseDraftChange({ ...customExerciseDraft, videoUrl: event.target.value })} placeholder="https://..." className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-200 placeholder:text-slate-700 focus:border-emerald-500 focus:outline-none" />
          </label>
        </div>
        <button type="button" onClick={onSaveCustomExercise} className="w-full rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500">{customExerciseDraft.sourceId == null ? 'Create exercise' : 'Update exercise'}</button>
        {customExerciseResult && <p className={`rounded-md border p-3 text-sm ${customExerciseResult.error ? 'border-rose-900 bg-rose-950/40 text-rose-300' : 'border-emerald-900 bg-emerald-950/40 text-emerald-300'}`}>{customExerciseResult.message}</p>}
      </div>

      <div className="space-y-3 border-t border-slate-800 pt-5">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="text-base font-semibold text-slate-200">Workout Playlist</h2>
          <span className="text-xs text-slate-500">{draft.items.length ? `${draft.items.length}/${maxPlaylistItems} · ${draftEstimate}` : `${draft.items.length}/${maxPlaylistItems}`}</span>
        </div>
        {draft.items.length > 0 && <EstimateSummary value={draftEstimate} />}
        <div className="grid grid-cols-2 gap-2">
          <label className="text-xs text-slate-500">
            Day
            <select value={draft.day} onChange={(event) => onDraftChange({ ...draft, day: event.target.value as Weekday })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none">
              {WEEKDAYS.map((day) => <option key={day}>{day}</option>)}
            </select>
          </label>
          <label className="text-xs text-slate-500">
            Start time
            <input type="time" value={draft.time} onChange={(event) => onDraftChange({ ...draft, time: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none" />
          </label>
        </div>
        {draft.items.length === 0 ? <p className="rounded-lg border border-dashed border-slate-800 py-8 text-center text-sm text-slate-500">Add exercises from the library to build a workout.</p> : <div className="space-y-2">
          {draft.items.map((item, index) => (
            <div key={item.sourceId} className="space-y-2 rounded-lg border border-slate-800 bg-slate-900 p-3">
              <div className="flex items-center gap-2">
                <span className="grid size-6 shrink-0 place-items-center rounded bg-emerald-500 text-xs font-bold text-slate-950">{index + 1}</span>
                <p className="min-w-0 flex-1 truncate text-sm font-medium">{item.name}</p>
                <button type="button" disabled={index === 0} title="Move up" onClick={() => onReorderDraftItem(index, -1)} className="size-8 rounded-md text-slate-400 hover:bg-slate-800 disabled:opacity-30">↑</button>
                <button type="button" disabled={index === draft.items.length - 1} title="Move down" onClick={() => onReorderDraftItem(index, 1)} className="size-8 rounded-md text-slate-400 hover:bg-slate-800 disabled:opacity-30">↓</button>
                <button type="button" title="Remove" onClick={() => onDraftChange({ ...draft, items: draft.items.filter((_, itemIndex) => itemIndex !== index) })} className="size-8 rounded-md text-slate-400 hover:bg-rose-950 hover:text-rose-300">×</button>
              </div>
              <div className="grid grid-cols-[4.5rem_1fr_5rem] gap-2 pl-8">
                <label className="text-[10px] uppercase text-slate-600">
                  Sets
                  <input type="number" min="1" max="99" value={item.sets} onChange={(event) => onUpdateDraftItem(index, { sets: Number(event.target.value) })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm focus:border-emerald-500 focus:outline-none" />
                </label>
                <label className="text-[10px] uppercase text-slate-600">
                  Reps / duration
                  <input type="text" maxLength={30} value={item.reps} onChange={(event) => onUpdateDraftItem(index, { reps: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm focus:border-emerald-500 focus:outline-none" />
                </label>
                <label className="text-[10px] uppercase text-slate-600">
                  Rest after
                  <input type="number" min="0" max="3600" value={item.rest} onChange={(event) => onUpdateDraftItem(index, { rest: Number(event.target.value) })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm focus:border-emerald-500 focus:outline-none" />
                </label>
              </div>
              <div className="grid grid-cols-[1fr_5rem] gap-2 pl-8">
                <label className="text-[10px] uppercase text-slate-600">
                  Planned load
                  <input
                    type="number"
                    min="0"
                    max="2000"
                    step="0.5"
                    inputMode="decimal"
                    placeholder="Optional"
                    value={item.loadWeight ?? ''}
                    onChange={(event) => onUpdateDraftItem(index, { loadWeight: event.target.value === '' ? null : Number(event.target.value), loadUnit: item.loadUnit ?? 'kg' })}
                    className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm placeholder:text-slate-700 focus:border-emerald-500 focus:outline-none"
                  />
                </label>
                <label className="text-[10px] uppercase text-slate-600">
                  Unit
                  <select
                    value={item.loadUnit ?? 'kg'}
                    onChange={(event) => onUpdateDraftItem(index, { loadUnit: event.target.value as WeightUnit })}
                    className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm focus:border-emerald-500 focus:outline-none"
                  >
                    <option value="kg">kg</option>
                    <option value="lb">lb</option>
                  </select>
                </label>
              </div>
            </div>
          ))}
        </div>}
        <div className="grid grid-cols-[1fr_auto] gap-2">
          <button type="button" disabled={!draft.items.length} onClick={onSavePlaylistToSchedule} className="rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-400 disabled:cursor-not-allowed disabled:opacity-40">Add to weekly schedule</button>
          <button type="button" disabled={!draft.items.length} onClick={() => { onDraftChange({ ...draft, items: [] }); onClearPlaylistResult(); }} className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40">Clear</button>
        </div>
        {playlistResult && <p className={`rounded-md border p-3 text-sm ${playlistResult.error ? 'border-rose-900 bg-rose-950/40 text-rose-300' : 'border-emerald-900 bg-emerald-950/40 text-emerald-300'}`}>{playlistResult.message}</p>}
      </div>
      <p className="border-t border-slate-800 pt-4 text-xs leading-relaxed text-slate-600">Reviewed metadata from <a href="https://wger.de/" target="_blank" rel="noreferrer" className="text-emerald-400 hover:text-emerald-300">wger contributors</a>. License and source attribution are retained per exercise.</p>
    </section>
  );
}
