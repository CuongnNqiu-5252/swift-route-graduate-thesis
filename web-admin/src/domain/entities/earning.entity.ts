export type EarningStatus = 'PENDING' | 'APPROVED' | 'PAID';
export type SettlementStatus = 'DRAFT' | 'APPROVED' | 'PAID';

export interface DriverEarning {
  id: string;
  order_id: string;
  delivery_task_id?: string;
  driver_id: string;
  driver_name: string;
  earning_type: string;
  delivery_fee: number;
  payable_distance_km: number;
  base_amount: number;
  distance_amount: number;
  bonus_amount: number;
  adjustment_amount: number;
  net_amount: number;
  status: EarningStatus;
  settlement_id?: string;
  completed_at: string;
}

export interface DriverSettlement {
  id: string;
  driver_id: string;
  driver_name: string;
  period_start: string;
  period_end: string;
  status: SettlementStatus;
  earning_count: number;
  total_amount: number;
  approved_at?: string;
  paid_at?: string;
  payment_reference?: string;
  created_at: string;
}

export interface EarningPolicy {
  tenant_id: string;
  base_amount: number;
  per_km_amount: number;
  minimum_amount: number;
  heavy_weight_threshold_kg: number;
  heavy_bonus_amount: number;
  currency: string;
}
