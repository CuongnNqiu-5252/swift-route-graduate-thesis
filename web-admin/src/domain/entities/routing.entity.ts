export interface LocationPoint {
  lat: number;
  lng: number;
}

export interface OptimizationStop {
  id: string;
  location: LocationPoint;
  order?: {
    id: string;
    delivery_address: string;
    order_code: string;
  };
}

export interface OptimizationRequest {
  depot: LocationPoint;
  stops: OptimizationStop[];
  algorithm: 'auto' | 'ortools';
}

export interface OptimizationResult {
  algorithm_used: string;
  execution_time_ms: number;
  total_distance_km: number;
  total_duration_min: number;
  ordered_stops: OptimizationStop[];
  route_polyline?: string;
}

export interface WhatIfRequest extends OptimizationRequest {
  baseline_driver_count: number;
  scenario_driver_count: number;
  max_shift_minutes?: number;
}

export interface WhatIfScenario {
  driver_count: number;
  total_distance_km: number;
  completion_minutes: number;
  late_risk_orders: number;
  algorithm_used: string;
}

export interface WhatIfResult {
  baseline: WhatIfScenario;
  scenario: WhatIfScenario;
  distance_delta_percent: number;
  completion_delta_percent: number;
  late_risk_delta: number;
}
