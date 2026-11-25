export interface LocationPoint {
  lat: number;
  lng: number;
}

export interface DispatchDepot extends LocationPoint {
  name: string;
  address: string;
  pickup_location_id?: string;
}
export interface MapOverviewRoute {
  id: string;
  label: string;
  status: PlanStatus;
  color: string;
  depot: DispatchDepot;
  stops: OptimizationStop[];
  driverId?: string;
  driverName?: string;
  warehouseId?: string;
}

export interface MapPlanHighlight {
  planId: string;
  stopId?: string;
}

export interface MapDriverMarker {
  id: string;
  position: [number, number];
  name?: string;
  vehicle?: string;
  signal?: 'live' | 'stale' | 'reconnecting' | 'waiting';
}
export interface OptimizationStop {
  id: string;
  location: LocationPoint;
  demand_kg?: number;
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
  vehicle_count?: number;
  max_stops_per_vehicle?: number;
}

export interface VehicleRouteResult {
  vehicle_index: number;
  total_distance_km: number;
  total_duration_min: number;
  total_load_kg: number;
  ordered_stops: OptimizationStop[];
}

export interface OptimizationResult {
  execution_time_ms: number;
  total_distance_km: number;
  total_duration_min: number;
  ordered_stops: OptimizationStop[];
  vehicle_routes?: VehicleRouteResult[];
  route_polyline?: string;
}

export type PlanStatus = 'DRAFT' | 'APPROVED' | 'DISPATCHED' | 'COMPLETED';

export interface DispatchPlanRecord {
  plan_id: string;
  version?: number;
  tenant_id: string;
  plan_status: PlanStatus;
  driver_id?: string | null;
  total_distance_km?: number | null;
  estimated_duration_minutes?: number | null;
  trigger_type?: string | null;
  order_count: number;
  ordered_stop_ids: string[];
  dispatched_at?: string | null;
  route_waypoints?: string | null;
  created_at: string;
  updated_at?: string | null;
}

