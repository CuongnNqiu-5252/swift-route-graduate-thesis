'use client';

import { useLocale } from '@/i18n/provider';

import { useState } from 'react';
import { Modal } from '@/components/ui/modal';
import { Button } from '@/components/ui/button';
import { Driver } from '@/domain/entities/driver.entity';
import { Order } from '@/domain/entities/order.entity';
import { OptimizationResult } from '@/domain/entities/routing.entity';
import { Truck, AlertCircle, Users, Check, RefreshCw } from 'lucide-react';

interface DriverAssignmentModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orders: Order[];
  result: OptimizationResult | null;
  drivers: Driver[];
  selectedDriverIds?: string[];
  onSelectedDriverIdsChange?: (ids: string[]) => void;
  onReoptimizeForCount?: (vehicleCount: number) => Promise<unknown>;
  isLoadingDrivers: boolean;
  onConfirm: (driverIds: string[]) => Promise<void>;
}

export function DriverAssignmentModal({
  open,
  onOpenChange,
  orders,
  result,
  drivers,
  selectedDriverIds: controlledDriverIds,
  onSelectedDriverIdsChange,
  onReoptimizeForCount,
  isLoadingDrivers,
  onConfirm,
}: DriverAssignmentModalProps) {
  const { t } = useLocale();
  const [localDriverIds, setLocalDriverIds] = useState<string[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isReoptimizing, setIsReoptimizing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedDriverIds = controlledDriverIds ?? localDriverIds;
  const setSelectedDriverIds = (next: string[] | ((prev: string[]) => string[])) => {
    const resolved = typeof next === 'function' ? next(selectedDriverIds) : next;
    if (onSelectedDriverIdsChange) {
      onSelectedDriverIdsChange(resolved);
    } else {
      setLocalDriverIds(resolved);
    }
  };

  const availableDrivers = drivers.filter(
    (d) =>
      d.status === 'AVAILABLE' ||
      (d.status as string) === 'ACTIVE' ||
      !d.status
  );

  const toggleDriver = (driverId: string) => {
    setSelectedDriverIds((prev) =>
      prev.includes(driverId)
        ? prev.filter((id) => id !== driverId)
        : [...prev, driverId]
    );
    setError(null);
  };

  const handleSelectAll = () => {
    if (selectedDriverIds.length === availableDrivers.length) {
      setSelectedDriverIds([]);
    } else {
      setSelectedDriverIds(availableDrivers.map((d) => d.user_id));
    }
    setError(null);
  };

  const handleRecalculateCvrp = async () => {
    if (!onReoptimizeForCount || selectedDriverIds.length === 0) return;
    setIsReoptimizing(true);
    setError(null);
    try {
      await onReoptimizeForCount(selectedDriverIds.length);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("Không thể tính lại tuyến CVRP"));
    } finally {
      setIsReoptimizing(false);
    }
  };

  const handleConfirm = async () => {
    if (selectedDriverIds.length === 0) {
      setError('Vui lòng chọn ít nhất 1 tài xế để bàn giao lộ trình');
      return;
    }
    setError(null);
    setIsSubmitting(true);
    try {
      await onConfirm(selectedDriverIds);
      onOpenChange(false);
      setSelectedDriverIds([]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Lỗi khi gán tài xế');
    } finally {
      setIsSubmitting(false);
    }
  };

  const totalCount = orders.length;
  const driverCount = selectedDriverIds.length;
  const vehicleRoutes = result?.vehicle_routes ?? [];
  const targetVehicles = Math.min(Math.max(1, driverCount), totalCount);
  const needsCvrpRecalc = driverCount > 0 && vehicleRoutes.length !== targetVehicles;

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title={t("Phê duyệt & Bàn giao Lộ trình CVRP")}
      description={`Phân bổ tối ưu ${totalCount} đơn hàng từ kho cho đội tài xế bằng thuật toán Google OR-Tools CVRP.`}
    >
      <div className="space-y-4">
        {/* Route Summary */}
        <div className="rounded-lg bg-gray-50 p-3.5 border border-gray-200">
          <div className="grid grid-cols-3 gap-2 text-center">
            <div>
              <p className="text-[11px] text-gray-600">{t("Tổng điểm giao")}</p>
              <p className="mt-0.5 text-sm font-bold text-gray-900 tabular-nums">{totalCount}{" "}{t("điểm")}</p>
            </div>
            <div>
              <p className="text-[11px] text-gray-600">{t("Tổng quãng đường")}</p>
              <p className="mt-0.5 text-sm font-bold text-emerald-700 tabular-nums">
                {result ? `${result.total_distance_km.toFixed(1)} km` : '—'}
              </p>
            </div>
            <div>
              <p className="text-[11px] text-gray-600">{t("Số tuyến CVRP")}</p>
              <p className="mt-0.5 text-sm font-bold text-blue-800 tabular-nums">
                {vehicleRoutes.length || 1}{" "}{t("tuyến")}
              </p>
            </div>
          </div>
        </div>

        {/* Notice if driver count changed since last CVRP run */}
        {needsCvrpRecalc && (
          <div className="flex items-center justify-between gap-3 rounded-lg border border-blue-200 bg-blue-50/80 p-3 text-xs text-blue-950">
            <div className="flex items-start gap-2">
              <Users size={15} className="mt-0.5 shrink-0 text-blue-700" />
              <span>
                {t("Bạn đã chọn")}{" "}<strong>{driverCount}{" "}{t("tài xế")}</strong>{" "}{t("(khác với")}{" "}{vehicleRoutes.length}{" "}{t("tuyến đã tính trước đó). Hệ thống sẽ giải lại CVRP cho")}{" "}{targetVehicles}{" "}{t("xe khi bàn giao, hoặc bấm cập nhật ngay để xem trước.")}
              </span>
            </div>
            {onReoptimizeForCount && (
              <Button
                size="sm"
                variant="outline"
                onClick={handleRecalculateCvrp}
                isLoading={isReoptimizing}
                className="shrink-0 border-blue-300 bg-white text-xs text-blue-800 hover:bg-blue-100"
              >
                <RefreshCw size={12} />{" "}{t("Tính lại CVRP")}
              </Button>
            )}
          </div>
        )}

        {/* Driver Selection */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="block text-sm font-semibold text-gray-800">
              {t("Chọn tài xế nhận tuyến (")}{driverCount}/{availableDrivers.length}):
            </label>
            {availableDrivers.length > 1 && (
              <button
                type="button"
                onClick={handleSelectAll}
                className="text-xs font-semibold text-blue-700 hover:text-blue-900 hover:underline"
              >
                {selectedDriverIds.length === availableDrivers.length ? t("Bỏ chọn tất cả") : t("Chọn tất cả")}
              </button>
            )}
          </div>

          {isLoadingDrivers ? (
            <div className="py-4 text-center text-sm text-gray-500">
              {t("Đang tải danh sách tài xế...")}
            </div>
          ) : availableDrivers.length === 0 ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">
              {t("Không có tài xế nào đang ở trạng thái Sẵn sàng (Available).")}
            </div>
          ) : (
            <div className="max-h-64 space-y-2 overflow-y-auto pr-1">
              {availableDrivers.map((driver) => {
                const selectedIndex = selectedDriverIds.indexOf(driver.user_id);
                const isSelected = selectedIndex !== -1;
                const assignedRoute = isSelected && !needsCvrpRecalc ? vehicleRoutes[selectedIndex] : undefined;

                return (
                  <div
                    key={driver.id}
                    onClick={() => toggleDriver(driver.user_id)}
                    className={`cursor-pointer rounded-lg border p-3 transition-colors ${
                      isSelected
                        ? 'border-blue-600 bg-blue-50/50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div
                          className={`flex h-8 w-8 items-center justify-center rounded-full transition-colors ${
                            isSelected ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700'
                          }`}
                        >
                          {isSelected ? <Check size={16} strokeWidth={2.5} /> : <Truck size={16} />}
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <p className="text-sm font-semibold text-gray-900">{driver.name}</p>
                            {isSelected && (
                              <span className="rounded-full bg-blue-100 px-2 py-0.5 text-[10px] font-semibold text-blue-900">
                                {t("Tuyến #")}{selectedIndex + 1}
                              </span>
                            )}
                          </div>
                          <p className="text-xs text-gray-600">
                            {driver.phone} • {driver.vehicle_plate} ({driver.vehicle_type})
                          </p>
                        </div>
                      </div>

                      <div className="flex items-center gap-2">
                        {isSelected ? (
                          <div className="flex h-5 w-5 items-center justify-center rounded-full bg-blue-600 text-white">
                            <Check size={12} strokeWidth={3} />
                          </div>
                        ) : (
                          <div className="h-5 w-5 rounded-full border-2 border-gray-300" />
                        )}
                      </div>
                    </div>

                    {assignedRoute && (
                      <div className="mt-2.5 rounded border border-blue-100 bg-white px-2.5 py-2 text-xs text-gray-800">
                        <div className="flex items-center justify-between font-medium text-blue-950 tabular-nums">
                          <span>{assignedRoute.ordered_stops.length}{" "}{t("điểm giao ·")}{" "}{assignedRoute.total_load_kg.toFixed(1)} kg</span>
                          <span>{assignedRoute.total_distance_km.toFixed(1)} km · ~{Math.ceil(assignedRoute.total_duration_min)}{" "}{t("phút")}</span>
                        </div>
                        <p className="mt-1 truncate text-[11px] text-gray-600">
                          Kho → {assignedRoute.ordered_stops.map((s) => s.order?.order_code || s.id.slice(0, 8)).join(' → ')}
                        </p>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {error && (
          <div className="flex items-center gap-2 rounded-lg bg-red-50 p-3 text-xs text-red-800">
            <AlertCircle size={14} />
            <span>{error}</span>
          </div>
        )}

        {/* Actions */}
        <div className="flex items-center justify-end gap-3 pt-2 border-t border-gray-100">
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            {t("Hủy")}
          </Button>
          <Button
            onClick={handleConfirm}
            isLoading={isSubmitting}
            disabled={driverCount === 0 || availableDrivers.length === 0}
          >
            {driverCount > 1
              ? `Phê duyệt & Giao ${driverCount} tuyến CVRP`
              : t("Phê duyệt & Giao đơn")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
