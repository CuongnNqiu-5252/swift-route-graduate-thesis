import type { LocationPoint } from '@/domain/entities/routing.entity';
import type { RainForecast } from '@/infrastructure/maps/dispatch-layers.client';

export function forecastPoints(points: LocationPoint[]) {
  const samples = points.flatMap(({ lat, lng }, index) =>
    Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180
      ? [{ index, lat, lng, coordinate: `${lat.toFixed(3)},${lng.toFixed(3)}` }]
      : [],
  );
  const seen = new Set<string>();
  const unique = samples.filter(({ coordinate }) => {
    if (seen.has(coordinate)) return false;
    seen.add(coordinate);
    return true;
  });
  if (unique.length <= 10) return unique;
  return Array.from({ length: 10 }, (_, index) => unique[Math.round(index * (unique.length - 1) / 9)]);
}

export function forecastAt(series: RainForecast[], timestamp: number) {
  // Open-Meteo's precipitation probability refers to the preceding hour.
  const hourEnd = (Math.floor(timestamp / 3600) + 1) * 3600;
  return series.find((item) => item.timestamp === hourEnd);
}

export function travelSecondsToPoint(legs: readonly (number | null)[], pointIndex: number) {
  if (pointIndex < 1 || pointIndex > legs.length) return null;
  let elapsed = 0;
  for (const duration of legs.slice(0, pointIndex)) {
    if (duration == null || !Number.isFinite(duration) || duration < 0) return null;
    elapsed += duration;
  }
  return elapsed;
}
