'use client';

import { useEffect, useState } from 'react';
import { refreshAccessToken } from '@/lib/api';
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

  useEffect(() => {
    setPosition(undefined);
    setBearing(undefined);
    setStepProgress(null);
    setIsSimulating(false);
    setIsPaused(false);
    setLastUpdate(undefined);
    setSignal('waiting');
    if (!planId && !driverProfileId) return;
    const streamPath = driverProfileId
      ? `/orders/dispatch/drivers/${driverProfileId}/tracking/stream`
      : `/orders/dispatch/plans/${planId}/tracking/stream`;

    let disposed = false;
    const controller = new AbortController();
    const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000/api';
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

    const connect = async () => {
      while (!disposed) {
        try {
          let token = localStorage.getItem('access_token');
          let response = await fetch(`${baseUrl}${streamPath}`, {
            headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), 'X-Tenant-Id': localStorage.getItem('tenant_id') || '' },
            signal: controller.signal,
          });
          if (response.status === 401) {
            token = await refreshAccessToken();
            response = await fetch(`${baseUrl}${streamPath}`, {
              headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': localStorage.getItem('tenant_id') || '' },
              signal: controller.signal,
            });
          }
          if (!response.ok || !response.body) throw new Error(`Plan tracking failed: ${response.status}`);
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          while (!disposed) {
            const chunk = await reader.read();
            if (chunk.done) break;
            buffer += decoder.decode(chunk.value, { stream: true }).replaceAll('\r\n', '\n');
            const blocks = buffer.split('\n\n');
            buffer = blocks.pop() ?? '';
            for (const block of blocks) {
              let eventName = 'message';
              const data: string[] = [];
              for (const line of block.split('\n')) {
                if (line.startsWith('event:')) eventName = line.slice(6).trim();
                if (line.startsWith('data:')) data.push(line.slice(5).trim());
              }
              if (!data.length) continue;
              if (eventName === 'connected') {
                const payload = JSON.parse(data.join('\n')) as { simulating?: boolean; paused?: boolean };
                if (typeof payload.simulating === 'boolean') setIsSimulating(payload.simulating);
                setIsPaused(payload.paused === true);
                continue;
              }
              if (eventName === 'status_update') {
                const update = JSON.parse(data.join('\n')) as { status?: string };
                if (update.status === 'SIMULATION_PAUSED') setIsPaused(true);
                if (update.status === 'SIMULATION_RESUMED') setIsPaused(false);
                if (update.status === 'SIMULATION_STOPPED') {
                  setIsPaused(false);
                  setIsSimulating(false);
                }
                continue;
              }
              if (eventName !== 'location_update' && eventName !== 'location_snapshot') continue;
              const location = JSON.parse(data.join('\n')) as LocationEvent;
              if (!Number.isFinite(location.lat) || !Number.isFinite(location.lng)) continue;
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
            }
          }
        } catch (error) {
          if (disposed || (error instanceof DOMException && error.name === 'AbortError')) return;
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
