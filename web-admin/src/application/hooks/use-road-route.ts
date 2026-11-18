'use client';

import { isRequestCancelled } from '@/lib/api';
import { useEffect, useMemo, useState } from 'react';
import { goongMapsClient } from '@/infrastructure/maps/goong-maps.client';

export type RoadRouteState = {
  coordinates: [number, number][];
  distanceMeters: number;
  durationSeconds: number;
  legDurations: readonly (number | null)[];
  isLoading: boolean;
  error: string;
};

const emptyRoute = { coordinates: [], distanceMeters: 0, durationSeconds: 0, legDurations: [], error: '' };

export function useRoadRoute(points: Array<{ lat: number; lng: number }>): RoadRouteState {
  const pointKey = useMemo(
    () => points.map((point) => `${point.lat.toFixed(5)},${point.lng.toFixed(5)}`).join('|'),
    [points],
  );
  const validPointCount = useMemo(
    () => pointKey.split('|').filter(Boolean).length,
    [pointKey],
  );
  const [state, setState] = useState<RoadRouteState & { resolvedPointKey: string }>({
    resolvedPointKey: '',
    ...emptyRoute,
    isLoading: false,
  });

  useEffect(() => {
    const validPoints = pointKey.split('|').filter(Boolean).map((entry) => {
      const [lat, lng] = entry.split(',').map(Number);
      return { lat, lng };
    });
    if (validPoints.length < 2) {
      return;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setState((current) => ({
        ...(current.resolvedPointKey === pointKey
          ? current
          : { ...emptyRoute, resolvedPointKey: pointKey }),
        isLoading: true,
        error: '',
      }));
      try {
        const payload = await goongMapsClient.getDirections(validPoints, controller.signal);
        if (controller.signal.aborted) return;
        setState({
          resolvedPointKey: pointKey,
          coordinates: Array.isArray(payload.coordinates) ? payload.coordinates : [],
          distanceMeters: Number(payload.distance_meters) || 0,
          durationSeconds: Number(payload.duration_seconds) || 0,
          legDurations: payload.leg_duration_seconds ?? [],
          isLoading: false,
          error: '',
        });
      } catch (error) {
        if (controller.signal.aborted || isRequestCancelled(error)) return;
        setState({
          resolvedPointKey: pointKey,
          ...emptyRoute,
          isLoading: false,
          error: error instanceof Error ? error.message : 'Không tải được tuyến đường Goong.',
        });
      }
    }, 500);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [pointKey]);

  if (validPointCount < 2) {
    return { ...emptyRoute, isLoading: false };
  }
  if (state.resolvedPointKey !== pointKey) {
    return { ...emptyRoute, isLoading: true };
  }
  return {
    coordinates: state.coordinates,
    distanceMeters: state.distanceMeters,
    durationSeconds: state.durationSeconds,
    legDurations: state.legDurations,
    isLoading: state.isLoading,
    error: state.error,
  };
}
