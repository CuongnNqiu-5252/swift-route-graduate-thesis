export type DriverStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE';

export type DriverAppActivationStatus =
  | 'NOT_ACTIVATED'
  | 'KEY_CREATED'
  | 'ACTIVATED'
  | 'KEY_EXPIRED'
  | 'DEACTIVATED';

export interface Driver {
  id: string;
  user_id: string;
  tenant_id: string;
  name: string;
  phone: string;
  email?: string;
  vehicle_type: string;
  vehicle_plate: string;
  status: DriverStatus;
  onboarding_status: 'INACTIVE' | 'PENDING_APPROVAL' | 'ACTIVE' | 'SUSPENDED';
  is_available: boolean;
  capacity?: number;
  shift_start?: string;
  shift_end?: string;
  review_note?: string;
  current_lat?: number;
  current_lng?: number;
  bank_code?: string;
  bank_account_number?: string;
  bank_account_name?: string;
  created_at: string;
}

export interface DriverActivationStatusItem {
  driver_id: string;
  driver_code: string;
  user_id?: string;
  full_name: string;
  phone?: string;
  email?: string;
  license_plate?: string;
  vehicle_type?: string;
  operational_status: 'AVAILABLE' | 'OFFLINE';
  profile_status: 'INACTIVE' | 'PENDING_APPROVAL' | 'ACTIVE' | 'SUSPENDED';
  activation_status: DriverAppActivationStatus;
  key_created_at?: string;
  key_expires_at?: string;
  app_version?: string;
  device_os?: string;
  os_version?: string;
  device_model?: string;
  activated_at?: string;
  last_seen_at?: string;
  deactivated_at?: string;
}

export interface DriverActivationStatusPage {
  content: DriverActivationStatusItem[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
  has_next: boolean;
  has_previous: boolean;
}

export interface DriverActivationKeyResult {
  driver_id: string;
  driver_name: string;
  email?: string;
  activation_key: string;
  formatted_activation_key: string;
  status: DriverAppActivationStatus;
  expires_at: string;
  email_sent: boolean;
}

export interface UpdateDriverBankAccountPayload {
  bank_code: string;
  bank_account_number: string;
  bank_account_name?: string;
}

export interface UpdateDriverPayload {
  full_name?: string;
  phone?: string;
  email?: string;
  license_plate?: string;
  vehicle_type?: 'MOTORCYCLE' | 'CAR' | 'VAN';
  capacity?: number;
  driver_type?: 'DEDICATED';
  shift_start?: string;
  shift_end?: string;
}

export interface CreateDriverPayload {
  userId?: string;
  tenantId: string;
  fullName: string;
  phone: string;
  email?: string;
  licensePlate?: string;
  vehicleType: 'MOTORCYCLE' | 'CAR' | 'VAN';
  capacity: number;
  driverType: 'DEDICATED';
  shiftStart?: string;
  shiftEnd?: string;
}
