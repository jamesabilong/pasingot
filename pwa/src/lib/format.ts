export function formatDuration(seconds: number): string {
  const boundedSeconds = Math.max(0, Math.round(seconds));
  const hours = Math.floor(boundedSeconds / 3_600);
  const minutes = Math.floor((boundedSeconds % 3_600) / 60);
  const remainder = boundedSeconds % 60;
  if (hours > 0) return `${hours}h ${String(minutes).padStart(2, '0')}m`;
  if (minutes > 0) return `${minutes}m ${String(remainder).padStart(2, '0')}s`;
  return `${remainder}s`;
}
