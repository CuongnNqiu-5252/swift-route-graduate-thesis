'use client';

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
import { AlertCircle, CheckCircle, Navigation, Play, RefreshCw, Square, Warehouse } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';

export default function DispatchPage() {
  const { orders, isLoading: isLoadingOrders, refetch: refetchOrders } = useOrders();
  const { drivers, isLoading: isLoadingDrivers } = useDrivers();
  const [pickupLocations, setPickupLocations] = useState<PickupLocation[]>([]);
  const [plans, setPlans] = useState<DispatchPlanRecord[]>([]);
  const [isLoadingPlans, setIsLoadingPlans] = useState(false);
  const [isTriggeringPlan, setIsTriggeringPlan] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState<DispatchPlanRecord | null>(null);
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

  const loadPlans = useCallback(async () => {
    setIsLoadingPlans(true);
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
    loadPlans();
    return () => { active = false; };
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

  const latestAssignedOrder = useMemo(() => orders
    .filter((order) => (order.status === 'PLANNED' || order.status === 'IN_TRANSIT') && Boolean(order.assigned_driver_id))
    .sort((left, right) => Date.parse(right.updated_at) - Date.parse(left.updated_at))[0], [orders]);

  const activeTrackedOrderId = useMemo(() => {
    if (selectedPlan) return undefined;
    if (lastAssignedRoute?.stops?.[0]?.id) return lastAssignedRoute.stops[0].id;
    return latestAssignedOrder?.id;
  }, [lastAssignedRoute, latestAssignedOrder, selectedPlan]);

  const planTracking = usePlanLiveTracking(
    selectedPlan?.plan_status === 'DISPATCHED' ? selectedPlan.plan_id : undefined,
    selectedPlan?.driver_id,
  );
  const orderTracking = useOrderLiveTracking(
    activeTrackedOrderId,
    latestAssignedOrder?.assigned_driver_id,
  );

  const activeDriverPosition = selectedPlan?.plan_status === 'DISPATCHED'
    ? planTracking.position
    : orderTracking.position ?? planTracking.position;
  const activeStepProgress = selectedPlan?.plan_status === 'DISPATCHED'
    ? planTracking.stepProgress
    : orderTracking.stepProgress;
  const isSimulating = selectedPlan?.plan_status === 'DISPATCHED'
    ? planTracking.isSimulating
    : orderTracking.isSimulating;

  const handleSelectAll = () => {
    setSelectedPlan(null);
    setLastAssignedRoute(null);
    selectAll(dispatchableOrders);
  };

  const handleDeselectAll = () => {
    setSelectedPlan(null);
    setLastAssignedRoute(null);
    clearSelection();
  };

  const handleOptimize = async () => {
    try {
      setSelectedPlan(null);
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
      setSelectedPlan(updated);
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
        setSelectedPlan(null);
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
        setSelectedPlan(updated);
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
        setSelectedPlan(updated);
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
      if (selectedPlan?.plan_status === 'DISPATCHED') {
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
      if (selectedPlan?.plan_status === 'DISPATCHED') {
        await orderRepository.stopPlanSimulation(selectedPlan.plan_id);
      } else if (activeTrackedOrderId) {
        await orderRepository.stopOrderSimulation(activeTrackedOrderId);
      }
      toast.info('Đã dừng giả lập di chuyển.');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không thể dừng giả lập');
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
                delivery_address: wp.location?.address || matchedOrder?.delivery_address || 'Điểm giao hàng',
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
  }, [orders, selectedPlan]);

  const latestAssignedStops = useMemo<OptimizationStop[]>(
    () =>
      latestAssignedOrder
        ? [
            {
              id: latestAssignedOrder.id,
              location: { lat: latestAssignedOrder.delivery_lat, lng: latestAssignedOrder.delivery_lng },
              order: {
                id: latestAssignedOrder.id,
                delivery_address: latestAssignedOrder.delivery_address,
                order_code: latestAssignedOrder.order_code,
              },
            },
          ]
        : [],
    [latestAssignedOrder],
  );

  const latestAssignedDepot = useMemo<DispatchDepot | null>(
    () =>
      latestAssignedOrder
        ? {
            lat: latestAssignedOrder.pickup_lat,
            lng: latestAssignedOrder.pickup_lng,
            name: latestAssignedOrder.pickup_location_name || 'Điểm lấy hàng',
            address: latestAssignedOrder.pickup_address,
            pickup_location_id: latestAssignedOrder.pickup_location_id,
          }
        : null,
    [latestAssignedOrder],
  );

  const stopsForMap = useMemo<OptimizationStop[]>(() => {
    if (planStopsForMap?.length) return planStopsForMap;
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
    return lastAssignedRoute?.stops ?? latestAssignedStops;
  }, [lastAssignedRoute?.stops, latestAssignedStops, planStopsForMap, result, selectedOrders]);

  const depotForMap = useMemo<DispatchDepot | null>(() => {
    if (selectedOrders.length || selectedPlan) return depot;
    return lastAssignedRoute?.depot ?? latestAssignedDepot ?? depot;
  }, [depot, lastAssignedRoute?.depot, latestAssignedDepot, selectedOrders.length, selectedPlan]);
  const resultForMetrics = result ?? lastAssignedRoute?.result ?? null;
  const canSimulate = Boolean(
    routeWaypoints.length >= 2 &&
    ((selectedPlan && selectedPlan.plan_status === 'DISPATCHED') || activeTrackedOrderId),
  );

  return (
    <div className="space-y-4">
      {/* Page Title Bar */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">
              Điều phối & bản đồ
            </h1>
          </div>
          <p className="text-sm text-gray-500">
            Quản lý kế hoạch tự động từ OR-Tools hoặc chọn đơn thủ công để tính tuyến và phân công tài xế.
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
          <RefreshCw size={14} /> Làm mới dữ liệu
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
          setSelectedPlan((prev) => (prev?.plan_id === plan.plan_id ? null : plan));
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
                Đã phân chia và bàn giao {assignmentSuccess.count} đơn hàng cho {assignmentSuccess.driverCount} tài xế thành công!
              </p>
              <p className="text-xs text-emerald-700 mt-0.5">
                Các đơn hàng đã chuyển sang trạng thái <strong>PLANNED</strong> và lộ trình giao đã được đồng bộ xuống ứng dụng Mobile của tài xế tương ứng.
              </p>
            </div>
          </div>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setAssignmentSuccess(null)}
            className="text-emerald-800 hover:bg-emerald-100/80 text-xs font-semibold"
          >
            Đóng
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
            onToggleOrder={(order) => {
              setSelectedPlan(null);
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
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-gray-200 bg-white px-3.5 py-2.5">
            <div className="flex items-center gap-2.5 text-xs text-gray-700">
              <Navigation size={15} className="text-emerald-600 shrink-0" />
              <div>
                <span className="font-semibold text-gray-900">Theo dõi & Giả lập lộ trình trực tiếp (SSE): </span>
                {activeDriverPosition ? (
                  <span className="text-emerald-700 font-medium">
                    TX ({activeDriverPosition[0].toFixed(4)}, {activeDriverPosition[1].toFixed(4)})
                    {activeStepProgress ? ` · Bước ${activeStepProgress.step}/${activeStepProgress.total}` : ''}
                  </span>
                ) : canSimulate ? (
                  <span className="text-gray-500">
                    Sẵn sàng {routeWaypoints.length} điểm tọa độ trên đường phố Goong
                  </span>
                ) : (
                  <span className="text-gray-500">
                    Chọn kế hoạch đang giao (DISPATCHED) hoặc gán đơn để bật mô phỏng xe chạy
                  </span>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2">
              {isSimulating ? (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleStopSimulation}
                  isLoading={isStartingSim}
                  className="text-xs border-rose-200 text-rose-700 hover:bg-rose-50"
                >
                  <Square size={12} /> Dừng giả lập
                </Button>
              ) : (
                <Button
                  size="sm"
                  onClick={handleStartSimulation}
                  disabled={!canSimulate}
                  isLoading={isStartingSim}
                  className="text-xs"
                >
                  <Play size={13} /> Giả lập tài xế chạy
                </Button>
              )}
            </div>
          </div>

          <div className="flex-1 overflow-hidden rounded-lg border border-gray-200 bg-white">
            <RouteMap
              depot={depotForMap}
              stops={stopsForMap}
              driver={activeDriverPosition}
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
        isLoadingDrivers={isLoadingDrivers}
        onConfirm={handleConfirmAssignment}
      />
    </div>
  );
}
