'use client';

import { useAuth } from '@/application/contexts/auth-context';
import { useDrivers } from '@/application/hooks/use-drivers';
import { useFleetLiveTracking } from '@/application/hooks/use-fleet-live-tracking';
import { useOrders } from '@/application/hooks/use-orders';
import { usePlanLiveTracking } from '@/application/hooks/use-plan-live-tracking';
import { AccessDeniedNotice } from '@/components/auth/permission-guard';
import { DispatchStopTimeline } from '@/components/dispatch/dispatch-stop-timeline';
import { PlanApprovalPanel } from '@/components/dispatch/plan-approval-panel';
import { RouteMap } from '@/components/dispatch/route-map';
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
import { ApiError } from '@/lib/api';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import { ClipboardList, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
const ROUTE_COLORS = ['#2563eb', '#059669', '#d97706', '#7c3aed', '#db2777', '#0891b2'];
export default function DispatchPage() {
  const { t } = useLocale();
  const { hasPermission, user } = useAuth();
  const { orders, isLoading: isLoadingOrders, refetch: refetchOrders } = useOrders();
  const { drivers } = useDrivers();
  const fleetTracking = useFleetLiveTracking();
  const [pickupLocations, setPickupLocations] = useState<PickupLocation[]>([]);
  const [fetchedPlans, setPlans] = useState<DispatchPlanRecord[]>([]);
  const [isLoadingPlans, setIsLoadingPlans] = useState(true);
  const [isTriggeringPlan, setIsTriggeringPlan] = useState(false);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [editingPlanId, setEditingPlanId] = useState<string | null>(null);
  const [isPlansOpen, setIsPlansOpen] = useState(false);
  const [highlightedPlan, setHighlightedPlan] = useState<MapPlanHighlight | null>(null);
  const [linkedPlanId, setLinkedPlanId] = useState<string | null>(null);
  const plansButtonRef = useRef<HTMLButtonElement | null>(null);
  const plans = useMemo(() => {
    const ordersById = new Map(orders.map((order) => [order.id, order]));
    return fetchedPlans.map((plan) => {
      if (plan.plan_status !== 'DISPATCHED' || !plan.ordered_stop_ids?.length) return plan;
      const stops = plan.ordered_stop_ids.map((id) => ordersById.get(id));
      const hasFinishedReturn = stops.some((order) => order?.status === 'RETURNED');
      const allFinished = stops.every(
        (order) => order && ['DELIVERED', 'CANCELLED', 'RETURNED'].includes(order.status),
      );
      return hasFinishedReturn && allFinished ? { ...plan, plan_status: 'COMPLETED' as const } : plan;
    });
  }, [fetchedPlans, orders]);

  const selectedPlan = plans.find((plan) => plan.plan_id === selectedPlanId) ?? null;
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
    try {
      const list = await orderRepository.getDispatchPlans();
      setPlans(list);
    } catch {
      // Keep existing state when endpoint is unreachable
    } finally {
      setIsLoadingPlans(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
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
          setIsPlansOpen(true);
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
  }, [linkedPlanId, isPlansOpen]);

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

  const handleTriggerPlanning = async () => {
    setIsTriggeringPlan(true);
    try {
      const result = await orderRepository.triggerDispatchPlanning();
      await Promise.all([loadPlans(), refetchOrders()]);
      if (result.outcome === 'PLANNED' && result.plan_ids.length > 0) {
        setSelectedPlanId(result.plan_ids[0]);
        toast.success(`${t('Đã tạo tuyến nháp chưa gán tài xế')}: ${result.plan_ids.length}`);
        return true;
      } else if (result.outcome === 'NO_ORDERS') {
        toast.info(t('Chưa có đơn đủ điều kiện lập kế hoạch.'));
      } else if (result.outcome === 'IN_PROGRESS') {
        toast.info(t('Một lượt lập kế hoạch khác đang chạy.'));
      } else {
        toast.warning(t('Chưa tạo được tuyến từ các đơn đang chờ.'));
      }
    } catch (err) {
      toast.error(err instanceof ApiError && err.status === 404
        ? t('Order Service đang dùng phiên bản lập kế hoạch cũ.')
        : err instanceof Error ? err.message : t('Không thể chạy lập kế hoạch. Vui lòng thử lại.'));
    } finally {
      setIsTriggeringPlan(false);
    }
    return false;
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
        throw new Error(t('Order Service đang dùng phiên bản lập kế hoạch cũ.'));
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
        throw new Error(t('Order Service đang dùng phiên bản lập kế hoạch cũ.'));
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
    const firstOrderId = plan.ordered_stop_ids?.[0];
    const matchedOrder = firstOrderId ? orders.find((o) => o.id === firstOrderId) : undefined;
    if (matchedOrder) {
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
        const depot = getPlanDepot(plan, orders, fallbackDepot, t('Điểm lấy hàng'));
        if (!depot || stops.length === 0) return null;
        const driver = drivers.find((d) => d.id === plan.driver_id || d.user_id === plan.driver_id);
        return {
          id: plan.plan_id,
          label: `Kế hoạch #${plan.plan_id.slice(0, 8)}`,
          status: plan.plan_status,
          color: routeColors.get(plan.plan_id) ?? ROUTE_COLORS[0],
          depot,
          stops,
          driverName: driver?.name,
        };
      })
      .filter((item): item is MapOverviewRoute => item !== null);
  }, [drivers, fallbackDepot, orders, plans, routeColors, t]);
  const overviewRoutes = useMemo(() => mapRoutes.filter((route) =>
    route.id === selectedPlanId || ['DRAFT', 'APPROVED', 'DISPATCHED'].includes(route.status),
  ), [mapRoutes, selectedPlanId]);
  const previewOverviewRoute = highlightedPlan && !overviewRoutes.some((route) => route.id === highlightedPlan.planId)
    ? mapRoutes.find((route) => route.id === highlightedPlan.planId)
    : undefined;
  if (!hasPermission(['dispatch:read', 'dispatch:execute'])) {
    return <AccessDeniedNotice requiredPermission="dispatch:read" />;
  }
  const canExecute = user?.role !== 'DRIVER' && hasPermission('dispatch:execute');
  const closePlansPanel = () => {
    setHighlightedPlan(null);
    setIsPlansOpen(false);
    plansButtonRef.current?.focus();
  };
  const selectedPlanTimeline = selectedPlan && (
    <DispatchStopTimeline
      key={selectedPlan.plan_id}
      plan={selectedPlan}
      depot={depotForMap}
      stops={stopsForMap}
      orders={orders}
      driver={selectedDriver}
      routeColor={routeColors.get(selectedPlan.plan_id)}
      onClose={() => setSelectedPlanId(null)}
    />
  );

  return (
    <div className="flex min-w-0 flex-1 flex-col"
      onKeyDown={(event) => {
        if (event.key === 'Escape' && !event.defaultPrevented && isPlansOpen) closePlansPanel();
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
        className={`relative isolate flex-1 overflow-hidden bg-slate-100 ${isPlansOpen ? 'min-h-60' : 'min-h-[24rem]'}`}
      >
        <Button
          ref={plansButtonRef}
          type="button"
          variant={isPlansOpen ? 'default' : 'outline'}
          size="sm"
          className="absolute left-2 top-2 z-20 min-h-11 gap-1.5 px-3 shadow-md sm:left-3 sm:top-3"
          aria-expanded={isPlansOpen}
          aria-controls="dispatch-tool-plans"
          onClick={isPlansOpen ? closePlansPanel : () => setIsPlansOpen(true)}
        >
          <ClipboardList size={16} aria-hidden="true" />
          {t('Kế hoạch')}
        </Button>

        <div className="absolute inset-0">
          <RouteMap
            depot={depotForMap}
            fleetDrivers={fleetDrivers}
            driver={activeDriverPosition}
            driverName={selectedDriver?.name}
            driverVehicle={selectedDriver?.vehicle_plate || selectedDriver?.vehicle_type}
            trackingSignal={planTracking.signal}
            overviewRoutes={overviewRoutes}
            selectedOverviewRouteId={selectedPlanId}
            highlightedPlan={highlightedPlan}
            previewOverviewRoute={previewOverviewRoute}
            showPlanDetails={Boolean(selectedPlan) && !isPlansOpen}
            onSelectOverviewRoute={(planId) =>
              setSelectedPlanId((current) => (current === planId ? null : planId))
            }
          />
          {!isPlansOpen && selectedPlanTimeline}
        </div>
      </section>
      <section
        id="dispatch-tool-plans"
        hidden={!isPlansOpen}
        aria-labelledby="dispatch-tool-heading-plans"
        className="min-w-0 shrink-0 border-t border-slate-200"
      >
        <PlanApprovalPanel
          key={linkedPlanId ?? 'default'}
          initialFilter={linkedPlanId ? 'ALL' : undefined}
          plans={plans}
          drivers={drivers}
          orders={orders}
          routeColors={routeColors}
          eligibleOrders={dispatchableOrders}
          canExecute={canExecute}
          isLoading={isLoadingPlans}
          isTriggering={isTriggeringPlan}
          selectedPlanId={selectedPlanId}
          onSelectPlan={setSelectedPlanId}
          onHighlightPlan={setHighlightedPlan}
          onClose={closePlansPanel}
          onRefresh={loadPlans}
          onTriggerPlanning={handleTriggerPlanning}
          onApprovePlan={handleApprovePlan}
          onDispatchPlan={handleDispatchPlan}
          onCancelPlan={handleCancelPlan}
          onInsertOrderIntoPlan={handleInsertOrderIntoPlan}
          onReorderStops={handleReorderStops}
          onMoveStop={handleMoveStop}
          editingPlanId={editingPlanId}
        />
      </section>
    </div>
  );
}
