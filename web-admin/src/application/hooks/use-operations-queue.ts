'use client';

import { useCallback, useEffect, useState } from 'react';
import { OperationsQueue, orderRepository } from '@/infrastructure/repositories/order.repository';

const emptyQueue: OperationsQueue = {
  ready_for_dispatch: [],
  active: [],
  intervention: [],
};

export function useOperationsQueue() {
  const [queue, setQueue] = useState<OperationsQueue>(emptyQueue);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refetch = useCallback(async () => {
    if (typeof window !== 'undefined' && !localStorage.getItem('tenant_id')) {
      setQueue(emptyQueue);
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      setQueue(await orderRepository.getOperationsQueue());
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể tải hàng đợi vận hành');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(refetch);

    if (typeof window === 'undefined') return;

    // Auto-refresh operations queue when real-time SSE alerts or updates arrive
    const handleRealtimeUpdate = () => {
      void refetch();
    };
    window.addEventListener('swiftroute:realtime-update', handleRealtimeUpdate);

    // Fallback periodic sync every 30 seconds when tab is active
    const interval = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        void refetch();
      }
    }, 30000);

    return () => {
      window.removeEventListener('swiftroute:realtime-update', handleRealtimeUpdate);
      window.clearInterval(interval);
    };
  }, [refetch]);

  const reassignOrder = async (orderId: string, driverId: string) => {
    await orderRepository.assignBatch({ assignments: [{ order_id: orderId, driver_id: driverId }] });
    setQueue((current) => ({
      ...current,
      active: current.active.map((order) =>
        order.id === orderId ? { ...order, assigned_driver_id: driverId, status: 'PLANNED' } : order
      ),
    }));
  };

  return { queue, isLoading, error, refetch, reassignOrder };
}
