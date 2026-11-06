import { goongErrorResponse, requestGoong } from '@/lib/goong-server';

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const input = searchParams.get('input')?.trim();
  if (!input || input.length < 3) return Response.json({ predictions: [] });

  try {
    const params = new URLSearchParams({ input, limit: '6' });
    const location = searchParams.get('location');
    const sessionToken = searchParams.get('sessiontoken');
    if (location) params.set('location', location);
    if (sessionToken) params.set('sessiontoken', sessionToken);
    const payload = await requestGoong('/Place/AutoComplete', params);
    return Response.json({ predictions: payload.predictions ?? [] });
  } catch (error) {
    return goongErrorResponse(error);
  }
}
