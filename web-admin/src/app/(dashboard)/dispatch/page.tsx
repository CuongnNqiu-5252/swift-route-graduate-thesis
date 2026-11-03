'use client';

import { useState } from 'react';
import { useOrders } from '@/application/hooks/use-orders';
import { useDispatch } from '@/application/hooks/use-dispatch';
import { useDrivers } from '@/application/hooks/use-drivers';
import { DispatchPanel } from '@/components/dispatch/dispatch-panel';
import { RouteMap } from '@/components/dispatch/route-map';
import { AlgorithmMetricsBar } from '@/components/dispatch/algorithm-metrics-bar';
import { DriverAssignmentModal } from '@/components/dispatch/driver-assignment-modal';
import { WhatIfComparison } from '@/components/dispatch/what-if-comparison';
import { Button } from '@/components/ui/button';
import { RefreshCw, Sparkles, AlertCircle, CheckCircle } from 'lucide-react';
import { toast } from 'sonner';
import { routingRepository } from '@/infrastructure/repositories/routing.repository';

export default function DispatchPage() {
  const { orders, isLoading: isLoadingOrders, refetch: refetchOrders } = useOrders();
  const { drivers, isLoading: isLoadingDrivers } = useDrivers();
  const [isAssignModalOpen, setIsAssignModalOpen] = useState(false);
  const [assignmentSuccess, setAssignmentSuccess] = useState<{ count: number; driverCount: number } | null>(null);

  const {
    selectedOrders,
    algorithm,
    setAlgorithm,
    result,
    isOptimizing,
    error: dispatchError,
    toggleOrder,
    optimize,
    assignToDriver,
    depot,
  } = useDispatch();

  // Filter orders eligible for dispatch: PAID or PLANNED
  const dispatchableOrders = orders.filter(
    (o) => o.status === 'PAID' || o.status === 'PLANNED'
  );

  const handleSelectAll = () => {
    dispatchableOrders.forEach((o) => {
      if (!selectedOrders.some((so) => so.id === o.id)) {
        toggleOrder(o);
      }
    });
  };

  const handleDeselectAll = () => {
    selectedOrders.forEach((o) => toggleOrder(o));
  };

  const handleOptimize = async () => {
    try {
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
      await assignToDriver(driverInput);
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
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Lỗi khi gán đơn');
      throw err;
    }
  };

  // Convert result ordered stops or fall back to selected orders points
  const stopsForMap = result?.ordered_stops?.length
    ? result.ordered_stops
    : selectedOrders.map((o) => ({
        id: o.id,
        location: { lat: o.delivery_lat, lng: o.delivery_lng },
        order: {
          id: o.id,
          delivery_address: o.delivery_address,
          order_code: o.order_code,
        },
      }));

  const handleWhatIf = (baselineDrivers: number, scenarioDrivers: number) =>
    routingRepository.whatIf({
      depot,
      stops: selectedOrders.map((o) => ({
        id: o.id,
        location: { lat: o.delivery_lat, lng: o.delivery_lng },
        order: { id: o.id, delivery_address: o.delivery_address, order_code: o.order_code },
      })),
      algorithm,
      baseline_driver_count: baselineDrivers,
      scenario_driver_count: scenarioDrivers,
    });

  return (
    <div className="space-y-4">
      {/* Page Title Bar */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">
              Trung tâm Điều phối Tuyến đường
            </h1>
            <span className="inline-flex items-center gap-1 rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-semibold text-blue-800">
              <Sparkles size={12} /> Held-Karp DP
            </span>
          </div>
          <p className="text-sm text-gray-500">
            Tối ưu hoá hành trình giao hàng đa điểm dừng và gán lộ trình tự động cho tài xế
          </p>
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={() => refetchOrders()}
          isLoading={isLoadingOrders}
        >
          <RefreshCw size={14} /> Làm mới danh sách
        </Button>
      </div>

      {/* Assignment Success Notification Banner */}
      {assignmentSuccess && (
        <div className="flex items-center justify-between rounded-xl bg-emerald-50 border border-emerald-200 p-4 text-emerald-950 shadow-sm animate-in fade-in duration-300">
          <div className="flex items-center gap-3">
            <div className="rounded-full bg-emerald-100 p-2 text-emerald-700 shrink-0">
              <CheckCircle size={20} />
            </div>
            <div>
              <p className="text-sm font-bold text-emerald-950">
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
      {result && <AlgorithmMetricsBar result={result} />}

      {dispatchError && (
        <div className="flex items-center gap-2 rounded-lg bg-red-50 p-3 text-xs text-red-700">
          <AlertCircle size={16} />
          <span>{dispatchError}</span>
        </div>
      )}

      {/* Main Two-Column Dispatch Workspace */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12 min-h-[600px]">
        {/* Left Column: Order Selection & Algorithm Controls (5 cols) */}
        <div className="lg:col-span-5 h-[620px]">
          <DispatchPanel
            orders={dispatchableOrders}
            selectedOrders={selectedOrders}
            onToggleOrder={toggleOrder}
            onSelectAll={handleSelectAll}
            onDeselectAll={handleDeselectAll}
            algorithm={algorithm}
            onAlgorithmChange={setAlgorithm}
            onOptimize={handleOptimize}
            isOptimizing={isOptimizing}
            result={result}
            onOpenAssignModal={() => setIsAssignModalOpen(true)}
          />
        </div>

        {/* Right Column: Interactive Map Visualization (7 cols) */}
        <div className="lg:col-span-7 h-[620px] flex flex-col">
          <div className="flex-1 overflow-hidden rounded-lg border border-gray-200 bg-white">
            <RouteMap
              depot={depot}
              stops={stopsForMap}
              routePolyline={result?.route_polyline}
            />
          </div>
        </div>
      </div>

      <WhatIfComparison
        stops={selectedOrders.map((o) => ({
          id: o.id,
          location: { lat: o.delivery_lat, lng: o.delivery_lng },
          order: { id: o.id, delivery_address: o.delivery_address, order_code: o.order_code },
        }))}
        onRun={handleWhatIf}
      />

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
