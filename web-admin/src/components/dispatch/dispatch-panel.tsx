'use client';

import { Order } from '@/domain/entities/order.entity';
import { OptimizationResult } from '@/domain/entities/routing.entity';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Sparkles, Send, CheckSquare, Square, MapPin, UserCheck, Clock } from 'lucide-react';

interface DispatchPanelProps {
  orders: Order[];
  selectedOrders: Order[];
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
  onToggleOrder,
  onSelectAll,
  onDeselectAll,
  onOptimize,
  isOptimizing,
  result,
  onOpenAssignModal,
}: DispatchPanelProps) {
  const isAllSelected = orders.length > 0 && selectedOrders.length === orders.length;

  return (
    <Card className="flex h-full flex-col shadow-sm">
      <CardHeader className="border-b border-gray-100 pb-3">
        <div className="flex items-center justify-between">
          <div>
            <CardTitle className="text-base font-bold text-gray-900">
              Danh sách Đơn chờ Điều phối
            </CardTitle>
            <p className="text-xs text-gray-500 mt-0.5">
              Chọn các đơn hàng cần lập tuyến giao tối ưu
            </p>
          </div>
          <Badge className="bg-blue-100 text-blue-800">
            {selectedOrders.length}/{orders.length} đã chọn
          </Badge>
        </div>

        {/* Bulk Selection Actions */}
        <div className="mt-3 flex items-center justify-between text-xs pt-1">
          <button
            type="button"
            onClick={isAllSelected ? onDeselectAll : onSelectAll}
            className="flex items-center gap-1.5 text-blue-600 hover:text-blue-700 font-medium"
          >
            {isAllSelected ? (
              <>
                <CheckSquare size={14} /> Bỏ chọn tất cả
              </>
            ) : (
              <>
                <Square size={14} /> Chọn tất cả ({orders.length})
              </>
            )}
          </button>
          {selectedOrders.length > 0 && (
            <span className="text-gray-500 font-medium">
              Tổng tải:{' '}
              <strong className="text-gray-800">
                {selectedOrders.reduce((sum, o) => sum + (o.weight_kg || 0), 0).toFixed(1)} kg
              </strong>
            </span>
          )}
        </div>
      </CardHeader>

      {/* Orders List */}
      <CardContent className="flex-1 overflow-y-auto p-3 space-y-2 max-h-[420px]">
        {orders.length === 0 ? (
          <div className="flex h-40 flex-col items-center justify-center text-center text-xs text-gray-400">
            <Clock size={28} className="text-gray-300 mb-2" />
            <p className="font-medium text-gray-500">Không có đơn hàng nào chờ điều phối</p>
            <p className="text-gray-400 mt-1">Các đơn hàng mới thanh toán sẽ hiển thị tại đây</p>
          </div>
        ) : (
          orders.map((order) => {
            const isSelected = selectedOrders.some((o) => o.id === order.id);
            const isAssigned = order.status === 'PLANNED';
            return (
              <div
                key={order.id}
                onClick={() => onToggleOrder(order)}
                className={`group flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-all ${
                  isSelected
                    ? 'border-blue-500 bg-blue-50/50 shadow-sm'
                    : 'border-gray-200 bg-white hover:border-gray-300'
                }`}
              >
                <input
                  type="checkbox"
                  checked={isSelected}
                  onChange={() => {}} // handled by parent onClick
                  className="mt-0.5 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-1">
                    <span className="font-mono text-xs font-bold text-gray-900">
                      {order.order_code}
                    </span>
                    <div className="flex items-center gap-1.5">
                      <span className="text-[11px] font-medium text-gray-500">
                        {order.weight_kg} kg
                      </span>
                      {isAssigned ? (
                        <span className="inline-flex items-center gap-1 rounded bg-emerald-50 px-1.5 py-0.5 text-[10px] font-medium text-emerald-700 border border-emerald-200">
                          <UserCheck size={10} /> Đã lên lộ trình
                        </span>
                      ) : (
                        <span className="inline-flex items-center rounded bg-amber-50 px-1.5 py-0.5 text-[10px] font-medium text-amber-700 border border-amber-200">
                          Chờ phân bổ
                        </span>
                      )}
                    </div>
                  </div>
                  <p className="mt-1 flex items-start gap-1 text-xs text-gray-600 line-clamp-2">
                    <MapPin size={12} className="shrink-0 text-red-500 mt-0.5" />
                    <span>{order.delivery_address}</span>
                  </p>
                  <div className="mt-1 flex items-center justify-between text-[11px] text-gray-400">
                    {order.tenant_name && (
                      <span className="truncate">Kho: {order.tenant_name}</span>
                    )}
                    {order.assigned_driver_name && (
                      <span className="font-medium text-indigo-600 truncate ml-2">
                        TX: {order.assigned_driver_name}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </CardContent>

      {/* Action Footer */}
      <div className="border-t border-gray-100 p-4 space-y-2 bg-gray-50/50">
        <Button
          onClick={onOptimize}
          disabled={selectedOrders.length === 0 || isOptimizing}
          isLoading={isOptimizing}
          className="w-full bg-blue-600 hover:bg-blue-700 font-semibold text-sm shadow-sm"
        >
          <Sparkles size={16} />
          {isOptimizing ? 'Đang tính toán tuyến đường tối ưu...' : `Tối ưu Tuyến đường (${selectedOrders.length} điểm)`}
        </Button>

        {result && (
          <Button
            onClick={onOpenAssignModal}
            variant="default"
            className="w-full bg-emerald-600 hover:bg-emerald-700 font-semibold text-sm shadow-sm"
          >
            <Send size={16} />
            Phê duyệt & Bàn giao Tài xế ({selectedOrders.length} đơn)
          </Button>
        )}
      </div>
    </Card>
  );
}
