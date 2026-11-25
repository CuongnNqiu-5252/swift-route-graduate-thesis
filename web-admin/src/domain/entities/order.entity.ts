export type OrderStatus =
  | 'READY_FOR_DISPATCH'
  | 'PLANNED'
  | 'IN_TRANSIT'
  | 'DELIVERED'
  | 'FAILED'
  | 'RETURNING'
  | 'CANCELLED'
  | 'RETURNED';

export type PackageCategory = 'DOCUMENT' | 'PARCEL' | 'FRAGILE' | 'PERISHABLE';

export interface Order {
  id: string;
  order_code: string;
  tracking_token?: string;
  tenant_id: string;
  tenant_name?: string;
  customer_id: string;
  customer_name?: string;
  sender_name?: string;
  sender_phone?: string;
  recipient_phone?: string;
  pickup_location_id?: string;
  pickup_location_name?: string;
  pickup_address: string;
  delivery_address: string;
  pickup_lat: number;
  pickup_lng: number;
  delivery_lat: number;
  delivery_lng: number;
  package_category: PackageCategory;
  weight_kg: number;
  status: OrderStatus;
  notes?: string;
  time_window_start?: string;
  time_window_end?: string;
  assigned_driver_id?: string;
  assigned_driver_name?: string;
  cod_amount: number;
  cod_status?: string;
  cod_collected_at?: string;
  total_amount?: number;
  created_at: string;
  updated_at: string;
}

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  READY_FOR_DISPATCH: 'Sẵn sàng điều phối',
  PLANNED: 'Đã lên lịch',
  IN_TRANSIT: 'Đang giao',
  DELIVERED: 'Đã giao',
  FAILED: 'Sự cố / Thất bại',
  RETURNING: 'Đang hoàn về kho',
  CANCELLED: 'Đã huỷ',
  RETURNED: 'Đã chuyển hoàn',
};

export const ORDER_STATUS_COLOR: Record<OrderStatus, string> = {
  READY_FOR_DISPATCH: 'bg-blue-100 text-blue-800',
  PLANNED: 'bg-purple-100 text-purple-800',
  IN_TRANSIT: 'bg-orange-100 text-orange-800',
  DELIVERED: 'bg-green-100 text-green-800',
  FAILED: 'bg-rose-100 text-rose-800',
  RETURNING: 'bg-amber-100 text-amber-800',
  CANCELLED: 'bg-gray-100 text-gray-800',
  RETURNED: 'bg-amber-100 text-amber-800',
};

export type OrderTimelineCategory =
  | 'STATUS'
  | 'DELIVERY'
  | 'INCIDENT'
  | 'HANDOVER'
  | 'RETURN'
  | 'COD';

export interface OrderTimelineEvent {
  id: string;
  event_type: string;
  category: OrderTimelineCategory | string;
  title: string;
  description?: string | null;
  status?: string | null;
  from_status?: string | null;
  to_status?: string | null;
  actor_id?: string | null;
  actor_role?: string | null;
  proof_url?: string | null;
  evidence_urls: string[];
  occurred_at: string;
  metadata?: Record<string, unknown>;
}

export interface OrderTimeline {
  order_id: string;
  order_code: string;
  tracking_token?: string | null;
  current_status: OrderStatus;
  assigned_driver_id?: string | null;
  custodian_driver_id?: string | null;
  dispatch_plan_id?: string | null;
  latest_proof_url?: string | null;
  created_at: string;
  updated_at: string;
  events: OrderTimelineEvent[];
}

export interface PublicTrackingMilestone {
  id: string;
  event_type: string;
  title: string;
  description?: string | null;
  status?: string | null;
  proof_url?: string | null;
  occurred_at: string;
}

export interface PublicOrderTracking {
  tracking_token: string;
  order_code: string;
  carrier_name: string;
  status: OrderStatus;
  progress_step: number;
  masked_sender_name: string;
  masked_pickup_area: string;
  masked_recipient_name: string;
  masked_recipient_phone: string;
  masked_delivery_area: string;
  package_category: PackageCategory;
  package_description?: string | null;
  weight_kg: number;
  cod_amount: number;
  cod_status?: string | null;
  time_window_start?: string | null;
  time_window_end?: string | null;
  latest_proof_url?: string | null;
  created_at: string;
  updated_at: string;
  milestones: PublicTrackingMilestone[];
}
