export interface TenantDispatchConfig {
  auto_dispatch_enabled: boolean;
  batch_size_threshold: number;
  max_wait_time_seconds: number;
  max_orders_per_driver: number;
  algorithm: string;
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
  status: string;
  address?: string;
  dispatch_config: TenantDispatchConfig;
  optimization_weights: OptimizationWeights;
}
