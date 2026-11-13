'use client';

import axios from 'axios';
import { useEffect, useState } from 'react';
import { streamSse } from '@/lib/api';

export type TrackingSignal = 'waiting' | 'live' | 'stale' | 'reconnecting';

type LocationEvent = {
  lat: number;
  lng: number;
  accuracy_m?: number;
  bearing?: number;
  step?: number;
  total_steps?: number;
  status?: string;
  recorded_at: string;
};

export function useOrderLiveTracking(orderId?: string, driverUserId?: string) {
  const [position, setPosition] = useState<[number, number]>();
  const [lastUpdate, setLastUpdate] = useState<Date>();
  const [accuracy, setAccuracy] = useState<number>();
  const [stepProgress, setStepProgress] = useState<{ step: number; total: number } | null>(null);
  const [isSimulating, setIsSimulating] = useState(false);
  const [signal, setSignal] = useState<TrackingSignal>('waiting');
  const [prevOrderId, setPrevOrderId] = useState(orderId);

  if (orderId !== prevOrderId) {
    setPrevOrderId(orderId);
    setPosition(undefined);
    setLastUpdate(undefined);
    setAccuracy(undefined);
    setStepProgress(null);
    setIsSimulating(false);
    setSignal('waiting');
  }

  useEffect(() => {
    if (!orderId) return;

    let disposed = false;
    const controller = new AbortController();
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
    const onEvent = (eventName: string, data: string) => {
      if (disposed) return;
      if (eventName === 'connected') {
        try {
          const payload = JSON.parse(data) as { simulating?: boolean };
          if (typeof payload.simulating === 'boolean') setIsSimulating(payload.simulating);
        } catch {
          // ignore
        }
        setSignal((current) => current === 'live' ? current : 'waiting');
        return;
      }
      if (eventName !== 'location_update' && eventName !== 'location_snapshot') return;
      const location = JSON.parse(data) as LocationEvent;
      if (!Number.isFinite(location.lat) || !Number.isFinite(location.lng)) return;
      setPosition([location.lat, location.lng]);
      setAccuracy(location.accuracy_m);
      if (typeof location.step === 'number' && typeof location.total_steps === 'number') {
        setStepProgress({ step: location.step, total: location.total_steps });
        setIsSimulating(location.step < location.total_steps);
      } else if (location.status === 'SIMULATING') {
        setIsSimulating(true);
      }
      const timestamp = new Date(location.recorded_at);
      setLastUpdate(timestamp);
      updateFreshness(timestamp);
    };

    const connect = async () => {
      while (!disposed) {
        try {
          await streamSse(`/orders/${orderId}/tracking/stream`, {
            signal: controller.signal,
            onEvent,
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
  }, [driverUserId, orderId]);

  return { position, lastUpdate, accuracy, stepProgress, isSimulating, signal };
}
