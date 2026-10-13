import { useEffect, useState } from 'react';
import type { PlaylistItem } from '../types';

const wholeNumber = (value: string, min: number, max: number) => /^\d+$/.test(value) && Number(value) >= min && Number(value) <= max;

export function CircuitBuilder({ items, onCreate }: {
  items: PlaylistItem[];
  onCreate: (ids: number[], rounds: number, restSeconds: number) => void;
}) {
  const [selected, setSelected] = useState<number[]>([]);
  const [rounds, setRounds] = useState('3');
  const [rest, setRest] = useState('60');
  const eligible = items.filter(item => !item.circuit);
  useEffect(() => { setSelected(value => value.filter(id => items.some(item => item.sourceId === id && !item.circuit))); }, [items]);
  if (eligible.length < 2) return null;
  const valid = selected.length >= 2 && wholeNumber(rounds, 1, 100) && wholeNumber(rest, 0, 3600);
  return <details className="circuit-builder">
    <summary>Create superset / circuit</summary>
    <p className="text-xs text-slate-400">Select adjacent exercises. Phone/PWA groups use no rest between exercises.</p>
    <div className="circuit-selection">
      {eligible.map(item => <label key={item.sourceId}>
        <input type="checkbox" aria-label={`Group ${item.name}`} checked={selected.includes(item.sourceId)} onChange={event => setSelected(value => event.target.checked ? [...value, item.sourceId] : value.filter(id => id !== item.sourceId))} />
        <span>{item.name}</span>
      </label>)}
    </div>
    <div className="circuit-settings">
      <label>Rounds<input type="number" min="1" max="100" inputMode="numeric" value={rounds} onChange={event => setRounds(event.target.value)} /></label>
      <label>Round rest (sec)<input type="number" min="0" max="3600" inputMode="numeric" value={rest} onChange={event => setRest(event.target.value)} /></label>
    </div>
    <button type="button" className="secondary-action" disabled={!valid} onClick={() => onCreate(selected, Number(rounds), Number(rest))}>Group {selected.length} exercises</button>
  </details>;
}

export function CircuitSettings({ id, rounds, restSeconds, members, onUpdate, onRemove }: {
  id: string;
  rounds: number;
  restSeconds: number;
  members: number;
  onUpdate: (id: string, updates: { rounds?: number; restSeconds?: number }) => void;
  onRemove: (id: string) => void;
}) {
  const [roundDraft, setRoundDraft] = useState(String(rounds));
  const [restDraft, setRestDraft] = useState(String(restSeconds));
  useEffect(() => { setRoundDraft(String(rounds)); setRestDraft(String(restSeconds)); }, [id, rounds, restSeconds]);
  const valid = wholeNumber(roundDraft, 1, 100) && wholeNumber(restDraft, 0, 3600);
  return <div className="circuit-group-settings" role="group" aria-label={`${members === 2 ? 'Superset' : 'Circuit'} settings`}>
    <div className="flex flex-wrap items-center justify-between gap-2">
      <strong>{members === 2 ? 'Superset' : 'Circuit'} · {members} exercises</strong>
      <button type="button" className="library-clear-filters" onClick={() => onRemove(id)}>Ungroup</button>
    </div>
    <span className="text-xs text-slate-400">Phone/PWA · No rest between exercises</span>
    <div className="circuit-settings">
      <label>Group rounds<input type="number" min="1" max="100" inputMode="numeric" value={roundDraft} onChange={event => setRoundDraft(event.target.value)} /></label>
      <label>Group rest (sec)<input type="number" min="0" max="3600" inputMode="numeric" value={restDraft} onChange={event => setRestDraft(event.target.value)} /></label>
    </div>
    <button type="button" className="secondary-action" disabled={!valid || (Number(roundDraft) === rounds && Number(restDraft) === restSeconds)} onClick={() => onUpdate(id, { rounds: Number(roundDraft), restSeconds: Number(restDraft) })}>Update group</button>
  </div>;
}
