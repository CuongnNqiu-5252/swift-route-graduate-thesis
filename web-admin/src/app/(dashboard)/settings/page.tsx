'use client';

import { useLocale } from '@/i18n/provider';

import { useState, useEffect, useCallback } from 'react';
import * as Tabs from '@radix-ui/react-tabs';
import {
  BusinessType,
  TenantSettings,
  BUSINESS_TYPE_LABEL,
  BUSINESS_TYPE_DESCRIPTION,
} from '@/domain/entities/tenant-settings.entity';
import { tenantSettingsRepository } from '@/infrastructure/repositories/tenant-settings.repository';
import DeliveryZoneSection from '@/components/settings/delivery-zone-section';
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
  Clock,
  Gauge,
  CheckCircle2,
  MapPin,
  type LucideIcon,
} from 'lucide-react';
import { toast } from 'sonner';

const BUSINESS_TYPE_ICONS: Record<BusinessType, LucideIcon> = {
  RESTAURANT: Utensils,
  CAFE: Coffee,
  PHARMACY: Pill,
  RETAIL: ShoppingBag,
  OTHER: Store,
};

const SCHEDULED_HOURS = Array.from({ length: 24 }, (_, i) => {
  const h = i.toString().padStart(2, '0');
  return `${h}:00`;
});

