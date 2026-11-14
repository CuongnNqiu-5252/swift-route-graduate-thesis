'use client';

import { useLocale } from '@/i18n/provider';

import { useMemo, useState } from 'react';
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

type PlanFilter = 'ACTION' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | 'ALL';

const FILTERS: Array<{ key: PlanFilter; label: string; hint: string }> = [
  { key: 'ACTION', label: 'Cần xử lý', hint: 'Bản nháp chờ duyệt và kế hoạch đã duyệt chờ giao tuyến' },
  { key: 'ACTIVE', label: 'Tuyến đang thực hiện', hint: 'Kế hoạch đã giao cho tài xế' },
  { key: 'COMPLETED', label: 'Hoàn thành', hint: 'Kế hoạch đã hoàn thành tất cả điểm giao' },
  { key: 'CANCELLED', label: 'Đã hủy', hint: 'Kế hoạch đã bị hủy' },
  { key: 'ALL', label: 'Tất cả', hint: 'Toàn bộ kế hoạch' },
];

function matchesFilter(plan: DispatchPlanRecord, filter: PlanFilter): boolean {
  switch (filter) {
    case 'ACTION': return plan.plan_status === 'DRAFT' || plan.plan_status === 'APPROVED';
    case 'ACTIVE': return plan.plan_status === 'DISPATCHED';
    case 'COMPLETED': return plan.plan_status === 'COMPLETED';
    case 'CANCELLED': return plan.plan_status === 'CANCELLED';
    default: return true;
  }
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
  const { t, intlLocale } = useLocale();
  const [busyPlanId, setBusyPlanId] = useState<string | null>(null);
  const [driverOverrides, setDriverOverrides] = useState<Record<string, string>>({});
  const [insertOrderIds, setInsertOrderIds] = useState<Record<string, string>>({});
  const [filter, setFilter] = useState<PlanFilter>('ACTION');

  // Newest plans first; counts are computed over all plans so tabs stay informative.
  const sortedPlans = useMemo(
    () => [...plans].sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at)),
    [plans],
  );
  const counts = useMemo(() => ({
    ACTION: plans.filter((p) => matchesFilter(p, 'ACTION')).length,
    ACTIVE: plans.filter((p) => matchesFilter(p, 'ACTIVE')).length,
    COMPLETED: plans.filter((p) => matchesFilter(p, 'COMPLETED')).length,
    CANCELLED: plans.filter((p) => matchesFilter(p, 'CANCELLED')).length,
    ALL: plans.length,
  }), [plans]);
  const visiblePlans = useMemo(() => sortedPlans.filter((p) => matchesFilter(p, filter)), [sortedPlans, filter]);

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
            {t("Kế hoạch điều phối tự động (")}{counts.ACTION}{" "}{t("cần xử lý /")}{" "}{plans.length}{" "}{t("tổng)")}
          </h2>
          <p className="text-xs text-gray-500">
            {t("Theo dõi các lộ trình được OR-Tools sinh tự động, phê duyệt bản nháp hoặc giao tuyến cho tài xế.")}
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={onRefresh}
            isLoading={isLoading}
          >
            <RefreshCw size={14} />{" "}{t("Làm mới kế hoạch")}
          </Button>
          <Button
            size="sm"
            onClick={onTriggerPlanning}
            isLoading={isTriggering}
          >
            <Play size={14} />{" "}{t("Chạy lập kế hoạch ngay")}
          </Button>
        </div>
      </div>

      {plans.length > 0 && (
        <div role="tablist" aria-label={t("Lọc kế hoạch theo trạng thái")} className="flex flex-wrap gap-1.5 border-b border-gray-200 px-4 py-2">
          {FILTERS.map((item) => (
            <button
              key={item.key}
              type="button"
              role="tab"
              aria-selected={filter === item.key}
              title={t(item.hint)}
              onClick={() => setFilter(item.key)}
              className={`rounded-md border px-3 py-1 text-xs font-medium transition-colors ${
                filter === item.key
                  ? 'border-gray-900 bg-gray-900 text-white'
                  : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'
              }`}
            >
              {t(item.label)} <span className="tabular-nums opacity-80">({counts[item.key]})</span>
            </button>
          ))}
        </div>
      )}

      {plans.length === 0 ? (
        <div className="px-4 py-8 text-center text-sm text-gray-500">
          {t("Chưa có kế hoạch điều phối nào. Khi đơn hàng ở trạng thái sẵn sàng điều phối đạt ngưỡng gom đơn hoặc quá thời gian chờ, hệ thống sẽ tự động sinh lộ trình tại đây.")}
        </div>
      ) : (
        <div className="max-h-[360px] overflow-auto">
          <table className="w-full border-collapse text-left text-sm">
            <thead>
              <tr className="sticky top-0 z-10 border-b border-gray-200 bg-white text-xs font-medium text-gray-500">
                <th className="px-4 py-2.5">{t("Mã kế hoạch")}</th>
                <th className="px-4 py-2.5">{t("Trạng thái")}</th>
                <th className="px-4 py-2.5">{t("Nguồn kích hoạt")}</th>
                <th className="px-4 py-2.5 text-right">{t("Số điểm giao")}</th>
                <th className="px-4 py-2.5 text-right">{t("Quãng đường")}</th>
                <th className="px-4 py-2.5 text-right">{t("Thời gian dự kiến")}</th>
                <th className="px-4 py-2.5">{t("Tài xế phụ trách")}</th>
                <th className="px-4 py-2.5 text-right">{t("Thao tác")}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {visiblePlans.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-sm text-gray-500">
                    {t("Không có kế hoạch nào trong nhóm này.")}
                  </td>
                </tr>
              )}
              {visiblePlans.map((plan) => {
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
                        {new Date(plan.created_at).toLocaleTimeString(intlLocale, {
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
                      {plan.order_count}{" "}{t("đơn")}
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
                          aria-label={t("Chọn tài xế cho kế hoạch")}
                          className="w-full max-w-[210px] rounded border border-gray-300 bg-white px-2 py-1 text-xs text-gray-900 focus:border-gray-900 focus:outline-none"
                        >
                          <option value="">{t("-- Chọn tài xế --")}</option>
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
                        <span className="text-gray-400">{t("Chưa gán")}</span>
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
                            <Check size={13} />{" "}{t("Duyệt")}
                          </Button>
                        )}

                        {plan.plan_status === 'APPROVED' && (
                          <Button
                            size="sm"
                            disabled={isBusy}
                            onClick={() => handleDispatch(plan.plan_id)}
                          >
                            <Play size={13} />{" "}{t("Giao tuyến")}
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
                              aria-label={t("Chọn đơn cần chèn vào tuyến đang chạy")}
                              className="max-w-[140px] rounded border border-gray-300 bg-white px-2 py-1 text-xs text-gray-900 focus:border-gray-900 focus:outline-none"
                            >
                              <option value="">{t("+ Chèn đơn...")}</option>
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
                              <Plus size={13} />{" "}{t("Chèn")}
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
                            <Route size={13} />{" "}{t("Tối ưu lại")}
                          </Button>
                        )}

                        {plan.plan_status !== 'CANCELLED' && plan.plan_status !== 'COMPLETED' && (
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={isBusy}
                            onClick={() => handleCancel(plan.plan_id)}
                            className="text-red-700 hover:bg-red-50"
                          >
                            <X size={13} />{" "}{t("Hủy")}
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
