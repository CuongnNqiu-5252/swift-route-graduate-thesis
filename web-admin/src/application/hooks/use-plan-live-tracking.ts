'use client';

import axios from 'axios';
import { useEffect, useState } from 'react';
import { streamSse } from '@/lib/api';
import type { TrackingSignal } from './use-order-live-tracking';

type LocationEvent = {
  lat: number;
  lng: number;
  bearing?: number;
  step?: number;
  total_steps?: number;
  status?: string;
  recorded_at: string;
};

export function usePlanLiveTracking(planId?: string, driverUserId?: string | null, driverProfileId?: string) {
  const [position, setPosition] = useState<[number, number]>();
  const [bearing, setBearing] = useState<number>();
  const [stepProgress, setStepProgress] = useState<{ step: number; total: number } | null>(null);
  const [isSimulating, setIsSimulating] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<Date>();
  const [signal, setSignal] = useState<TrackingSignal>('waiting');
  const streamKey = `${planId || ''}:${driverProfileId || ''}`;
  const [prevStreamKey, setPrevStreamKey] = useState(streamKey);

  if (streamKey !== prevStreamKey) {
    setPrevStreamKey(streamKey);
    setPosition(undefined);
    setBearing(undefined);
    setStepProgress(null);
    setIsSimulating(false);
    setIsPaused(false);
    setLastUpdate(undefined);
    setSignal('waiting');
  }

  useEffect(() => {
    if (!planId && !driverProfileId) return;
    const streamPath = driverProfileId
      ? `/orders/dispatch/drivers/${driverProfileId}/tracking/stream`
      : `/orders/dispatch/plans/${planId}/tracking/stream`;

    let disposed = false;
    const controller = new AbortController();
    const tenantId = localStorage.getItem('tenant_id') || '';
    let staleTimer: number | undefined;

    const updateFreshness = (timestamp: Date) => {
      if (staleTimer) window.clearTimeout(staleTimer);
      const remaining = 60_000 - (Date.now() - timestamp.getTime());
      if (!Number.isFinite(remaining) || remaining <= 0) {
        setSignal('stale');
        return;
      }
      setSignal('live');
      staleTimer = window.setTimeout(() => setSignal('stale'), remaining);
    };

    const handleEvent = (eventName: string, rawData: string) => {
      if (disposed) return;
      if (eventName === 'connected') {
        const payload = JSON.parse(rawData) as { simulating?: boolean; paused?: boolean };
        if (typeof payload.simulating === 'boolean') setIsSimulating(payload.simulating);
        setIsPaused(payload.paused === true);
        return;
      }
      if (eventName === 'status_update') {
        const update = JSON.parse(rawData) as { status?: string };
        if (update.status === 'SIMULATION_PAUSED') setIsPaused(true);
        if (update.status === 'SIMULATION_RESUMED') setIsPaused(false);
        if (update.status === 'SIMULATION_STOPPED') {
          setIsPaused(false);
          setIsSimulating(false);
        }
        return;
      }
      if (eventName !== 'location_update' && eventName !== 'location_snapshot') return;
      const location = JSON.parse(rawData) as LocationEvent;
      if (!Number.isFinite(location.lat) || !Number.isFinite(location.lng)) return;
      const timestamp = new Date(location.recorded_at);
      setPosition([location.lat, location.lng]);
      if (typeof location.bearing === 'number') setBearing(location.bearing);
      if (typeof location.step === 'number' && typeof location.total_steps === 'number') {
        setStepProgress({ step: location.step, total: location.total_steps });
        setIsSimulating(location.step < location.total_steps);
      } else if (location.status === 'SIMULATING') {
        setIsSimulating(true);
      }
      setLastUpdate(timestamp);
      updateFreshness(timestamp);
    };

    const connect = async () => {
      while (!disposed) {
        try {
          await streamSse(streamPath, {
            signal: controller.signal,
            headers: tenantId ? { 'X-Tenant-Id': tenantId } : undefined,
            onEvent: handleEvent,
          });
        } catch (error) {
          if (disposed || axios.isCancel(error) || controller.signal.aborted) return;
          if (staleTimer) window.clearTimeout(staleTimer);
          setSignal('reconnecting');
        }
        await new Promise((resolve) => window.setTimeout(resolve, 3_000));
      }
    };
    void connect();
    return () => {
      disposed = true;
      controller.abort();
      if (staleTimer) window.clearTimeout(staleTimer);
    };
  }, [driverUserId, planId, driverProfileId]);

  return { position, bearing, stepProgress, isSimulating, isPaused, lastUpdate, signal };
}
