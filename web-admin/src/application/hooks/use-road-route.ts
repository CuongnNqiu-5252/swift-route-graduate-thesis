'use client';

import axios from 'axios';
import { useEffect, useMemo, useState } from 'react';
import api from '@/lib/api';

export type RoadRouteState = {
  coordinates: [number, number][];
  distanceMeters: number;
  durationSeconds: number;
  isLoading: boolean;
  error: string;
};

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
      return;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setState((current) => ({
        ...(current.resolvedPointKey === pointKey
          ? current
          : { coordinates: [], distanceMeters: 0, durationSeconds: 0, resolvedPointKey: pointKey }),
        isLoading: true,
        error: '',
      }));
      try {
        const { data: payload } = await api.post<{
          coordinates?: [number, number][];
          distance_meters?: number;
          duration_seconds?: number;
          error?: string;
        }>(
          '/api/maps/directions',
          { points: validPoints, vehicle: 'bike' },
          { baseURL: '', signal: controller.signal },
        );
        setState({
          resolvedPointKey: pointKey,
          coordinates: Array.isArray(payload.coordinates) ? payload.coordinates : [],
          distanceMeters: Number(payload.distance_meters) || 0,
          durationSeconds: Number(payload.duration_seconds) || 0,
          isLoading: false,
          error: '',
        });
      } catch (error) {
        if (controller.signal.aborted || axios.isCancel(error)) return;
        setState({
          resolvedPointKey: pointKey,
          coordinates: [],
          distanceMeters: 0,
          durationSeconds: 0,
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
    return { coordinates: [], distanceMeters: 0, durationSeconds: 0, isLoading: false, error: '' };
  }
  if (state.resolvedPointKey !== pointKey) {
    return { coordinates: [], distanceMeters: 0, durationSeconds: 0, isLoading: true, error: '' };
  }
  return {
    coordinates: state.coordinates,
    distanceMeters: state.distanceMeters,
    durationSeconds: state.durationSeconds,
    isLoading: state.isLoading,
    error: state.error,
  };
}
