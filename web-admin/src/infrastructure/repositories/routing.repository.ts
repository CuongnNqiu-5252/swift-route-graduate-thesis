import axios from 'axios';
import {
  OptimizationRequest,
  OptimizationResult,
  OptimizationStop,
  VehicleRouteResult,
  WhatIfRequest,
  WhatIfResult,
} from '@/domain/entities/routing.entity';

// Routing engine is called directly (not through gateway) for performance
function routingHeaders() {
  return { Authorization: `Bearer ${localStorage.getItem('access_token') || ''}`, 'X-Tenant-Id': localStorage.getItem('tenant_id') || '' };
}

const ROUTING_BASE =
  process.env.NEXT_PUBLIC_ROUTING_URL || 'http://localhost:8084';

interface RawOrderedStop {
  stop_id?: string;
  id?: string;
  stop_index?: number;
  location?: { lat: number; lng: number };
}

interface RawVehicleRoute {
  vehicle_index?: number;
  total_distance_km?: number;
  total_duration_minutes?: number;
  total_load_kg?: number;
  ordered_stops?: RawOrderedStop[];
}

interface RawRoutingResponse {
  job_id?: string;
  algorithm_used?: string;
  execution_time_ms?: number;
  total_distance_km?: number;
  total_duration_minutes?: number;
  total_duration_min?: number;
  route_polyline?: string;
  ordered_stops?: RawOrderedStop[];
  vehicle_routes?: RawVehicleRoute[];
}

export const routingRepository = {
  async optimize(request: OptimizationRequest): Promise<OptimizationResult> {
    const response = await axios.post<RawRoutingResponse>(
      `${ROUTING_BASE}/api/v1/optimize/batch`,
      request, { headers: routingHeaders() }
    );
    const data = response.data;

    // Build lookup for original stops
    const stopsMap = new Map<string, OptimizationStop>();
    request.stops.forEach((s) => stopsMap.set(s.id, s));

    const mapStopItem = (s: RawOrderedStop): OptimizationStop => {
      const stopId = s.stop_id || s.id || '';
      const original = stopsMap.get(stopId);
      return {
        id: stopId,
        location: s.location || original?.location || { lat: 0, lng: 0 },
        demand_kg: original?.demand_kg,
        order: original?.order,
      };
    };

    const mappedStops: OptimizationStop[] = (data.ordered_stops || []).map(mapStopItem);

    const mappedVehicleRoutes: VehicleRouteResult[] = (data.vehicle_routes || []).map(
      (vr, idx) => ({
        vehicle_index: vr.vehicle_index ?? idx,
        total_distance_km: vr.total_distance_km ?? 0,
        total_duration_min: vr.total_duration_minutes ?? 0,
        total_load_kg: vr.total_load_kg ?? 0,
        ordered_stops: (vr.ordered_stops || []).map(mapStopItem),
      })
    );

    return {
      algorithm_used: data.algorithm_used || 'ortools',
      execution_time_ms: data.execution_time_ms ?? 0.1,
      total_distance_km: data.total_distance_km ?? 0,
      total_duration_min: data.total_duration_minutes ?? data.total_duration_min ?? 0,
      ordered_stops: mappedStops,
      vehicle_routes: mappedVehicleRoutes.length > 0
        ? mappedVehicleRoutes
        : mappedStops.length > 0
          ? [{
              vehicle_index: 0,
              total_distance_km: data.total_distance_km ?? 0,
              total_duration_min: data.total_duration_minutes ?? data.total_duration_min ?? 0,
              total_load_kg: mappedStops.reduce((sum, s) => sum + (s.demand_kg ?? 0), 0),
              ordered_stops: mappedStops,
            }]
          : [],
      route_polyline: data.route_polyline,
    };
  },

  async whatIf(request: WhatIfRequest): Promise<WhatIfResult> {
    const response = await axios.post<WhatIfResult>(
      `${ROUTING_BASE}/api/v1/optimize/what-if`,
      request, { headers: routingHeaders() }
    );
    return response.data;
  },
};
