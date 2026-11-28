import type { Map as MapLibreMap, StyleSpecification } from 'maplibre-gl';

export type MapBasemapId = 'streets' | 'light' | 'satellite' | 'osm';

export interface BasemapOption {
  id: MapBasemapId;
  label: string;
  provider: string;
}

export const BASEMAP_OPTIONS: readonly BasemapOption[] = [
  { id: 'streets', label: 'Đường phố', provider: 'Goong' },
  { id: 'light', label: 'Tối giản', provider: 'Goong' },
  { id: 'satellite', label: 'Vệ tinh', provider: 'Goong' },
  { id: 'osm', label: 'OpenStreetMap', provider: 'OSM' },
];

export const ROUTE_ARROW_IMAGE_ID = 'dispatch-route-direction-arrow';

const OSM_STYLE_SPEC: StyleSpecification = {
  version: 8,
  sources: {
    'osm-raster': {
      type: 'raster',
      tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      tileSize: 256,
      maxzoom: 19,
      attribution: '© OpenStreetMap contributors',
    },
  },
  layers: [
    {
      id: 'osm-raster-layer',
      type: 'raster',
      source: 'osm-raster',
    },
  ],
};

/**
 * Resolves the MapLibre style URL (Goong vector/satellite) or inline raster style (OSM).
 */
export function resolveBasemapStyle(
  basemap: MapBasemapId,
  mapKey: string,
): string | StyleSpecification {
  const encodedKey = encodeURIComponent(mapKey);
  switch (basemap) {
    case 'light':
      return `https://tiles.goong.io/assets/goong_light_v2.json?api_key=${encodedKey}`;
    case 'satellite':
      return `https://tiles.goong.io/assets/goong_satellite.json?api_key=${encodedKey}`;
    case 'osm':
      return OSM_STYLE_SPEC;
    case 'streets':
    default:
      return `https://tiles.goong.io/assets/goong_map_web.json?api_key=${encodedKey}`;
  }
}

/**
 * Draws and registers a crisp canvas chevron pointing along +X so MapLibre
 * `symbol-placement: 'line'` aligns arrows cleanly along route direction.
 */
export function ensureRouteDirectionArrowImage(map: MapLibreMap): void {
  if (typeof document === 'undefined' || map.hasImage(ROUTE_ARROW_IMAGE_ID)) {
    return;
  }
  const size = 32;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return;
  }

  ctx.clearRect(0, 0, size, size);
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';

  const drawChevron = () => {
    ctx.beginPath();
    ctx.moveTo(10, 7);
    ctx.lineTo(23, 16);
    ctx.lineTo(10, 25);
  };

  // Dark casing ensures legibility against light, bright, and satellite tiles.
  drawChevron();
  ctx.strokeStyle = 'rgba(15, 23, 42, 0.78)';
  ctx.lineWidth = 6.5;
  ctx.stroke();

  drawChevron();
  ctx.strokeStyle = '#ffffff';
  ctx.lineWidth = 3.2;
  ctx.stroke();

  const imageData = ctx.getImageData(0, 0, size, size);
  map.addImage(
    ROUTE_ARROW_IMAGE_ID,
    { width: size, height: size, data: imageData.data },
    { pixelRatio: 2 },
  );
}
