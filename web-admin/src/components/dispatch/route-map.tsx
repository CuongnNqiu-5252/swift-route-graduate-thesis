'use client';

import dynamic from 'next/dynamic';
import { LocationPoint, OptimizationStop } from '@/domain/entities/routing.entity';

interface RouteMapProps {
  depot: LocationPoint;
  stops: OptimizationStop[];
  routePolyline?: string;
}

const DynamicRouteMap = dynamic(
  () => import('./route-map-inner'),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-[480px] w-full items-center justify-center rounded-xl border border-gray-200 bg-gray-50">
        <div className="flex flex-col items-center gap-2">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
          <p className="text-sm text-gray-500">Đang tải bản đồ điều phối...</p>
        </div>
      </div>
    ),
  }
);

export function RouteMap(props: RouteMapProps) {
  return <DynamicRouteMap {...props} />;
}

