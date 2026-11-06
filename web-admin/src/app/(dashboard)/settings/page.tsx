'use client';

import { useState, useEffect } from 'react';
import {
  BusinessType,
  TenantSettings,
  BUSINESS_TYPE_LABEL,
  BUSINESS_TYPE_DESCRIPTION,
} from '@/domain/entities/tenant-settings.entity';
import { tenantSettingsRepository } from '@/infrastructure/repositories/tenant-settings.repository';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  Sliders,
  Utensils,
  Coffee,
  Pill,
  ShoppingBag,
  Store,
  Sparkles,
  Save,
  RotateCcw,
  Users2,
  Clock,
  Gauge,
  CheckCircle2,
} from 'lucide-react';
import { toast } from 'sonner';

const BUSINESS_TYPE_ICONS: Record<BusinessType, any> = {
  RESTAURANT: Utensils,
  CAFE: Coffee,
  PHARMACY: Pill,
  RETAIL: ShoppingBag,
  OTHER: Store,
};

export default function SettingsPage() {
  const [settings, setSettings] = useState<TenantSettings | null>(null);
  const [selectedType, setSelectedType] = useState<BusinessType>('OTHER');
  const [timeWeight, setTimeWeight] = useState(0.5);
  const [maxOrders, setMaxOrders] = useState(5);
  const [maxWaitMinutes, setMaxWaitMinutes] = useState(5);
  const [urgentMode, setUrgentMode] = useState<'IMMEDIATE_SINGLE' | 'BATCH_IMMEDIATE'>('IMMEDIATE_SINGLE');
  const [allowPool, setAllowPool] = useState(false);
  const [requireApproval, setRequireApproval] = useState(false);

  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isApplyingPreset, setIsApplyingPreset] = useState(false);

  const fetchSettings = async () => {
    setIsLoading(true);
    try {
      const data = await tenantSettingsRepository.getSettings();
      setSettings(data);
      setSelectedType(data.business_type);
      setTimeWeight(data.optimization_weights.time_weight);
      setMaxOrders(data.dispatch_config.max_orders_per_driver);
      setMaxWaitMinutes(Math.round(data.dispatch_config.max_wait_time_seconds / 60));
      setUrgentMode(data.dispatch_config.urgent_order_mode);
      setAllowPool(data.dispatch_config.allow_pool_drivers);
      setRequireApproval(data.dispatch_config.require_approval);
    } catch (err: unknown) {
      toast.error('Không thể tải cấu hình doanh nghiệp');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchSettings();
  }, []);

  const handleApplyPreset = async () => {
    setIsApplyingPreset(true);
    try {
      const updated = await tenantSettingsRepository.applyPreset(selectedType);
      toast.success(`Đã áp dụng cấu hình đề xuất cho ngành ${BUSINESS_TYPE_LABEL[selectedType]}`);
      await fetchSettings();
    } catch (err: unknown) {
      toast.error('Lỗi khi áp dụng cấu hình đề xuất');
    } finally {
      setIsApplyingPreset(false);
    }
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      const costWeight = Number((1.0 - timeWeight).toFixed(2));
      await tenantSettingsRepository.updateSettings({
        business_type: selectedType,
        optimization_weights: {
          time_weight: Number(timeWeight.toFixed(2)),
          cost_weight: costWeight,
        },
        dispatch_config: {
          max_orders_per_driver: maxOrders,
          max_wait_time_seconds: maxWaitMinutes * 60,
          urgent_order_mode: urgentMode,
          allow_pool_drivers: allowPool,
          require_approval: requireApproval,
        },
      });
      toast.success('Đã lưu cấu hình doanh nghiệp thành công!');
      await fetchSettings();
    } catch (err: unknown) {
      toast.error('Lỗi khi lưu cấu hình');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading || !settings) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="flex flex-col items-center gap-2">
          <div className="h-7 w-7 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
          <p className="text-sm text-gray-500">Đang tải thông tin cấu hình...</p>
        </div>
      </div>
    );
  }

  const costWeightPercent = Math.round((1 - timeWeight) * 100);
  const timeWeightPercent = Math.round(timeWeight * 100);

  return (
    <div className="space-y-6 max-w-5xl">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-gray-200 pb-5">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">
            Cài đặt Doanh nghiệp & Điều phối
          </h1>
          <p className="text-sm text-gray-500">
            Quản trị loại hình kinh doanh, tinh chỉnh hàm mục tiêu tối ưu và thiết lập ràng buộc gom đơn
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <Button
            variant="outline"
            size="sm"
            onClick={fetchSettings}
            disabled={isSaving || isApplyingPreset}
          >
            <RotateCcw size={14} className="mr-1.5" /> Khôi phục
          </Button>
          <Button
            size="sm"
            onClick={handleSave}
            isLoading={isSaving}
            className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm"
          >
            <Save size={15} className="mr-1.5" /> Lưu cài đặt
          </Button>
        </div>
      </div>

      {/* 1. Industry Presets */}
      <Card className="border border-gray-200 shadow-sm">
        <CardContent className="p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
                <Store size={18} className="text-blue-600" />
                Hồ sơ Ngành hàng & Loại hình kinh doanh
              </h2>
              <p className="text-xs text-gray-500">
                Lựa chọn mô hình kinh doanh để hệ thống áp dụng bộ thông số thực tế được tối ưu trước
              </p>
            </div>
            <Button
              size="sm"
              variant="outline"
              onClick={handleApplyPreset}
              isLoading={isApplyingPreset}
              className="border-blue-200 text-blue-700 hover:bg-blue-50"
            >
              <Sparkles size={14} className="mr-1.5 text-blue-600" /> Áp dụng hồ sơ đề xuất
            </Button>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-5">
            {(Object.keys(BUSINESS_TYPE_LABEL) as BusinessType[]).map((type) => {
              const Icon = BUSINESS_TYPE_ICONS[type];
              const isSelected = selectedType === type;
              return (
                <div
                  key={type}
                  onClick={() => setSelectedType(type)}
                  className={`cursor-pointer rounded-xl border p-4 transition-all ${
                    isSelected
                      ? 'border-blue-600 bg-blue-50/50 ring-2 ring-blue-600/20 shadow-sm'
                      : 'border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50/50'
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <div
                      className={`flex h-8 w-8 items-center justify-center rounded-lg ${
                        isSelected ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'
                      }`}
                    >
                      <Icon size={16} />
                    </div>
                    {isSelected && <CheckCircle2 size={16} className="text-blue-600" />}
                  </div>
                  <p className="text-xs font-semibold text-gray-900">{BUSINESS_TYPE_LABEL[type]}</p>
                </div>
              );
            })}
          </div>

          <div className="rounded-lg bg-gray-50 p-3.5 border border-gray-100 text-xs text-gray-600">
            <span className="font-semibold text-gray-800">Đặc điểm khuyến nghị: </span>
            {BUSINESS_TYPE_DESCRIPTION[selectedType]}
          </div>
        </CardContent>
      </Card>

      {/* 2. Optimization Weights */}
      <Card className="border border-gray-200 shadow-sm">
        <CardContent className="p-6 space-y-5">
          <div>
            <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
              <Sliders size={18} className="text-blue-600" />
              Trọng số Tối ưu hóa Lộ trình (Multi-criteria Objective)
            </h2>
            <p className="text-xs text-gray-500">
              Cân bằng giữa ưu tiên tốc độ giao hàng (rút ngắn thời gian) và cắt giảm chi phí (rút ngắn cự ly di chuyển)
            </p>
          </div>

          <div className="space-y-3 rounded-xl border border-gray-100 bg-gray-50/60 p-5">
            <div className="flex items-center justify-between text-sm font-medium">
              <span className="flex items-center gap-1.5 text-blue-700">
                <Clock size={16} /> Thời gian giao hàng: {timeWeightPercent}%
              </span>
              <span className="flex items-center gap-1.5 text-emerald-700">
                <Gauge size={16} /> Tiết kiệm chi phí & Quãng đường: {costWeightPercent}%
              </span>
            </div>

            <input
              type="range"
              min="0"
              max="1"
              step="0.05"
              value={timeWeight}
              onChange={(e) => setTimeWeight(parseFloat(e.target.value))}
              className="w-full accent-blue-600 cursor-pointer h-2 bg-gray-200 rounded-lg"
            />

            <div className="flex justify-between text-[11px] text-gray-400">
              <span>0% Thời gian (Tối đa hóa lợi nhuận)</span>
              <span>Cân bằng (50/50)</span>
              <span>100% Thời gian (Giao siêu tốc)</span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 3. Dispatch Constraints & Fleet Policies */}
      <Card className="border border-gray-200 shadow-sm">
        <CardContent className="p-6 space-y-5">
          <div>
            <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
              <Gauge size={18} className="text-blue-600" />
              Ràng buộc Gom đơn & Chính sách Đội xe
            </h2>
            <p className="text-xs text-gray-500">
              Kiểm soát số lượng kiện hàng trên mỗi chuyến xe và quy chế phân công tài xế
            </p>
          </div>

          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
            <div>
              <label className="text-xs font-semibold text-gray-700 block mb-1">
                Số đơn gộp tối đa mỗi tài xế / chuyến
              </label>
              <div className="flex items-center gap-3">
                <input
                  type="number"
                  min="1"
                  max="20"
                  value={maxOrders}
                  onChange={(e) => setMaxOrders(parseInt(e.target.value) || 1)}
                  className="w-24 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 font-semibold"
                />
                <span className="text-xs text-gray-500">đơn hàng / chuyến xe (Khuyến nghị 2 - 10)</span>
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-700 block mb-1">
                Thời gian chờ gom đơn tối đa trước khi xuất bến
              </label>
              <div className="flex items-center gap-3">
                <input
                  type="number"
                  min="1"
                  max="60"
                  value={maxWaitMinutes}
                  onChange={(e) => setMaxWaitMinutes(parseInt(e.target.value) || 1)}
                  className="w-24 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 font-semibold"
                />
                <span className="text-xs text-gray-500">phút (Kích hoạt điều phối ngay cả khi chưa đủ số đơn)</span>
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-700 block mb-1">
                Chiến lược xử lý đơn hỏa tốc (Urgent Orders)
              </label>
              <select
                value={urgentMode}
                onChange={(e) => setUrgentMode(e.target.value as any)}
                className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900"
              >
                <option value="IMMEDIATE_SINGLE">Gán ngay tài xế gần nhất (Khuyến nghị)</option>
                <option value="BATCH_IMMEDIATE">Kích hoạt giải toán lại toàn bộ lô gom ngay</option>
              </select>
            </div>

            <div className="space-y-3 pt-1">
              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={allowPool}
                  onChange={(e) => setAllowPool(e.target.checked)}
                  className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <div>
                  <span className="text-sm font-medium text-gray-900">Cho phép dùng Tài xế Pool (Sàn chung)</span>
                  <p className="text-[11px] text-gray-500">
                    Tự động huy động thêm tài xế Pool của sàn khi đội xe nội bộ quá tải hoặc thiếu người
                  </p>
                </div>
              </label>

              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={requireApproval}
                  onChange={(e) => setRequireApproval(e.target.checked)}
                  className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <div>
                  <span className="text-sm font-medium text-gray-900">Yêu cầu nhân viên duyệt trước khi phát xe</span>
                  <p className="text-[11px] text-gray-500">
                    Lộ trình tạo ra ở trạng thái DRAFT chờ nhân viên điều phối bấm Duyệt trên Web Admin
                  </p>
                </div>
              </label>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

