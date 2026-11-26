'use client';

import { useAuth } from '@/application/contexts/auth-context';
import {
  EMPTY_DISPATCH_MAP_FILTERS, filterDispatchMapRoutes, UNASSIGNED_DRIVER, UNKNOWN_WAREHOUSE,
  type DispatchMapFilters,
} from '@/application/dispatch-map-filters';
import { reportDispatchPlanning } from '@/application/dispatch-planning';
import { useDrivers } from '@/application/hooks/use-drivers';
import { useFleetLiveTracking } from '@/application/hooks/use-fleet-live-tracking';
import { useOrders } from '@/application/hooks/use-orders';
import { usePlanLiveTracking } from '@/application/hooks/use-plan-live-tracking';
import { AccessDeniedNotice } from '@/components/auth/permission-guard';
import { DispatchStopTimeline } from '@/components/dispatch/dispatch-stop-timeline';
import { PlanApprovalPanel } from '@/components/dispatch/plan-approval-panel';
import { RouteMap } from '@/components/dispatch/route-map';
import { RouteMapFilters } from '@/components/dispatch/route-map-filters';
import { OrdersWorkspace } from '@/components/orders/orders-workspace';
import { Button } from '@/components/ui/button';
import { Order } from '@/domain/entities/order.entity';
import { PickupLocation } from '@/domain/entities/pickup-location.entity';
import {
  DispatchDepot,
  DispatchPlanRecord,
  MapDriverMarker,
  MapOverviewRoute,
  MapPlanHighlight,
  OptimizationStop,
} from '@/domain/entities/routing.entity';
import { useLocale } from '@/i18n/provider';
import { operationFlowRepository } from '@/infrastructure/repositories/operation-flow.repository';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import { tenantSettingsRepository } from '@/infrastructure/repositories/tenant-settings.repository';
import { ApiError } from '@/lib/api';
import * as Tabs from '@radix-ui/react-tabs';
import { ChevronDown, ClipboardList, Play, RefreshCw, X } from 'lucide-react';
import { useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';

const ROUTE_COLORS = ['#2563eb', '#059669', '#d97706', '#7c3aed', '#db2777', '#0891b2'];
const tabClass = 'inline-flex min-h-10 shrink-0 cursor-pointer items-center gap-2 whitespace-nowrap border-b-2 border-transparent px-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50 hover:text-slate-950 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-teal-700 data-[state=active]:border-teal-700 data-[state=active]:text-slate-950 sm:px-3.5';

function setDispatchTab(tab: string) {
  const url = new URL(window.location.href);
  const next = tab === 'orders' ? tab : null;
  if (url.searchParams.get('tab') === next) return;
  if (next) url.searchParams.set('tab', next);
  else url.searchParams.delete('tab');
  window.history.replaceState(null, '', url);
}

export default function DispatchPage() {
  const { hasPermission, user } = useAuth();
  const canReadOrders = hasPermission('order:read');
  const canViewRoutes = hasPermission(['dispatch:read', 'dispatch:execute']);

  return (
    <Suspense fallback={<div className="min-h-40 flex-1 animate-pulse bg-slate-100" />}>
      {canViewRoutes ? (
        <DispatchWorkspace
          canReadOrders={canReadOrders}
          canExecute={user?.role !== 'DRIVER' && hasPermission('dispatch:execute')}
        />
      ) : canReadOrders ? (
        <div className="p-3 sm:p-4">
          <OrdersWorkspace />
        </div>
      ) : <AccessDeniedNotice requiredPermission="dispatch:read" />}
    </Suspense>
  );
}

function DispatchWorkspace({ canReadOrders, canExecute }: { canReadOrders: boolean; canExecute: boolean }) {
  const { locale, t } = useLocale();
  const searchParams = useSearchParams();
  const activeTab = canReadOrders && searchParams.get('tab') === 'orders' ? 'orders' : 'routes';
  const { orders, isLoading: isLoadingOrders, refetch: fetchOrdersForMap } = useOrders();
  const { drivers } = useDrivers();
  const fleetTracking = useFleetLiveTracking();
  const [pickupLocations, setPickupLocations] = useState<PickupLocation[]>([]);
  const [fetchedPlans, setPlans] = useState<DispatchPlanRecord[]>([]);
  const [isLoadingPlans, setIsLoadingPlans] = useState(true);
  const [isTriggeringPlan, setIsTriggeringPlan] = useState(false);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [editingPlanId, setEditingPlanId] = useState<string | null>(null);
  const [isPlanningPanelOpen, setIsPlanningPanelOpen] = useState(false);
  const [selectedOrderIds, setSelectedOrderIds] = useState<Set<string>>(() => new Set());
  const [orderPageRevision, setOrderPageRevision] = useState(0);
  const isPlansOpen = isPlanningPanelOpen || activeTab === 'orders';
  const [highlightedPlan, setHighlightedPlan] = useState<MapPlanHighlight | null>(null);
  const [mapFilters, setMapFilters] = useState(EMPTY_DISPATCH_MAP_FILTERS);
  const [showDeliveryStops, setShowDeliveryStops] = useState(true);
  const [linkedPlanId, setLinkedPlanId] = useState<string | null>(null);
  const [maxOrdersPerDriver, setMaxOrdersPerDriver] = useState<number | undefined>(undefined);
  const [isPlanningMenuOpen, setIsPlanningMenuOpen] = useState(false);
  const planningMenuRef = useRef<HTMLDivElement>(null);

  const plansButtonRef = useRef<HTMLButtonElement | null>(null);
  const planningInProgress = useRef(false);

  const refetchOrders = useCallback(async () => {
    await fetchOrdersForMap();
    setOrderPageRevision((revision) => revision + 1);
  }, [fetchOrdersForMap]);
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (planningMenuRef.current && !planningMenuRef.current.contains(event.target as Node)) {
        setIsPlanningMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    }
  }, []);
  const plans = useMemo(() => {
    const ordersById = new Map(orders.map((order) => [order.id, order]));
    return fetchedPlans
      .map((plan) => {
        if (plan.plan_status !== 'DISPATCHED' || !plan.ordered_stop_ids?.length) return plan;
        const stops = plan.ordered_stop_ids.map((id) => ordersById.get(id));
        const hasFinishedReturn = stops.some((order) => order?.status === 'RETURNED');
        const allFinished = stops.every(
          (order) => order && ['DELIVERED', 'CANCELLED', 'RETURNED'].includes(order.status),
        );
        return hasFinishedReturn && allFinished ? { ...plan, plan_status: 'COMPLETED' as const } : plan;
      })
      .filter((plan) =>
        plan.plan_status === 'DRAFT'
        || plan.plan_status === 'APPROVED'
        || plan.plan_status === 'DISPATCHED',
      );
  }, [fetchedPlans, orders]);

  const selectedPlan = plans.find((plan) => plan.plan_id === selectedPlanId) ?? null;

  useEffect(() => {
    window.dispatchEvent(new CustomEvent('swiftroute:assistant-plan', { detail: { planId: selectedPlanId ?? undefined } }));
    return () => {
      window.dispatchEvent(new CustomEvent('swiftroute:assistant-plan', { detail: {} }));
    };
  }, [selectedPlanId]);

  const selectedDriverId = selectedPlan?.driver_id ?? null;
  const isSelectedPlanDispatched = selectedPlan?.plan_status === 'DISPATCHED';

  const selectedDriver = useMemo(
    () =>
      selectedDriverId
        ? drivers.find(
          (driver) => driver.id === selectedDriverId || driver.user_id === selectedDriverId,
        )
        : undefined,
    [drivers, selectedDriverId],
  );

  const planTracking = usePlanLiveTracking(
    isSelectedPlanDispatched ? selectedPlan.plan_id : undefined,
    selectedDriverId,
  );

  const selectedDriverCachedPosition = useMemo<[number, number] | undefined>(() => {
    if (!selectedDriver || !isSelectedPlanDispatched) return undefined;
    const { current_lat: lat, current_lng: lng } = selectedDriver;
    if (
      typeof lat === 'number' &&
      typeof lng === 'number' &&
      Number.isFinite(lat) &&
      Number.isFinite(lng) &&
      (lat !== 0 || lng !== 0)
    ) {
      return [lat, lng];
    }
    return undefined;
  }, [isSelectedPlanDispatched, selectedDriver]);

  const activeDriverPosition = isSelectedPlanDispatched
    ? (planTracking.position ?? selectedDriverCachedPosition)
    : undefined;

  const fleetDrivers = useMemo<MapDriverMarker[]>(() => {
    const excludedIds = new Set<string>();
    if (activeDriverPosition) {
      if (selectedDriverId) excludedIds.add(selectedDriverId);
      if (selectedDriver?.id) excludedIds.add(selectedDriver.id);
      if (selectedDriver?.user_id) excludedIds.add(selectedDriver.user_id);
    }

    const byDriverId = new Map<string, MapDriverMarker>();
    for (const [streamDriverId, live] of Object.entries(fleetTracking.positions)) {
      if (excludedIds.has(streamDriverId)) continue;
      const matched = drivers.find(
        (driver) => driver.user_id === streamDriverId || driver.id === streamDriverId,
      );
      if (matched && (excludedIds.has(matched.id) || excludedIds.has(matched.user_id))) continue;
      const markerKey = matched?.user_id || matched?.id || streamDriverId;
      byDriverId.set(markerKey, {
        id: markerKey,
        position: live.position,
        name: matched?.name || t('Tài xế'),
        vehicle: matched?.vehicle_plate || matched?.vehicle_type,
        signal: live.signal,
      });
    }

    for (const driver of drivers) {
      const markerKey = driver.user_id || driver.id;
      if (!markerKey || excludedIds.has(markerKey) || excludedIds.has(driver.id) || byDriverId.has(markerKey)) {
        continue;
      }
      if (driver.onboarding_status !== 'ACTIVE') continue;
      const lat = driver.current_lat;
      const lng = driver.current_lng;
      if (
        typeof lat === 'number' &&
        typeof lng === 'number' &&
        Number.isFinite(lat) &&
        Number.isFinite(lng) &&
        (lat !== 0 || lng !== 0)
      ) {
        byDriverId.set(markerKey, {
          id: markerKey,
          position: [lat, lng],
          name: driver.name || t('Tài xế'),
          vehicle: driver.vehicle_plate || driver.vehicle_type,
          signal: 'stale',
        });
      }
    }

    return Array.from(byDriverId.values());
  }, [activeDriverPosition, drivers, fleetTracking.positions, selectedDriver, selectedDriverId, t]);

  const loadPlans = useCallback(async () => {
    tenantSettingsRepository
      .getSettings()
      .then((s) => setMaxOrdersPerDriver(s.dispatch_config.max_orders_per_driver))
      .catch(() => { });
    try {
      const list = await orderRepository.getDispatchPlans();
      setPlans(list);
      return list;
    } catch {
      // Keep existing state when endpoint is unreachable
    } finally {
      setIsLoadingPlans(false);
    }
  }, []);

  useEffect(() => {
    function openPlan(event: Event) {
      const { planId } = (event as CustomEvent<{ planId: string }>).detail;
      setMapFilters(EMPTY_DISPATCH_MAP_FILTERS);
      setLinkedPlanId(planId);
      setSelectedPlanId(planId);
      setDispatchTab('routes');
      setIsPlanningPanelOpen(true);
      if (!plans.some((plan) => plan.plan_id === planId)) {
        void Promise.all([loadPlans(), refetchOrders()]);
      }
    }
    window.addEventListener('swiftroute:assistant-open-plan', openPlan);
    return () => window.removeEventListener('swiftroute:assistant-open-plan', openPlan);
  }, [loadPlans, plans, refetchOrders]);

  useEffect(() => {
    let active = true;
    tenantSettingsRepository
      .getSettings()
      .then((s) => {
        if (active) setMaxOrdersPerDriver(s.dispatch_config.max_orders_per_driver);
      })
      .catch(() => { });
    pickupLocationRepository
      .getPickupLocations('ACTIVE')
      .then((locations) => {
        if (active) setPickupLocations(locations);
      })
      .catch(() => {
        if (active) setPickupLocations([]);
      });
    orderRepository
      .getDispatchPlans()
      .then((list) => {
        if (!active) return;
        setPlans(list);
        const requested = new URLSearchParams(window.location.search).get('plan');
        if (requested && list.some((plan) => plan.plan_id === requested)) {
          setLinkedPlanId(requested);
          if (new URLSearchParams(window.location.search).get('tab') !== 'orders') {
            setIsPlanningPanelOpen(true);
          }
          setSelectedPlanId((current) => current ?? requested);
        }
      })
      .catch(() => { })
      .finally(() => {
        if (active) setIsLoadingPlans(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!linkedPlanId || !isPlansOpen) return;
    const frame = requestAnimationFrame(() => {
      document.getElementById(`dispatch-plan-${linkedPlanId}`)?.scrollIntoView({ block: 'nearest' });
    });
    return () => cancelAnimationFrame(frame);
  }, [linkedPlanId, isPlansOpen, selectedPlan]);

  useEffect(() => {
    const refresh = () => {
      void loadPlans();
    };
    window.addEventListener('swiftroute:realtime-update', refresh);
    return () => {
      window.removeEventListener('swiftroute:realtime-update', refresh);
    };
  }, [loadPlans]);

  const fallbackDepot = useMemo<DispatchDepot | null>(() => {
    const location = pickupLocations.find((item) => item.is_default) || pickupLocations[0];
    return location
      ? {
        lat: location.lat,
        lng: location.lng,
        name: location.name,
        address: location.address,
        pickup_location_id: location.id,
      }
      : null;
  }, [pickupLocations]);

  // Eligible orders for inserting into an active plan.
  const dispatchableOrders = orders.filter((order) => order.status === 'READY_FOR_DISPATCH');

  const handleTriggerPlanning = async (scope: 'SELECTED' | 'ALL') => {
    if (planningInProgress.current || (scope === 'SELECTED' && selectedOrderIds.size === 0)) return;
    setIsPlanningMenuOpen(false);
    planningInProgress.current = true;
    const orderIds = Array.from(selectedOrderIds);
    setHighlightedPlan(null);
    setIsTriggeringPlan(true);
    try {
      const result = scope === 'SELECTED'
        ? await orderRepository.triggerDispatchPlanning({ scope, orderIds })
        : await operationFlowRepository.runPlanning(null);
      if ('unplanned_order_ids' in result && result.unplanned_order_ids) {
        setSelectedOrderIds(new Set(result.unplanned_order_ids));
      }
      const [refreshedPlans] = await Promise.all([loadPlans(), refetchOrders()]);
      if (scope === 'ALL' && refreshedPlans && result.plan_ids.length > 0 && orderIds.length > 0) {
        const newPlanIds = new Set(result.plan_ids);
        const plannedOrderIds = new Set(refreshedPlans
          .filter((plan) => newPlanIds.has(plan.plan_id))
          .flatMap((plan) => plan.ordered_stop_ids ?? []));
        setSelectedOrderIds((current) => new Set([...current].filter((id) => !plannedOrderIds.has(id))));
      }
      const planId = reportDispatchPlanning(result, t);
      if (planId) {
        selectPlanFromPanel(planId);
        setLinkedPlanId(planId);
        setIsPlanningPanelOpen(true);
        setDispatchTab('routes');
      }
    } catch {
      toast.error(t('Không thể chạy lập kế hoạch. Vui lòng thử lại.'));
    } finally {
      planningInProgress.current = false;
      setIsTriggeringPlan(false);
    }
  };

  const handleReorderStops = async (plan: DispatchPlanRecord, orderedStopIds: string[]) => {
    if (typeof plan.version !== 'number') throw new Error(t('Hãy tải lại tuyến trước khi chỉnh sửa.'));
    setEditingPlanId(plan.plan_id);
    try {
      const updated = await orderRepository.reorderDraftStops(plan.plan_id, plan.version, orderedStopIds);
      setPlans((current) => current.map((item) => item.plan_id === updated.plan_id
        && (item.version ?? 0) <= (updated.version ?? 0) ? updated : item));
      return updated;
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        await loadPlans();
        throw new Error(t('Không thể cập nhật điểm giao. Hãy thử tải lại kế hoạch.'));
      }
      if (error instanceof ApiError && (error.status === 409 || error.status === 400)) {
        await loadPlans();
        throw new Error(t('Tuyến đã thay đổi. Hãy kiểm tra lại trước khi di chuyển điểm.'));
      }
      throw error;
    } finally {
      setEditingPlanId(null);
    }
  };

  const handleMoveStop = async (
    sourcePlan: DispatchPlanRecord,
    orderId: string,
    targetPlanId: string,
    targetIndex: number,
  ) => {
    const targetPlan = plans.find((item) => item.plan_id === targetPlanId);
    if (!targetPlan || typeof sourcePlan.version !== 'number' || typeof targetPlan.version !== 'number') {
      throw new Error(t('Hãy tải lại tuyến trước khi chỉnh sửa.'));
    }
    const targetStopCount = targetPlan.ordered_stop_ids?.length ?? targetPlan.order_count;
    if (typeof maxOrdersPerDriver === 'number' && targetStopCount >= maxOrdersPerDriver) {
      const msg = t('Tuyến đích đã đạt số đơn gộp tối đa mỗi chuyến.');
      toast.error(msg);
      throw new Error(msg);
    }
    setEditingPlanId(sourcePlan.plan_id);
    try {
      const result = await orderRepository.moveDraftStop({ sourcePlan, targetPlan, orderId, targetIndex });
      setPlans((current) => current.flatMap((item) => {
        if (item.plan_id === sourcePlan.plan_id) {
          return result.source_plan ? [result.source_plan] : [];
        }
        if (item.plan_id === result.target_plan.plan_id) return [result.target_plan];
        return [item];
      }));
      if (selectedPlanId === sourcePlan.plan_id && !result.source_plan) {
        setSelectedPlanId(result.target_plan.plan_id);
      }
      await refetchOrders();
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        await loadPlans();
        throw new Error(t('Không thể cập nhật điểm giao. Hãy thử tải lại kế hoạch.'));
      }
      if (error instanceof ApiError && (error.status === 409 || error.status === 400)) {
        await loadPlans();
        throw new Error(t('Tuyến đã thay đổi. Hãy kiểm tra lại trước khi di chuyển điểm.'));
      }
      if (error instanceof ApiError && error.status === 422) {
        await loadPlans();
        const msg = t('Tuyến đích đã đạt số đơn gộp tối đa mỗi chuyến.');
        toast.error(msg);
        throw new Error(msg);
      }
      throw error;
    } finally {
      setEditingPlanId(null);
    }
  };

  const handleApprovePlan = async (planId: string, driverId: string) => {
    try {
      await orderRepository.approveDispatchPlan(planId, driverId);
      toast.success(t('Đã gán tài xế'));
      await loadPlans();
      return true;
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể phê duyệt kế hoạch');
      return false;
    }
  };

  const handleDispatchPlan = async (planId: string) => {
    try {
      const updated = await orderRepository.dispatchApprovedPlan(planId);
      setSelectedPlanId(updated.plan_id);
      toast.success('Đã giao lộ trình cho tài xế');
      await Promise.all([loadPlans(), refetchOrders()]);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể giao lộ trình');
    }
  };

  const handleCancelPlan = async (planId: string, reason: string) => {
    try {
      await orderRepository.cancelDispatchPlan(planId, reason);
      toast.success('Đã hủy kế hoạch và hoàn trả đơn về hàng đợi');
      if (selectedPlan?.plan_id === planId) {
        setSelectedPlanId(null);
      }
      await Promise.all([loadPlans(), refetchOrders()]);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể hủy kế hoạch');
    }
  };

  const handleInsertOrderIntoPlan = async (planId: string, orderId: string) => {
    try {
      const updated = await orderRepository.insertOrderIntoPlan(planId, orderId);
      toast.success(`Đã chèn đơn hàng vào tuyến đang chạy (${updated.order_count} điểm giao)`);
      if (selectedPlan?.plan_id === planId) {
        setSelectedPlanId(updated.plan_id);
      }
      await Promise.all([loadPlans(), refetchOrders()]);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể chèn đơn hàng vào lộ trình');
    }
  };
  function getPlanStops(
    plan: DispatchPlanRecord,
    orders: Order[],
    fallbackLabel: string,
  ): OptimizationStop[] {
    if (plan.route_waypoints) {
      try {
        const parsed = JSON.parse(plan.route_waypoints) as Array<{
          stop_id?: string;
          location?: { lat: number; lng: number; address?: string };
        }>;
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed.map((wp, idx) => {
            const matchedOrder = orders.find((o) => o.id === wp.stop_id);
            return {
              id: wp.stop_id || `stop-${idx}`,
              location: {
                lat: wp.location?.lat ?? matchedOrder?.delivery_lat ?? 0,
                lng: wp.location?.lng ?? matchedOrder?.delivery_lng ?? 0,
              },
              order: {
                id: wp.stop_id || `stop-${idx}`,
                delivery_address:
                  wp.location?.address || matchedOrder?.delivery_address || fallbackLabel,
                order_code:
                  matchedOrder?.order_code || (wp.stop_id ? wp.stop_id.slice(0, 8) : `#${idx + 1}`),
              },
            };
          });
        }
      } catch {
        // Fallback sang ordered_stop_ids bên dưới
      }
    }

    if (plan.ordered_stop_ids?.length) {
      return plan.ordered_stop_ids
        .map((id) => orders.find((o) => o.id === id))
        .filter((o): o is NonNullable<typeof o> => Boolean(o))
        .map((o) => ({
          id: o.id,
          location: { lat: o.delivery_lat, lng: o.delivery_lng },
          order: {
            id: o.id,
            delivery_address: o.delivery_address,
            order_code: o.order_code,
          },
        }));
    }

    return [];
  }

  function getPlanDepot(
    plan: DispatchPlanRecord,
    orders: Order[],
    fallbackDepot: DispatchDepot | null,
    fallbackLabel: string,
  ): DispatchDepot | null {
    for (const orderId of plan.ordered_stop_ids ?? []) {
      const matchedOrder = orders.find((order) => order.id === orderId);
      if (!matchedOrder) continue;
      return {
        lat: matchedOrder.pickup_lat,
        lng: matchedOrder.pickup_lng,
        name: matchedOrder.pickup_location_name || fallbackLabel,
        address: matchedOrder.pickup_address,
        pickup_location_id: matchedOrder.pickup_location_id,
      };
    }
    return fallbackDepot;
  }
  const stopsForMap = useMemo<OptimizationStop[]>(
    () => (selectedPlan ? getPlanStops(selectedPlan, orders, t('Điểm giao hàng')) : []),
    [orders, selectedPlan, t],
  );
  const depotForMap = useMemo<DispatchDepot | null>(
    () => (selectedPlan ? getPlanDepot(selectedPlan, orders, fallbackDepot, t('Điểm lấy hàng')) : fallbackDepot),
    [fallbackDepot, orders, selectedPlan, t],
  );
  const routeColors = useMemo(() => new Map([...plans]
    .sort((a, b) => a.created_at.localeCompare(b.created_at) || a.plan_id.localeCompare(b.plan_id))
    .map((plan, index) => [
      plan.plan_id,
      ROUTE_COLORS[index] ?? `hsl(${Math.round(index * 137.508) % 360}, 64%, 39%)`,
    ])), [plans]);

  const mapRoutes = useMemo<MapOverviewRoute[]>(() => {
    return plans
      .map((plan): MapOverviewRoute | null => {
        const stops = getPlanStops(plan, orders, t('Điểm giao hàng'));
        const planDepot = getPlanDepot(plan, orders, null, t('Điểm lấy hàng'));
        const depot = planDepot ?? fallbackDepot;
        if (!depot || stops.length === 0) return null;
        const driver = drivers.find((d) => d.id === plan.driver_id || d.user_id === plan.driver_id);
        return {
          id: plan.plan_id,
          label: `Kế hoạch #${plan.plan_id.slice(0, 8)}`,
          status: plan.plan_status,
          color: routeColors.get(plan.plan_id) ?? ROUTE_COLORS[0],
          depot,
          stops,
          driverId: driver?.user_id || driver?.id || plan.driver_id || undefined,
          driverName: driver?.name,
          warehouseId: planDepot ? (planDepot.pickup_location_id || `location:${planDepot.lat}:${planDepot.lng}`) : undefined,
        };
      })
      .filter((item): item is MapOverviewRoute => item !== null);
  }, [drivers, fallbackDepot, orders, plans, routeColors, t]);
  const overviewRoutes = useMemo(() => filterDispatchMapRoutes(mapRoutes, mapFilters), [mapRoutes, mapFilters]);
  const mapFilterKey = Object.values(mapFilters).some(Boolean) ? JSON.stringify(mapFilters) : '';
  const filterDrivers = new Map(drivers.map((driver) => [driver.user_id || driver.id, driver.name]));
  const filterWarehouses = new Map(pickupLocations.map((pickup) => [pickup.id, pickup.name]));
  for (const route of mapRoutes) {
    const driverKey = route.driverId ?? UNASSIGNED_DRIVER;
    const warehouseKey = route.warehouseId ?? UNKNOWN_WAREHOUSE;
    if (!filterDrivers.has(driverKey)) {
      filterDrivers.set(driverKey, route.driverId ? `${t('Tài xế')} #${route.driverId.slice(0, 8)}` : t('Chưa gán tài xế'));
    }
    if (!filterWarehouses.has(warehouseKey)) {
      filterWarehouses.set(warehouseKey, route.warehouseId ? route.depot.name : t('Kho chưa xác định'));
    }
  }
  const visibleFleetDrivers = useMemo(() => {
    if (!mapFilterKey) return fleetDrivers;
    const matchingDriverIds = new Set(overviewRoutes.map((route) => route.driverId));
    return fleetDrivers.filter((driver) => matchingDriverIds.has(driver.id));
  }, [mapFilterKey, fleetDrivers, overviewRoutes]);
  const visibleSelectedPlan = selectedPlan && overviewRoutes.some((route) => route.id === selectedPlan.plan_id)
    ? selectedPlan : null;
  const visibleHighlight = highlightedPlan && overviewRoutes.some((route) => route.id === highlightedPlan.planId)
    ? highlightedPlan : null;
  const selectedOrderCount = selectedOrderIds.size;
  const updatePlanningSelection = (selection: Order[], checked: boolean) => {
    setSelectedOrderIds((current) => {
      const next = new Set(current);
      for (const order of selection) {
        if (checked) next.add(order.id);
        else next.delete(order.id);
      }
      return next;
    });
  };
  const changeMapFilters = (filters: DispatchMapFilters) => {
    setMapFilters(filters);
    setSelectedPlanId(null);
    setHighlightedPlan(null);
  };
  const selectPlanFromPanel = (planId: string | null) => {
    if (planId && mapFilterKey && !overviewRoutes.some((route) => route.id === planId)) {
      setMapFilters(EMPTY_DISPATCH_MAP_FILTERS);
    }
    setSelectedPlanId(planId);
  };
  const closePlansPanel = () => {
    setHighlightedPlan(null);
    setIsPlanningMenuOpen(false);
    setDispatchTab('routes');
    setIsPlanningPanelOpen(false);
    plansButtonRef.current?.focus();
  };
  const selectedPlanTimeline = selectedPlan && (!mapFilterKey || visibleSelectedPlan) && (
    <DispatchStopTimeline
      key={selectedPlan.plan_id}
      plan={selectedPlan}
      depot={depotForMap}
      stops={stopsForMap}
      orders={orders}
      driver={selectedDriver}
      routeColor={routeColors.get(selectedPlan.plan_id)}
      highlightedStopId={highlightedPlan?.planId === selectedPlan.plan_id ? highlightedPlan.stopId : undefined}
      onHighlightStop={(stopId) =>
        setHighlightedPlan(stopId ? { planId: selectedPlan.plan_id, stopId } : null)
      }
      onClose={() => setSelectedPlanId(null)}
    />
  );

  const planningButton = canExecute ? (
    <div ref={planningMenuRef} className="relative inline-block">
      <Button
        type="button"
        size="sm"
        className="min-h-11 cursor-pointer bg-teal-700 text-white hover:bg-teal-800 sm:min-h-9"
        aria-expanded={isPlanningMenuOpen}
        onClick={() => setIsPlanningMenuOpen(prev => !prev)}
        isLoading={isTriggeringPlan}
        disabled={isTriggeringPlan}
      >
        {!isTriggeringPlan && <Play size={14} />}
        {t('Lập tuyến')}
        <ChevronDown size={14} />
      </Button>

      {isPlanningMenuOpen && (
        <div className="absolute right-0 top-full z-50 mt-1 min-w-[250px] rounded-md border border-gray-200 bg-white p-1 shadow-lg">
          <button
            type="button"
            className="w-full cursor-pointer rounded px-4 py-2 text-left text-sm hover:bg-gray-100"
            onClick={() => { void handleTriggerPlanning('ALL'); }}
          >
            {t('Lập tuyến tất cả đơn hàng')}
          </button>

          <button
            type="button"
            disabled={selectedOrderCount === 0}
            className="w-full cursor-pointer rounded px-4 py-2 text-left text-sm hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40"
            onClick={() => { void handleTriggerPlanning('SELECTED'); }}
          >
            {t('Lập tuyến các đơn đã chọn')}
          </button>
        </div>
      )}
    </div>
  ) : null;

  return (
    <div className="flex min-w-0 flex-1 flex-col"
      onKeyDown={(event) => {
        if (event.key === 'Escape' && !event.defaultPrevented && isPlansOpen
          && event.target instanceof Node && event.currentTarget.contains(event.target)) {
          closePlansPanel();
        }
      }}>
      <header className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-200 bg-white px-3 py-1.5 sm:px-4">
        <h1 className="text-xl font-semibold text-slate-950">{t('Điều phối & bản đồ')}</h1>
        <Button
          variant="ghost"
          size="sm"
          className="min-h-11 min-w-11 shrink-0 px-2.5"
          aria-label={t('Làm mới dữ liệu')}
          title={t('Làm mới dữ liệu')}
          onClick={() => {
            void Promise.all([refetchOrders(), loadPlans()]);
          }}
          isLoading={isLoadingOrders || isLoadingPlans}
        >
          {!(isLoadingOrders || isLoadingPlans) && <RefreshCw size={16} aria-hidden="true" />}
        </Button>
      </header>

      <section
        aria-label={t('Bản đồ điều phối theo đường phố')}
        className="relative isolate min-h-[26rem] flex-1 overflow-hidden bg-slate-100"
      >
        <div className="absolute left-2 right-14 top-2 z-20 flex flex-wrap items-center gap-2 sm:left-3 sm:top-3">
          <Button
            ref={plansButtonRef}
            type="button"
            variant={isPlansOpen ? 'default' : 'outline'}
            size="sm"
            className="min-h-11 gap-2 rounded-xl px-3.5 shadow-lg backdrop-blur-md transition-all"
            aria-expanded={isPlansOpen}
            aria-controls="dispatch-tool-plans"
            onClick={isPlansOpen ? closePlansPanel : () => setIsPlanningPanelOpen(true)}
          >
            <ClipboardList size={16} aria-hidden="true" />
            <span>{t(canReadOrders ? 'Tuyến & đơn hàng' : 'Kế hoạch')}</span>
            {plans.length > 0 && (
              <span
                className={`rounded-full px-1.5 py-0.5 text-[11px] font-bold leading-none tabular-nums ${isPlansOpen ? 'bg-white/20 text-white' : 'bg-[#17766c]/10 text-[#17766c]'
                  }`}
              >
                {plans.length}
              </span>
            )}
          </Button>
          {!isPlansOpen && <RouteMapFilters
            filters={mapFilters}
            drivers={filterDrivers}
            warehouses={filterWarehouses}
            showDeliveryStops={showDeliveryStops}
            visibleCount={overviewRoutes.length}
            totalCount={mapRoutes.length}
            onChange={changeMapFilters}
            onShowDeliveryStopsChange={(visible) => {
              setShowDeliveryStops(visible);
              if (!visible) setHighlightedPlan(null);
            }}
          />}
        </div>

        <div className="absolute inset-0">
          <RouteMap
            depot={depotForMap ?? mapRoutes[0]?.depot ?? null}
            fleetDrivers={visibleFleetDrivers}
            fleetOrigin={fleetDrivers[0]}
            driver={!mapFilterKey || visibleSelectedPlan ? activeDriverPosition : undefined}
            driverName={selectedDriver?.name}
            driverVehicle={selectedDriver?.vehicle_plate || selectedDriver?.vehicle_type}
            trackingSignal={planTracking.signal}
            overviewRoutes={overviewRoutes}
            selectedOverviewRouteId={visibleSelectedPlan?.plan_id}
            highlightedPlan={visibleHighlight}
            filterKey={mapFilterKey}
            showDeliveryStops={showDeliveryStops}
            showPlanDetails={Boolean(visibleSelectedPlan) && !isPlansOpen}
            isPlansOpen={isPlansOpen}
            onHoverOverviewPlan={setHighlightedPlan}
            onSelectOverviewRoute={(planId) =>
              setSelectedPlanId((current) => (current === planId ? null : planId))
            }
          />
          {!isPlansOpen && selectedPlanTimeline}
        </div>

        <section
          id="dispatch-tool-plans"
          aria-label={t('Kế hoạch điều phối')}
          className={`absolute inset-x-2 bottom-2 z-30 max-h-[min(65dvh,calc(100%_-_4.5rem))] min-w-0 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white sm:inset-x-4 sm:bottom-4 ${isPlansOpen ? 'flex' : 'hidden'}`}
        >
          <Tabs.Root value={activeTab} onValueChange={(tab) => {
            setHighlightedPlan(null);
            setIsPlanningMenuOpen(false);
            setIsPlanningPanelOpen(true);
            setDispatchTab(tab);
          }} className="flex min-h-0 flex-col">
            <div className="flex shrink-0 flex-wrap items-center gap-x-2 gap-y-1 border-b border-slate-200 px-2 sm:px-3">
              <Tabs.List aria-label={t('Kế hoạch điều phối')} className="mr-auto flex items-center gap-1">
                <Tabs.Trigger value="routes" className={tabClass}>
                  {locale === 'en' ? 'Routes' : 'Tuyến đường'}
                  <span className="text-xs tabular-nums text-slate-600">{plans.length}</span>
                </Tabs.Trigger>
                {canReadOrders && <Tabs.Trigger value="orders" className={tabClass}>
                  {t('Đơn hàng')}
                  {selectedOrderCount > 0 && <span className="text-xs tabular-nums text-teal-800">{selectedOrderCount}</span>}
                </Tabs.Trigger>}
              </Tabs.List>
              <div className="ml-auto flex items-center gap-1 py-1">
                {activeTab === 'routes' && planningButton}
                <Button type="button" variant="ghost" size="sm" className="size-9 cursor-pointer px-0"
                  onClick={closePlansPanel} aria-label={t('Thu gọn')} title={t('Thu gọn')}>
                  <X size={17} aria-hidden="true" />
                </Button>
              </div>
            </div>

            <Tabs.Content value="routes" forceMount className="min-h-0 overflow-y-auto overscroll-contain data-[state=inactive]:hidden">
              <PlanApprovalPanel
                key={linkedPlanId ?? 'default'}
                plans={plans}
                drivers={drivers}
                orders={orders}
                routeColors={routeColors}
                eligibleOrders={dispatchableOrders}
                canExecute={canExecute}
                isLoading={isLoadingPlans}
                selectedPlanId={selectedPlanId}
                highlightedPlan={highlightedPlan}
                onSelectPlan={selectPlanFromPanel}
                onHighlightPlan={setHighlightedPlan}
                onApprovePlan={handleApprovePlan}
                onDispatchPlan={handleDispatchPlan}
                onCancelPlan={handleCancelPlan}
                onInsertOrderIntoPlan={handleInsertOrderIntoPlan}
                onReorderStops={handleReorderStops}
                onMoveStop={handleMoveStop}
                editingPlanId={editingPlanId}
                maxOrdersPerDriver={maxOrdersPerDriver}
              />
            </Tabs.Content>
            {canReadOrders && isPlansOpen && (
              <Tabs.Content value="orders" forceMount className="min-h-0 overflow-y-auto overscroll-contain p-2.5 data-[state=inactive]:hidden sm:p-4">
                <OrdersWorkspace embedded refreshRevision={orderPageRevision} onOrdersChanged={fetchOrdersForMap}
                  headerActions={activeTab === 'orders' ? planningButton : undefined}
                  selection={canExecute ? {
                    ids: selectedOrderIds,
                    disabled: isTriggeringPlan,
                    update: updatePlanningSelection,
                  } : undefined}
                />
              </Tabs.Content>
            )}
          </Tabs.Root>
        </section>
      </section>
    </div>
  );
}
