import axios from 'axios';
import {
  OptimizationRequest,
  OptimizationResult,
  OptimizationStop,
  WhatIfRequest,
  WhatIfResult,
} from '@/domain/entities/routing.entity';

// Routing engine is called directly (not through gateway) for performance
function routingHeaders() {
  return { Authorization: `Bearer ${localStorage.getItem('access_token') || ''}`, 'X-Tenant-Id': localStorage.getItem('tenant_id') || '' };
}

const ROUTING_BASE =
  process.env.NEXT_PUBLIC_ROUTING_URL || 'http://localhost:8084';

interface RawRoutingResponse {
  job_id?: string;
  algorithm_used?: string;
  execution_time_ms?: number;
  total_distance_km?: number;
  total_duration_minutes?: number;
  total_duration_min?: number;
  route_polyline?: string;
  ordered_stops?: Array<{
    stop_id?: string;
    id?: string;
    stop_index?: number;
    location?: { lat: number; lng: number };
  }>;
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

    const mappedStops: OptimizationStop[] = (data.ordered_stops || []).map(
      (s) => {
        const stopId = s.stop_id || s.id || '';
        const original = stopsMap.get(stopId);
        return {
          id: stopId,
          location: s.location || original?.location || { lat: 0, lng: 0 },
          order: original?.order,
        };
      }
    );

    return {
      algorithm_used: data.algorithm_used || 'ortools',
      execution_time_ms: data.execution_time_ms ?? 0.1,
      total_distance_km: data.total_distance_km ?? 0,
      total_duration_min: data.total_duration_minutes ?? data.total_duration_min ?? 0,
      ordered_stops: mappedStops,
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
