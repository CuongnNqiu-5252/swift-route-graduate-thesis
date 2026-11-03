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
  }, [fetchOrders]);

  const updateStatus = async (orderId: string, status: OrderStatus) => {
    await orderRepository.updateStatus(orderId, status);
    setOrders((prev) =>
      prev.map((o) => (o.id === orderId ? { ...o, status } : o))
    );
  };

  const cancelOrder = async (orderId: string, reason?: string) => {
    const updated = await orderRepository.cancelOrder(orderId, reason);
    setOrders((prev) =>
      prev.map((o) => (o.id === orderId ? { ...o, status: 'CANCELLED', notes: updated.notes } : o))
    );
  };

  const reassignOrder = async (orderId: string, driverId: string) => {
    await orderRepository.assignBatch({
      assignments: [{ order_id: orderId, driver_id: driverId }],
    });
    setOrders((prev) =>
      prev.map((order) =>
        order.id === orderId
          ? { ...order, assigned_driver_id: driverId, status: 'PLANNED' }
          : order
      )
    );
  };

  return { orders, isLoading, error, refetch: fetchOrders, updateStatus, cancelOrder, reassignOrder };
}
