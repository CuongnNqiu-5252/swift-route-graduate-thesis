import type { Feature, FeatureCollection, LineString, Polygon } from 'geojson';

const EARTH_RADIUS_KM = 6371.0;

/**
 * Computes spherical Haversine distance in kilometers between two WGS-84 points.
 */
export function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return EARTH_RADIUS_KM * c;
}

/**
 * Computes a destination point at [distanceKm] and [bearingDeg] (0 = North, 90 = East).
 */
export function destinationPoint(
  lat: number,
  lng: number,
  distanceKm: number,
  bearingDeg: number,
): [number, number] {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const toDeg = (rad: number) => (rad * 180) / Math.PI;

  const angularDist = distanceKm / EARTH_RADIUS_KM;
  const latRad = toRad(lat);
  const lngRad = toRad(lng);
  const bearingRad = toRad(bearingDeg);

  const nextLatRad = Math.asin(
    Math.sin(latRad) * Math.cos(angularDist) +
      Math.cos(latRad) * Math.sin(angularDist) * Math.cos(bearingRad),
  );
  const nextLngRad =
    lngRad +
    Math.atan2(
      Math.sin(bearingRad) * Math.sin(angularDist) * Math.cos(latRad),
      Math.cos(angularDist) - Math.sin(latRad) * Math.sin(nextLatRad),
    );

  return [toDeg(nextLngRad), toDeg(nextLatRad)];
}

/**
 * Generates a 64-vertex GeoJSON Polygon approximating a circle on WGS-84.
 */
export function createCircleFeatureCollection(
  centerLat: number,
  centerLng: number,
  radiusKm: number,
  steps = 64,
): FeatureCollection<Polygon> {
  const ring: Array<[number, number]> = [];
  for (let i = 0; i <= steps; i += 1) {
    const bearing = (i / steps) * 360;
    ring.push(destinationPoint(centerLat, centerLng, radiusKm, bearing));
  }
  const feature: Feature<Polygon> = {
    type: 'Feature',
    properties: { radius_km: radiusKm },
    geometry: {
      type: 'Polygon',
      coordinates: [ring],
    },
  };
  return {
    type: 'FeatureCollection',
    features: [feature],
  };
}

/**
 * Generates a GeoJSON line segment connecting the center marker to the East resize handle.
 */
export function createRadiusLineCollection(
  centerLat: number,
  centerLng: number,
  radiusKm: number,
): FeatureCollection<LineString> {
  const edge = destinationPoint(centerLat, centerLng, radiusKm, 90);
  const feature: Feature<LineString> = {
    type: 'Feature',
    properties: {},
    geometry: {
      type: 'LineString',
      coordinates: [[centerLng, centerLat], edge],
    },
  };
  return {
    type: 'FeatureCollection',
    features: [feature],
  };
}

/**
 * Returns south-west and north-east corner pairs enclosing the zone circle.
 */
export function getCircleBounds(
  centerLat: number,
  centerLng: number,
  radiusKm: number,
): [[number, number], [number, number]] {
  const [, north] = destinationPoint(centerLat, centerLng, radiusKm, 0);
  const [east] = destinationPoint(centerLat, centerLng, radiusKm, 90);
  const [, south] = destinationPoint(centerLat, centerLng, radiusKm, 180);
  const [west] = destinationPoint(centerLat, centerLng, radiusKm, 270);
  return [
    [west, south],
    [east, north],
  ];
}
