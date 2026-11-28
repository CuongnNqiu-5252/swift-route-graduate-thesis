'use client';

import { useLocale } from '@/i18n/provider';

import { useAuth } from '@/application/contexts/auth-context';
import { AccessDeniedNotice, PermissionGuard } from '@/components/auth/permission-guard';
import DeliveryZoneSection from '@/components/settings/delivery-zone-section';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import type { TenantSettings } from '@/domain/entities/tenant-settings.entity';
import { tenantSettingsRepository } from '@/infrastructure/repositories/tenant-settings.repository';
import * as Tabs from '@radix-ui/react-tabs';
import {
  Clock,
  Gauge,
  MapPin,
  RotateCcw,
  Save,
  Sliders,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';

const ROUTE_PRIORITIES = [
  { label: 'Ưu tiên quãng đường', timeWeight: 0.2 },
  { label: 'Cân bằng', timeWeight: 0.5 },
  { label: 'Ưu tiên thời gian', timeWeight: 0.8 },
] as const;

export default function SettingsPage() {
  const { t } = useLocale();
  const { hasPermission } = useAuth();
  const canEdit = hasPermission('tenant:settings:manage');
  const [settings, setSettings] = useState<TenantSettings | null>(null);
  const [timeWeight, setTimeWeight] = useState(0.5);
  const [maxOrders, setMaxOrders] = useState(5);
  const [maxWaitMinutes, setMaxWaitMinutes] = useState(5);
  const [autoDispatchEnabled, setAutoDispatchEnabled] = useState(true);

  const [activeTab, setActiveTab] = useState<'general' | 'delivery-zone'>('general');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  const applySettingsData = useCallback((data: TenantSettings) => {
    setSettings(data);
    setTimeWeight(data.optimization_weights.time_weight);
    setMaxOrders(data.dispatch_config.max_orders_per_driver);
    setMaxWaitMinutes(data.dispatch_config.max_wait_time_seconds / 60);
    setAutoDispatchEnabled(data.dispatch_config.auto_dispatch_enabled);
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

  const handleSave = async () => {
    if (!canEdit || !settings) return;
    setIsSaving(true);
    try {
      const costWeight = Number((1.0 - timeWeight).toFixed(2));
      await tenantSettingsRepository.updateSettings({
        optimization_weights: timeWeight === settings.optimization_weights.time_weight ? settings.optimization_weights : {
          time_weight: Number(timeWeight.toFixed(2)),
          cost_weight: costWeight,
        },
        dispatch_config: {
          ...settings.dispatch_config,
          auto_dispatch_enabled: autoDispatchEnabled,
          max_orders_per_driver: maxOrders,
          max_wait_time_seconds: Math.round(maxWaitMinutes * 60),
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

  if (!hasPermission('tenant:settings:read')) {
    return <AccessDeniedNotice requiredPermission="tenant:settings:read" />;
  }

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
            {t("Chọn ưu tiên lộ trình, quy tắc điều phối và vùng giao hàng.")}
          </p>
        </div>
        {activeTab === 'general' && (
          <PermissionGuard permission="tenant:settings:manage">
            <div className="flex items-center gap-2.5">
              <Button
                variant="outline"
                size="sm"
                onClick={reloadSettings}
                disabled={isSaving}
              >
                <RotateCcw size={14} className="mr-1.5" />{" "}{t("Bỏ thay đổi")}
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
          </PermissionGuard>
        )}
      </div>

      <Tabs.Root
        value={activeTab}
        onValueChange={(value) => setActiveTab(value as 'general' | 'delivery-zone')}
        className="space-y-6"
      >
        <Tabs.List
          aria-label={t("Danh mục cài đặt")}
          className="inline-flex rounded-sm border border-slate-200 bg-white p-1"
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
          <Card className="border border-gray-200 shadow-sm">
            <CardContent className="p-6 space-y-5">
              <div>
                <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
                  <Sliders size={18} className="text-blue-600" />
                  {t("Ưu tiên lộ trình")}
                </h2>
                <p className="text-xs text-gray-500">
                  {t("Chọn cấu hình có sẵn hoặc chỉnh tỷ lệ giữa thời gian và quãng đường.")}
                </p>
              </div>

              <div role="group" aria-label={t("Ưu tiên lộ trình")} className="flex flex-wrap gap-2">
                {ROUTE_PRIORITIES.map(({ label, timeWeight: weight }) => (
                  <Button
                    key={label}
                    type="button"
                    size="sm"
                    variant={timeWeight === weight ? 'default' : 'outline'}
                    aria-pressed={timeWeight === weight}
                    disabled={!canEdit || isSaving}
                    onClick={() => setTimeWeight(weight)}
                  >
                    {t(label)}
                  </Button>
                ))}
              </div>

              <div className="space-y-3 rounded-xl border border-gray-100 bg-gray-50/60 p-5">
                <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 text-sm font-medium">
                  <span className="flex items-center gap-1.5 text-blue-700">
                    <Clock size={16} />{" "}{t("Thời gian")}{" "}{timeWeightPercent}%
                  </span>
                  <span className="flex items-center gap-1.5 text-emerald-700">
                    <Gauge size={16} />{" "}{t("Quãng đường")}{" "}{costWeightPercent}%
                  </span>
                </div>

                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.01"
                  aria-label={t("Mức ưu tiên thời gian")}
                  aria-valuetext={`${t("Thời gian")} ${timeWeightPercent}% · ${t("Quãng đường")} ${costWeightPercent}%`}
                  disabled={!canEdit || isSaving}
                  value={timeWeight}
                  onChange={(e) => setTimeWeight(parseFloat(e.target.value))}
                  className="w-full accent-blue-600 cursor-pointer h-2 bg-gray-200 rounded-sm"
                />
              </div>
            </CardContent>
          </Card>

          <Card className="border border-gray-200 shadow-sm">
            <CardContent className="p-6 space-y-5">
              <div>
                <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
                  <Gauge size={18} className="text-blue-600" />
                  {t("Quy tắc điều phối")}
                </h2>
                <p className="text-xs text-gray-500">
                  {t("Kiểm soát số lượng kiện hàng trên mỗi chuyến xe và quy chế phân công tài xế")}
                </p>
              </div>

              <fieldset disabled={!canEdit || isSaving} className="grid grid-cols-1 gap-6 sm:grid-cols-2">
                <div>
                  <label htmlFor="max-orders" className="text-xs font-semibold text-gray-700 block mb-1">
                    {t("Số đơn gộp tối đa mỗi tài xế / chuyến")}
                  </label>
                  <div className="flex items-center gap-3">
                    <input
                      type="number"
                      id="max-orders"
                      min="1"
                      max="20"
                      value={maxOrders}
                      onChange={(e) => setMaxOrders(parseInt(e.target.value) || 1)}
                      className="w-24 rounded-sm border border-gray-300 px-3 py-2 text-sm text-gray-900 font-semibold"
                    />
                    <span className="text-xs text-gray-500">{t("đơn hàng / chuyến xe (Khuyến nghị 2 - 10)")}</span>
                  </div>
                </div>

                <div>
                  <label htmlFor="max-batch-wait" className="text-xs font-semibold text-gray-700 block mb-1">
                    {t("Thời gian chờ gom đơn tối đa trước khi xuất bến")}
                  </label>
                  <div className="flex items-center gap-3">
                    <input
                      type="number"
                      id="max-batch-wait"
                      min="0.5"
                      max="60"
                      step="any"
                      value={maxWaitMinutes}
                      onChange={(e) => setMaxWaitMinutes(e.target.valueAsNumber || 0.5)}
                      className="w-24 rounded-sm border border-gray-300 px-3 py-2 text-sm text-gray-900 font-semibold"
                    />
                    <span className="text-xs text-gray-500">{t("phút (Kích hoạt điều phối ngay cả khi chưa đủ số đơn)")}</span>
                  </div>
                </div>

                <div className="border-t border-gray-100 pt-3 sm:col-span-2">
                  <label className="flex items-center gap-3 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={autoDispatchEnabled}
                      onChange={(e) => setAutoDispatchEnabled(e.target.checked)}
                      className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                    />
                    <div>
                      <span className="text-sm font-medium text-gray-900">{t("Tự động gom đơn và điều phối")}</span>
                      <p className="text-xs text-gray-600">
                        {t("Chạy theo số đơn, thời gian chờ hoặc mức ưu tiên. Lịch cố định được quản lý trong Operation Flow.")}
                      </p>
                    </div>
                  </label>
                </div>
              </fieldset>
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
