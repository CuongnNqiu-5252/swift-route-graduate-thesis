import { goongErrorResponse, requestGoong } from '@/lib/goong-server';

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const placeId = searchParams.get('placeId')?.trim();
  if (!placeId) return Response.json({ error: 'Thiếu placeId.' }, { status: 400 });

  try {
    const params = new URLSearchParams({ place_id: placeId });
    const sessionToken = searchParams.get('sessiontoken');
    if (sessionToken) params.set('sessiontoken', sessionToken);
    const payload = await requestGoong('/Place/Detail', params);
    const result = (payload.result as Record<string, unknown> | undefined) ?? payload;
    const geometry = result?.geometry as Record<string, unknown> | undefined;
    const location = geometry?.location as Record<string, unknown> | undefined;
    return Response.json({
      address: result?.formatted_address ?? result?.name,
      lat: location?.lat,
      lng: location?.lng,
    });
  } catch (error) {
    return goongErrorResponse(error);
  }
}
