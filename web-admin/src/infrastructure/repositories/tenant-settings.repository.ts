import { TenantSettings, BusinessType } from '@/domain/entities/tenant-settings.entity';
import api from '@/lib/api';

export interface UpdateSettingsPayload {
  business_name?: string;
  business_type?: BusinessType;
  address?: string;
  dispatch_config?: Partial<TenantSettings['dispatch_config']>;
  optimization_weights?: Partial<TenantSettings['optimization_weights']>;
}

export const tenantSettingsRepository = {
  async getSettings(): Promise<TenantSettings> {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const response = await api.get<any>('/tenants/me/settings');
    const data = response.data;
    return {
      tenant_id: String(data.tenant_id || data.tenantId || ''),
      business_name: String(data.business_name || data.businessName || ''),
      business_type: (data.business_type || data.businessType || 'OTHER') as BusinessType,
      status: String(data.status || 'APPROVED'),
      address: data.address || '',
      dispatch_config: {
        auto_dispatch_enabled: data.dispatch_config?.auto_dispatch_enabled ?? true,
        batch_size_threshold: data.dispatch_config?.batch_size_threshold ?? 5,
        max_wait_time_seconds: data.dispatch_config?.max_wait_time_seconds ?? 300,
        urgent_order_mode: data.dispatch_config?.urgent_order_mode || 'IMMEDIATE_SINGLE',
        require_approval: data.dispatch_config?.require_approval ?? false,
        max_orders_per_driver: data.dispatch_config?.max_orders_per_driver ?? 5,
        algorithm: data.dispatch_config?.algorithm || 'auto',
        allow_pool_drivers: data.dispatch_config?.allow_pool_drivers ?? false,
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

  async applyPreset(businessType: BusinessType): Promise<TenantSettings> {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const response = await api.post<any>('/tenants/me/settings/apply-preset', {
      business_type: businessType,
    });
    return response.data;
  },
};

