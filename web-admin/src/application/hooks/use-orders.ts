'use client';

import { useState, useEffect, useCallback } from 'react';
import { Order, OrderStatus } from '@/domain/entities/order.entity';
import { orderRepository } from '@/infrastructure/repositories/order.repository';

export function useOrders(statusFilter?: OrderStatus) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchOrders = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await orderRepository.getOrders({ status: statusFilter });
      setOrders(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Lỗi tải đơn hàng');
    } finally {
      setIsLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    void Promise.resolve().then(fetchOrders);

    if (typeof window === 'undefined') return;

    // Auto-refresh when an SSE real-time operational event arrives
    const handleRealtimeUpdate = () => {
      void fetchOrders();
    };
    window.addEventListener('swiftroute:realtime-update', handleRealtimeUpdate);

    return () => {
      window.removeEventListener('swiftroute:realtime-update', handleRealtimeUpdate);
    };
  }, [fetchOrders]);

  return { orders, isLoading, error, refetch: fetchOrders };
}
