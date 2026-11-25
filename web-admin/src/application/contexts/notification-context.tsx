'use client';

import { isRequestCancelled } from '@/lib/api';
import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/application/contexts/auth-context';
import { formatNotification } from '@/lib/notification-formatter';
import {
  DispatchAlertDto,
  InboxNotificationDto,
  notificationRepository,
} from '@/infrastructure/repositories/notification.repository';

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
  const { user, token, hasPermission, applyLivePermissions, refreshProfile } = useAuth();
  const router = useRouter();
  const [alerts, setAlerts] = useState<DispatchAlertDto[]>([]);
  const [inbox, setInbox] = useState<InboxNotificationDto[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [isConnected, setIsConnected] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const tenantId = user?.tenantId;
  const hasOperatorAccess =
    hasPermission('notification:read') ||
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
      const formattedAlerts = activeAlerts.map((alert) => {
        const formatted = formatNotification(alert);
        return {
          ...alert,
          title: formatted.title,
          body: formatted.body,
          severity: formatted.severity as DispatchAlertDto['severity'],
          action_url: formatted.actionUrl || alert.action_url,
        };
      });
      const formattedInbox = inboxItems.map((item) => {
        const formatted = formatNotification(item);
        return {
          ...item,
          title: formatted.title,
          body: formatted.body,
          severity: formatted.severity as InboxNotificationDto['severity'],
          category: formatted.category as InboxNotificationDto['category'],
          action_url: formatted.actionUrl || item.action_url,
        };
      });
      setAlerts(formattedAlerts);
      setInbox(formattedInbox);
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

      if (eventName === 'permissions-updated') {
        try {
          const payload = JSON.parse(dataStr) as {
            tenant_id?: string;
            user_id?: string;
            membership_role?: string;
            status?: string;
            permissions?: string[];
            permissions_version?: number;
          };

          if (typeof window !== 'undefined') {
            window.dispatchEvent(
              new CustomEvent('swiftroute:permissions-updated', {
                detail: payload,
              })
            );
          }

          if (payload.user_id && payload.user_id === user?.id && payload.tenant_id === tenantId) {
            applyLivePermissions({
              membershipRole: payload.membership_role,
              status: payload.status,
              permissions: payload.permissions,
              permissionsVersion: payload.permissions_version,
            });
            void refreshProfile();

            if (payload.status && payload.status.toUpperCase() !== 'ACTIVE') {
              toast.warning('Quyền truy cập của bạn tại doanh nghiệp đã bị tạm khóa hoặc thu hồi.', {
                duration: 6000,
              });
            } else {
              toast.info('Quyền hạn của bạn trong doanh nghiệp vừa được cập nhật.', {
                duration: 4500,
              });
            }
          }
        } catch {
          // Ignore malformed permissions sync event
        }
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
          const rawAlert = JSON.parse(dataStr) as DispatchAlertDto & {
            template_code?: string;
            category?: InboxNotificationDto['category'];
          };
          const formatted = formatNotification(rawAlert);
          const newAlert: DispatchAlertDto & {
            template_code?: string;
            category?: InboxNotificationDto['category'];
          } = {
            ...rawAlert,
            title: formatted.title,
            body: formatted.body,
            severity: (formatted.severity || rawAlert.severity || 'MEDIUM') as DispatchAlertDto['severity'],
            action_url: formatted.actionUrl || rawAlert.action_url || `/operations?incident=${rawAlert.incident_id}`,
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
          const formatted = formatNotification({
            ...resolved,
            category: 'INCIDENT',
            severity: resolved.severity || 'LOW',
            template_code: resolved.template_code || 'incident.resolved',
          });
          if (resolvedId) {
            setAlerts((current) => current.filter((item) => item.incident_id !== resolvedId));
          }
          if (resolved.id && formatted.title) {
            prependToInbox({
              id: resolved.id,
              template_code: resolved.template_code || 'incident.resolved',
              category: 'INCIDENT',
              severity: (formatted.severity || 'LOW') as InboxNotificationDto['severity'],
              title: formatted.title,
              body: formatted.body || 'Sự cố đã được cập nhật phương án xử lý.',
              action_url: formatted.actionUrl || resolved.action_url || '/operations',
              incident_id: resolvedId || null,
              order_id: resolved.order_id || null,
              dispatch_plan_id: resolved.dispatch_plan_id || null,
              created_at: resolved.created_at || new Date().toISOString(),
            });
          }
          toast.success(formatted.title || 'Sự cố đã được giải quyết', {
            description: formatted.body || 'Cảnh báo đã được cập nhật trên bản đồ điều phối.',
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
          const formatted = formatNotification(opAlert);
          const fullItem: InboxNotificationDto = {
            id: opAlert.id || crypto.randomUUID(),
            template_code: opAlert.template_code || 'operational',
            category: (formatted.category || opAlert.category || 'DELIVERY') as InboxNotificationDto['category'],
            severity: (formatted.severity || opAlert.severity || 'LOW') as InboxNotificationDto['severity'],
            title: formatted.title,
            body: formatted.body,
            action_url: formatted.actionUrl || opAlert.action_url || '/orders',
            incident_id: opAlert.incident_id || null,
            order_id: opAlert.order_id || null,
            dispatch_plan_id: opAlert.dispatch_plan_id || null,
            created_at: opAlert.created_at || new Date().toISOString(),
          };

          if (opAlert.id && formatted.title) {
            prependToInbox(fullItem);
            setUnreadCount((count) => count + 1);
          }

          if (
            opAlert.template_code === 'order.delivery_completed.v1' ||
            opAlert.template_code === 'plan.acknowledged' ||
            opAlert.template_code === 'plan.completed' ||
            opAlert.template_code === 'tenant.approved.v1' ||
            opAlert.template_code === 'tenant.approved' ||
            opAlert.template_code === 'tenant.verification_submitted.v1'
          ) {
            toast.success(formatted.title, {
              description: formatted.body,
              duration: 5000,
            });
          } else if (
            formatted.severity === 'CRITICAL' ||
            opAlert.template_code === 'tenant.suspended.v1'
          ) {
            toast.error(formatted.title, {
              description: formatted.body,
              duration: 7000,
            });
          } else if (
            formatted.severity === 'HIGH' ||
            opAlert.template_code === 'tenant.verification_changes_requested.v1' ||
            opAlert.template_code === 'tenant.rejected.v1'
          ) {
            toast.warning(formatted.title, {
              description: formatted.body,
              duration: 6000,
            });
          } else {
            toast.info(formatted.title, {
              description: formatted.body,
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
          await notificationRepository.streamAlerts({
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
          if (disposed || isRequestCancelled(err) || controller.signal.aborted) {
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
  }, [tenantId, token, user?.id, hasOperatorAccess, refresh, applyLivePermissions, refreshProfile, router]);

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
