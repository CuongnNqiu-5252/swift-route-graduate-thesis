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
  driverType: 'DEDICATED';
  shiftStart?: string;
  shiftEnd?: string;
}
