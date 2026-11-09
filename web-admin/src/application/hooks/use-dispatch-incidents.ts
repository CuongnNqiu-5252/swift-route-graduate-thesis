'use client';

import type { DispatchAlertDto } from '@/infrastructure/repositories/notification.repository';
import { useNotifications } from '@/application/contexts/notification-context';

const emptyAlerts: DispatchAlertDto[] = [];
const noRefresh = async () => undefined;

// The dashboard layout always provides NotificationProvider. Keeping a single
// SSE-backed source prevents the map and notification bell from drifting apart.
export function useDispatchIncidents() {
  const notifications = useNotifications();

  return {
    incidents: notifications?.alerts ?? emptyAlerts,
    error: notifications?.error ?? null,
    refresh: notifications?.refresh ?? noRefresh,
  };
}
