'use client';

import { useState } from 'react';
import Link from 'next/link';
import {
  AlertCircle,
  CheckCircle2,
  MapPin,
  Radar,
  RotateCcw,
  Save,
  ShieldAlert,
  ShieldCheck,
} from 'lucide-react';
import { useDeliveryZone } from '@/application/hooks/use-delivery-zone';
import {
  ENFORCEMENT_MODE_OPTIONS,
  QUICK_RADIUS_KM,
} from '@/domain/entities/delivery-zone.entity';
import { goongMapsClient, type MapPlacePrediction } from '@/infrastructure/maps/goong-maps.client';
import { Button } from '@/components/ui/button';
import DeliveryZoneMapEditor, { type MapInteractionMode } from './delivery-zone-map-editor';

export default function DeliveryZoneSection() {
  const {
    savedZone,
    defaultPickup,
    draft,
    centerAddress,
    isLoading,
    loadError,
    isSaving,
    isChecking,
    checkResult,
    isDirty,
    reload,
    updateCenter,
    updateRadius,
    updateEnforcementMode,
    updateActive,
    resetToSaved,
    resetCenterToDefaultPickup,
    save,
    checkTestPoint,
  } = useDeliveryZone();

  const [interactionMode, setInteractionMode] = useState<MapInteractionMode>('edit');
  const [emptySearchInput, setEmptySearchInput] = useState('');
  const [emptyPredictions, setEmptyPredictions] = useState<MapPlacePrediction[]>([]);
  const [searchingInitial, setSearchingInitial] = useState(false);

  async function handleEmptySearchChange(value: string) {
    setEmptySearchInput(value);
    if (value.trim().length < 3) {
      setEmptyPredictions([]);
      return;
    }
    setSearchingInitial(true);
    try {
      const items = await goongMapsClient.searchPlaces(
        value,
        undefined,
        'initial-zone-center',
      );
      setEmptyPredictions(items);
    } catch {
      setEmptyPredictions([]);
    } finally {
      setSearchingInitial(false);
    }
  }

  async function handleSelectInitialPlace(prediction: MapPlacePrediction) {
    setSearchingInitial(true);
    try {
      const detail = await goongMapsClient.getPlaceDetail(prediction.place_id, 'initial-zone-center');
      if (detail) {
        updateCenter(detail.lat, detail.lng, detail.address || prediction.description);
        setEmptyPredictions([]);
      }
    } finally {
      setSearchingInitial(false);
    }
  }

  if (isLoading) {
    return (
      <div className="flex h-72 items-center justify-center rounded-lg border border-slate-200 bg-white">
        <p className="text-sm text-slate-500">Đang tải cấu hình vùng giao hàng…</p>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="rounded-lg border border-rose-200 bg-rose-50/70 p-6">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <AlertCircle className="mt-0.5 h-5 w-5 text-rose-600" />
            <div>
              <h2 className="text-sm font-semibold text-rose-900">Không thể tải vùng giao hàng</h2>
              <p className="mt-1 text-sm text-rose-700">{loadError}</p>
            </div>
          </div>
          <Button variant="outline" size="sm" onClick={() => void reload()}>
            Thử lại
          </Button>
        </div>
      </div>
    );
  }

  if (!draft) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-8">
        <div className="mx-auto max-w-xl space-y-5 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full border border-slate-200 bg-slate-50 text-slate-700">
            <MapPin className="h-5 w-5" />
          </div>
          <div className="space-y-1.5">
            <h2 className="text-base font-semibold text-slate-900">
              Chưa có tọa độ tâm để hiển thị bản đồ
            </h2>
            <p className="text-sm text-slate-600">
              Doanh nghiệp chưa có vùng giao hàng đã lưu và chưa có điểm lấy hàng hoạt động. Hãy thêm kho lấy hàng mặc định hoặc tìm một địa điểm thực tế để bắt đầu khoanh vùng phục vụ.
            </p>
          </div>

          <div className="relative mx-auto max-w-md text-left">
            <input
              value={emptySearchInput}
              onChange={(event) => void handleEmptySearchChange(event.target.value)}
              placeholder="Tìm địa chỉ cửa hàng hoặc khu vực phục vụ…"
              className="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none focus:border-slate-500"
            />
            {searchingInitial && (
              <p className="mt-1.5 text-xs text-slate-500">Đang tìm địa điểm…</p>
            )}
            {emptyPredictions.length > 0 && (
              <ul className="mt-1.5 max-h-52 overflow-y-auto rounded-lg border border-slate-200 bg-white py-1">
                {emptyPredictions.map((prediction) => (
                  <li key={prediction.place_id}>
                    <button
                      type="button"
                      onClick={() => void handleSelectInitialPlace(prediction)}
                      className="flex w-full items-start gap-2 px-3 py-2 text-left text-sm text-slate-800 hover:bg-slate-50"
                    >
                      <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-[#17766c]" />
                      <span className="truncate">{prediction.description}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="flex flex-wrap items-center justify-center gap-3 pt-1">
            <Link
              href="/locations"
              className="inline-flex items-center gap-1.5 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
            >
              Thiết lập điểm lấy hàng mặc định
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-5 py-4">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <Radar className="h-4 w-4 text-[#17766c]" />
            <h2 className="text-base font-semibold text-slate-900">
              Phạm vi giao hàng trực quan
            </h2>
            <span
              className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                savedZone?.configured
                  ? draft.isActive
                    ? 'bg-emerald-50 text-emerald-700'
                    : 'bg-slate-100 text-slate-600'
                  : 'bg-amber-50 text-amber-800'
              }`}
            >
              {savedZone?.configured
                ? draft.isActive
                  ? 'Đang áp dụng'
                  : 'Đang tạm tắt'
                : 'Nháp chưa lưu'}
            </span>
          </div>
          <p className="text-xs text-slate-500">
            Cấu hình hình tròn trên bản đồ để tự động kiểm tra điểm giao khi tạo vận đơn.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {savedZone?.configured && (
            <Button
              variant="outline"
              size="sm"
              onClick={resetToSaved}
              disabled={!isDirty || isSaving}
            >
              <RotateCcw className="h-3.5 w-3.5" />
              Hoàn tác
            </Button>
          )}
          <Button
            size="sm"
            onClick={() => void save()}
            isLoading={isSaving}
            disabled={!isDirty && Boolean(savedZone?.configured)}
            className="bg-[#17766c] text-white hover:bg-[#136058] disabled:bg-slate-300"
          >
            <Save className="h-3.5 w-3.5" />
            Lưu vùng giao hàng
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-12">
        <div className="lg:col-span-8">
          <DeliveryZoneMapEditor
            centerLat={draft.centerLat}
            centerLng={draft.centerLng}
            radiusKm={draft.radiusKm}
            isActive={draft.isActive}
            enforcementMode={draft.enforcementMode}
            centerAddress={centerAddress}
            interactionMode={interactionMode}
            onInteractionModeChange={setInteractionMode}
            onCenterChange={updateCenter}
            onRadiusChange={updateRadius}
            onTestPoint={checkTestPoint}
            isCheckingPoint={isChecking}
            checkResult={checkResult}
          />
        </div>

        <div className="space-y-4 lg:col-span-4">
          <div className="rounded-lg border border-slate-200 bg-white p-5 space-y-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-slate-900">Bán kính phục vụ</p>
                <p className="text-xs text-slate-500">Điều chỉnh trực tiếp hoặc chọn nhanh</p>
              </div>
              <span className="num text-lg font-semibold text-[#17766c]">
                {draft.radiusKm.toFixed(1)} km
              </span>
            </div>

            <div className="flex flex-wrap gap-1.5">
              {QUICK_RADIUS_KM.map((preset) => {
                const selected = Math.abs(draft.radiusKm - preset) < 0.05;
                return (
                  <button
                    key={preset}
                    type="button"
                    onClick={() => updateRadius(preset)}
                    className={`num rounded-md border px-2.5 py-1 text-xs font-medium transition-colors ${
                      selected
                        ? 'border-[#17766c] bg-[#17766c]/10 text-[#17766c]'
                        : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    {preset} km
                  </button>
                );
              })}
            </div>

            <div className="space-y-1.5">
              <input
                type="range"
                min="0.5"
                max="50"
                step="0.5"
                value={draft.radiusKm}
                onChange={(event) => updateRadius(parseFloat(event.target.value))}
                aria-label="Thanh kéo bán kính vùng giao hàng"
                className="h-2 w-full cursor-pointer accent-[#17766c]"
                style={{ minHeight: 'auto' }}
              />
              <div className="num flex justify-between text-[11px] text-slate-400">
                <span>0.5 km</span>
                <span>25 km</span>
                <span>50 km</span>
              </div>
            </div>

            <div className="border-t border-slate-100 pt-3.5 space-y-2">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-medium text-slate-700">Tâm vùng hiện tại</p>
                {defaultPickup && (
                  <button
                    type="button"
                    onClick={resetCenterToDefaultPickup}
                    className="text-xs font-medium text-[#17766c] hover:underline"
                  >
                    Đặt theo kho mặc định
                  </button>
                )}
              </div>
              <p className="text-xs leading-relaxed text-slate-600">
                {centerAddress || 'Đã chọn tâm vùng trên bản đồ (chưa có địa chỉ văn bản).'}
              </p>
            </div>
          </div>

          <div className="rounded-lg border border-slate-200 bg-white p-5 space-y-4">
            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={draft.isActive}
                onChange={(event) => updateActive(event.target.checked)}
                className="mt-0.5 h-4 w-4 rounded border-slate-300 text-[#17766c]"
              />
              <div>
                <span className="text-sm font-semibold text-slate-900">
                  Kích hoạt kiểm tra vùng giao hàng
                </span>
                <p className="text-xs text-slate-500">
                  Khi tắt, đơn hàng ở mọi địa điểm đều được tiếp nhận bình thường.
                </p>
              </div>
            </label>

            <div className="border-t border-slate-100 pt-3.5 space-y-2.5">
              <p className="text-xs font-medium text-slate-700">Chế độ xử lý đơn ngoài vùng</p>
              <div className="space-y-2">
                {ENFORCEMENT_MODE_OPTIONS.map((option) => {
                  const selected = draft.enforcementMode === option.value;
                  return (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => updateEnforcementMode(option.value)}
                      className={`w-full rounded-lg border p-3 text-left transition-colors ${
                        selected
                          ? 'border-[#17766c] bg-[#17766c]/5'
                          : 'border-slate-200 bg-white hover:bg-slate-50'
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-sm font-medium text-slate-900">{option.label}</span>
                        {option.value === 'STRICT_BLOCK' ? (
                          <ShieldAlert className="h-4 w-4 text-slate-600" />
                        ) : (
                          <ShieldCheck className="h-4 w-4 text-slate-600" />
                        )}
                      </div>
                      <p className="mt-1 text-xs text-slate-500">{option.description}</p>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          {checkResult && (
            <div
              className={`rounded-lg border p-4 text-xs ${
                checkResult.allowed
                  ? checkResult.in_zone
                    ? 'border-emerald-200 bg-emerald-50/70 text-emerald-900'
                    : 'border-amber-200 bg-amber-50/70 text-amber-900'
                  : 'border-rose-200 bg-rose-50/70 text-rose-900'
              }`}
            >
              <div className="flex items-center gap-2 font-semibold">
                {checkResult.allowed ? (
                  <CheckCircle2 className="h-4 w-4 shrink-0" />
                ) : (
                  <AlertCircle className="h-4 w-4 shrink-0" />
                )}
                <span>Kết quả kiểm tra điểm giao</span>
              </div>
              <p className="mt-1.5 leading-relaxed">{checkResult.message}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
