import { PickupLocation, PickupLocationStatus } from '@/domain/entities/pickup-location.entity';
import api from '@/lib/api';

export interface CreatePickupLocationPayload {
  name: string;
  address: string;
  lat: number;
  lng: number;
  contactName?: string;
  contactPhone?: string;
  isDefault?: boolean;
}

export interface UpdatePickupLocationPayload {
  name: string;
  address: string;
  lat: number;
  lng: number;
  contactName?: string;
  contactPhone?: string;
  isDefault?: boolean;
  status?: PickupLocationStatus;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function mapLocation(raw: any): PickupLocation {
  return {
    id: raw.id, tenant_id: raw.tenantId ?? raw.tenant_id, name: raw.name, address: raw.address,
    lat: Number(raw.lat), lng: Number(raw.lng), contact_name: raw.contactName ?? raw.contact_name,
    contact_phone: raw.contactPhone ?? raw.contact_phone, is_default: raw.isDefault ?? raw.is_default ?? false,
    status: raw.status, created_at: raw.createdAt ?? raw.created_at, updated_at: raw.updatedAt ?? raw.updated_at,
  };
}

// Axios already unwraps the platform envelope. Keep compatibility with a direct envelope response.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function body(raw: any): any { return raw?.data ?? raw; }

export const pickupLocationRepository = {
  async getPickupLocations(status?: PickupLocationStatus): Promise<PickupLocation[]> {
    const params = status ? { status } : {};
    const res = await api.get('/pickup-locations', { params });
    const data = body(res.data);
    return Array.isArray(data) ? data.map(mapLocation) : [];
  },

  async getPickupLocation(id: string): Promise<PickupLocation> {
    const res = await api.get(`/pickup-locations/${id}`);
    return mapLocation(body(res.data));
  },

  async createPickupLocation(payload: CreatePickupLocationPayload): Promise<PickupLocation> {
    const res = await api.post('/pickup-locations', {
      name: payload.name,
      address: payload.address,
      lat: payload.lat,
      lng: payload.lng,
      contactName: payload.contactName,
      contactPhone: payload.contactPhone,
      isDefault: payload.isDefault ?? false,
    });
    return mapLocation(body(res.data));
  },

  async updatePickupLocation(id: string, payload: UpdatePickupLocationPayload): Promise<PickupLocation> {
    const res = await api.put(`/pickup-locations/${id}`, {
      name: payload.name,
      address: payload.address,
      lat: payload.lat,
      lng: payload.lng,
      contactName: payload.contactName,
      contactPhone: payload.contactPhone,
      isDefault: payload.isDefault,
      status: payload.status,
    });
    return mapLocation(body(res.data));
  },

  async setDefaultLocation(id: string): Promise<PickupLocation> {
    const res = await api.patch(`/pickup-locations/${id}/set-default`);
    return mapLocation(body(res.data));
  },

  async deletePickupLocation(id: string): Promise<void> {
    await api.delete(`/pickup-locations/${id}`);
  },
};
