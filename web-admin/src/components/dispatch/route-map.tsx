'use client';

import dynamic from 'next/dynamic';
import { useLocale } from '@/i18n/provider';
import { DispatchDepot, OptimizationStop } from '@/domain/entities/routing.entity';
import type { TrackingSignal } from '@/application/hooks/use-order-live-tracking';

interface RouteMapProps {
  depot: DispatchDepot | null;
  stops: OptimizationStop[];
  driver?: [number, number];
  driverName?: string;
  driverVehicle?: string;
  trackingSignal?: TrackingSignal;
  driverIncident?: { id: string; label: string; count: number };
  routeOrigin?: [number, number];
  skipPickup?: boolean;
  onPickOrigin?: (position: [number, number]) => void;
  onRouteMetricsChange?: (metrics: { distanceMeters: number; durationSeconds: number } | null) => void;
  onRouteCoordinatesChange?: (waypoints: Array<{ lat: number; lng: number }>) => void;
}

const DynamicRouteMap = dynamic(() => import('./route-map-inner'), {
  ssr: false,
  loading: function MapLoading() {
    const { t } = useLocale();
    return (
      <div className="flex h-[480px] w-full items-center justify-center rounded-xl border border-gray-200 bg-gray-50">
        <div className="flex flex-col items-center gap-2">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
          <p className="text-sm text-gray-500">{t("Đang tải bản đồ điều phối...")}</p>
        </div>
      </div>
    );
  },
});

export function RouteMap(props: RouteMapProps) {
  return <DynamicRouteMap {...props} />;
}
