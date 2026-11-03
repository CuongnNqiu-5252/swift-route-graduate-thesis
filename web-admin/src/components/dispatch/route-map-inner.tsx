'use client';

import { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { LocationPoint, OptimizationStop } from '@/domain/entities/routing.entity';

interface RouteMapInnerProps {
  depot: LocationPoint;
  stops: OptimizationStop[];
  routePolyline?: string;
}

// Auto-fit bounds when stops change
function MapBoundsUpdater({ points }: { points: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length > 0) {
      const bounds = L.latLngBounds(points);
      map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
    }
  }, [points, map]);
  return null;
}

// Custom DivIcon for Depot
const createDepotIcon = () =>
  L.divIcon({
    className: 'custom-depot-marker',
    html: `
      <div style="background-color: #2563eb; color: white; width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; border: 3px solid white; box-shadow: 0 4px 6px rgba(0,0,0,0.3);">
        🏢
      </div>
    `,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });

// Custom DivIcon for numbered Delivery Stops
const createStopIcon = (index: number) =>
  L.divIcon({
    className: 'custom-stop-marker',
    html: `
      <div style="background-color: #ef4444; color: white; width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 13px; border: 2px solid white; box-shadow: 0 3px 5px rgba(0,0,0,0.3);">
        ${index}
      </div>
    `,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });

export default function RouteMapInner({ depot, stops }: RouteMapInnerProps) {
  const allPoints: [number, number][] = [
    [depot.lat, depot.lng],
    ...stops.map((s) => [s.location.lat, s.location.lng] as [number, number]),
  ];

  return (
    <div className="relative h-full w-full overflow-hidden rounded-xl border border-gray-200 z-0 isolate">
      <MapContainer
        center={[depot.lat, depot.lng]}
        zoom={13}
        scrollWheelZoom={true}
        style={{ height: '100%', width: '100%', minHeight: '480px' }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        <MapBoundsUpdater points={allPoints} />

        {/* Depot Marker */}
        <Marker position={[depot.lat, depot.lng]} icon={createDepotIcon()}>
          <Popup>
            <div className="p-1">
              <p className="font-bold text-blue-700">🏢 SwiftRoute Hub (Kho xuất phát)</p>
              <p className="text-xs text-gray-600">Điểm khởi hành cho tài xế</p>
              <p className="text-[11px] text-gray-400">
                {depot.lat.toFixed(4)}, {depot.lng.toFixed(4)}
              </p>
            </div>
          </Popup>
        </Marker>

        {/* Delivery Stops Markers */}
        {stops.map((stop, idx) => (
          <Marker
            key={stop.id || idx}
            position={[stop.location.lat, stop.location.lng]}
            icon={createStopIcon(idx + 1)}
          >
            <Popup>
              <div className="p-1">
                <p className="font-bold text-red-600">Điểm dừng #{idx + 1}</p>
                {stop.order?.order_code && (
                  <p className="text-xs font-semibold text-gray-800">
                    Đơn hàng: {stop.order.order_code}
                  </p>
                )}
                {stop.order?.delivery_address && (
                  <p className="text-xs text-gray-600">{stop.order.delivery_address}</p>
                )}
                <p className="text-[11px] text-gray-400">
                  {stop.location.lat.toFixed(4)}, {stop.location.lng.toFixed(4)}
                </p>
              </div>
            </Popup>
          </Marker>
        ))}

        {/* Polyline connecting Depot and Stops in optimized order */}
        {allPoints.length > 1 && (
          <Polyline
            positions={allPoints}
            pathOptions={{
              color: '#2563eb',
              weight: 4,
              opacity: 0.8,
              dashArray: '8, 8',
            }}
          />
        )}
      </MapContainer>
    </div>
  );
}

