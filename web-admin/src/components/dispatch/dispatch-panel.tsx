'use client';

import { Driver } from '@/domain/entities/driver.entity';
import { Order } from '@/domain/entities/order.entity';
import { DispatchDepot, OptimizationResult } from '@/domain/entities/routing.entity';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Sparkles, Send, CheckSquare, Square, MapPin, Clock, Warehouse, LockKeyhole, Users, Route, Check } from 'lucide-react';

interface DispatchPanelProps {
  orders: Order[];
  selectedOrders: Order[];
  drivers?: Driver[];
  selectedDriverIds?: string[];
  onToggleDriver?: (driverUserId: string) => void;
  onSelectAllDrivers?: (driverUserIds: string[]) => void;
  activeVehicleIndex?: number | null;
  onSelectVehicleRoute?: (vehicleIndex: number | null) => void;
  depot: DispatchDepot | null;
  isOrderSelectable: (order: Order) => boolean;
  onToggleOrder: (order: Order) => void;
  onSelectAll: () => void;
  onDeselectAll: () => void;
  algorithm?: 'auto' | 'ortools';
  onAlgorithmChange?: (algo: 'auto' | 'ortools') => void;
  onOptimize: () => void;
  isOptimizing: boolean;
  result: OptimizationResult | null;
  onOpenAssignModal: () => void;
}

