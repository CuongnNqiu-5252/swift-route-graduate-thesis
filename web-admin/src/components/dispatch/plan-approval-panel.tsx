'use client';

import { useState } from 'react';
import { DispatchPlanRecord } from '@/domain/entities/routing.entity';
import { Driver } from '@/domain/entities/driver.entity';
import { Order } from '@/domain/entities/order.entity';
import { PlanStatusBadge } from '@/components/dispatch/plan-status-badge';
import { Button } from '@/components/ui/button';
import { Check, Play, X, RefreshCw, Route, Plus } from 'lucide-react';

interface PlanApprovalPanelProps {
  plans: DispatchPlanRecord[];
  drivers: Driver[];
  eligibleOrders?: Order[];
  isLoading: boolean;
  isTriggering: boolean;
  selectedPlanId?: string | null;
  onSelectPlan?: (plan: DispatchPlanRecord) => void;
  onRefresh: () => void;
  onTriggerPlanning: () => Promise<void>;
  onApprovePlan: (planId: string, driverId?: string | null) => Promise<void>;
  onDispatchPlan: (planId: string) => Promise<void>;
  onCancelPlan: (planId: string, reason: string) => Promise<void>;
  onReoptimizePlan?: (planId: string) => Promise<void>;
  onInsertOrderIntoPlan?: (planId: string, orderId: string) => Promise<void>;
}

const TRIGGER_LABELS: Record<string, string> = {
  BATCH_SIZE: 'Đủ ngưỡng gom đơn',
  MAX_WAIT: 'Quá thời gian chờ',
  SCHEDULED: 'Khung giờ cố định',
  URGENT: 'Đơn khẩn cấp',
  MANUAL: 'Điều phối viên kích hoạt',
};

