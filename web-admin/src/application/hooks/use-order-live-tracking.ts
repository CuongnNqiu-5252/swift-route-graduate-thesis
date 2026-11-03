'use client';

import { useEffect, useState } from 'react';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { refreshAccessToken } from '@/lib/api';

export type TrackingSignal = 'waiting' | 'live' | 'stale' | 'reconnecting';

type LocationEvent = {
  lat: number;
  lng: number;
  accuracy_m?: number;
  bearing?: number;
  recorded_at: string;
};

export function useOrderLiveTracking(orderId?: string, driverUserId?: string) {
  const [position, setPosition] = useState<[number, number]>();
  const [lastUpdate, setLastUpdate] = useState<Date>();
  const [accuracy, setAccuracy] = useState<number>();
  const [signal, setSignal] = useState<TrackingSignal>('waiting');

  useEffect(() => {
    setPosition(undefined);
    setLastUpdate(undefined);
    setAccuracy(undefined);
    setSignal('waiting');
    if (!orderId || !driverUserId) return;

    let disposed = false;
    void orderRepository.getDriverPosition(driverUserId).then((driver) => {
      if (!disposed && driver.currentLat != null && driver.currentLng != null) {
        setPosition([driver.currentLat, driver.currentLng]);
      }
    }).catch(() => undefined);

    const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000/api';
    const controller = new AbortController();
    const onEvent = (eventName: string, data: string) => {
      if (disposed) return;
      if (eventName === 'connected') {
        setSignal((current) => current === 'live' ? current : 'waiting');
        return;
      }
      if (eventName !== 'location_update') return;
      const location = JSON.parse(data) as LocationEvent;
      if (!Number.isFinite(location.lat) || !Number.isFinite(location.lng)) return;
      setPosition([location.lat, location.lng]);
      setAccuracy(location.accuracy_m);
      setLastUpdate(new Date(location.recorded_at));
      setSignal('live');
    };

    const connect = async () => {
      while (!disposed) {
        try {
          let token = localStorage.getItem('access_token');
          let response = await fetch(`${baseUrl}/orders/${orderId}/tracking/stream`, { headers: token ? { Authorization: `Bearer ${token}` } : {}, signal: controller.signal });
          if (response.status === 401) {
            token = await refreshAccessToken();
            response = await fetch(`${baseUrl}/orders/${orderId}/tracking/stream`, { headers: { Authorization: `Bearer ${token}` }, signal: controller.signal });
          }
          if (!response.ok || !response.body) throw new Error(`Tracking stream failed: ${response.status}`);
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
              if (data.length) onEvent(eventName, data.join('\n'));
            }
          }
        } catch (error) {
          if (disposed || (error instanceof DOMException && error.name === 'AbortError')) return;
          setSignal('reconnecting');
        }
        await new Promise((resolve) => window.setTimeout(resolve, 3_000));
      }
    };
    void connect();

    const freshnessTimer = window.setInterval(() => {
      setLastUpdate((current) => {
        if (current && Date.now() - current.getTime() > 60_000) setSignal('stale');
        return current;
      });
    }, 5_000);

    return () => {
      disposed = true;
      controller.abort();
      window.clearInterval(freshnessTimer);
    };
  }, [driverUserId, orderId]);

  return { position, lastUpdate, accuracy, signal };
}
