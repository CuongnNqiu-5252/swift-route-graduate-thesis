export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'READY_FOR_DISPATCH'
  | 'PLANNED'
  | 'IN_TRANSIT'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'FAILED';

export type PackageCategory = 'DOCUMENT' | 'PARCEL' | 'FRAGILE' | 'PERISHABLE';

export interface Order {
  id: string;
  order_code: string;
  tenant_id: string;
  tenant_name?: string;
  customer_id: string;
  customer_name?: string;
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
  payment_status: string;
  created_at: string;
  updated_at: string;
}

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING_PAYMENT: 'Chờ thanh toán',
  READY_FOR_DISPATCH: 'Sẵn sàng điều phối',
  PLANNED: 'Đã lên lịch',
  IN_TRANSIT: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã huỷ',
  FAILED: 'Sự cố / Thất bại',
};

export const ORDER_STATUS_COLOR: Record<OrderStatus, string> = {
  PENDING_PAYMENT: 'bg-yellow-100 text-yellow-800',
  READY_FOR_DISPATCH: 'bg-blue-100 text-blue-800',
  PLANNED: 'bg-purple-100 text-purple-800',
  IN_TRANSIT: 'bg-orange-100 text-orange-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-gray-100 text-gray-800',
  FAILED: 'bg-rose-100 text-rose-800',
};
