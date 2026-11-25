import api from '@/lib/api';

export interface WeatherAffectedStop {
  order_id: string;
  order_code: string;
  eta: string;
  rain_probability: number;
  buffer_minutes: number | null;
  urgent: boolean;
  forecast_fetched_at: string;
}
export interface RouteWeatherRisk {
  plan_id: string;
  stops: WeatherAffectedStop[];
  urgent_stops: number;
}
export interface RouteWeatherReport {
  source: string;
  as_of: string;
  period: string;
  from: string;
  to: string;
  rain_threshold: number;
  tight_buffer_minutes: number;
  evaluated_stops: number;
  unknown_stops: number;
  outside_period_stops: number;
  routes: RouteWeatherRisk[];
}

export const routeWeatherRepository = {
  async get(signal: AbortSignal, tenantId: string) {
    const { data } = await api.get<RouteWeatherReport>('/orders/dispatch/weather-risks', {
      signal, headers: { 'X-Tenant-Id': tenantId },
    });
    return data;
  },
};
