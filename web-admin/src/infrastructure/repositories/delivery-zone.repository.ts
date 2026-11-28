import {
  DeliveryZoneCheckResult,
  TenantDeliveryZone,
  UpsertDeliveryZonePayload,
} from '@/domain/entities/delivery-zone.entity';
import api from '@/lib/api';

function unwrap<T>(payload: unknown): T {
  if (payload && typeof payload === 'object' && 'data' in payload && 'error' in payload) {
    return (payload as { data: T }).data;
  }
  return payload as T;
}

export const deliveryZoneRepository = {
  async getMyZone(): Promise<TenantDeliveryZone> {
    const response = await api.get<TenantDeliveryZone>('/tenants/me/delivery-zone');
    return unwrap<TenantDeliveryZone>(response.data);
  },

  async upsertMyZone(payload: UpsertDeliveryZonePayload): Promise<TenantDeliveryZone> {
    const response = await api.put<TenantDeliveryZone>('/tenants/me/delivery-zone', payload);
    return unwrap<TenantDeliveryZone>(response.data);
  },

  async checkMyLocation(lat: number, lng: number): Promise<DeliveryZoneCheckResult> {
    const response = await api.post<DeliveryZoneCheckResult>('/tenants/me/delivery-zone/check', {
      lat,
      lng,
    });
    return unwrap<DeliveryZoneCheckResult>(response.data);
  },
};
