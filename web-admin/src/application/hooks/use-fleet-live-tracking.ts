'use client';

import { useEffect, useState } from 'react';
import { isRequestCancelled } from '@/lib/api';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import type { TrackingSignal } from './use-order-live-tracking';

type FleetLocationEvent = {
  driver_id?: string;
  lat: number;
  lng: number;
  bearing?: number;
  recorded_at?: string;
};

export interface FleetDriverPosition {
  driverId: string;
  position: [number, number];
  bearing?: number;
  recordedAtMs: number;
  signal: TrackingSignal;
}

const STALE_AFTER_MS = 60_000;

function toFleetEntry(payload: FleetLocationEvent, nowMs: number): FleetDriverPosition | null {
  if (!payload.driver_id || !Number.isFinite(payload.lat) || !Number.isFinite(payload.lng)) {
    return null;
  }
  const parsedMs = payload.recorded_at ? Date.parse(payload.recorded_at) : nowMs;
  const recordedAtMs = Number.isFinite(parsedMs) ? parsedMs : nowMs;
  const isStale = nowMs - recordedAtMs >= STALE_AFTER_MS;
  return {
    driverId: payload.driver_id,
    position: [payload.lat, payload.lng],
    bearing: typeof payload.bearing === 'number' ? payload.bearing : undefined,
    recordedAtMs,
    signal: isStale ? 'stale' : 'live',
  };
}

export function useFleetLiveTracking() {
  const [positions, setPositions] = useState<Record<string, FleetDriverPosition>>({});
  const [connectionSignal, setConnectionSignal] = useState<TrackingSignal>('waiting');

  useEffect(() => {
    let disposed = false;
    const controller = new AbortController();
    const tenantId = localStorage.getItem('tenant_id') || '';

    const handleEvent = (eventName: string, rawData: string) => {
      if (disposed) return;
      const nowMs = Date.now();
      if (eventName === 'connected') {
        setConnectionSignal('live');
        return;
      }
      if (eventName === 'fleet_snapshot') {
        const items = JSON.parse(rawData) as FleetLocationEvent[];
        if (!Array.isArray(items)) return;
        setPositions((current) => {
          const next = { ...current };
          for (const item of items) {
            const entry = toFleetEntry(item, nowMs);
            if (entry) next[entry.driverId] = entry;
          }
          return next;
        });
        return;
      }
      if (eventName !== 'location_update') return;
      const item = JSON.parse(rawData) as FleetLocationEvent;
      const entry = toFleetEntry(item, nowMs);
      if (!entry) return;
      setPositions((current) => ({
        ...current,
        [entry.driverId]: entry,
      }));
      setConnectionSignal('live');
    };

    const connect = async () => {
      while (!disposed) {
        try {
          await orderRepository.streamFleetTracking({
            signal: controller.signal,
            headers: tenantId ? { 'X-Tenant-Id': tenantId } : undefined,
            onEvent: handleEvent,
          });
        } catch (error) {
          if (disposed || isRequestCancelled(error) || controller.signal.aborted) return;
          setConnectionSignal('reconnecting');
        }
        await new Promise((resolve) => window.setTimeout(resolve, 3_000));
      }
    };

    const freshnessTimer = window.setInterval(() => {
      const nowMs = Date.now();
      setPositions((current) => {
        let changed = false;
        const next: Record<string, FleetDriverPosition> = {};
        for (const [id, item] of Object.entries(current)) {
          const nextSignal: TrackingSignal =
            nowMs - item.recordedAtMs >= STALE_AFTER_MS ? 'stale' : 'live';
          if (item.signal !== nextSignal) {
            changed = true;
            next[id] = { ...item, signal: nextSignal };
          } else {
            next[id] = item;
          }
        }
        return changed ? next : current;
      });
    }, 15_000);

    void connect();
    return () => {
      disposed = true;
      controller.abort();
      window.clearInterval(freshnessTimer);
    };
  }, []);

  return { positions, connectionSignal };
}
