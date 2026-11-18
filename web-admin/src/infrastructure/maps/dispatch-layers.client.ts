import { mapsApi } from '@/lib/api';

export type RainForecast = { timestamp: number; probability: number };

export const dispatchLayersClient = {
  async getWeather(points: string, signal: AbortSignal) {
    const { data } = await mapsApi.get<{ forecasts: RainForecast[][] }>('/api/maps/weather', {
      params: { points },
      signal,
    });
    return data.forecasts;
  },

  async hasTraffic(signal: AbortSignal) {
    const { data } = await mapsApi.get<{ available: boolean }>('/api/maps/traffic', { signal });
    return data.available;
  },

  trafficTiles() {
    const version = Math.floor(Date.now() / 120_000);
    return `${window.location.origin}/api/maps/traffic/{z}/{x}/{y}?v=${version}`;
  },
};
