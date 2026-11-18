import { TenantSettings } from '@/domain/entities/tenant-settings.entity';
import api from '@/lib/api';

export interface UpdateSettingsPayload {
  business_name?: string;
  address?: string;
  dispatch_config?: Partial<TenantSettings['dispatch_config']>;
  optimization_weights?: Partial<TenantSettings['optimization_weights']>;
}

export const tenantSettingsRepository = {
  async getBusinessName(tenantId: string): Promise<string> {
    const { data } = await api.get<{ business_name: string }>(`/tenants/${tenantId}`);
    return data.business_name;
  },

  async getSettings(): Promise<TenantSettings> {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const response = await api.get<any>('/tenants/me/settings');
    const data = response.data;
    return {
      tenant_id: String(data.tenant_id || data.tenantId || ''),
      business_name: String(data.business_name || data.businessName || ''),
      status: String(data.status || 'APPROVED'),
      address: data.address || '',
      dispatch_config: {
        auto_dispatch_enabled: data.dispatch_config?.auto_dispatch_enabled ?? true,
        batch_size_threshold: data.dispatch_config?.batch_size_threshold ?? 5,
        max_wait_time_seconds: data.dispatch_config?.max_wait_time_seconds ?? 300,
        max_orders_per_driver: data.dispatch_config?.max_orders_per_driver ?? 5,
        algorithm: data.dispatch_config?.algorithm || 'auto',
        scheduled_dispatch_enabled: data.dispatch_config?.scheduled_dispatch_enabled ?? true,
        scheduled_dispatch_time: data.dispatch_config?.scheduled_dispatch_time || '06:00',
      },
      optimization_weights: {
        time_weight: Number(data.optimization_weights?.time_weight ?? data.optimization_weights?.timeWeight ?? 0.5),
        cost_weight: Number(data.optimization_weights?.cost_weight ?? data.optimization_weights?.costWeight ?? 0.5),
      },
    };
  },

  async updateSettings(payload: UpdateSettingsPayload): Promise<TenantSettings> {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const response = await api.put<any>('/tenants/me/settings', payload);
    return response.data;
  },

};

