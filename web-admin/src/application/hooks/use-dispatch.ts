'use client';

import { useState, useCallback } from 'react';
import { Order } from '@/domain/entities/order.entity';
import { OptimizationResult } from '@/domain/entities/routing.entity';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { routingRepository } from '@/infrastructure/repositories/routing.repository';

// Default depot: SwiftRoute Hub — Ninh Kieu, Can Tho
const DEFAULT_DEPOT = { lat: 10.0371, lng: 105.7828 };

export function useDispatch() {
  const [selectedOrders, setSelectedOrders] = useState<Order[]>([]);
  const [algorithm, setAlgorithm] = useState<'auto' | 'ortools'>('auto');
  const [result, setResult] = useState<OptimizationResult | null>(null);
  const [isOptimizing, setIsOptimizing] = useState(false);
  const [isAssigning, setIsAssigning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggleOrder = useCallback((order: Order) => {
    setSelectedOrders((prev) =>
      prev.find((o) => o.id === order.id)
        ? prev.filter((o) => o.id !== order.id)
        : [...prev, order]
    );
  }, []);

  const optimize = useCallback(async () => {
    if (selectedOrders.length === 0) return;
    setIsOptimizing(true);
    setError(null);
    console.group('🚀 [SwiftRoute Dispatch] BẮT ĐẦU TỐI ƯU HÓA TUYẾN ĐƯỜNG');
    console.log('📍 [Bước 1/4] Điểm xuất phát (Depot):', DEFAULT_DEPOT);
    console.log(`📦 [Bước 2/4] Số đơn hàng được chọn: ${selectedOrders.length} đơn`, selectedOrders.map(o => ({
      code: o.order_code,
      address: o.delivery_address,
      lat: o.delivery_lat,
      lng: o.delivery_lng,
    })));
    console.log(`🧠 [Bước 3/4] Thuật toán được cấu hình: ${algorithm.toUpperCase()}`);
    console.time('⏱️ Thời gian giải bài toán');

    try {
      const stops = selectedOrders.map((o) => ({
        id: o.id,
        location: { lat: o.delivery_lat, lng: o.delivery_lng },
        order: {
          id: o.id,
          delivery_address: o.delivery_address,
          order_code: o.order_code,
        },
      }));

      console.log('📡 Đang gửi request tới Routing Engine (/api/routing/optimize/batch)...');
      const res = await routingRepository.optimize({
        depot: DEFAULT_DEPOT,
        stops,
        algorithm,
      });
      console.timeEnd('⏱️ Thời gian giải bài toán');

      console.log('✅ [Bước 4/4] Nhận kết quả tối ưu từ Routing Engine:');
      console.log(`   - Thuật toán thực tế sử dụng: ${res.algorithm_used}`);
      console.log(`   - Tổng quãng đường tối ưu: ${res.total_distance_km} km`);
      console.log(`   - Tổng thời gian ước tính: ${res.total_duration_min} phút`);
      console.log(`   - Thời gian tính toán CPU: ${res.execution_time_ms} ms`);
      console.log('📋 Thứ tự giao hàng tối ưu (Optimal Sequence):', res.ordered_stops?.map((s, idx) => ({
        thứ_tự: idx + 1,
        mã_đơn: s.order?.order_code || s.id.substring(0, 8),
        địa_chỉ: s.order?.delivery_address,
      })));

      setResult(res);
    } catch (err) {
      console.error('❌ Lỗi khi tối ưu hóa tuyến đường:', err);
      setError(err instanceof Error ? err.message : 'Lỗi tối ưu hoá tuyến đường');
    } finally {
      console.groupEnd();
      setIsOptimizing(false);
    }
  }, [selectedOrders, algorithm]);

  const assignToDriver = useCallback(
    async (driverInput: string | string[]) => {
      if (!result || selectedOrders.length === 0) return;
      setIsAssigning(true);
      console.group('🚛 [SwiftRoute Dispatch] GÁN ĐƠN HÀNG CHO TÀI XẾ');
      try {
        const driversList = Array.isArray(driverInput) ? driverInput : [driverInput];
        if (driversList.length === 0) return;

        // Use optimal visitation sequence from TSP result, or fall back to selectedOrders
        const stopIds = (result.ordered_stops && result.ordered_stops.length > 0)
          ? result.ordered_stops.map((s) => s.id)
          : selectedOrders.map((o) => o.id);

        const chunkSize = Math.ceil(stopIds.length / driversList.length);
        const assignments: Array<{ order_id: string; driver_id: string; plan_id?: string | null }> = [];

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

        console.log(`📋 [Bước 1/2] Tạo danh sách ${assignments.length} đơn được gán cho ${driversList.length} tài xế:`, assignments);
        console.log('📡 [Bước 2/2] Gửi request cập nhật trạng thái đơn (POST /api/orders/batch-assign)...');

        await orderRepository.assignBatch({ assignments });
        console.log('✅ Gán đơn hàng thành công! Các đơn chuyển sang trạng thái PLANNED.');
        setSelectedOrders([]);
        setResult(null);
        return assignments;
      } catch (err) {
        console.error('❌ Lỗi khi gán đơn hàng cho tài xế:', err);
        throw err;
      } finally {
        console.groupEnd();
        setIsAssigning(false);
      }
    },
    [result, selectedOrders]
  );

  return {
    selectedOrders,
    algorithm,
    setAlgorithm,
    result,
    isOptimizing,
    isAssigning,
    error,
    toggleOrder,
    optimize,
    assignToDriver,
    depot: DEFAULT_DEPOT,
  };
}

