export type PickupLocationStatus = 'ACTIVE' | 'INACTIVE';

export interface PickupLocation {
  id: string;
  tenant_id: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
  contact_name?: string | null;
  contact_phone?: string | null;
  is_default: boolean;
  status: PickupLocationStatus;
  created_at: string;
  updated_at: string;
}

export const PICKUP_LOCATION_STATUS_LABEL: Record<PickupLocationStatus, string> = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
};

export const PICKUP_LOCATION_STATUS_COLOR: Record<PickupLocationStatus, string> = {
  ACTIVE: 'bg-emerald-100 text-emerald-800 border-emerald-200',
  INACTIVE: 'bg-gray-100 text-gray-700 border-gray-200',
};

