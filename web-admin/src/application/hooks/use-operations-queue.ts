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
