type Hourly = { time: number[]; precipitation_probability: (number | null)[] };

export async function GET(request: Request) {
  const points = new URL(request.url).searchParams.get('points')?.split(';') ?? [];
  const coordinates = points.map((point) => point.split(',').map(Number));
  if (
    points.length < 1 || points.length > 10 ||
    coordinates.some(([lat, lng, ...rest], index) =>
      !/^-?\d{1,3}(?:\.\d{1,3})?,-?\d{1,3}(?:\.\d{1,3})?$/.test(points[index]) ||
      rest.length || !Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180)
  ) {
    return Response.json({ error: 'Invalid forecast coordinates' }, { status: 400 });
  }

  const key = process.env.OPEN_METEO_API_KEY?.trim();
  const url = new URL('/v1/forecast', key ? 'https://customer-api.open-meteo.com' : 'https://api.open-meteo.com');
  url.search = new URLSearchParams({
    latitude: coordinates.map(([lat]) => lat).join(','),
    longitude: coordinates.map(([, lng]) => lng).join(','),
    hourly: 'precipitation_probability',
    timeformat: 'unixtime',
    forecast_days: '2',
    timezone: 'Asia/Ho_Chi_Minh',
  }).toString();
  if (key) url.searchParams.set('apikey', key);

  try {
    const upstream = await fetch(url, {
      signal: AbortSignal.timeout(8000),
      next: { revalidate: 600 },
    });
    if (!upstream.ok) throw new Error('Forecast unavailable');
    const body = await upstream.json() as { hourly?: Hourly } | { hourly?: Hourly }[];
    const forecasts = (Array.isArray(body) ? body : [body]).map(({ hourly }) =>
      hourly?.time?.flatMap((timestamp, index) => {
        const probability = hourly.precipitation_probability?.[index];
        return Number.isFinite(timestamp) && typeof probability === 'number' && probability >= 0 && probability <= 100
          ? [{ timestamp, probability }]
          : [];
      }) ?? [],
    );
    if (forecasts.length !== points.length || !forecasts.some((forecast) => forecast.length)) {
      throw new Error('Invalid forecast response');
    }
    return Response.json({ forecasts }, { headers: { 'Cache-Control': 'private, max-age=300' } });
  } catch {
    return Response.json({ error: 'Forecast unavailable' }, { status: 502 });
  }
}
