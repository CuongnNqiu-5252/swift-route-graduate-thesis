import assert from 'node:assert/strict';
import test from 'node:test';
import { forecastAt, forecastPoints, travelSecondsToPoint } from '../src/application/dispatch-weather.ts';
import { GET as weather } from '../src/app/api/maps/weather/route.ts';
import { GET as trafficTile } from '../src/app/api/maps/traffic/[z]/[x]/[y]/route.ts';

test('weather samples a long route in order, including first and last valid stops', () => {
  const points = Array.from({ length: 21 }, (_, index) => ({ lat: 10 + index / 100, lng: 105 }));
  points.splice(2, 0, { ...points[0] }, { lat: 900, lng: 0 });
  const samples = forecastPoints(points);
  assert.equal(samples.length, 10);
  assert.equal(samples[0].index, 0);
  assert.equal(samples.at(-1).index, points.length - 1);
  assert.ok(samples.every((point, index) => index === 0 || point.index > samples[index - 1].index));
});

test('weather uses the hour covering the estimated arrival and handles missing legs', () => {
  const series = [{ timestamp: 3600, probability: 20 }, { timestamp: 7200, probability: 70 }];
  const departure = 3500;
  assert.equal(forecastAt(series, departure).probability, 20);
  assert.equal(forecastAt(series, departure + travelSecondsToPoint([150, 200], 2)).probability, 70);
  assert.equal(travelSecondsToPoint([150, null, 200], 3), null);
  assert.equal(forecastAt(series, 10_800), undefined);
});

test('forecast proxy batches coordinates and omits unknown probabilities', async (t) => {
  const fetch = t.mock.method(globalThis, 'fetch', async (url) => {
    assert.equal(url.searchParams.get('latitude'), '10.03,10.04');
    assert.equal(url.searchParams.get('longitude'), '105.78,105.79');
    assert.equal(url.searchParams.get('timezone'), 'Asia/Ho_Chi_Minh');
    return Response.json([
      { hourly: { time: [3600, 7200], precipitation_probability: [null, 60] } },
      { hourly: { time: [3600], precipitation_probability: [0] } },
    ]);
  });
  const response = await weather(new Request('http://localhost/api/maps/weather?points=10.03,105.78;10.04,105.79'));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    forecasts: [[{ timestamp: 7200, probability: 60 }], [{ timestamp: 3600, probability: 0 }]],
  });
  const invalid = await weather(new Request('http://localhost/api/maps/weather?points=900,105'));
  assert.equal(invalid.status, 400);
  assert.equal(fetch.mock.callCount(), 1);
});

test('traffic proxy keeps the provider key server-side and relays only PNG tiles', async (t) => {
  const previous = process.env.TOMTOM_TRAFFIC_API_KEY;
  t.after(() => {
    if (previous === undefined) delete process.env.TOMTOM_TRAFFIC_API_KEY;
    else process.env.TOMTOM_TRAFFIC_API_KEY = previous;
  });
  const request = new Request('http://localhost/api/maps/traffic/14/13006/7733');
  const tile = (z, x, y) => trafficTile(request, { params: Promise.resolve({ z, x, y }) });

  delete process.env.TOMTOM_TRAFFIC_API_KEY;
  assert.equal((await tile('14', '13006', '7733')).status, 503);
  process.env.TOMTOM_TRAFFIC_API_KEY = 'test-provider-key';
  const fetch = t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url.host, 'api.tomtom.com');
    assert.equal(url.pathname, '/maps/orbis/traffic/flow/raster/tile/14/13006/7733');
    assert.equal(url.searchParams.has('key'), false);
    assert.equal(options.headers['TomTom-Api-Key'], 'test-provider-key');
    return new Response(new Uint8Array([1, 2]), { headers: { 'Content-Type': 'image/png' } });
  });
  assert.equal((await tile('14', '99999', '7733')).status, 400);
  const response = await tile('14', '13006', '7733');
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('content-type'), 'image/png');
  assert.deepEqual([...new Uint8Array(await response.arrayBuffer())], [1, 2]);
  assert.equal(fetch.mock.callCount(), 1);
});
