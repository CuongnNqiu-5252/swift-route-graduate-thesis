import { goongErrorResponse, requestGoong } from '@/lib/goong-server';

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const lat = Number(searchParams.get('lat'));
  const lng = Number(searchParams.get('lng'));
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    return Response.json({ error: 'Tọa độ không hợp lệ.' }, { status: 400 });
  }

  try {
    const payload = await requestGoong('/Geocode', new URLSearchParams({ latlng: `${lat},${lng}` }));
    const results = payload.results as Array<Record<string, unknown>> | undefined;
    const address = results?.[0]?.formatted_address;
    if (!address) return Response.json({ error: 'Không tìm thấy địa chỉ tại vị trí đã chọn.' }, { status: 404 });
    return Response.json({ address, lat, lng });
  } catch (error) {
    return goongErrorResponse(error);
  }
}
