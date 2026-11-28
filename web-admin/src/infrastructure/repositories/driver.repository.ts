import {
  CreateDriverPayload,
  Driver,
  DriverActivationKeyResult,
  DriverActivationStatusItem,
  DriverActivationStatusPage,
  DriverAppActivationStatus,
  DriverStatus,
  UpdateDriverBankAccountPayload,
  UpdateDriverPayload,
} from '@/domain/entities/driver.entity';
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
  return 'OFFLINE';
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function unwrapEnvelope(raw: any): any {
  if (raw && typeof raw === 'object' && 'data' in raw && raw.data !== undefined && raw.data !== null) {
    return raw.data;
  }
  return raw;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function mapDriver(rawInput: any): Driver {
  const raw = unwrapEnvelope(rawInput) ?? {};
  const rawId = raw.driverId || raw.id || raw.driver_id || '';
  return {
    id: rawId,
    user_id: raw.userId || raw.user_id || '',
    tenant_id: raw.tenantId || raw.tenant_id || '',
    name: raw.full_name || raw.fullName || raw.name || '',
    phone: raw.phone || '',
    email: raw.email || undefined,
    vehicle_type: raw.vehicle_type || raw.vehicleType || '',
    vehicle_plate: raw.license_plate || raw.licensePlate || raw.vehicle_plate || '',
    status: mapDriverStatus(raw.status, raw.is_available ?? raw.isAvailable),
    onboarding_status: raw.status || 'INACTIVE',
    is_available: Boolean(raw.is_available ?? raw.isAvailable),
    capacity: raw.capacity ?? undefined,
    shift_start: raw.shift_start ?? raw.shiftStart ?? undefined,
    shift_end: raw.shift_end ?? raw.shiftEnd ?? undefined,
    review_note: raw.review_note || raw.reviewNote || undefined,
    current_lat: raw.current_lat ?? raw.currentLat ?? 0,
    current_lng: raw.current_lng ?? raw.currentLng ?? 0,
    bank_code: raw.bank_code || raw.bankCode || undefined,
    bank_account_number: raw.bank_account_number || raw.bankAccountNumber || undefined,
    bank_account_name: raw.bank_account_name || raw.bankAccountName || undefined,
    created_at: raw.created_at || raw.createdAt || '',
  };
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function mapActivationStatusItem(raw: any): DriverActivationStatusItem {
  return {
    driver_id: raw.driver_id || raw.driverId || '',
    driver_code: raw.driver_code || raw.driverCode || '',
    user_id: raw.user_id || raw.userId || undefined,
    full_name: raw.full_name || raw.fullName || '',
    phone: raw.phone || undefined,
    email: raw.email || undefined,
    license_plate: raw.license_plate || raw.licensePlate || undefined,
    vehicle_type: raw.vehicle_type || raw.vehicleType || undefined,
    operational_status: raw.operational_status || raw.operationalStatus || 'OFFLINE',
    profile_status: raw.profile_status || raw.profileStatus || 'INACTIVE',
    activation_status: (raw.activation_status || raw.activationStatus || 'NOT_ACTIVATED') as DriverAppActivationStatus,
    key_created_at: raw.key_created_at || raw.keyCreatedAt || undefined,
    key_expires_at: raw.key_expires_at || raw.keyExpiresAt || undefined,
    app_version: raw.app_version || raw.appVersion || undefined,
    device_os: raw.device_os || raw.deviceOs || undefined,
    os_version: raw.os_version || raw.osVersion || undefined,
    device_model: raw.device_model || raw.deviceModel || undefined,
    activated_at: raw.activated_at || raw.activatedAt || undefined,
    last_seen_at: raw.last_seen_at || raw.lastSeenAt || undefined,
    deactivated_at: raw.deactivated_at || raw.deactivatedAt || undefined,
  };
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function mapActivationKeyResult(rawInput: any): DriverActivationKeyResult {
  const raw = unwrapEnvelope(rawInput) ?? {};
  const rawKey = String(raw.activation_key || raw.activationKey || '');
  const formatted =
    raw.formatted_activation_key ||
    raw.formattedActivationKey ||
    (rawKey.length === 12 ? `${rawKey.slice(0, 4)} - ${rawKey.slice(4, 8)} - ${rawKey.slice(8, 12)}` : rawKey);
  return {
    driver_id: raw.driver_id || raw.driverId || '',
    driver_name: raw.driver_name || raw.driverName || '',
    email: raw.email || undefined,
    activation_key: rawKey,
    formatted_activation_key: formatted,
    status: (raw.status || 'KEY_CREATED') as DriverAppActivationStatus,
    expires_at: raw.expires_at || raw.expiresAt || '',
    email_sent: Boolean(raw.email_sent ?? raw.emailSent),
  };
}

export const driverRepository = {
  async getDrivers(): Promise<Driver[]> {
    try {
      const response = await api.get<unknown>('/drivers');
      const data = unwrapEnvelope(response.data);
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
        email: payload.email || undefined,
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

  async updateDriver(driverId: string, payload: UpdateDriverPayload): Promise<Driver> {
    const response = await api.put<unknown>(`/drivers/${driverId}`, payload);
    return mapDriver(response.data);
  },

  async updateBankAccount(driverId: string, payload: UpdateDriverBankAccountPayload): Promise<Driver> {
    const response = await api.patch<unknown>(`/drivers/${driverId}/bank-account`, payload);
    return mapDriver(response.data);
  },

  async getActivationStatus(params?: {
    page?: number;
    size?: number;
    query?: string;
    status?: string;
  }): Promise<DriverActivationStatusPage> {
    const response = await api.get<unknown>('/drivers/activation-status', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? 25,
        query: params?.query?.trim() || undefined,
        status: params?.status && params.status !== 'ALL' ? params.status : undefined,
      },
    });
    const data = unwrapEnvelope(response.data) as Record<string, unknown>;
    const rawContent = Array.isArray(data?.content) ? data.content : [];
    return {
      content: rawContent.map(mapActivationStatusItem),
      page: Number(data?.page ?? 0),
      size: Number(data?.size ?? 25),
      total_elements: Number(data?.total_elements ?? data?.totalElements ?? rawContent.length),
      total_pages: Number(data?.total_pages ?? data?.totalPages ?? 0),
      has_next: Boolean(data?.has_next ?? data?.hasNext),
      has_previous: Boolean(data?.has_previous ?? data?.hasPrevious),
    };
  },

  async generateActivationCode(driverId: string): Promise<DriverActivationKeyResult> {
    const response = await api.post<unknown>(`/drivers/${driverId}/activation`);
    return mapActivationKeyResult(response.data);
  },

  async regenerateActivationCode(driverId: string): Promise<DriverActivationKeyResult> {
    const response = await api.post<unknown>(`/drivers/${driverId}/activation/regenerate`);
    return mapActivationKeyResult(response.data);
  },

  async deactivateDriverApp(driverId: string): Promise<void> {
    await api.delete(`/drivers/${driverId}/activation`);
  },

  async sendActivationEmail(driverId: string, email?: string): Promise<DriverActivationKeyResult> {
    const response = await api.post<unknown>(
      `/drivers/${driverId}/activation/send-email`,
      email ? { email } : {}
    );
    return mapActivationKeyResult(response.data);
  },

  async bulkActivateDrivers(driverIds: string[], sendEmail = false): Promise<DriverActivationKeyResult[]> {
    const response = await api.post<unknown>('/drivers/activation/bulk', {
      driver_ids: driverIds,
      send_email: sendEmail,
    });
    const data = unwrapEnvelope(response.data);
    return Array.isArray(data) ? data.map(mapActivationKeyResult) : [];
  },

  async bulkSendActivationEmail(driverIds: string[]): Promise<DriverActivationKeyResult[]> {
    const response = await api.post<unknown>('/drivers/activation/bulk-send-email', {
      driver_ids: driverIds,
      send_email: true,
    });
    const data = unwrapEnvelope(response.data);
    return Array.isArray(data) ? data.map(mapActivationKeyResult) : [];
  },

  async bulkDeactivateDrivers(driverIds: string[]): Promise<void> {
    await api.post('/drivers/activation/bulk-deactivate', {
      driver_ids: driverIds,
      send_email: false,
    });
  },
};
