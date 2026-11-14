'use client';

import { useLocale } from '@/i18n/provider';

import { useDispatch } from '@/application/hooks/use-dispatch';
import { useDrivers } from '@/application/hooks/use-drivers';
import { useOrderLiveTracking } from '@/application/hooks/use-order-live-tracking';
import { useOrders } from '@/application/hooks/use-orders';
import { usePlanLiveTracking } from '@/application/hooks/use-plan-live-tracking';
import { AlgorithmMetricsBar } from '@/components/dispatch/algorithm-metrics-bar';
import { DispatchPanel } from '@/components/dispatch/dispatch-panel';
import { DriverAssignmentModal } from '@/components/dispatch/driver-assignment-modal';
import { PlanApprovalPanel } from '@/components/dispatch/plan-approval-panel';
import { RouteMap } from '@/components/dispatch/route-map';
import { Button } from '@/components/ui/button';
import { PickupLocation } from '@/domain/entities/pickup-location.entity';
import { DispatchDepot, DispatchPlanRecord, OptimizationResult, OptimizationStop } from '@/domain/entities/routing.entity';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import { AlertCircle, CheckCircle, MapPin, Navigation, PackageCheck, Play, RefreshCw, Square, UserRound, Warehouse } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';
import api from '@/lib/api';
import { useDispatchIncidents } from '@/application/hooks/use-dispatch-incidents';
import { operationsRepository } from '@/infrastructure/repositories/operations.repository';

