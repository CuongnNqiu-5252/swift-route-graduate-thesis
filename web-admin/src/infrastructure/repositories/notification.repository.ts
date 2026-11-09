import api from '@/lib/api';

export interface DispatchAlertDto {
  id: string;
  incident_id: string;
  driver_id: string | null;
  dispatch_plan_id: string | null;
  order_id: string | null;
  title: string;
  body: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  action_url: string;
  created_at: string;
}

export interface InboxNotificationDto {
  id: string;
  template_code: string;
  category: 'INCIDENT' | 'DELIVERY' | 'DISPATCH' | 'COD' | 'EARNINGS' | 'WORKSPACE' | 'SYSTEM';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  title: string;
  body: string;
  action_url: string;
  incident_id: string | null;
  order_id: string | null;
  dispatch_plan_id: string | null;
  created_at: string;
}

export const notificationRepository = {
  async getDispatchAlerts(): Promise<DispatchAlertDto[]> {
    const response = await api.get<DispatchAlertDto[]>('/notifications/dispatch-alerts');
    return response.data;
  },

  async getTenantInbox(): Promise<InboxNotificationDto[]> {
    const response = await api.get<InboxNotificationDto[]>('/notifications/inbox');
    return response.data;
  },
};
