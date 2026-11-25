'use client';

import type { TrackingSignal } from '@/application/hooks/use-order-live-tracking';
import {
  DispatchDepot,
  MapDriverMarker,
  MapOverviewRoute,
  MapPlanHighlight,
} from '@/domain/entities/routing.entity';
import { useLocale } from '@/i18n/provider';
import dynamic from 'next/dynamic';

interface RouteMapProps {
  depot: DispatchDepot | null;
  overviewRoutes?: MapOverviewRoute[];
  selectedOverviewRouteId?: string | null;
  highlightedPlan?: MapPlanHighlight | null;
  filterKey?: string;
  showDeliveryStops?: boolean;
  showPlanDetails?: boolean;
  isPlansOpen?: boolean;
  onSelectOverviewRoute?: (planId: string) => void;
  onHoverOverviewPlan?: (highlight: MapPlanHighlight | null) => void;
  fleetDrivers?: MapDriverMarker[];
  fleetOrigin?: MapDriverMarker;
  driver?: [number, number];
  driverName?: string;
  driverVehicle?: string;
  trackingSignal?: TrackingSignal;
}

const DynamicRouteMap = dynamic(() => import('./route-map-inner'), {
  ssr: false,
  loading: function MapLoading() {
    const { t } = useLocale();
    return (
      <div className="flex h-full w-full items-center justify-center bg-slate-50">
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