export default function DispatchPage() {
  const { t, intlLocale } = useLocale();
  const { orders, isLoading: isLoadingOrders, refetch: refetchOrders } = useOrders();
  const { drivers, isLoading: isLoadingDrivers } = useDrivers();
  const [pickupLocations, setPickupLocations] = useState<PickupLocation[]>([]);
  const [fetchedPlans, setPlans] = useState<DispatchPlanRecord[]>([]);
  const [isLoadingPlans, setIsLoadingPlans] = useState(true);
  const [isTriggeringPlan, setIsTriggeringPlan] = useState(false);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [isAssignModalOpen, setIsAssignModalOpen] = useState(false);
  const [assignmentSuccess, setAssignmentSuccess] = useState<{ count: number; driverCount: number } | null>(null);
  const [lastAssignedRoute, setLastAssignedRoute] = useState<{
    depot: DispatchDepot;
    stops: OptimizationStop[];
    result: OptimizationResult;
  } | null>(null);
  const [roadMetrics, setRoadMetrics] = useState<{ distanceMeters: number; durationSeconds: number } | null>(null);
  const [routeWaypoints, setRouteWaypoints] = useState<Array<{ lat: number; lng: number }>>([]);
  const [isStartingSim, setIsStartingSim] = useState(false);
  const [trackedDriverId, setTrackedDriverId] = useState('');
  const [simulationOrigin, setSimulationOrigin] = useState<[number, number]>();
  const [isPickingOrigin, setIsPickingOrigin] = useState(false);
  const [isReportingIncident, setIsReportingIncident] = useState(false);
  const { incidents, error: incidentError, refresh: refreshIncidents } = useDispatchIncidents();

  const plans = useMemo(() => {
    const ordersById = new Map(orders.map((order) => [order.id, order]));
    return fetchedPlans.map((plan) => {
      if (plan.plan_status !== 'DISPATCHED' || !plan.ordered_stop_ids?.length) return plan;
      const stops = plan.ordered_stop_ids.map((id) => ordersById.get(id));
      const hasFinishedReturn = stops.some((order) => order?.status === 'RETURNED');
      const allFinished = stops.every((order) => order && ['DELIVERED', 'CANCELLED', 'RETURNED'].includes(order.status));
      return hasFinishedReturn && allFinished ? { ...plan, plan_status: 'COMPLETED' as const } : plan;
    });
  }, [fetchedPlans, orders]);
  const selectedPlan = plans.find((plan) => plan.plan_id === selectedPlanId) ?? null;

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
    pickupLocationRepository.getPickupLocations('ACTIVE')
      .then((locations) => { if (active) setPickupLocations(locations); })
      .catch(() => { if (active) setPickupLocations([]); });
    orderRepository.getDispatchPlans()
      .then((list) => { if (active) setPlans(list); })
      .catch(() => {})
      .finally(() => { if (active) setIsLoadingPlans(false); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const refresh = () => { void loadPlans(); };
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    window.addEventListener('swiftroute:realtime-update', refresh);
    document.addEventListener('visibilitychange', refreshWhenVisible);
    const interval = window.setInterval(refreshWhenVisible, 30000);
    return () => {
      window.removeEventListener('swiftroute:realtime-update', refresh);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
      window.clearInterval(interval);
    };
  }, [loadPlans]);

  const fallbackDepot = useMemo(() => {
    const location = pickupLocations.find((item) => item.is_default) || pickupLocations[0];
    return location ? {
      lat: location.lat,
      lng: location.lng,
      name: location.name,
      address: location.address,
      pickup_location_id: location.id,
    } : null;
  }, [pickupLocations]);

  const {
    selectedOrders,
    selectedDriverIds,
    setSelectedDriverIds,
    toggleDriverSelection,
    activeVehicleIndex,
    setActiveVehicleIndex,
    algorithm,
    setAlgorithm,
    result,
    isOptimizing,
    error: dispatchError,
    selectionWarning,
    toggleOrder,
    selectAll,
    clearSelection,
    isOrderSelectable,
    optimize,
    assignToDriver,
    depot,
  } = useDispatch({ fallbackDepot });

  // A new dispatch plan can only consume orders that have not been planned yet.
  const dispatchableOrders = orders.filter((order) => order.status === 'READY_FOR_DISPATCH');

  const trackedDriver = useMemo(
    () => drivers.find((driver) => driver.id === trackedDriverId),
    [drivers, trackedDriverId],
  );

  const trackedDriverOrders = useMemo(() => {
    if (!trackedDriver) return [];
    return orders
      .filter((order) =>
        (order.status === 'PLANNED' || order.status === 'IN_TRANSIT')
        && (order.assigned_driver_id === trackedDriver.id
          || order.assigned_driver_id === trackedDriver.user_id),
      )
      .sort((left, right) => Date.parse(right.updated_at) - Date.parse(left.updated_at));
  }, [orders, trackedDriver]);

  const returningOrderCount = trackedDriver ? orders.filter((order) =>
    order.status === 'FAILED' && order.fulfillment_status === 'RETURNING'
    && (order.assigned_driver_id === trackedDriver.id || order.assigned_driver_id === trackedDriver.user_id),
  ).length : 0;

  const trackedDriverPlan = useMemo(() => {
    if (!trackedDriver) return null;
    const driverPlans = plans.filter((plan) =>
      plan.plan_status === 'DISPATCHED'
      && (plan.driver_id === trackedDriver.id || plan.driver_id === trackedDriver.user_id),
    );
    if (trackedDriverOrders.length > 0) {
      const activeIds = new Set(trackedDriverOrders.map((order) => order.id));
      const matched = driverPlans.find((plan) =>
        plan.ordered_stop_ids?.some((stopId) => activeIds.has(stopId)),
      );
      if (matched) return matched;
    }
    return driverPlans[0] ?? null;
  }, [plans, trackedDriver, trackedDriverOrders]);

  const latestAssignedOrder = useMemo(() => {
    if (trackedDriverId) return trackedDriverOrders[0];
    return orders
      .filter((order) =>
        (order.status === 'PLANNED' || order.status === 'IN_TRANSIT')
        && Boolean(order.assigned_driver_id),
      )
      .sort((left, right) => Date.parse(right.updated_at) - Date.parse(left.updated_at))[0];
  }, [orders, trackedDriverId, trackedDriverOrders]);

  const activeTrackedOrderId = useMemo(() => {
    if (selectedPlan) return undefined;
    if (lastAssignedRoute?.stops?.[0]?.id) return lastAssignedRoute.stops[0].id;
    return latestAssignedOrder?.id;
  }, [lastAssignedRoute, latestAssignedOrder, selectedPlan]);

  const planTracking = usePlanLiveTracking(
    selectedPlan?.plan_status === 'DISPATCHED' ? selectedPlan.plan_id : undefined,
    selectedPlan?.driver_id,
    trackedDriverId || undefined,
  );
  const orderTracking = useOrderLiveTracking(
    activeTrackedOrderId,
    latestAssignedOrder?.assigned_driver_id,
  );

  const trackedDriverCachedPosition = useMemo<[number, number] | undefined>(() => {
    if (!trackedDriver) return undefined;
    const { current_lat: lat, current_lng: lng } = trackedDriver;
    if (typeof lat === 'number' && typeof lng === 'number' && Number.isFinite(lat) && Number.isFinite(lng) && (lat !== 0 || lng !== 0)) {
      return [lat, lng];
    }
    return undefined;
  }, [trackedDriver]);

  const activeDriverPosition = trackedDriverId
    ? (planTracking.position ?? trackedDriverCachedPosition)
    : selectedPlan?.plan_status === 'DISPATCHED'
      ? planTracking.position
      : orderTracking.position ?? planTracking.position;
  const activeStepProgress = selectedPlan?.plan_status === 'DISPATCHED'
    ? planTracking.stepProgress
    : orderTracking.stepProgress;
  const isSimulating = trackedDriverId ? planTracking.isSimulating : selectedPlan?.plan_status === 'DISPATCHED'
    ? planTracking.isSimulating
    : orderTracking.isSimulating;

  const incidentPlanId = trackedDriverId ? trackedDriverPlan?.plan_id : selectedPlan?.plan_id;
  const incidentOrder = trackedDriverId ? trackedDriverOrders.find((order) => order.status === 'IN_TRANSIT')
    : latestAssignedOrder?.status === 'IN_TRANSIT' ? latestAssignedOrder : undefined;
  const activeRouteOrderIds = useMemo(() => new Set(
    incidentPlanId
      ? (trackedDriverId ? trackedDriverPlan?.ordered_stop_ids : selectedPlan?.ordered_stop_ids) ?? []
      : trackedDriverId
        ? trackedDriverOrders.map((order) => order.id)
        : incidentOrder ? [incidentOrder.id] : [],
  ), [incidentOrder, incidentPlanId, selectedPlan?.ordered_stop_ids, trackedDriverId, trackedDriverOrders, trackedDriverPlan?.ordered_stop_ids]);

  const activeDriverIncidents = useMemo(() => {
    const identities = trackedDriver
      ? [trackedDriver.id, trackedDriver.user_id]
      : [selectedPlan?.driver_id ?? latestAssignedOrder?.assigned_driver_id];
    return incidents.filter((incident) => {
      if (!incident.driver_id || !identities.includes(incident.driver_id)) return false;
      if (incident.dispatch_plan_id) return incident.dispatch_plan_id === incidentPlanId;
      return Boolean(incident.order_id && activeRouteOrderIds.has(incident.order_id));
    })
      .sort((left, right) => {
        const priority = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
        return priority[right.severity] - priority[left.severity]
          || Date.parse(right.created_at) - Date.parse(left.created_at);
      });
  }, [activeRouteOrderIds, incidentPlanId, incidents, trackedDriver, selectedPlan?.driver_id, latestAssignedOrder?.assigned_driver_id]);
  const driverIncident = useMemo(() => activeDriverIncidents[0] ? {
    id: activeDriverIncidents[0].incident_id,
    label: activeDriverIncidents[0].title,
    count: activeDriverIncidents.length,
  } : undefined, [activeDriverIncidents]);
  const handleSimulateIncident = async () => {
    if (!isSimulating || (!incidentPlanId && !incidentOrder) || driverIncident) return;
    setIsReportingIncident(true);
    try {
      await operationsRepository.reportIncident({
        ...(incidentPlanId ? { dispatch_plan_id: incidentPlanId } : { order_id: incidentOrder!.id }),
        incident_type: 'VEHICLE_BREAKDOWN',
        severity: 'HIGH',
        notes: '[Giả lập] Tài xế báo xe gặp vấn đề trên đường giao, cần điều phối viên hỗ trợ.',
        can_continue_delivery: false,
        needs_immediate_assistance: true,
      });
      const stopped = await handleStopSimulation();
      await refreshIncidents();
      toast.success(stopped
        ? 'Đã ghi nhận sự cố và dừng giả lập. Thông báo sẽ xuất hiện sau vài giây.'
        : 'Đã ghi nhận sự cố. Chưa dừng được giả lập; hãy thử bấm Dừng giả lập.');
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Không thể tạo sự cố giả lập');
    } finally {
      setIsReportingIncident(false);
    }
  };

  const handleSelectAll = () => {
    setSelectedPlanId(null);
    setLastAssignedRoute(null);
    selectAll(dispatchableOrders);
  };

  const handleDeselectAll = () => {
    setSelectedPlanId(null);
    setLastAssignedRoute(null);
    clearSelection();
  };

  const handleOptimize = async () => {
    try {
      setSelectedPlanId(null);
      await optimize();
      toast.success('Tối ưu hóa tuyến đường thành công!');
    } catch {
      toast.error('Lỗi khi tính toán thuật toán TSP');
    }
  };

  const handleConfirmAssignment = async (driverInput: string | string[]) => {
    try {
      const assignedCount = selectedOrders.length;
      const count = Array.isArray(driverInput) ? driverInput.length : 1;
      const assignedRoute = result && depot ? {
        depot,
        stops: result.ordered_stops.length ? result.ordered_stops : selectedOrders.map((order) => ({
          id: order.id,
          location: { lat: order.delivery_lat, lng: order.delivery_lng },
          order: {
            id: order.id,
            delivery_address: order.delivery_address,
            order_code: order.order_code,
          },
        })),
        result,
      } : null;
      await assignToDriver(driverInput);
      setLastAssignedRoute(assignedRoute);
      setAssignmentSuccess({
        count: assignedCount,
        driverCount: count,
      });
      toast.success(
        count > 1
          ? `Đã phân chia và gán lộ trình cho ${count} tài xế thành công!`
          : 'Đã gán lộ trình cho tài xế thành công!'
      );
      refetchOrders();
      loadPlans();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Lỗi khi gán đơn');
      throw err;
    }
  };

  const handleTriggerPlanning = async () => {
    setIsTriggeringPlan(true);
    try {
      await orderRepository.triggerDispatchPlanning();
      toast.success('Đã kích hoạt chu kỳ lập kế hoạch điều phối tự động');
      await Promise.all([loadPlans(), refetchOrders()]);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể kích hoạt lập kế hoạch');
    } finally {
      setIsTriggeringPlan(false);
    }
  };

  const handleApprovePlan = async (planId: string, driverId?: string | null) => {
    try {
      await orderRepository.approveDispatchPlan(planId, driverId);
      toast.success('Đã phê duyệt kế hoạch điều phối');
      await loadPlans();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể phê duyệt kế hoạch');
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

  const handleReoptimizePlan = async (planId: string) => {
    try {
      const updated = await orderRepository.reoptimizeDispatchPlan(planId);
      toast.success(
        `Đã tối ưu lại các chặng còn lại (${Number(updated.total_distance_km ?? 0).toFixed(2)} km)`,
      );
      if (selectedPlan?.plan_id === planId) {
        setSelectedPlanId(updated.plan_id);
      }
      await Promise.all([loadPlans(), refetchOrders()]);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể tối ưu lại lộ trình');
    }
  };

  const handleInsertOrderIntoPlan = async (planId: string, orderId: string) => {
    try {
      const updated = await orderRepository.insertOrderIntoPlan(planId, orderId);
      toast.success(
        `Đã chèn đơn hàng vào tuyến đang chạy (${updated.order_count} điểm giao)`,
      );
      if (selectedPlan?.plan_id === planId) {
        setSelectedPlanId(updated.plan_id);
      }
      await Promise.all([loadPlans(), refetchOrders()]);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể chèn đơn hàng vào lộ trình');
    }
  };

  const handleStartSimulation = async () => {
    if (routeWaypoints.length < 2) {
      toast.error('Chưa có tọa độ đường đi trên bản đồ để bắt đầu giả lập.');
      return;
    }
    setIsStartingSim(true);
    try {
      if (trackedDriverId) {
        let pauseAtIndex: number | undefined;
        if (depotForMap && latestAssignedOrder?.status !== 'IN_TRANSIT') {
          let nearest = Infinity;
          routeWaypoints.forEach((point, index) => {
            const distance = (point.lat - depotForMap.lat) ** 2 + (point.lng - depotForMap.lng) ** 2;
            if (distance < nearest) { nearest = distance; pauseAtIndex = index; }
          });
        }
        await api.post(`/orders/dispatch/drivers/${trackedDriverId}/tracking/simulate`, {
          waypoints: routeWaypoints, interval_ms: 1000, pause_at_index: pauseAtIndex,
        });
        toast.success('Đã bắt đầu giả lập tài xế');
      } else if (selectedPlan?.plan_status === 'DISPATCHED') {
        await orderRepository.startPlanSimulation(selectedPlan.plan_id, routeWaypoints, 1000);
        toast.success(`Đã bắt đầu giả lập di chuyển (${routeWaypoints.length} điểm tọa độ)`);
      } else if (activeTrackedOrderId) {
        await orderRepository.startOrderSimulation(activeTrackedOrderId, routeWaypoints, 1000);
        toast.success(`Đã bắt đầu giả lập di chuyển cho đơn hàng (${routeWaypoints.length} điểm tọa độ)`);
      } else {
        toast.error('Hãy chọn một kế hoạch đang giao (DISPATCHED) hoặc gán đơn cho tài xế trước khi giả lập.');
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể kích hoạt giả lập đường đi');
    } finally {
      setIsStartingSim(false);
    }
  };

  const handleStopSimulation = async () => {
    setIsStartingSim(true);
    try {
      if (trackedDriverId) {
        await api.post(`/orders/dispatch/drivers/${trackedDriverId}/tracking/simulate/stop`);
      } else if (selectedPlan?.plan_status === 'DISPATCHED') {
        await orderRepository.stopPlanSimulation(selectedPlan.plan_id);
      } else if (activeTrackedOrderId) {
        await orderRepository.stopOrderSimulation(activeTrackedOrderId);
      }
      toast.info('Đã dừng giả lập di chuyển.');
      return true;
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể dừng giả lập');
      return false;
    } finally {
      setIsStartingSim(false);
    }
  };

  const planStopsForMap = useMemo<OptimizationStop[] | null>(() => {
    if (!selectedPlan) return null;
    if (selectedPlan.route_waypoints) {
      try {
        const parsed = JSON.parse(selectedPlan.route_waypoints) as Array<{
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
                delivery_address: wp.location?.address || matchedOrder?.delivery_address || t("Điểm giao hàng"),
                order_code: matchedOrder?.order_code || (wp.stop_id ? wp.stop_id.slice(0, 8) : `#${idx + 1}`),
              },
            };
          });
        }
      } catch {
        // Fallback to ordered_stop_ids lookup below
      }
    }
    if (selectedPlan.ordered_stop_ids?.length) {
      return selectedPlan.ordered_stop_ids
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
    return null;
  }, [orders, selectedPlan, t]);

  const assignedStops = useMemo<OptimizationStop[]>(() => {
    const activeOrders = trackedDriverId
      ? trackedDriverOrders
      : latestAssignedOrder ? [latestAssignedOrder] : [];
    const orderById = new Map(activeOrders.map((order) => [order.id, order]));
    const ordered = trackedDriverPlan?.ordered_stop_ids?.length
      ? [
          ...trackedDriverPlan.ordered_stop_ids
            .map((orderId) => orderById.get(orderId))
            .filter((order): order is NonNullable<typeof order> => Boolean(order)),
          ...activeOrders.filter((order) => !trackedDriverPlan.ordered_stop_ids.includes(order.id)),
        ]
      : activeOrders;
    return ordered.map((order) => ({
      id: order.id,
      location: { lat: order.delivery_lat, lng: order.delivery_lng },
      order: {
        id: order.id,
        delivery_address: order.delivery_address,
        order_code: order.order_code,
      },
    }));
  }, [latestAssignedOrder, trackedDriverId, trackedDriverOrders, trackedDriverPlan]);

  const latestAssignedDepot = useMemo<DispatchDepot | null>(
    () =>
      latestAssignedOrder
        ? {
            lat: latestAssignedOrder.pickup_lat,
            lng: latestAssignedOrder.pickup_lng,
            name: latestAssignedOrder.pickup_location_name || t("Điểm lấy hàng"),
            address: latestAssignedOrder.pickup_address,
            pickup_location_id: latestAssignedOrder.pickup_location_id,
          }
        : null,
    [latestAssignedOrder, t],
  );

  const stopsForMap = useMemo<OptimizationStop[]>(() => {
    if (planStopsForMap?.length) return planStopsForMap;
    if (trackedDriverId) return assignedStops;
    if (
      activeVehicleIndex !== null &&
      result?.vehicle_routes?.[activeVehicleIndex]?.ordered_stops?.length
    ) {
      return result.vehicle_routes[activeVehicleIndex].ordered_stops;
    }
    if (result?.ordered_stops?.length) return result.ordered_stops;
    if (selectedOrders.length) {
      return selectedOrders.map((o) => ({
        id: o.id,
        location: { lat: o.delivery_lat, lng: o.delivery_lng },
        order: {
          id: o.id,
          delivery_address: o.delivery_address,
          order_code: o.order_code,
        },
      }));
    }
    return lastAssignedRoute?.stops ?? assignedStops;
  }, [activeVehicleIndex, assignedStops, lastAssignedRoute?.stops, planStopsForMap, result, selectedOrders, trackedDriverId]);

  const selectedPlanDepot = useMemo<DispatchDepot | null>(() => {
    if (!selectedPlan) return null;
    const firstOrderId = selectedPlan.ordered_stop_ids?.[0];
    const matchedOrder = firstOrderId ? orders.find((o) => o.id === firstOrderId) : undefined;
    if (!matchedOrder) return null;
    return {
      lat: matchedOrder.pickup_lat,
      lng: matchedOrder.pickup_lng,
      name: matchedOrder.pickup_location_name || t("Điểm lấy hàng"),
      address: matchedOrder.pickup_address,
      pickup_location_id: matchedOrder.pickup_location_id,
    };
  }, [orders, selectedPlan, t]);

  const depotForMap: DispatchDepot | null = selectedOrders.length
    ? depot
    : selectedPlan
      ? (selectedPlanDepot ?? depot)
      : lastAssignedRoute?.depot ?? latestAssignedDepot ?? depot;
  const resultForMetrics = result ?? lastAssignedRoute?.result ?? null;
  const canSimulate = Boolean(
    routeWaypoints.length >= 2 &&
    (trackedDriverId || (selectedPlan && selectedPlan.plan_status === 'DISPATCHED') || activeTrackedOrderId),
  );

  return (
    <div className="space-y-4">
      {/* Page Title Bar */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">
              {t("Điều phối & bản đồ")}
            </h1>
          </div>
          <p className="text-sm text-gray-500">
            {t("Quản lý kế hoạch tự động từ OR-Tools hoặc chọn đơn thủ công để tính tuyến và phân công tài xế.")}
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            refetchOrders();
            loadPlans();
          }}
          isLoading={isLoadingOrders || isLoadingPlans}
        >
          <RefreshCw size={14} />{" "}{t("Làm mới dữ liệu")}
        </Button>
      </div>

      {/* Plan Lifecycle Approval Panel */}
      <PlanApprovalPanel
        plans={plans}
        drivers={drivers}
        eligibleOrders={dispatchableOrders}
        isLoading={isLoadingPlans}
        isTriggering={isTriggeringPlan}
        selectedPlanId={selectedPlan?.plan_id}
        onSelectPlan={(plan) => {
          setLastAssignedRoute(null);
          setSelectedPlanId((prev) => (prev === plan.plan_id ? null : plan.plan_id));
        }}
        onRefresh={loadPlans}
        onTriggerPlanning={handleTriggerPlanning}
        onApprovePlan={handleApprovePlan}
        onDispatchPlan={handleDispatchPlan}
        onCancelPlan={handleCancelPlan}
        onReoptimizePlan={handleReoptimizePlan}
        onInsertOrderIntoPlan={handleInsertOrderIntoPlan}
      />

      {/* Assignment Success Notification Banner */}
      {assignmentSuccess && (
        <div className="flex items-center justify-between rounded-lg bg-emerald-50 border border-emerald-200 p-4 text-emerald-950">
          <div className="flex items-center gap-3">
            <CheckCircle size={18} className="text-emerald-700 shrink-0" />
            <div>
              <p className="text-sm font-semibold text-emerald-950">
                {t("Đã phân chia và bàn giao")}{" "}{assignmentSuccess.count}{" "}{t("đơn hàng cho")}{" "}{assignmentSuccess.driverCount}{" "}{t("tài xế thành công!")}
              </p>
              <p className="text-xs text-emerald-700 mt-0.5">
                {t("Các đơn hàng đã chuyển sang trạng thái")}{" "}<strong>PLANNED</strong>{" "}{t("và lộ trình giao đã được đồng bộ xuống ứng dụng Mobile của tài xế tương ứng.")}
              </p>
            </div>
          </div>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setAssignmentSuccess(null)}
            className="text-emerald-800 hover:bg-emerald-100/80 text-xs font-semibold"
          >
            {t("Đóng")}
          </Button>
        </div>
      )}

      {/* Metrics Banner (shows when optimization completes) */}
      {resultForMetrics && <AlgorithmMetricsBar result={resultForMetrics} roadMetrics={roadMetrics} />}

      {dispatchError && (
        <div className="flex items-center gap-2 rounded-lg bg-red-50 p-3 text-xs text-red-700">
          <AlertCircle size={16} />
          <span>{dispatchError}</span>
        </div>
      )}

      {selectionWarning && (
        <div className="flex items-start gap-2 rounded-lg bg-amber-50 p-3 text-xs leading-5 text-amber-800">
          <Warehouse size={16} className="mt-0.5 shrink-0" />
          <span>{selectionWarning}</span>
        </div>
      )}

      {/* Main Two-Column Dispatch Workspace */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12 min-h-[600px]">
        {/* Left Column: Order Selection & Algorithm Controls (4 cols) */}
        <div className="lg:col-span-4 h-[520px] lg:h-[660px]">
          <DispatchPanel
            orders={dispatchableOrders}
            selectedOrders={selectedOrders}
            drivers={drivers}
            selectedDriverIds={selectedDriverIds}
            onToggleDriver={toggleDriverSelection}
            onSelectAllDrivers={setSelectedDriverIds}
            activeVehicleIndex={activeVehicleIndex}
            onSelectVehicleRoute={setActiveVehicleIndex}
            onToggleOrder={(order) => {
              setSelectedPlanId(null);
              setLastAssignedRoute(null);
              toggleOrder(order);
            }}
            onSelectAll={handleSelectAll}
            onDeselectAll={handleDeselectAll}
            algorithm={algorithm}
            onAlgorithmChange={setAlgorithm}
            onOptimize={handleOptimize}
            isOptimizing={isOptimizing}
            result={result}
            depot={depot}
            isOrderSelectable={isOrderSelectable}
            onOpenAssignModal={() => setIsAssignModalOpen(true)}
          />
        </div>

        {/* Right Column: Interactive Map Visualization (8 cols) */}
        <div className="lg:col-span-8 h-[460px] lg:h-[660px] flex flex-col gap-2">
          {/* Live Simulation & Telemetry Toolbar */}
          <div className="grid gap-2 rounded-lg border border-gray-200 bg-white p-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
            <label className="flex min-w-0 items-center gap-3 text-sm font-medium text-gray-800">
              <UserRound size={16} className="shrink-0 text-gray-500" />
              <span className="shrink-0">{t("Theo dõi tài xế")}</span>
              <select className="min-h-11 min-w-0 flex-1 rounded-md border border-gray-300 bg-white px-3 text-sm" value={trackedDriverId}
              onChange={(event) => {
                const id = event.target.value;
                setTrackedDriverId(id);
                setLastAssignedRoute(null);
                const driver = drivers.find((item) => item.id === id);
                const driverOrders = driver
                  ? orders.filter((order) =>
                    (order.status === 'PLANNED' || order.status === 'IN_TRANSIT')
                    && (order.assigned_driver_id === driver.id || order.assigned_driver_id === driver.user_id))
                  : [];
                const activeOrderIds = new Set(driverOrders.map((order) => order.id));
                const driverPlans = plans.filter((plan) => plan.plan_status === 'DISPATCHED'
                  && (plan.driver_id === driver?.id || plan.driver_id === driver?.user_id));
                const activePlan = (activeOrderIds.size > 0
                  ? driverPlans.find((plan) => plan.ordered_stop_ids?.some((stopId) => activeOrderIds.has(stopId)))
                  : undefined) ?? driverPlans[0];
                setSelectedPlanId(activePlan?.plan_id ?? null);
                setSimulationOrigin(driver && typeof driver.current_lat === 'number' && typeof driver.current_lng === 'number' && (driver.current_lat !== 0 || driver.current_lng !== 0)
                  ? [driver.current_lat, driver.current_lng] : undefined);
              }}>
              <option value="">{t("Chọn tài xế")}</option>
              {drivers.map((driver) => <option key={driver.id} value={driver.id}>{driver.name || t("Chưa có họ tên")}</option>)}
              </select>
            </label>
            <div className="flex items-center gap-2 text-xs text-gray-600">
              <PackageCheck size={15} className="text-emerald-700" />
              <span><strong className="text-gray-900">{trackedDriverOrders.length}</strong>{" "}{t("đơn giao đang phụ trách")}</span>
              {returningOrderCount > 0 && <span>· {returningOrderCount}{" "}{t("đơn đang hoàn về kho")}</span>}
            </div>
          </div>
          {trackedDriver && (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-700">
              <div className="flex min-w-0 items-center gap-2">
                <span className={`h-2 w-2 shrink-0 rounded-full ${planTracking.signal === 'live' ? 'bg-emerald-500' : planTracking.signal === 'stale' ? 'bg-amber-500' : 'bg-slate-400'}`} />
                <span className="font-semibold text-slate-950">{trackedDriver.name || t("Tài xế chưa có tên")}</span>
                <span className="truncate">{trackedDriver.vehicle_plate || trackedDriver.vehicle_type || t("Chưa có thông tin xe")}</span>
                <span>· GPS {planTracking.signal === 'live' ? t("trực tiếp") : planTracking.signal === 'stale' ? t("đã cũ") : planTracking.signal === 'reconnecting' ? t("đang kết nối lại") : t("đang chờ")}</span>
              </div>
              {planTracking.lastUpdate && <span>{t("Cập nhật")}{" "}{planTracking.lastUpdate.toLocaleTimeString(intlLocale)}</span>}
            </div>
          )}
          <div className="flex flex-wrap items-center gap-3 text-sm">
            <Button variant="outline" disabled={isSimulating || !trackedDriverId}
              onClick={() => setIsPickingOrigin((current) => !current)}>
              {isPickingOrigin ? t("Bấm trên bản đồ để đặt vị trí") : t("Đặt điểm bắt đầu giả lập")}
            </Button>
            {simulationOrigin && <span className="flex items-center gap-1 text-xs text-gray-600"><MapPin size={13} />{" "}{t("Điểm giả lập")}{" "}{simulationOrigin[0].toFixed(5)}, {simulationOrigin[1].toFixed(5)}</span>}
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-gray-200 bg-white px-3.5 py-2.5">
            <div className="flex items-center gap-2.5 text-xs text-gray-700">
              <Navigation size={15} className="text-emerald-600 shrink-0" />
              <div>
                <span className="font-semibold text-gray-900">{t("Theo dõi & Giả lập lộ trình trực tiếp (SSE):")}{" "}</span>
                {activeDriverPosition ? (
                  <span className="text-emerald-700 font-medium">
                    TX ({activeDriverPosition[0].toFixed(4)}, {activeDriverPosition[1].toFixed(4)})
                    {activeStepProgress ? ` · Bước ${activeStepProgress.step}/${activeStepProgress.total}` : ''}
                  </span>
                ) : canSimulate ? (
                  <span className="text-gray-500">
                    {t("Sẵn sàng")}{" "}{routeWaypoints.length}{" "}{t("điểm tọa độ trên đường phố Goong")}
                  </span>
                ) : (
                  <span className="text-gray-500">
                    {t("Chọn kế hoạch đang giao (DISPATCHED) hoặc gán đơn để bật mô phỏng xe chạy")}
                  </span>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2">
              {isSimulating && (
                <Button size="sm" variant="outline" onClick={handleSimulateIncident}
                  disabled={(!incidentPlanId && !incidentOrder) || Boolean(driverIncident)}
                  isLoading={isReportingIncident} className="text-rose-700 border-rose-200"
                  title="Tạo sự cố thật trong hệ thống để thử quy trình xử lý">
                  <AlertCircle size={14} />{" "}{t("Giả lập sự cố")}
                </Button>
              )}
              {trackedDriverId && planTracking.isPaused && (
                <Button size="sm" onClick={async () => {
                  try {
                    await api.post(`/orders/dispatch/drivers/${trackedDriverId}/tracking/simulate/resume`);
                    toast.info('Tiếp tục mô phỏng di chuyển. Trạng thái lấy hàng thực tế vẫn do backend nghiệp vụ xác nhận.');
                  } catch (error) { toast.error(error instanceof Error ? error.message : t("Không thể tiếp tục")); }
                }}>{t("Đã tới kho · tiếp tục giả lập")}</Button>
              )}
              {isSimulating ? (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleStopSimulation}
                  isLoading={isStartingSim}
                  className="text-xs border-rose-200 text-rose-700 hover:bg-rose-50"
                >
                  <Square size={12} />{" "}{t("Dừng giả lập")}
                </Button>
              ) : (
                <Button
                  size="sm"
                  onClick={handleStartSimulation}
                  disabled={!canSimulate}
                  isLoading={isStartingSim}
                  className="text-xs"
                >
                  <Play size={13} />{" "}{t("Giả lập tài xế chạy")}
                </Button>
              )}
            </div>
          </div>

          {incidentError && <p role="status" className="text-xs text-amber-800">{incidentError}</p>}
          {driverIncident && <a href={`/operations?incident=${encodeURIComponent(driverIncident.id)}`}
            className="flex items-center gap-2 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800 underline underline-offset-2">
            <AlertCircle size={16} /> {driverIncident.count}{" "}{t("sự cố cần xử lý ·")}{" "}{driverIncident.label}
          </a>}
          <div className="flex-1 overflow-hidden rounded-lg border border-gray-200 bg-white">
            <RouteMap
              depot={depotForMap}
              stops={stopsForMap}
              driver={activeDriverPosition}
              driverIncident={driverIncident}
              driverName={trackedDriver?.name}
              driverVehicle={trackedDriver?.vehicle_plate || trackedDriver?.vehicle_type}
              trackingSignal={trackedDriver ? planTracking.signal : orderTracking.signal}
              routeOrigin={simulationOrigin}
              onPickOrigin={isPickingOrigin ? (position) => {
                setSimulationOrigin(position);
                setIsPickingOrigin(false);
              } : undefined}
              skipPickup={trackedDriverId
                ? trackedDriverOrders.some((order) => order.status === 'IN_TRANSIT')
                : latestAssignedOrder?.status === 'IN_TRANSIT'}
              onRouteMetricsChange={setRoadMetrics}
              onRouteCoordinatesChange={setRouteWaypoints}
            />
          </div>
        </div>
      </div>

      {/* Modal to assign driver */}
      <DriverAssignmentModal
        open={isAssignModalOpen}
        onOpenChange={setIsAssignModalOpen}
        orders={selectedOrders}
        result={result}
        drivers={drivers}
        selectedDriverIds={selectedDriverIds}
        onSelectedDriverIdsChange={setSelectedDriverIds}
        onReoptimizeForCount={optimize}
        isLoadingDrivers={isLoadingDrivers}
        onConfirm={handleConfirmAssignment}
      />
    </div>
  );
}
