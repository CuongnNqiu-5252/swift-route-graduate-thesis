'use client';

import { useCallback, useEffect, useState } from 'react';
import { orderRepository, type OrderListPage, type OrderPageFilters } from '@/infrastructure/repositories/order.repository';

export function useOrderPage(filters: OrderPageFilters) {
  const [result, setResult] = useState<{ filters: OrderPageFilters; data?: OrderListPage; error?: boolean }>();
  const [revision, setRevision] = useState(0);
  const refetch = useCallback(() => setRevision((current) => current + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      orderRepository.getOrderPage(filters, controller.signal).then((data) => {
        if (!controller.signal.aborted) setResult({ filters, data });
      }).catch(() => {
        if (!controller.signal.aborted) setResult({ filters, error: true });
      });
    }, filters.query ? 250 : 0);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [filters, revision]);

  useEffect(() => {
    const refreshVisible = () => {
      if (document.visibilityState === 'visible') refetch();
    };
    window.addEventListener('swiftroute:realtime-update', refetch);
    const interval = window.setInterval(refreshVisible, 30000);
    return () => {
      window.removeEventListener('swiftroute:realtime-update', refetch);
      window.clearInterval(interval);
    };
  }, [refetch]);

  const current = result?.filters === filters ? result : undefined;
  return { data: current?.data, isLoading: !current, error: current?.error, refetch };
}
