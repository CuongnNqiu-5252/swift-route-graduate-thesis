'use client';

import axios from 'axios';
import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/application/contexts/auth-context';
import {
  DispatchAlertDto,
  InboxNotificationDto,
  notificationRepository,
} from '@/infrastructure/repositories/notification.repository';
import { streamSse } from '@/lib/api';

interface NotificationContextValue {
  alerts: DispatchAlertDto[];
  inbox: InboxNotificationDto[];
  unreadCount: number;
  isConnected: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  markAllAsRead: () => void;
}

const OPERATOR_NOTIFICATION_ROLES = new Set(['SYSTEM_ADMIN', 'OWNER', 'TENANT_ADMIN', 'OPERATOR']);

const NotificationContext = createContext<NotificationContextValue | null>(null);

export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const { user, token } = useAuth();
  const router = useRouter();
  const [alerts, setAlerts] = useState<DispatchAlertDto[]>([]);
  const [inbox, setInbox] = useState<InboxNotificationDto[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [isConnected, setIsConnected] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const tenantId = user?.tenantId;
  const hasOperatorAccess =
    user?.systemRole?.toUpperCase() === 'SYSTEM_ADMIN' ||
    OPERATOR_NOTIFICATION_ROLES.has((user?.tenantRole || user?.role || '').toUpperCase());
  const mountedRef = useRef<boolean>(true);

  const refresh = useCallback(async () => {
    if (!tenantId || !hasOperatorAccess) return;
    try {
      const [activeAlerts, inboxItems] = await Promise.all([
        notificationRepository.getDispatchAlerts(),
        notificationRepository.getTenantInbox().catch(() => [] as InboxNotificationDto[]),
      ]);
      if (!mountedRef.current) return;
      setAlerts(activeAlerts);
      setInbox(inboxItems);
      setError(null);
    } catch {
      if (mountedRef.current) {
        setError('Chưa cập nhật được hòm thư thông báo. Dữ liệu hiển thị có thể đã cũ.');
      }
    }
  }, [tenantId, hasOperatorAccess]);

  const markAllAsRead = useCallback(() => {
    setUnreadCount(0);
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    if (!tenantId || !token || !hasOperatorAccess) {
      return;
    }

    let disposed = false;
    const controller = new AbortController();

    const prependToInbox = (item: InboxNotificationDto) => {
      setInbox((current) => {
        const exists = current.some((existing) => existing.id === item.id);
        if (exists) {
          return current.map((existing) => (existing.id === item.id ? { ...existing, ...item } : existing));
        }
        return [item, ...current].slice(0, 40);
      });
    };

    const handleSseMessage = (eventName: string, dataStr: string) => {
      if (disposed || !dataStr) return;

      if (eventName === 'connect' || eventName === 'connected') {
        setIsConnected(true);
        setError(null);
        return;
      }

      if (eventName === 'heartbeat' || eventName === 'ping') {
        setIsConnected(true);
        return;
      }

      // Notify active screens (orders, operations queue, dispatch) to refresh data
      if (typeof window !== 'undefined') {
        window.dispatchEvent(
          new CustomEvent('swiftroute:realtime-update', {
            detail: { eventName },
          })
        );
      }

      if (eventName === 'dispatch-alert') {
        try {
          const newAlert = JSON.parse(dataStr) as DispatchAlertDto & {
            template_code?: string;
            category?: InboxNotificationDto['category'];
          };
          setAlerts((current) => {
            const exists = current.some((item) => item.incident_id === newAlert.incident_id);
            if (exists) {
              return current.map((item) =>
                item.incident_id === newAlert.incident_id ? { ...item, ...newAlert } : item,
              );
            }
            return [newAlert, ...current];
          });
          prependToInbox({
            id: newAlert.id,
            template_code: newAlert.template_code || 'delivery.incident',
            category: newAlert.category || 'INCIDENT',
            severity: newAlert.severity || 'MEDIUM',
            title: newAlert.title,
            body: newAlert.body,
            action_url: newAlert.action_url || `/operations?incident=${newAlert.incident_id}`,
            incident_id: newAlert.incident_id,
            order_id: newAlert.order_id,
            dispatch_plan_id: newAlert.dispatch_plan_id,
            created_at: newAlert.created_at || new Date().toISOString(),
          });
          setUnreadCount((count) => count + 1);

          toast.warning(newAlert.title || 'Sự cố giao hàng trên tuyến', {
            description: newAlert.body || `Mức độ nghiêm trọng: ${newAlert.severity}`,
            duration: 8000,
            action: {
              label: 'Xử lý ngay',
              onClick: () => {
                router.push(newAlert.action_url || `/operations?incident=${newAlert.incident_id}`);
              },
            },
          });
        } catch {
          // Ignore json parse error
        }
        return;
      }

      if (eventName === 'incident-resolved') {
        try {
          const resolved = JSON.parse(dataStr) as Partial<InboxNotificationDto> & {
            incident_id?: string;
            incidentId?: string;
          };
          const resolvedId = resolved.incident_id || resolved.incidentId;
          if (resolvedId) {
            setAlerts((current) => current.filter((item) => item.incident_id !== resolvedId));
          }
          if (resolved.id && resolved.title) {
            prependToInbox({
              id: resolved.id,
              template_code: resolved.template_code || 'incident.resolved',
              category: 'INCIDENT',
              severity: resolved.severity || 'LOW',
              title: resolved.title,
              body: resolved.body || 'Sự cố đã được cập nhật phương án xử lý.',
              action_url: resolved.action_url || '/operations',
              incident_id: resolvedId || null,
              order_id: resolved.order_id || null,
              dispatch_plan_id: resolved.dispatch_plan_id || null,
              created_at: resolved.created_at || new Date().toISOString(),
            });
          }
          toast.success(resolved.title || 'Sự cố đã được giải quyết', {
            description: resolved.body || 'Cảnh báo đã được cập nhật trên bản đồ điều phối.',
            duration: 5000,
          });
        } catch {
          // Ignore json parse error
        }
        return;
      }

      if (eventName === 'operational-alert') {
        try {
          const opAlert = JSON.parse(dataStr) as Partial<InboxNotificationDto>;
          if (opAlert.id && opAlert.title) {
            prependToInbox({
              id: opAlert.id,
              template_code: opAlert.template_code || 'operational',
              category: opAlert.category || 'DELIVERY',
              severity: opAlert.severity || 'LOW',
              title: opAlert.title,
              body: opAlert.body || '',
              action_url: opAlert.action_url || '/orders',
              incident_id: opAlert.incident_id || null,
              order_id: opAlert.order_id || null,
              dispatch_plan_id: opAlert.dispatch_plan_id || null,
              created_at: opAlert.created_at || new Date().toISOString(),
            });
            setUnreadCount((count) => count + 1);
          }

          if (opAlert.template_code === 'order.delivery_completed.v1' || opAlert.template_code === 'plan.acknowledged') {
            toast.success(opAlert.title || 'Cập nhật giao nhận', {
              description: opAlert.body,
              duration: 5000,
            });
          } else if (opAlert.severity === 'HIGH' || opAlert.severity === 'CRITICAL') {
            toast.warning(opAlert.title || 'Cảnh báo vận hành', {
              description: opAlert.body,
              duration: 6000,
            });
          } else {
            toast.info(opAlert.title || 'Thông báo vận hành', {
              description: opAlert.body,
              duration: 5000,
            });
          }
        } catch {
          // Ignore json parse error
        }
      }
    };

    const connectSse = async () => {
      while (!disposed) {
        try {
          const currentTenantId = localStorage.getItem('tenant_id') || tenantId;
          await streamSse('/notifications/stream', {
            signal: controller.signal,
            headers: { 'X-Tenant-Id': currentTenantId },
            onOpen: async () => {
              setIsConnected(true);
              setError(null);
              await refresh();
            },
            onEvent: handleSseMessage,
          });
        } catch (err) {
          if (disposed || axios.isCancel(err) || controller.signal.aborted) {
            return;
          }
          if ((err as { status?: number })?.status === 403) {
            setIsConnected(false);
            setError(null);
            return;
          }
          setIsConnected(false);
          setError('Mất kết nối thông báo trực tiếp. Hệ thống đang tự kết nối lại.');
        }

        await new Promise((resolve) => window.setTimeout(resolve, 3000));
      }
    };

    void connectSse();

    return () => {
      disposed = true;
      mountedRef.current = false;
      controller.abort();
    };
  }, [tenantId, token, hasOperatorAccess, refresh, router]);

  return (
    <NotificationContext.Provider
      value={{
        alerts: hasOperatorAccess ? alerts : [],
        inbox: hasOperatorAccess ? inbox : [],
        unreadCount: hasOperatorAccess ? unreadCount : 0,
        isConnected: hasOperatorAccess && Boolean(tenantId && token) ? isConnected : false,
        error: hasOperatorAccess && Boolean(tenantId && token) ? error : null,
        refresh,
        markAllAsRead,
      }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const context = useContext(NotificationContext);
  return context;
}
