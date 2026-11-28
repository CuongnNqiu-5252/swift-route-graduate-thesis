export const dynamic = 'force-dynamic';

export function GET() {
  return Response.json(
    { available: Boolean(process.env.TOMTOM_TRAFFIC_API_KEY?.trim()) },
    { headers: { 'Cache-Control': 'no-store' } },
  );
}
