'use client';

import { useState, useCallback, useMemo } from 'react';
import { Order } from '@/domain/entities/order.entity';
import { DispatchDepot, OptimizationResult } from '@/domain/entities/routing.entity';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { routingRepository } from '@/infrastructure/repositories/routing.repository';

interface UseDispatchOptions {
  fallbackDepot?: DispatchDepot | null;
}

function pickupKey(order: Order) {
  if (order.pickup_location_id) return order.pickup_location_id;
  return `${order.pickup_lat.toFixed(5)}:${order.pickup_lng.toFixed(5)}:${order.pickup_address.trim().toLocaleLowerCase('vi-VN')}`;
}

function depotFromOrder(order: Order): DispatchDepot {
  return {
    lat: order.pickup_lat,
    lng: order.pickup_lng,
    name: order.pickup_location_name || 'Điểm lấy hàng',
    address: order.pickup_address,
    pickup_location_id: order.pickup_location_id,
  };
}

export function useDispatch({ fallbackDepot = null }: UseDispatchOptions = {}) {
  const [selectedOrders, setSelectedOrders] = useState<Order[]>([]);
  const [selectedDriverIds, setSelectedDriverIds] = useState<string[]>([]);
  const [activeVehicleIndex, setActiveVehicleIndex] = useState<number | null>(null);
  const [algorithm, setAlgorithm] = useState<'auto' | 'ortools'>('auto');
  const [result, setResult] = useState<OptimizationResult | null>(null);
  const [isOptimizing, setIsOptimizing] = useState(false);
  const [isAssigning, setIsAssigning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectionWarning, setSelectionWarning] = useState<string | null>(null);

  const depot = useMemo(
    () => selectedOrders[0] ? depotFromOrder(selectedOrders[0]) : fallbackDepot,
    [fallbackDepot, selectedOrders]
  );

  const toggleDriverSelection = useCallback((driverUserId: string) => {
    setSelectedDriverIds((prev) =>
      prev.includes(driverUserId)
        ? prev.filter((id) => id !== driverUserId)
        : [...prev, driverUserId]
    );
    setResult(null);
    setActiveVehicleIndex(null);
  }, []);

  const toggleOrder = useCallback((order: Order) => {
    const alreadySelected = selectedOrders.some((selected) => selected.id === order.id);
    if (alreadySelected) {
      setSelectedOrders(selectedOrders.filter((selected) => selected.id !== order.id));
      setResult(null);
      setError(null);
      setSelectionWarning(null);
      return;
    }

    const firstOrder = selectedOrders[0];
    if (firstOrder && pickupKey(firstOrder) !== pickupKey(order)) {
      setSelectionWarning(`Đơn ${order.order_code} xuất phát từ kho khác. Hãy tối ưu từng kho trong một lượt riêng.`);
      return;
    }

    setSelectedOrders([...selectedOrders, order]);
    setResult(null);
    setError(null);
    setSelectionWarning(null);
  }, [selectedOrders]);

  const selectAll = useCallback((orders: Order[]) => {
    if (orders.length === 0) return;
    const reference = selectedOrders[0] || orders[0];
    const compatible = orders.filter((order) => pickupKey(order) === pickupKey(reference));
    setSelectedOrders(compatible);
    setResult(null);
    setError(null);
    setSelectionWarning(
      compatible.length < orders.length
        ? `Đã chọn ${compatible.length} đơn cùng kho “${reference.pickup_location_name || reference.pickup_address}”. Các kho khác cần được tối ưu riêng.`
        : null
    );
  }, [selectedOrders]);

  const clearSelection = useCallback(() => {
    setSelectedOrders([]);
    setResult(null);
    setError(null);
    setSelectionWarning(null);
  }, []);

  const isOrderSelectable = useCallback((order: Order) => {
    if (selectedOrders.some((selected) => selected.id === order.id)) return true;
    return selectedOrders.length === 0 || pickupKey(selectedOrders[0]) === pickupKey(order);
  }, [selectedOrders]);

  const optimize = useCallback(async (overrideVehicleCount?: number) => {
    if (selectedOrders.length === 0 || !depot) return null;
    setIsOptimizing(true);
    setError(null);
    const vehicleCount = Math.min(
      selectedOrders.length,
      Math.max(1, overrideVehicleCount ?? selectedDriverIds.length)
    );
    console.group('🚀 [SwiftRoute Dispatch] BẮT ĐẦU TỐI ƯU HÓA TUYẾN ĐƯỜNG');
    console.log('📍 [Bước 1/4] Điểm xuất phát:', depot);
    console.log(`📦 [Bước 2/4] Số đơn hàng: ${selectedOrders.length} đơn | Số tài xế: ${vehicleCount}`);
    console.log(`🧠 [Bước 3/4] Thuật toán được cấu hình: ${algorithm.toUpperCase()}`);
    console.time('⏱️ Thời gian giải bài toán');

    try {
      const stops = selectedOrders.map((o) => ({
        id: o.id,
        location: { lat: o.delivery_lat, lng: o.delivery_lng },
        demand_kg: o.weight_kg || 1.0,
        order: {
          id: o.id,
          delivery_address: o.delivery_address,
          order_code: o.order_code,
        },
      }));

      console.log('📡 Đang gửi request tới Routing Engine (/api/v1/optimize/batch)...');
      const res = await routingRepository.optimize({
        depot: { lat: depot.lat, lng: depot.lng },
        stops,
        algorithm,
        vehicle_count: vehicleCount,
      });
      console.timeEnd('⏱️ Thời gian giải bài toán');

      console.log('✅ [Bước 4/4] Nhận kết quả tối ưu từ Routing Engine:');
      console.log(`   - Thuật toán thực tế sử dụng: ${res.algorithm_used}`);
      console.log(`   - Tổng quãng đường tối ưu: ${res.total_distance_km} km`);
      console.log(`   - Số tuyến tài xế (CVRP routes): ${res.vehicle_routes?.length || 1}`);

      setResult(res);
      setActiveVehicleIndex(null);
      return res;
    } catch (err) {
      console.error('❌ Lỗi khi tối ưu hóa tuyến đường:', err);
      setError(err instanceof Error ? err.message : 'Lỗi tối ưu hoá tuyến đường');
      return null;
    } finally {
      console.groupEnd();
      setIsOptimizing(false);
    }
  }, [selectedOrders, selectedDriverIds.length, algorithm, depot]);

  const assignToDriver = useCallback(
    async (driverInput: string | string[]) => {
      if (!result || selectedOrders.length === 0) return;
      setIsAssigning(true);
      console.group('🚛 [SwiftRoute Dispatch] GÁN ĐƠN HÀNG CHO TÀI XẾ (CVRP)');
      try {
        const driversList = Array.isArray(driverInput) ? driverInput : [driverInput];
        if (driversList.length === 0) return;

        let activeResult = result;
        const targetVehicles = Math.min(driversList.length, selectedOrders.length);
        const currentRouteCount = activeResult.vehicle_routes?.length || 0;

        // If user changed the number of drivers in modal, re-solve CVRP for that exact fleet count
        if (targetVehicles > 1 && currentRouteCount !== targetVehicles && depot) {
          const stops = selectedOrders.map((o) => ({
            id: o.id,
            location: { lat: o.delivery_lat, lng: o.delivery_lng },
            demand_kg: o.weight_kg || 1.0,
            order: {
              id: o.id,
              delivery_address: o.delivery_address,
              order_code: o.order_code,
            },
          }));
          activeResult = await routingRepository.optimize({
            depot: { lat: depot.lat, lng: depot.lng },
            stops,
            algorithm,
            vehicle_count: targetVehicles,
          });
          setResult(activeResult);
        }

        const assignments: Array<{ order_id: string; driver_id: string; plan_id?: string | null }> = [];
        const vehicleRoutes = activeResult.vehicle_routes && activeResult.vehicle_routes.length > 0
          ? activeResult.vehicle_routes
          : null;

        if (vehicleRoutes && vehicleRoutes.length > 0) {
          vehicleRoutes.forEach((vRoute, idx) => {
            const assignedDriverId = driversList[Math.min(idx, driversList.length - 1)];
            if (!assignedDriverId) return;
            vRoute.ordered_stops.forEach((stop) => {
              assignments.push({
                order_id: stop.id,
                driver_id: assignedDriverId,
                plan_id: null,
              });
            });
          });
        } else {
          const stopIds = (activeResult.ordered_stops && activeResult.ordered_stops.length > 0)
            ? activeResult.ordered_stops.map((s) => s.id)
            : selectedOrders.map((o) => o.id);
          const chunkSize = Math.ceil(stopIds.length / driversList.length);
          driversList.forEach((dId, idx) => {
            const start = idx * chunkSize;
            const end = Math.min(start + chunkSize, stopIds.length);
            for (let i = start; i < end; i++) {
              assignments.push({
                order_id: stopIds[i],
                driver_id: dId,
                plan_id: null,
              });
            }
          });
        }

        console.log(`📋 [Bước 1/2] Tạo danh sách ${assignments.length} đơn theo lộ trình CVRP cho ${driversList.length} tài xế:`, assignments);
        console.log('📡 [Bước 2/2] Gửi request cập nhật trạng thái đơn (POST /api/orders/assign-batch)...');

        await orderRepository.assignBatch({ assignments });
        console.log('✅ Gán đơn hàng thành công! Các đơn chuyển sang trạng thái PLANNED.');
        setSelectedOrders([]);
        setSelectedDriverIds([]);
        setResult(null);
        setActiveVehicleIndex(null);
        return assignments;
      } catch (err) {
        console.error('❌ Lỗi khi gán đơn hàng cho tài xế:', err);
        throw err;
      } finally {
        console.groupEnd();
        setIsAssigning(false);
      }
    },
    [result, selectedOrders, depot, algorithm]
  );

  return {
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
    isAssigning,
    error,
    selectionWarning,
    toggleOrder,
    selectAll,
    clearSelection,
    isOrderSelectable,
    optimize,
    assignToDriver,
    depot,
  };
}

