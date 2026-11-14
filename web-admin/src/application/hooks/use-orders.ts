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

    // Fallback periodic sync every 30 seconds when tab is active
    const interval = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        void fetchOrders();
      }
    }, 30000);

    return () => {
      window.removeEventListener('swiftroute:realtime-update', handleRealtimeUpdate);
      window.clearInterval(interval);
    };
  }, [fetchOrders]);

  return { orders, isLoading, error, refetch: fetchOrders };
}
