'use client';

import { useState } from 'react';
import { Modal } from '@/components/ui/modal';
import { Button } from '@/components/ui/button';
import { Driver } from '@/domain/entities/driver.entity';
import { Order } from '@/domain/entities/order.entity';
import { OptimizationResult } from '@/domain/entities/routing.entity';
import { Truck, AlertCircle, Users, Check } from 'lucide-react';

interface DriverAssignmentModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orders: Order[];
  result: OptimizationResult | null;
  drivers: Driver[];
  isLoadingDrivers: boolean;
  onConfirm: (driverIds: string[]) => Promise<void>;
}

export function DriverAssignmentModal({
  open,
  onOpenChange,
  orders,
  result,
  drivers,
  isLoadingDrivers,
  onConfirm,
}: DriverAssignmentModalProps) {
  const [selectedDriverIds, setSelectedDriverIds] = useState<string[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
  const stopsPerDriver = driverCount > 0 ? Math.ceil(totalCount / driverCount) : 0;

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="Bàn giao Lộ trình cho Tài xế"
      description={`Phê duyệt lộ trình đã tối ưu và gán ${totalCount} đơn hàng cho tài xế.`}
    >
      <div className="space-y-4">
        {/* Route Summary */}
        <div className="rounded-lg bg-gray-50 p-4 border border-gray-100">
          <div className="flex items-center justify-between text-sm">
            <span className="text-gray-500">Số điểm dừng:</span>
            <span className="font-semibold text-gray-900">{totalCount} điểm</span>
          </div>
          {result && (
            <>
              <div className="mt-2 flex items-center justify-between text-sm">
                <span className="text-gray-500">Quãng đường dự kiến:</span>
                <span className="font-semibold text-emerald-600">
                  {result.total_distance_km.toFixed(1)} km
                </span>
              </div>
              <div className="mt-2 flex items-center justify-between text-sm">
                <span className="text-gray-500">Thời gian dự kiến:</span>
                <span className="font-semibold text-gray-900">
                  ~{result.total_duration_min.toFixed(0)} phút
                </span>
              </div>
            </>
          )}
        </div>

        {/* Multi-driver distribution note */}
        {driverCount > 1 && (
          <div className="flex items-start gap-2.5 rounded-lg border border-blue-200 bg-blue-50/70 p-3 text-xs text-blue-800">
            <Users size={16} className="mt-0.5 shrink-0 text-blue-600" />
            <div>
              <p className="font-semibold">Chế độ phân chia lộ trình ({driverCount} tài xế):</p>
              <p className="mt-0.5 text-blue-700">
                Hệ thống sẽ tự động chia đều {totalCount} đơn hàng cho {driverCount} tài xế theo thứ tự lộ trình tối ưu (~{stopsPerDriver} đơn/tài xế).
              </p>
            </div>
          </div>
        )}

        {/* Driver Selection */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="block text-sm font-medium text-gray-700">
              Chọn tài xế thực hiện ({driverCount}/{availableDrivers.length}):
            </label>
            {availableDrivers.length > 1 && (
              <button
                type="button"
                onClick={handleSelectAll}
                className="text-xs font-semibold text-blue-600 hover:text-blue-800 hover:underline"
              >
                {selectedDriverIds.length === availableDrivers.length ? 'Bỏ chọn tất cả' : 'Chọn tất cả'}
              </button>
            )}
          </div>

          {isLoadingDrivers ? (
            <div className="py-4 text-center text-sm text-gray-400">
              Đang tải danh sách tài xế...
            </div>
          ) : availableDrivers.length === 0 ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
              Không có tài xế nào đang ở trạng thái Sẵn sàng (Available).
            </div>
          ) : (
            <div className="max-h-60 space-y-2 overflow-y-auto pr-1">
              {availableDrivers.map((driver) => {
                const selectedIndex = selectedDriverIds.indexOf(driver.user_id);
                const isSelected = selectedIndex !== -1;

                return (
                  <div
                    key={driver.id}
                    onClick={() => toggleDriver(driver.user_id)}
                    className={`flex cursor-pointer items-center justify-between rounded-lg border p-3 transition-all ${
                      isSelected
                        ? 'border-blue-500 bg-blue-50/50 shadow-sm ring-1 ring-blue-500'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div
                        className={`flex h-8 w-8 items-center justify-center rounded-full transition-colors ${
                          isSelected ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'
                        }`}
                      >
                        {isSelected ? <Check size={16} strokeWidth={2.5} /> : <Truck size={16} />}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="text-sm font-semibold text-gray-900">{driver.name}</p>
                          {isSelected && driverCount > 1 && (
                            <span className="rounded-full bg-blue-100 px-2 py-0.5 text-[10px] font-semibold text-blue-800">
                              Chặng {selectedIndex + 1}
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-gray-500">
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
                );
              })}
            </div>
          )}
        </div>

        {error && (
          <div className="flex items-center gap-2 rounded-lg bg-red-50 p-3 text-xs text-red-700">
            <AlertCircle size={14} />
            <span>{error}</span>
          </div>
        )}

        {/* Actions */}
        <div className="flex items-center justify-end gap-3 pt-2 border-t border-gray-100">
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            Hủy
          </Button>
          <Button
            onClick={handleConfirm}
            isLoading={isSubmitting}
            disabled={driverCount === 0 || availableDrivers.length === 0}
          >
            {driverCount > 1
              ? `Xác nhận & Giao cho ${driverCount} tài xế`
              : 'Xác nhận & Giao đơn'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