export function PlanApprovalPanel({
  plans,
  drivers,
  eligibleOrders = [],
  isLoading,
  isTriggering,
  selectedPlanId,
  onSelectPlan,
  onRefresh,
  onTriggerPlanning,
  onApprovePlan,
  onDispatchPlan,
  onCancelPlan,
  onReoptimizePlan,
  onInsertOrderIntoPlan,
}: PlanApprovalPanelProps) {
  const [busyPlanId, setBusyPlanId] = useState<string | null>(null);
  const [driverOverrides, setDriverOverrides] = useState<Record<string, string>>({});
  const [insertOrderIds, setInsertOrderIds] = useState<Record<string, string>>({});

  const driverNameMap = new Map<string, string>();
  drivers.forEach((d) => {
    driverNameMap.set(d.user_id, `${d.name} (${d.vehicle_plate})`);
    driverNameMap.set(d.id, `${d.name} (${d.vehicle_plate})`);
  });

  const handleApprove = async (plan: DispatchPlanRecord) => {
    setBusyPlanId(plan.plan_id);
    try {
      const chosenDriver = driverOverrides[plan.plan_id] || plan.driver_id || null;
      await onApprovePlan(plan.plan_id, chosenDriver);
    } finally {
      setBusyPlanId(null);
    }
  };

  const handleDispatch = async (planId: string) => {
    setBusyPlanId(planId);
    try {
      await onDispatchPlan(planId);
    } finally {
      setBusyPlanId(null);
    }
  };

  const handleCancel = async (planId: string) => {
    setBusyPlanId(planId);
    try {
      await onCancelPlan(planId, 'Điều phối viên hủy kế hoạch để lập lại lộ trình');
    } finally {
      setBusyPlanId(null);
    }
  };

  const handleReoptimize = async (planId: string) => {
    if (!onReoptimizePlan) return;
    setBusyPlanId(planId);
    try {
      await onReoptimizePlan(planId);
    } finally {
      setBusyPlanId(null);
    }
  };

  const handleInsertOrder = async (planId: string) => {
    const orderId = insertOrderIds[planId];
    if (!onInsertOrderIntoPlan || !orderId) return;
    setBusyPlanId(planId);
    try {
      await onInsertOrderIntoPlan(planId, orderId);
      setInsertOrderIds((prev) => ({ ...prev, [planId]: '' }));
    } finally {
      setBusyPlanId(null);
    }
  };

  return (
    <section className="rounded-lg border border-gray-200 bg-white">
      <div className="flex flex-col gap-2 border-b border-gray-200 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-base font-semibold text-gray-900">
            Kế hoạch điều phối tự động ({plans.length})
          </h2>
          <p className="text-xs text-gray-500">
            Theo dõi các lộ trình được OR-Tools sinh tự động, phê duyệt bản nháp hoặc giao tuyến cho tài xế.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={onRefresh}
            isLoading={isLoading}
          >
            <RefreshCw size={14} /> Làm mới kế hoạch
          </Button>
          <Button
            size="sm"
            onClick={onTriggerPlanning}
            isLoading={isTriggering}
          >
            <Play size={14} /> Chạy lập kế hoạch ngay
          </Button>
        </div>
      </div>

      {plans.length === 0 ? (
        <div className="px-4 py-8 text-center text-sm text-gray-500">
          Chưa có kế hoạch điều phối nào. Khi đơn hàng ở trạng thái sẵn sàng điều phối đạt ngưỡng gom đơn hoặc quá thời gian chờ, hệ thống sẽ tự động sinh lộ trình tại đây.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-xs font-medium text-gray-500">
                <th className="px-4 py-2.5">Mã kế hoạch</th>
                <th className="px-4 py-2.5">Trạng thái</th>
                <th className="px-4 py-2.5">Nguồn kích hoạt</th>
                <th className="px-4 py-2.5 text-right">Số điểm giao</th>
                <th className="px-4 py-2.5 text-right">Quãng đường</th>
                <th className="px-4 py-2.5 text-right">Thời gian dự kiến</th>
                <th className="px-4 py-2.5">Tài xế phụ trách</th>
                <th className="px-4 py-2.5 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {plans.map((plan) => {
                const isSelected = selectedPlanId === plan.plan_id;
                const isBusy = busyPlanId === plan.plan_id;
                const currentDriverId = driverOverrides[plan.plan_id] ?? plan.driver_id ?? '';

                return (
                  <tr
                    key={plan.plan_id}
                    onClick={() => onSelectPlan?.(plan)}
                    className={`cursor-pointer transition-colors ${
                      isSelected ? 'bg-gray-50' : 'hover:bg-gray-50/60'
                    }`}
                  >
                    <td className="px-4 py-3 font-mono text-xs text-gray-900">
                      #{plan.plan_id.slice(0, 8)}
                      <div className="text-[11px] font-sans text-gray-500">
                        {new Date(plan.created_at).toLocaleTimeString('vi-VN', {
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </div>
                    </td>

                    <td className="px-4 py-3">
                      <PlanStatusBadge
                        status={plan.plan_status}
                        acknowledgedAt={plan.acknowledged_at}
                      />
                    </td>

                    <td className="px-4 py-3 text-xs text-gray-700">
                      {plan.trigger_type
                        ? TRIGGER_LABELS[plan.trigger_type] || plan.trigger_type
                        : '—'}
                    </td>

                    <td className="px-4 py-3 text-right font-mono text-xs tabular-nums text-gray-900">
                      {plan.order_count} đơn
                    </td>

                    <td className="px-4 py-3 text-right font-mono text-xs tabular-nums text-gray-900">
                      {plan.total_distance_km != null
                        ? `${Number(plan.total_distance_km).toFixed(2)} km`
                        : '—'}
                    </td>

                    <td className="px-4 py-3 text-right font-mono text-xs tabular-nums text-gray-900">
                      {plan.estimated_duration_minutes != null
                        ? `${plan.estimated_duration_minutes} phút`
                        : '—'}
                    </td>

                    <td
                      className="px-4 py-3 text-xs text-gray-800"
                      onClick={(e) => e.stopPropagation()}
                    >
                      {plan.plan_status === 'DRAFT' ? (
                        <select
                          value={currentDriverId}
                          onChange={(e) =>
                            setDriverOverrides((prev) => ({
                              ...prev,
                              [plan.plan_id]: e.target.value,
                            }))
                          }
                          aria-label="Chọn tài xế cho kế hoạch"
                          className="w-full max-w-[210px] rounded border border-gray-300 bg-white px-2 py-1 text-xs text-gray-900 focus:border-gray-900 focus:outline-none"
                        >
                          <option value="">-- Chọn tài xế --</option>
                          {drivers.map((d) => (
                            <option key={d.user_id || d.id} value={d.user_id || d.id}>
                              {d.name} ({d.vehicle_plate})
                            </option>
                          ))}
                        </select>
                      ) : plan.driver_id ? (
                        driverNameMap.get(plan.driver_id) ||
                        `Tài xế #${plan.driver_id.slice(0, 8)}`
                      ) : (
                        <span className="text-gray-400">Chưa gán</span>
                      )}
                    </td>

                    <td
                      className="px-4 py-3 text-right"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <div className="inline-flex items-center justify-end gap-1.5">
                        {plan.plan_status === 'DRAFT' && (
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={isBusy}
                            onClick={() => handleApprove(plan)}
                          >
                            <Check size={13} /> Duyệt
                          </Button>
                        )}

                        {plan.plan_status === 'APPROVED' && (
                          <Button
                            size="sm"
                            disabled={isBusy}
                            onClick={() => handleDispatch(plan.plan_id)}
                          >
                            <Play size={13} /> Giao tuyến
                          </Button>
                        )}

                        {plan.plan_status === 'DISPATCHED' && onInsertOrderIntoPlan && eligibleOrders.length > 0 && (
                          <div className="inline-flex items-center gap-1">
                            <select
                              value={insertOrderIds[plan.plan_id] ?? ''}
                              onChange={(e) =>
                                setInsertOrderIds((prev) => ({
                                  ...prev,
                                  [plan.plan_id]: e.target.value,
                                }))
                              }
                              aria-label="Chọn đơn cần chèn vào tuyến đang chạy"
                              className="max-w-[140px] rounded border border-gray-300 bg-white px-2 py-1 text-xs text-gray-900 focus:border-gray-900 focus:outline-none"
                            >
                              <option value="">+ Chèn đơn...</option>
                              {eligibleOrders.map((o) => (
                                <option key={o.id} value={o.id}>
                                  {o.order_code} ({o.weight_kg ?? 1}kg)
                                </option>
                              ))}
                            </select>
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={isBusy || !insertOrderIds[plan.plan_id]}
                              onClick={() => handleInsertOrder(plan.plan_id)}
                              title="Chèn đơn hàng vào vị trí tối ưu trên tuyến đang chạy"
                            >
                              <Plus size={13} /> Chèn
                            </Button>
                          </div>
                        )}

                        {plan.plan_status === 'DISPATCHED' && onReoptimizePlan && (
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={isBusy}
                            onClick={() => handleReoptimize(plan.plan_id)}
                            title="Tối ưu lại thứ tự các chặng chưa giao dựa trên vị trí hiện tại"
                          >
                            <Route size={13} /> Tối ưu lại
                          </Button>
                        )}

                        {plan.plan_status !== 'CANCELLED' && (
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={isBusy}
                            onClick={() => handleCancel(plan.plan_id)}
                            className="text-red-700 hover:bg-red-50"
                          >
                            <X size={13} /> Hủy
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
