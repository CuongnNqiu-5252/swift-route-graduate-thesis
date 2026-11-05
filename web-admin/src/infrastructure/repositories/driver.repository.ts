import { CreateDriverPayload, Driver, DriverStatus } from '@/domain/entities/driver.entity';
import api from '@/lib/api';

function mapDriverStatus(status?: string, isAvailable?: boolean): DriverStatus {
  if (status === 'SUSPENDED' || status === 'INACTIVE' || status === 'PENDING_APPROVAL') {
    return 'OFFLINE';
  }
  if ((status === 'ACTIVE' || status === 'AVAILABLE') && isAvailable === true) {
    return 'AVAILABLE';
  }
  if (status === 'BUSY' || status === 'IN_TRANSIT') {
    return 'BUSY';
  }
  if (status === 'SUSPENDED' || status === 'INACTIVE' || status === 'OFFLINE') {
    return 'OFFLINE';
  }
  return 'OFFLINE';
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function mapDriver(raw: any): Driver {
  const rawId = raw.driverId || raw.id || raw.driver_id || '';
  return {
    id: rawId,
    user_id: raw.userId || raw.user_id || '',
    tenant_id: raw.tenantId || raw.tenant_id || '',
    name: raw.full_name || raw.fullName || raw.name || '',
    phone: raw.phone || '',
    vehicle_type: raw.vehicle_type || raw.vehicleType || '',
    vehicle_plate: raw.license_plate || raw.licensePlate || raw.vehicle_plate || '',
    status: mapDriverStatus(raw.status, raw.is_available ?? raw.isAvailable),
    onboarding_status: raw.status || 'INACTIVE',
    is_available: Boolean(raw.is_available ?? raw.isAvailable),
    capacity: raw.capacity ?? undefined,
    review_note: raw.review_note || raw.reviewNote || undefined,
    current_lat: raw.current_lat ?? raw.currentLat ?? 0,
    current_lng: raw.current_lng ?? raw.currentLng ?? 0,
    created_at: raw.created_at || raw.createdAt || '',
  };
}

export const driverRepository = {
  async getDrivers(): Promise<Driver[]> {
    try {
      const response = await api.get<unknown>('/drivers');
      const responseBody = response.data as { data?: unknown };
      const data = responseBody?.data ?? responseBody;
      if (Array.isArray(data) && data.length > 0) {
        return data.map(mapDriver);
      }
      const paged = data as { content?: unknown };
      if (Array.isArray(paged?.content) && paged.content.length > 0) {
        return paged.content.map(mapDriver);
      }
      return [];
    } catch {
      throw new Error('Không thể tải tài xế');
    }
  },

  async createDriver(payload: CreateDriverPayload): Promise<Driver> {
    try {
      const response = await api.post<unknown>('/drivers', {
        user_id: payload.userId,
        tenant_id: payload.tenantId,
        full_name: payload.fullName,
        phone: payload.phone,
        license_plate: payload.licensePlate,
        vehicle_type: payload.vehicleType,
        capacity: payload.capacity,
        driver_type: payload.driverType,
        shift_start: payload.shiftStart,
        shift_end: payload.shiftEnd,
      });
      return mapDriver(response.data);
    } catch (error) {
      throw error instanceof Error ? error : new Error('Không thể tạo hồ sơ tài xế');
    }
  },

  async reviewDriver(driverId: string, approved: boolean, note?: string): Promise<Driver> {
    const response = await api.post<unknown>(`/drivers/${driverId}/review`, { approved, note });
    return mapDriver(response.data);
  },
};
