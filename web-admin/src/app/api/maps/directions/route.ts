import { goongErrorResponse, requestGoong } from '@/lib/goong-server';
import { decode } from '@googlemaps/polyline-codec';

type RoutePoint = { lat: number; lng: number };

function isValidPoint(value: unknown): value is RoutePoint {
  if (!value || typeof value !== 'object') return false;
  const point = value as Partial<RoutePoint>;
  return Number.isFinite(point.lat) && Number.isFinite(point.lng)
    && Number(point.lat) >= -90 && Number(point.lat) <= 90
    && Number(point.lng) >= -180 && Number(point.lng) <= 180;
}

export async function POST(request: Request) {
  try {
    const body = await request.json() as { points?: unknown; vehicle?: unknown };
    const points = Array.isArray(body.points) && body.points.every(isValidPoint) ? body.points : [];
    if (points.length < 2 || points.length > 25) {
      return Response.json({ error: 'Tuyến đường cần từ 2 đến 25 tọa độ hợp lệ.' }, { status: 400 });
    }

    const vehicle = body.vehicle === 'car' ? 'car' : 'bike';
    const segments = await Promise.all(points.slice(0, -1).map(async (origin, index) => {
      const destination = points[index + 1];
      const params = new URLSearchParams({
        origin: `${origin.lat},${origin.lng}`,
        destination: `${destination.lat},${destination.lng}`,
        vehicle,
      });
      const payload = await requestGoong('/Direction', params);
      const route = Array.isArray(payload.routes) ? payload.routes[0] as Record<string, unknown> | undefined : undefined;
      const legs = Array.isArray(route?.legs) ? route.legs as Array<Record<string, unknown>> : [];
      const leg = legs[0];
      const polyline = route?.overview_polyline as { points?: unknown } | undefined;
      const encoded = typeof polyline?.points === 'string' ? polyline.points : '';
      const distance = leg?.distance as { value?: unknown } | undefined;
      const duration = leg?.duration as { value?: unknown } | undefined;
      if (!encoded) throw new Error('Goong không trả về hình học tuyến đường.');
      return {
        // The codec returns [lat, lng]; GeoJSON/MapLibre requires [lng, lat].
        coordinates: decode(encoded, 5).map(([lat, lng]) => [lng, lat] as [number, number]),
        distanceMeters: Number(distance?.value) || 0,
        durationSeconds: Number(duration?.value) || 0,
      };
    }));

    const coordinates = segments.flatMap((segment, index) => index === 0
      ? segment.coordinates
      : segment.coordinates.slice(1));
    return Response.json({
      coordinates,
      distance_meters: segments.reduce((sum, segment) => sum + segment.distanceMeters, 0),
      duration_seconds: segments.reduce((sum, segment) => sum + segment.durationSeconds, 0),
      provider: 'GOONG',
    });
  } catch (error) {
    console.error('[Goong directions] Failed to build road route', error);
    return goongErrorResponse(error, 'Không thể tính tuyến đường từ Goong. Vui lòng thử lại sau.');
  }
}