export function DispatchPanel({
  orders,
  selectedOrders,
  drivers = [],
  selectedDriverIds = [],
  onToggleDriver,
  onSelectAllDrivers,
  activeVehicleIndex = null,
  onSelectVehicleRoute,
  depot,
  isOrderSelectable,
  onToggleOrder,
  onSelectAll,
  onDeselectAll,
  onOptimize,
  isOptimizing,
  result,
  onOpenAssignModal,
}: DispatchPanelProps) {
  const compatibleOrders = orders.filter(isOrderSelectable);
  const isAllSelected = compatibleOrders.length > 0
    && compatibleOrders.every((order) => selectedOrders.some((selected) => selected.id === order.id));

  const availableDrivers = drivers.filter(
    (d) => d.status === 'AVAILABLE' || (d.status as string) === 'ACTIVE' || !d.status
  );
  const isAllDriversSelected = availableDrivers.length > 0
    && availableDrivers.every((d) => selectedDriverIds.includes(d.user_id));

  const vehicleRoutes = result?.vehicle_routes ?? [];
  const effectiveFleetCount = Math.max(1, selectedDriverIds.length);

  return (
    <Card className="flex h-full flex-col border border-gray-200">
      <CardHeader className="border-b border-gray-100 pb-3">
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="text-base font-bold text-gray-900">Đơn chờ điều phối</CardTitle>
            <p className="mt-0.5 text-xs text-gray-600">Chọn các đơn cùng kho và đội xe để giải bài toán CVRP.</p>
          </div>
          <Badge className="shrink-0 bg-blue-100 text-blue-900 tabular-nums">{selectedOrders.length}/{orders.length} đơn</Badge>
        </div>

        {depot && (
          <div className="mt-3 rounded-lg border border-blue-100 bg-blue-50/70 p-2.5">
            <div className="flex items-start gap-2">
              <Warehouse size={15} className="mt-0.5 shrink-0 text-blue-800" />
              <div className="min-w-0">
                <p className="truncate text-xs font-semibold text-blue-950">{depot.name}</p>
                <p className="mt-0.5 line-clamp-2 text-[11px] leading-4 text-blue-800">{depot.address}</p>
              </div>
            </div>
          </div>
        )}

        <div className="mt-3 flex items-center justify-between pt-1 text-xs">
          <button type="button" onClick={isAllSelected ? onDeselectAll : onSelectAll} className="flex items-center gap-1.5 font-medium text-blue-700 hover:text-blue-800">
            {isAllSelected
              ? <><CheckSquare size={14} /> Bỏ chọn tất cả</>
              : <><Square size={14} /> Chọn đơn cùng kho ({compatibleOrders.length})</>}
          </button>
          {selectedOrders.length > 0 && (
            <span className="font-medium text-gray-600 tabular-nums">Tổng tải: <strong className="text-gray-900">{selectedOrders.reduce((sum, order) => sum + (order.weight_kg || 0), 0).toFixed(1)} kg</strong></span>
          )}
        </div>
      </CardHeader>

      <CardContent className="flex-1 space-y-2 overflow-y-auto p-3">
        {orders.length === 0 ? (
          <div className="flex h-40 flex-col items-center justify-center text-center text-xs text-gray-600">
            <Clock size={28} className="mb-2 text-gray-400" />
            <p className="font-medium text-gray-700">Không có đơn sẵn sàng điều phối</p>
            <p className="mt-1 text-gray-600">Đơn mới hợp lệ sẽ tự động xuất hiện tại đây khi sẵn sàng điều phối.</p>
          </div>
        ) : orders.map((order) => {
          const isSelected = selectedOrders.some((selected) => selected.id === order.id);
          const canSelect = isOrderSelectable(order);
          const pickupLabel = order.pickup_location_name || order.pickup_address;
          return (
            <label key={order.id} className={`group flex items-start gap-3 rounded-lg border p-3 transition-colors ${isSelected ? 'border-blue-600 bg-blue-50/50' : canSelect ? 'cursor-pointer border-gray-200 bg-white hover:border-gray-300' : 'cursor-not-allowed border-gray-100 bg-gray-50 opacity-60'}`}>
              <input type="checkbox" checked={isSelected} disabled={!canSelect} onChange={() => onToggleOrder(order)} aria-label={`Chọn đơn ${order.order_code}`} className="mt-0.5 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500 disabled:cursor-not-allowed" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-xs font-bold text-gray-900">{order.order_code}</span>
                  <span className="text-[11px] font-medium text-gray-600 tabular-nums">{order.weight_kg} kg</span>
                </div>
                <p className="mt-1 flex items-start gap-1 text-xs text-gray-700"><MapPin size={12} className="mt-0.5 shrink-0 text-red-600" /><span className="line-clamp-2">{order.delivery_address}</span></p>
                <div className="mt-1.5 flex items-start gap-1 text-[11px] text-gray-600">
                  {canSelect ? <Warehouse size={11} className="mt-0.5 shrink-0" /> : <LockKeyhole size={11} className="mt-0.5 shrink-0 text-amber-700" />}
                  <span className="line-clamp-1">{canSelect ? pickupLabel : `Kho khác: ${pickupLabel}`}</span>
                </div>
              </div>
            </label>
          );
        })}
      </CardContent>

      <div className="space-y-3 border-t border-gray-100 bg-gray-50/60 p-3.5">
        {/* Multi-Driver Fleet Selection before Optimization */}
        {availableDrivers.length > 0 && onToggleDriver && (
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-xs">
              <span className="flex items-center gap-1.5 font-semibold text-gray-800">
                <Users size={13} className="text-blue-700" />
                Đội xe tham gia ({selectedDriverIds.length || 1}/{availableDrivers.length} tài xế)
              </span>
              {availableDrivers.length > 1 && onSelectAllDrivers && (
                <button
                  type="button"
                  onClick={() =>
                    onSelectAllDrivers(
                      isAllDriversSelected ? [] : availableDrivers.map((d) => d.user_id)
                    )
                  }
                  className="font-semibold text-blue-700 hover:text-blue-900 hover:underline"
                >
                  {isAllDriversSelected ? 'Đặt lại 1 xe' : `Chọn cả đội (${availableDrivers.length})`}
                </button>
              )}
            </div>
            <div className="flex flex-wrap gap-1.5 max-h-20 overflow-y-auto py-0.5">
              {availableDrivers.map((driver) => {
                const isPicked = selectedDriverIds.includes(driver.user_id);
                return (
                  <button
                    key={driver.id}
                    type="button"
                    onClick={() => onToggleDriver(driver.user_id)}
                    className={`inline-flex items-center gap-1 rounded-md border px-2 py-1 text-[11px] font-medium transition-colors ${
                      isPicked
                        ? 'border-blue-600 bg-blue-600 text-white'
                        : 'border-gray-300 bg-white text-gray-800 hover:border-blue-400'
                    }`}
                  >
                    {isPicked && <Check size={11} strokeWidth={2.5} />}
                    <span className="truncate max-w-[110px]">{driver.name}</span>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* Per-Vehicle Route Breakdown when CVRP returns multiple routes */}
        {vehicleRoutes.length > 1 && (
          <div className="space-y-1.5 rounded-lg border border-blue-200 bg-white p-2.5">
            <div className="flex items-center justify-between text-[11px]">
              <span className="flex items-center gap-1 font-semibold text-blue-950">
                <Route size={12} className="text-blue-700" />
                Kết quả chia tuyến CVRP ({vehicleRoutes.length} xe)
              </span>
              {onSelectVehicleRoute && (
                <button
                  type="button"
                  onClick={() => onSelectVehicleRoute(null)}
                  className={`font-semibold ${activeVehicleIndex === null ? 'text-blue-800 underline' : 'text-gray-600 hover:text-blue-800'}`}
                >
                  Xem toàn đội
                </button>
              )}
            </div>
            <div className="space-y-1 max-h-28 overflow-y-auto">
              {vehicleRoutes.map((vr, idx) => {
                const driverId = selectedDriverIds[idx];
                const matchedDriver = availableDrivers.find((d) => d.user_id === driverId);
                const isFocused = activeVehicleIndex === idx;
                return (
                  <button
                    key={vr.vehicle_index}
                    type="button"
                    onClick={() => onSelectVehicleRoute?.(isFocused ? null : idx)}
                    className={`flex w-full items-center justify-between rounded border px-2 py-1.5 text-left text-[11px] transition-colors ${
                      isFocused
                        ? 'border-blue-600 bg-blue-50 text-blue-950'
                        : 'border-gray-100 bg-gray-50/70 text-gray-800 hover:border-gray-300'
                    }`}
                  >
                    <span className="font-semibold truncate max-w-[130px]">
                      Tuyến #{idx + 1}: {matchedDriver?.name || `Tài xế ${idx + 1}`}
                    </span>
                    <span className="shrink-0 font-medium text-gray-700 tabular-nums">
                      {vr.ordered_stops.length} đơn · {vr.total_distance_km.toFixed(1)} km · ~{Math.ceil(vr.total_duration_min)}p
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        <Button onClick={onOptimize} disabled={selectedOrders.length === 0 || isOptimizing} isLoading={isOptimizing} className="w-full bg-blue-600 text-sm font-semibold hover:bg-blue-700">
          <Sparkles size={16} />
          {isOptimizing
            ? 'Đang giải bài toán định tuyến...'
            : effectiveFleetCount > 1
              ? `Tối ưu CVRP (${selectedOrders.length} đơn • ${Math.min(effectiveFleetCount, selectedOrders.length)} xe)`
              : `Tối ưu lộ trình (${selectedOrders.length} điểm)`}
        </Button>
        {result && (
          <Button onClick={onOpenAssignModal} variant="default" className="w-full bg-emerald-600 text-sm font-semibold hover:bg-emerald-700">
            <Send size={16} />
            {vehicleRoutes.length > 1
              ? `Phê duyệt & giao ${vehicleRoutes.length} tuyến (${selectedOrders.length} đơn)`
              : `Phê duyệt và giao tài xế (${selectedOrders.length} đơn)`}
          </Button>
        )}
      </div>
    </Card>
  );
}
