'use client';

import { useEffect, useMemo, useState } from 'react';

export type RoadRouteState = {
  coordinates: [number, number][];
  distanceMeters: number;
  durationSeconds: number;
  isLoading: boolean;
  error: string;
};

export function useRoadRoute(points: Array<{ lat: number; lng: number }>) {
  const pointKey = useMemo(
    () => points.map((point) => `${point.lat.toFixed(5)},${point.lng.toFixed(5)}`).join('|'),
    [points],
  );
  const [state, setState] = useState<RoadRouteState>({
    coordinates: [],
    distanceMeters: 0,
    durationSeconds: 0,
    isLoading: false,
    error: '',
  });

  useEffect(() => {
    const validPoints = pointKey.split('|').filter(Boolean).map((entry) => {
      const [lat, lng] = entry.split(',').map(Number);
      return { lat, lng };
    });
    if (validPoints.length < 2) {
      setState({ coordinates: [], distanceMeters: 0, durationSeconds: 0, isLoading: false, error: '' });
      return;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setState((current) => ({ ...current, isLoading: true, error: '' }));
      try {
        const response = await fetch('/api/maps/directions', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ points: validPoints, vehicle: 'bike' }),
          signal: controller.signal,
        });
        const payload = await response.json() as {
          coordinates?: [number, number][];
          distance_meters?: number;
          duration_seconds?: number;
          error?: string;
        };
        if (!response.ok) throw new Error(payload.error || 'Không tải được tuyến đường Goong.');
        setState({
          coordinates: Array.isArray(payload.coordinates) ? payload.coordinates : [],
          distanceMeters: Number(payload.distance_meters) || 0,
          durationSeconds: Number(payload.duration_seconds) || 0,
          isLoading: false,
          error: '',
        });
      } catch (error) {
        if (controller.signal.aborted) return;
        setState((current) => ({
          ...current,
          coordinates: [],
          isLoading: false,
          error: error instanceof Error ? error.message : 'Không tải được tuyến đường Goong.',
        }));
      }
    }, 500);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [pointKey]);

  return state;
}
