export type DeliveryZoneType = 'CIRCLE';

export type DeliveryZoneEnforcementMode = 'STRICT_BLOCK' | 'WARN_ONLY';

export interface TenantDeliveryZone {
  configured: boolean;
  zone_id: string | null;
  tenant_id: string;
  zone_type: DeliveryZoneType;
  center_lat: number | null;
  center_lng: number | null;
  radius_km: number | null;
  enforcement_mode: DeliveryZoneEnforcementMode;
  is_active: boolean;
  updated_at: string | null;
}

export interface UpsertDeliveryZonePayload {
  center_lat: number;
  center_lng: number;
  radius_km: number;
  enforcement_mode: DeliveryZoneEnforcementMode;
  is_active: boolean;
}

export interface DeliveryZoneCheckResult {
  configured: boolean;
  allowed: boolean;
  in_zone: boolean;
  distance_km: number;
  radius_km: number | null;
  enforcement_mode: DeliveryZoneEnforcementMode | null;
  message: string;
}

export const ENFORCEMENT_MODE_OPTIONS: Array<{
  value: DeliveryZoneEnforcementMode;
  label: string;
  description: string;
}> = [
  {
    value: 'STRICT_BLOCK',
    label: 'Chặn ngoài vùng',
    description: 'Từ chối tạo đơn hàng nếu điểm giao nằm ngoài bán kính phục vụ.',
  },
  {
    value: 'WARN_ONLY',
    label: 'Chỉ cảnh báo',
    description: 'Ghi nhận cảnh báo trên hệ thống nhưng vẫn cho phép tạo đơn.',
  },
];

export const QUICK_RADIUS_KM = [3, 5, 10, 15, 25] as const;
