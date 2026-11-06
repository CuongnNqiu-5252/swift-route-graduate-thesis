export type DriverStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE';

export interface Driver {
  id: string;
  user_id: string;
  tenant_id: string;
  name: string;
  phone: string;
  vehicle_type: string;
  vehicle_plate: string;
  status: DriverStatus;
  onboarding_status: 'INACTIVE' | 'PENDING_APPROVAL' | 'ACTIVE' | 'SUSPENDED';
  is_available: boolean;
  capacity?: number;
  review_note?: string;
  current_lat?: number;
  current_lng?: number;
  created_at: string;
}

export interface CreateDriverPayload {
  userId?: string;
  tenantId: string;
  fullName: string;
  phone: string;
  licensePlate?: string;
  vehicleType: 'MOTORCYCLE' | 'CAR' | 'VAN';
  capacity: number;
  driverType: 'DEDICATED' | 'POOLED';
  shiftStart?: string;
  shiftEnd?: string;
}

export const DRIVER_STATUS_LABEL: Record<DriverStatus, string> = {
  AVAILABLE: 'Sẵn sàng',
  BUSY: 'Đang giao',
  OFFLINE: 'Offline',
};

export const DRIVER_STATUS_COLOR: Record<DriverStatus, string> = {
  AVAILABLE: 'bg-green-100 text-green-800',
  BUSY: 'bg-orange-100 text-orange-800',
  OFFLINE: 'bg-gray-100 text-gray-600',
};
