export type BusinessType = 'RESTAURANT' | 'CAFE' | 'PHARMACY' | 'RETAIL' | 'OTHER';

export interface TenantDispatchConfig {
  auto_dispatch_enabled: boolean;
  batch_size_threshold: number;
  max_wait_time_seconds: number;
  urgent_order_mode: 'IMMEDIATE_SINGLE' | 'BATCH_IMMEDIATE';
  require_approval: boolean;
  max_orders_per_driver: number;
  algorithm: string;
  allow_pool_drivers: boolean;
  scheduled_dispatch_enabled?: boolean;
  scheduled_dispatch_time?: string;
}

export interface OptimizationWeights {
  time_weight: number;
  cost_weight: number;
}

export interface TenantSettings {
  tenant_id: string;
  business_name: string;
  business_type: BusinessType;
  status: string;
  address?: string;
  dispatch_config: TenantDispatchConfig;
  optimization_weights: OptimizationWeights;
}

export const BUSINESS_TYPE_LABEL: Record<BusinessType, string> = {
  RESTAURANT: 'Quán ăn / Nhà hàng',
  CAFE: 'Quán cafe / Đồ uống',
  PHARMACY: 'Hiệu thuốc / Y tế',
  RETAIL: 'Bán lẻ / Tạp hóa',
  OTHER: 'Ngành nghề khác',
};

export const BUSINESS_TYPE_DESCRIPTION: Record<BusinessType, string> = {
  RESTAURANT: 'Giao nhanh đồ ăn nóng, gom tối đa 3 đơn/tài xế, ưu tiên thời gian (80%) và cho phép dùng tài xế Pool.',
  CAFE: 'Giao nhanh đồ uống chống tan đá, gom tối đa 4 đơn/tài xế, ưu tiên thời gian (70%) và cho phép dùng tài xế Pool.',
  PHARMACY: 'Giao thuốc men cấp thiết, gom tối đa 2 đơn/tài xế, ưu tiên thời gian tuyệt đối (90%) và cho phép dùng tài xế Pool.',
  RETAIL: 'Giao hàng khô/tạp hóa, gom tối đa 8 đơn/tài xế để tiết kiệm chi phí (60%), không dùng tài xế Pool.',
  OTHER: 'Cấu hình tiêu chuẩn cân bằng giữa thời gian (50%) và chi phí (50%), gom tối đa 5 đơn/tài xế.',
};

