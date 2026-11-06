'use client';

import { Order } from '@/domain/entities/order.entity';
import { DispatchDepot, OptimizationResult } from '@/domain/entities/routing.entity';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Sparkles, Send, CheckSquare, Square, MapPin, Clock, Warehouse, LockKeyhole } from 'lucide-react';

interface DispatchPanelProps {
  orders: Order[];
  selectedOrders: Order[];
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

export function DispatchPanel({ orders, selectedOrders, depot, isOrderSelectable, onToggleOrder, onSelectAll, onDeselectAll, onOptimize, isOptimizing, result, onOpenAssignModal }: DispatchPanelProps) {
  const compatibleOrders = orders.filter(isOrderSelectable);
  const isAllSelected = compatibleOrders.length > 0
    && compatibleOrders.every((order) => selectedOrders.some((selected) => selected.id === order.id));

  return (
    <Card className="flex h-full flex-col shadow-sm">
      <CardHeader className="border-b border-gray-100 pb-3">
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="text-base font-bold text-gray-900">Đơn chờ điều phối</CardTitle>
            <p className="mt-0.5 text-xs text-gray-500">Mỗi lượt tối ưu chỉ gồm các đơn xuất phát từ cùng một kho.</p>
          </div>
          <Badge className="shrink-0 bg-blue-100 text-blue-800">{selectedOrders.length}/{orders.length} đã chọn</Badge>
        </div>

        {depot && (
          <div className="mt-3 rounded-lg border border-blue-100 bg-blue-50/70 p-2.5">
            <div className="flex items-start gap-2">
              <Warehouse size={15} className="mt-0.5 shrink-0 text-blue-700" />
              <div className="min-w-0">
                <p className="truncate text-xs font-semibold text-blue-950">{depot.name}</p>
                <p className="mt-0.5 line-clamp-2 text-[11px] leading-4 text-blue-700">{depot.address}</p>
              </div>
            </div>
          </div>
        )}

        <div className="mt-3 flex items-center justify-between pt-1 text-xs">
          <button type="button" onClick={isAllSelected ? onDeselectAll : onSelectAll} className="flex items-center gap-1.5 font-medium text-blue-600 hover:text-blue-700">
            {isAllSelected
              ? <><CheckSquare size={14} /> Bỏ chọn tất cả</>
              : <><Square size={14} /> Chọn các đơn cùng kho ({compatibleOrders.length})</>}
          </button>
          {selectedOrders.length > 0 && (
            <span className="font-medium text-gray-500">Tổng tải: <strong className="text-gray-800">{selectedOrders.reduce((sum, order) => sum + (order.weight_kg || 0), 0).toFixed(1)} kg</strong></span>
          )}
        </div>
      </CardHeader>

      <CardContent className="flex-1 space-y-2 overflow-y-auto p-3">
        {orders.length === 0 ? (
          <div className="flex h-40 flex-col items-center justify-center text-center text-xs text-gray-400">
            <Clock size={28} className="mb-2 text-gray-300" />
            <p className="font-medium text-gray-500">Không có đơn sẵn sàng điều phối</p>
            <p className="mt-1 text-gray-400">Đơn mới hợp lệ sẽ tự động xuất hiện tại đây khi sẵn sàng điều phối.</p>
          </div>
        ) : orders.map((order) => {
          const isSelected = selectedOrders.some((selected) => selected.id === order.id);
          const canSelect = isOrderSelectable(order);
          const pickupLabel = order.pickup_location_name || order.pickup_address;
          return (
            <label key={order.id} className={`group flex items-start gap-3 rounded-lg border p-3 transition-all ${isSelected ? 'border-blue-500 bg-blue-50/50 shadow-sm' : canSelect ? 'cursor-pointer border-gray-200 bg-white hover:border-gray-300' : 'cursor-not-allowed border-gray-100 bg-gray-50 opacity-60'}`}>
              <input type="checkbox" checked={isSelected} disabled={!canSelect} onChange={() => onToggleOrder(order)} aria-label={`Chọn đơn ${order.order_code}`} className="mt-0.5 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500 disabled:cursor-not-allowed" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-xs font-bold text-gray-900">{order.order_code}</span>
                  <span className="text-[11px] font-medium text-gray-500">{order.weight_kg} kg</span>
                </div>
                <p className="mt-1 flex items-start gap-1 text-xs text-gray-600"><MapPin size={12} className="mt-0.5 shrink-0 text-red-500" /><span className="line-clamp-2">{order.delivery_address}</span></p>
                <div className="mt-1.5 flex items-start gap-1 text-[11px] text-gray-500">
                  {canSelect ? <Warehouse size={11} className="mt-0.5 shrink-0" /> : <LockKeyhole size={11} className="mt-0.5 shrink-0 text-amber-600" />}
                  <span className="line-clamp-1">{canSelect ? pickupLabel : `Kho khác: ${pickupLabel}`}</span>
                </div>
              </div>
            </label>
          );
        })}
      </CardContent>

      <div className="space-y-2 border-t border-gray-100 bg-gray-50/50 p-4">
        <Button onClick={onOptimize} disabled={selectedOrders.length === 0 || isOptimizing} isLoading={isOptimizing} className="w-full bg-blue-600 text-sm font-semibold shadow-sm hover:bg-blue-700">
          <Sparkles size={16} />
          {isOptimizing ? 'Đang tính toán thứ tự giao...' : `Tối ưu thứ tự giao (${selectedOrders.length} điểm)`}
        </Button>
        {result && (
          <Button onClick={onOpenAssignModal} variant="default" className="w-full bg-emerald-600 text-sm font-semibold shadow-sm hover:bg-emerald-700">
            <Send size={16} /> Phê duyệt và giao tài xế ({selectedOrders.length} đơn)
          </Button>
        )}
      </div>
    </Card>
  );
}
