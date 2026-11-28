export function formatRoadDistance(distanceMeters: number): string {
  if (!Number.isFinite(distanceMeters) || distanceMeters <= 0) return '0 m';
  if (distanceMeters < 1_000) return `${Math.round(distanceMeters)} m`;
  return `${(distanceMeters / 1_000).toFixed(1)} km`;
}