export default function SettingsPage() {
  const { t } = useLocale();
  const [settings, setSettings] = useState<TenantSettings | null>(null);
  const [selectedType, setSelectedType] = useState<BusinessType>('OTHER');
  const [timeWeight, setTimeWeight] = useState(0.5);
  const [maxOrders, setMaxOrders] = useState(5);
  const [maxWaitMinutes, setMaxWaitMinutes] = useState(5);
  const [urgentMode, setUrgentMode] = useState<'IMMEDIATE_SINGLE' | 'BATCH_IMMEDIATE'>('IMMEDIATE_SINGLE');
  const [allowPool, setAllowPool] = useState(false);
  const [requireApproval, setRequireApproval] = useState(false);
  const [scheduledEnabled, setScheduledEnabled] = useState(true);
  const [scheduledTime, setScheduledTime] = useState('06:00');

  const [activeTab, setActiveTab] = useState<'general' | 'delivery-zone'>('general');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isApplyingPreset, setIsApplyingPreset] = useState(false);

  const applySettingsData = useCallback((data: TenantSettings) => {
    setSettings(data);
    setSelectedType(data.business_type);
    setTimeWeight(data.optimization_weights.time_weight);
    setMaxOrders(data.dispatch_config.max_orders_per_driver);
    setMaxWaitMinutes(Math.round(data.dispatch_config.max_wait_time_seconds / 60));
    setUrgentMode(data.dispatch_config.urgent_order_mode);
    setAllowPool(data.dispatch_config.allow_pool_drivers);
    setRequireApproval(data.dispatch_config.require_approval);
    setScheduledEnabled(data.dispatch_config.scheduled_dispatch_enabled ?? true);
    setScheduledTime(data.dispatch_config.scheduled_dispatch_time || '06:00');
  }, []);

  const reloadSettings = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await tenantSettingsRepository.getSettings();
      applySettingsData(data);
    } catch {
      toast.error(t("Không thể tải cấu hình doanh nghiệp"));
    } finally {
      setIsLoading(false);
    }
  }, [applySettingsData, t]);

  useEffect(() => {
    let mounted = true;
    tenantSettingsRepository.getSettings()
      .then((data) => {
        if (!mounted) return;
        applySettingsData(data);
      })
      .catch(() => {
        if (!mounted) return;
        toast.error(t("Không thể tải cấu hình doanh nghiệp"));
      })
      .finally(() => {
        if (!mounted) return;
        setIsLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [applySettingsData, t]);

  const handleApplyPreset = async () => {
    setIsApplyingPreset(true);
    try {
      await tenantSettingsRepository.applyPreset(selectedType);
      toast.success(`${t("Đã áp dụng cấu hình đề xuất cho ngành")} ${t(BUSINESS_TYPE_LABEL[selectedType])}`);
      await reloadSettings();
    } catch {
      toast.error(t("Lỗi khi áp dụng cấu hình đề xuất"));
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
          scheduled_dispatch_enabled: scheduledEnabled,
          scheduled_dispatch_time: scheduledTime,
        },
      });
      toast.success(t("Đã lưu cấu hình doanh nghiệp thành công!"));
      await reloadSettings();
    } catch {
      toast.error(t("Lỗi khi lưu cấu hình"));
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading || !settings) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="flex flex-col items-center gap-2">
          <div className="h-7 w-7 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
          <p className="text-sm text-gray-500">{t("Đang tải thông tin cấu hình...")}</p>
        </div>
      </div>
    );
  }

  const costWeightPercent = Math.round((1 - timeWeight) * 100);
  const timeWeightPercent = Math.round(timeWeight * 100);

  return (
    <div className="space-y-6 max-w-6xl">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-gray-200 pb-5">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">
            {t("Cài đặt Doanh nghiệp & Điều phối")}
          </h1>
          <p className="text-sm text-gray-500">
            {t("Quản trị loại hình kinh doanh, vùng phục vụ giao hàng, hàm mục tiêu và ràng buộc gom đơn")}
          </p>
        </div>
        {activeTab === 'general' && (
          <div className="flex items-center gap-2.5">
            <Button
              variant="outline"
              size="sm"
              onClick={reloadSettings}
              disabled={isSaving || isApplyingPreset}
            >
              <RotateCcw size={14} className="mr-1.5" />{" "}{t("Khôi phục")}
            </Button>
            <Button
              size="sm"
              onClick={handleSave}
              isLoading={isSaving}
              className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm"
            >
              <Save size={15} className="mr-1.5" />{" "}{t("Lưu cài đặt")}
            </Button>
          </div>
        )}
      </div>

      <Tabs.Root
        value={activeTab}
        onValueChange={(value) => setActiveTab(value as 'general' | 'delivery-zone')}
        className="space-y-6"
      >
        <Tabs.List
          aria-label={t("Danh mục cài đặt")}
          className="inline-flex rounded-lg border border-slate-200 bg-white p-1"
        >
          <Tabs.Trigger
            value="general"
            className="inline-flex items-center gap-2 rounded-md px-3.5 py-2 text-sm font-medium text-slate-600 transition-colors data-[state=active]:bg-slate-900 data-[state=active]:text-white"
          >
            <Sliders size={15} />
            {t("Cài đặt chung & Điều phối")}
          </Tabs.Trigger>
          <Tabs.Trigger
            value="delivery-zone"
            className="inline-flex items-center gap-2 rounded-md px-3.5 py-2 text-sm font-medium text-slate-600 transition-colors data-[state=active]:bg-slate-900 data-[state=active]:text-white"
          >
            <MapPin size={15} />
            {t("Vùng giao hàng")}
          </Tabs.Trigger>
        </Tabs.List>

        <Tabs.Content value="general" className="space-y-6 focus:outline-none">

      {/* 1. Industry Presets */}
      <Card className="border border-gray-200 shadow-sm">
        <CardContent className="p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
                <Store size={18} className="text-blue-600" />
                {t("Hồ sơ Ngành hàng & Loại hình kinh doanh")}
              </h2>
              <p className="text-xs text-gray-500">
                {t("Lựa chọn mô hình kinh doanh để hệ thống áp dụng bộ thông số thực tế được tối ưu trước")}
              </p>
            </div>
            <Button
              size="sm"
              variant="outline"
              onClick={handleApplyPreset}
              isLoading={isApplyingPreset}
              className="border-blue-200 text-blue-700 hover:bg-blue-50"
            >
              <Sparkles size={14} className="mr-1.5 text-blue-600" />{" "}{t("Áp dụng hồ sơ đề xuất")}
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
                  <p className="text-xs font-semibold text-gray-900">{t(BUSINESS_TYPE_LABEL[type])}</p>
                </div>
              );
            })}
          </div>

          <div className="rounded-lg bg-gray-50 p-3.5 border border-gray-100 text-xs text-gray-600">
            <span className="font-semibold text-gray-800">{t("Đặc điểm khuyến nghị:")}{" "}</span>
            {t(BUSINESS_TYPE_DESCRIPTION[selectedType])}
          </div>
        </CardContent>
      </Card>

      {/* 2. Optimization Weights */}
      <Card className="border border-gray-200 shadow-sm">
        <CardContent className="p-6 space-y-5">
          <div>
            <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
              <Sliders size={18} className="text-blue-600" />
              {t("Trọng số Tối ưu hóa Lộ trình (Multi-criteria Objective)")}
            </h2>
            <p className="text-xs text-gray-500">
              {t("Cân bằng giữa ưu tiên tốc độ giao hàng (rút ngắn thời gian) và cắt giảm chi phí (rút ngắn cự ly di chuyển)")}
            </p>
          </div>

          <div className="space-y-3 rounded-xl border border-gray-100 bg-gray-50/60 p-5">
            <div className="flex items-center justify-between text-sm font-medium">
              <span className="flex items-center gap-1.5 text-blue-700">
                <Clock size={16} />{" "}{t("Thời gian giao hàng:")}{" "}{timeWeightPercent}%
              </span>
              <span className="flex items-center gap-1.5 text-emerald-700">
                <Gauge size={16} />{" "}{t("Tiết kiệm chi phí & Quãng đường:")}{" "}{costWeightPercent}%
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
              <span>{t("0% Thời gian (Tối đa hóa lợi nhuận)")}</span>
              <span>{t("Cân bằng (50/50)")}</span>
              <span>{t("100% Thời gian (Giao siêu tốc)")}</span>
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
              {t("Ràng buộc Gom đơn & Chính sách Đội xe")}
            </h2>
            <p className="text-xs text-gray-500">
              {t("Kiểm soát số lượng kiện hàng trên mỗi chuyến xe và quy chế phân công tài xế")}
            </p>
          </div>

          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
            <div>
              <label className="text-xs font-semibold text-gray-700 block mb-1">
                {t("Số đơn gộp tối đa mỗi tài xế / chuyến")}
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
                <span className="text-xs text-gray-500">{t("đơn hàng / chuyến xe (Khuyến nghị 2 - 10)")}</span>
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-700 block mb-1">
                {t("Thời gian chờ gom đơn tối đa trước khi xuất bến")}
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
                <span className="text-xs text-gray-500">{t("phút (Kích hoạt điều phối ngay cả khi chưa đủ số đơn)")}</span>
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-700 block mb-1">
                {t("Chiến lược xử lý đơn hỏa tốc (Urgent Orders)")}
              </label>
              <select
                value={urgentMode}
                onChange={(e) => setUrgentMode(e.target.value as 'IMMEDIATE_SINGLE' | 'BATCH_IMMEDIATE')}
                className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900"
              >
                <option value="IMMEDIATE_SINGLE">{t("Gán ngay tài xế gần nhất (Khuyến nghị)")}</option>
                <option value="BATCH_IMMEDIATE">{t("Kích hoạt giải toán lại toàn bộ lô gom ngay")}</option>
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
                  <span className="text-sm font-medium text-gray-900">{t("Cho phép dùng Tài xế Pool (Sàn chung)")}</span>
                  <p className="text-[11px] text-gray-500">
                    {t("Tự động huy động thêm tài xế Pool của sàn khi đội xe nội bộ quá tải hoặc thiếu người")}
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
                  <span className="text-sm font-medium text-gray-900">{t("Yêu cầu nhân viên duyệt trước khi phát xe")}</span>
                  <p className="text-[11px] text-gray-500">
                    {t("Lộ trình tạo ra ở trạng thái DRAFT chờ nhân viên điều phối bấm Duyệt trên Web Admin")}
                  </p>
                </div>
              </label>

              <div className="pt-3 border-t border-gray-100 space-y-3">
                <label className="flex items-center gap-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={scheduledEnabled}
                    onChange={(e) => setScheduledEnabled(e.target.checked)}
                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  <div>
                    <span className="text-sm font-medium text-gray-900">
                      {t("Tự động gom đơn theo ca cố định hàng ngày (Scheduled Wave)")}
                    </span>
                    <p className="text-[11px] text-gray-500">
                      {t("Hệ thống sẽ tự động quét và phân tuyến toàn bộ đơn hàng đang chờ vào đúng khung giờ được chọn")}
                    </p>
                  </div>
                </label>

                {scheduledEnabled && (
                  <div className="ml-7 flex flex-wrap items-center gap-3 bg-gray-50 p-3 rounded-lg border border-gray-200">
                    <label className="text-xs font-semibold text-gray-700">
                      {t("Khung giờ xuất bến hàng ngày:")}
                    </label>
                    <select
                      value={scheduledTime}
                      onChange={(e) => setScheduledTime(e.target.value)}
                      className="w-32 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm font-semibold text-gray-900 shadow-sm focus:border-blue-500 focus:ring-blue-500"
                    >
                      {SCHEDULED_HOURS.map((hour) => (
                        <option key={hour} value={hour}>
                          {hour}
                        </option>
                      ))}
                    </select>
                    <span className="text-xs text-gray-500">
                      {t("(Định dạng 24h, theo múi giờ Việt Nam GMT+7)")}
                    </span>
                  </div>
                )}
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
        </Tabs.Content>

        <Tabs.Content value="delivery-zone" className="focus:outline-none">
          {activeTab === 'delivery-zone' && <DeliveryZoneSection />}
        </Tabs.Content>
      </Tabs.Root>
    </div>
  );
}

