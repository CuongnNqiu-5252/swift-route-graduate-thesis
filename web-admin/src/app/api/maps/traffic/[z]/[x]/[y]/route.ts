export async function GET(
  _request: Request,
  { params }: { params: Promise<{ z: string; x: string; y: string }> },
) {
  const { z, x, y } = await params;
  const key = process.env.TOMTOM_TRAFFIC_API_KEY?.trim();
  if (!key) return new Response(null, { status: 503 });

  const zoom = Number(z);
  const tileCount = 2 ** zoom;
  if (![z, x, y].every((value) => /^\d{1,7}$/.test(value)) || zoom < 8 || zoom > 19 ||
      Number(x) >= tileCount || Number(y) >= tileCount) {
    return new Response(null, { status: 400 });
  }

  const url = new URL(`https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/${zoom}/${Number(x)}/${Number(y)}`);
  url.search = 'apiVersion=2&style=light&tileSize=256';
  try {
    const tile = await fetch(url, {
      headers: { 'TomTom-Api-Key': key },
      signal: AbortSignal.timeout(8000),
      next: { revalidate: 60 },
    });
    if (!tile.ok || !tile.headers.get('content-type')?.startsWith('image/png')) {
      return new Response(null, { status: 502 });
    }
    return new Response(tile.body, {
      headers: {
        'Content-Type': 'image/png',
        'Cache-Control': 'private, max-age=60',
      },
    });
  } catch {
    return new Response(null, { status: 502 });
  }
}
