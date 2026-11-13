import axios from 'axios';

export interface MapPlacePrediction {
  place_id: string;
  description: string;
  structured_formatting?: {
    main_text?: string;
    secondary_text?: string;
  };
}

export interface MapPlaceDetail {
  address: string;
  lat: number;
  lng: number;
}

/**
 * Axios client for Next.js local Goong Maps proxy routes (/api/maps/*).
 */
export const goongMapsClient = {
  async reverseGeocode(lat: number, lng: number, signal?: AbortSignal): Promise<string | null> {
    const response = await axios.get<{ address?: string }>('/api/maps/reverse', {
      params: { lat, lng },
      signal,
    });
    return response.data?.address ?? null;
  },

  async searchPlaces(
    input: string,
    center: { lat: number; lng: number } | undefined,
    sessionToken: string,
    signal?: AbortSignal,
  ): Promise<MapPlacePrediction[]> {
    const response = await axios.get<{ predictions?: MapPlacePrediction[] }>('/api/maps/search', {
      params: {
        input: input.trim(),
        ...(center ? { location: `${center.lat},${center.lng}` } : {}),
        sessiontoken: sessionToken,
      },
      signal,
    });
    return response.data?.predictions ?? [];
  },

  async getPlaceDetail(placeId: string, sessionToken: string): Promise<MapPlaceDetail | null> {
    const response = await axios.get<Partial<MapPlaceDetail>>('/api/maps/place-detail', {
      params: { placeId, sessiontoken: sessionToken },
    });
    if (typeof response.data?.lat !== 'number' || typeof response.data?.lng !== 'number') {
      return null;
    }
    return {
      address: response.data.address ?? '',
      lat: response.data.lat,
      lng: response.data.lng,
    };
  },
};
