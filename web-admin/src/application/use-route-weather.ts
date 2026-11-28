'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/application/contexts/auth-context';
import { routeWeatherRepository, type RouteWeatherReport } from '@/infrastructure/repositories/route-weather.repository';

export function useRouteWeather(revision = '') {
  const { user, hasPermission } = useAuth();
  const tenantId = user?.tenantId;
  const allowed = !!tenantId && hasPermission('order:read')
    && (hasPermission('dispatch:read') || hasPermission('dispatch:execute'))
    && ['OWNER', 'TENANT_ADMIN', 'OPERATOR'].includes(user?.tenantRole || user?.role || '');
  const key = `${tenantId}:${revision}`;
  const [snapshot, setSnapshot] = useState<{ key: string; data?: RouteWeatherReport; error?: boolean }>();

  useEffect(() => {
    if (!allowed || !tenantId) return;
    const controller = new AbortController();
    let pending = false;
    async function refresh() {
      if (pending) return;
      pending = true;
      try {
        const data = await routeWeatherRepository.get(controller.signal, tenantId!);
        if (!controller.signal.aborted) setSnapshot({ key, data });
      } catch {
        if (!controller.signal.aborted) setSnapshot({ key, error: true });
      } finally {
        pending = false;
      }
    }
    void refresh();
    const timer = window.setInterval(refresh, 10 * 60_000);
    return () => { controller.abort(); window.clearInterval(timer); };
  }, [allowed, tenantId, key]);

  const current = snapshot?.key === key ? snapshot : undefined;
  return { allowed, data: current?.data, error: current?.error ?? false, loading: allowed && !current };
}
