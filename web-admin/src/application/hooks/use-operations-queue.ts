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
  const [hasLoaded, setHasLoaded] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refetch = useCallback(async () => {
    if (typeof window !== 'undefined' && !localStorage.getItem('tenant_id')) {
      setQueue(emptyQueue);
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      setQueue(await orderRepository.getOperationsQueue());
      setHasLoaded(true);
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

    return () => {
      window.removeEventListener('swiftroute:realtime-update', handleRealtimeUpdate);
    };
  }, [refetch]);

  return { queue, hasLoaded, isLoading, error, refetch };
}
