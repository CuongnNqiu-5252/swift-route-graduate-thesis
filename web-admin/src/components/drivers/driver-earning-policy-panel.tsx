'use client';

import { useMemo, useState } from 'react';
import { toast } from 'sonner';
import { useLocale } from '@/i18n/provider';
import { EarningPolicy } from '@/domain/entities/earning.entity';
import { earningRepository } from '@/infrastructure/repositories/earning.repository';
import { Button } from '@/components/ui/button';
import { formatVnd } from './driver-formatters';

interface DriverEarningPolicyPanelProps {
  policy: EarningPolicy | null;
  loading: boolean;
  error: string | null;
  onSaved: () => Promise<void>;
  onReload: () => Promise<void>;
}

interface PolicyDraft {
  base_amount: string;
  per_km_amount: string;
  minimum_amount: string;
  heavy_weight_threshold_kg: string;
  heavy_bonus_amount: string;
}

function toDraft(policy: EarningPolicy): PolicyDraft {
  return {
    base_amount: String(policy.base_amount),
    per_km_amount: String(policy.per_km_amount),
    minimum_amount: String(policy.minimum_amount),
    heavy_weight_threshold_kg: String(policy.heavy_weight_threshold_kg),
    heavy_bonus_amount: String(policy.heavy_bonus_amount),
  };
}

function parseNonNegative(value: string): number | null {
  if (value.trim() === '') return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) return null;
  return parsed;
}

/** Configuration form and interactive trip-payout calculator for driver earnings. */
export function DriverEarningPolicyPanel({
  policy,
  loading,
  error,
  onSaved,
  onReload,
}: DriverEarningPolicyPanelProps) {
  const { t } = useLocale();

  if (loading && !policy) {
    return (
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="h-80 animate-pulse rounded-xl border border-slate-200 bg-slate-50" />
        <div className="h-80 animate-pulse rounded-xl border border-slate-200 bg-slate-50" />
      </div>
    );
  }

  if (error && !policy) {
    return (
      <div
        role="alert"
        className="flex flex-col gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 sm:flex-row sm:items-center sm:justify-between"
      >
        <span>{error}</span>
        <Button size="sm" variant="outline" onClick={() => void onReload()}>
          {t('Thử lại')}
        </Button>
      </div>
    );
  }

  if (!policy) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center text-sm text-slate-500">
        {t('Chưa có dữ liệu chính sách thu nhập.')}
      </div>
    );
  }

  const policyKey = [
    policy.base_amount,
    policy.per_km_amount,
    policy.minimum_amount,
    policy.heavy_weight_threshold_kg,
    policy.heavy_bonus_amount,
  ].join('|');

  return <PolicyEditor key={policyKey} policy={policy} onSaved={onSaved} />;
}

function PolicyEditor({
  policy,
  onSaved,
}: {
  policy: EarningPolicy;
  onSaved: () => Promise<void>;
}) {
  const { t, intlLocale } = useLocale();
  const [draft, setDraft] = useState<PolicyDraft>(() => toDraft(policy));
  const [saving, setSaving] = useState(false);
  const [sampleKm, setSampleKm] = useState('4.5');
  const [sampleKg, setSampleKg] = useState('6');

  const parsed = useMemo(() => {
    const base_amount = parseNonNegative(draft.base_amount);
    const per_km_amount = parseNonNegative(draft.per_km_amount);
    const minimum_amount = parseNonNegative(draft.minimum_amount);
    const heavy_weight_threshold_kg = parseNonNegative(draft.heavy_weight_threshold_kg);
    const heavy_bonus_amount = parseNonNegative(draft.heavy_bonus_amount);
    const thresholdValid = heavy_weight_threshold_kg !== null && heavy_weight_threshold_kg >= 0.01;
    const allValid =
      base_amount !== null &&
      per_km_amount !== null &&
      minimum_amount !== null &&
      thresholdValid &&
      heavy_bonus_amount !== null;

    return {
      base_amount,
      per_km_amount,
      minimum_amount,
      heavy_weight_threshold_kg,
      heavy_bonus_amount,
      thresholdValid,
      allValid,
    };
  }, [draft]);

  const isDirty = useMemo(() => {
    if (!parsed.allValid) return true;
    return (
      parsed.base_amount !== policy.base_amount ||
      parsed.per_km_amount !== policy.per_km_amount ||
      parsed.minimum_amount !== policy.minimum_amount ||
      parsed.heavy_weight_threshold_kg !== policy.heavy_weight_threshold_kg ||
      parsed.heavy_bonus_amount !== policy.heavy_bonus_amount
    );
  }, [parsed, policy]);

  const simulation = useMemo(() => {
    const km = parseNonNegative(sampleKm) ?? 0;
    const kg = parseNonNegative(sampleKg) ?? 0;
    const base = parsed.base_amount ?? policy.base_amount;
    const perKm = parsed.per_km_amount ?? policy.per_km_amount;
    const minimum = parsed.minimum_amount ?? policy.minimum_amount;
    const threshold = parsed.heavy_weight_threshold_kg ?? policy.heavy_weight_threshold_kg;
    const bonusConfig = parsed.heavy_bonus_amount ?? policy.heavy_bonus_amount;

    const distanceAmount = Math.round(km * perKm);
    const heavyApplied = kg > threshold;
    const bonusAmount = heavyApplied ? bonusConfig : 0;
    const subtotal = base + distanceAmount + bonusAmount;
    const floored = Math.max(subtotal, minimum);
    const rounded = Math.round(floored / 1000) * 1000;

    return {
      km,
      kg,
      base,
      perKm,
      minimum,
      threshold,
      distanceAmount,
      heavyApplied,
      bonusAmount,
      subtotal,
      flooredByMinimum: minimum > subtotal,
      rounded,
    };
  }, [parsed, policy, sampleKm, sampleKg]);

  async function save() {
    if (!parsed.allValid) {
      toast.error(t('Vui lòng nhập đầy đủ giá trị hợp lệ (ngưỡng hàng nặng từ 0,01 kg trở lên).'));
      return;
    }
    setSaving(true);
    try {
      await earningRepository.updatePolicy({
        base_amount: parsed.base_amount!,
        per_km_amount: parsed.per_km_amount!,
        minimum_amount: parsed.minimum_amount!,
        heavy_weight_threshold_kg: parsed.heavy_weight_threshold_kg!,
        heavy_bonus_amount: parsed.heavy_bonus_amount!,
      });
      toast.success(t('Đã cập nhật chính sách thu nhập'));
      await onSaved();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('Không thể lưu chính sách'));
    } finally {
      setSaving(false);
    }
  }

  function renderField({
    key,
    label,
    suffix,
    step = '1000',
    min = '0',
    formattedHint,
    errorText,
  }: {
    key: keyof PolicyDraft;
    label: string;
    suffix: string;
    step?: string;
    min?: string;
    formattedHint?: string;
    errorText?: string;
  }) {
    const id = `policy-${key}`;
    return (
      <div className="space-y-1.5">
        <div className="flex items-baseline justify-between gap-2">
          <label htmlFor={id} className="text-sm font-medium text-slate-700">
            {label}
          </label>
          {formattedHint && (
            <span className="text-xs tabular-nums text-slate-500">{formattedHint}</span>
          )}
        </div>
        <div
          className={`flex overflow-hidden rounded-lg border bg-white focus-within:ring-2 ${
            errorText
              ? 'border-red-400 focus-within:border-red-500 focus-within:ring-red-500/20'
              : 'border-slate-300 focus-within:border-blue-500 focus-within:ring-blue-500/20'
          }`}
        >
          <input
            id={id}
            type="number"
            min={min}
            step={step}
            value={draft[key]}
            onChange={(event) => setDraft((current) => ({ ...current, [key]: event.target.value }))}
            className="min-w-0 flex-1 bg-transparent px-3 py-2 text-sm tabular-nums text-slate-950 outline-none"
          />
          <span className="border-l border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
            {suffix}
          </span>
        </div>
        {errorText && <p className="text-xs text-red-600">{errorText}</p>}
      </div>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
      <section className="rounded-xl border border-slate-200 bg-white p-6">
        <div>
          <h2 className="text-base font-semibold text-slate-950">{t('Chính sách thu nhập')}</h2>
          <p className="mt-1 text-sm text-slate-500">{t('Áp dụng cho nhiệm vụ mới')}</p>
        </div>

        <div className="mt-6 space-y-6">
          <div>
            <h3 className="text-sm font-medium text-slate-900">{t('Định mức cơ bản')}</h3>
            <div className="mt-3 grid gap-4 sm:grid-cols-2">
              {renderField({
                key: 'base_amount',
                label: t('Phí cơ bản'),
                suffix: t('đ'),
                formattedHint:
                  parsed.base_amount !== null ? formatVnd(parsed.base_amount, intlLocale) : undefined,
                errorText:
                  draft.base_amount !== '' && parsed.base_amount === null ? t('Giá trị không âm') : undefined,
              })}
              {renderField({
                key: 'per_km_amount',
                label: t('Đơn giá khoảng cách'),
                suffix: t('đ/km'),
                step: '500',
                formattedHint:
                  parsed.per_km_amount !== null ? formatVnd(parsed.per_km_amount, intlLocale) : undefined,
                errorText:
                  draft.per_km_amount !== '' && parsed.per_km_amount === null ? t('Giá trị không âm') : undefined,
              })}
              <div className="sm:col-span-2 sm:max-w-[calc(50%-0.5rem)]">
                {renderField({
                  key: 'minimum_amount',
                  label: t('Mức tối thiểu'),
                  suffix: t('đ'),
                  formattedHint:
                    parsed.minimum_amount !== null ? formatVnd(parsed.minimum_amount, intlLocale) : undefined,
                  errorText:
                    draft.minimum_amount !== '' && parsed.minimum_amount === null
                      ? t('Giá trị không âm')
                      : undefined,
                })}
              </div>
            </div>
          </div>

          <div className="border-t border-slate-100 pt-5">
            <h3 className="text-sm font-medium text-slate-900">{t('Phụ phí hàng nặng')}</h3>
            <div className="mt-3 grid gap-4 sm:grid-cols-2">
              {renderField({
                key: 'heavy_weight_threshold_kg',
                label: t('Ngưỡng hàng nặng'),
                suffix: 'kg',
                min: '0.01',
                step: '0.5',
                errorText:
                  draft.heavy_weight_threshold_kg !== '' && !parsed.thresholdValid
                    ? t('Tối thiểu 0,01 kg')
                    : undefined,
              })}
              {renderField({
                key: 'heavy_bonus_amount',
                label: t('Phụ phí hàng nặng'),
                suffix: t('đ'),
                formattedHint:
                  parsed.heavy_bonus_amount !== null
                    ? formatVnd(parsed.heavy_bonus_amount, intlLocale)
                    : undefined,
                errorText:
                  draft.heavy_bonus_amount !== '' && parsed.heavy_bonus_amount === null
                    ? t('Giá trị không âm')
                    : undefined,
              })}
            </div>
          </div>
        </div>

        <div className="mt-6 flex flex-col gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-xs text-slate-500">
            {isDirty ? t('Bạn có thay đổi chưa lưu.') : t('Đang dùng cấu hình đã lưu.')}
          </p>
          <div className="flex gap-2">
            {isDirty && (
              <Button type="button" variant="outline" onClick={() => setDraft(toDraft(policy))} disabled={saving}>
                {t('Đặt lại')}
              </Button>
            )}
            <Button
              type="button"
              isLoading={saving}
              disabled={!isDirty || !parsed.allValid}
              onClick={() => void save()}
            >
              {t('Lưu chính sách')}
            </Button>
          </div>
        </div>
      </section>

      <aside className="self-start rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="text-base font-semibold text-slate-950">{t('Xem trước cách tính')}</h2>
        <p className="mt-1 text-xs text-slate-500">
          {t('Thử nghiệm thu nhập dự kiến của một nhiệm vụ theo biểu phí hiện tại.')}
        </p>

        <div className="mt-4 grid grid-cols-2 gap-3">
          <label className="block text-xs font-medium text-slate-700">
            {t('Quãng đường (km)')}
            <input
              type="number"
              min="0"
              step="0.5"
              value={sampleKm}
              onChange={(event) => setSampleKm(event.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm tabular-nums text-slate-950 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
            />
          </label>
          <label className="block text-xs font-medium text-slate-700">
            {t('Khối lượng (kg)')}
            <input
              type="number"
              min="0"
              step="0.5"
              value={sampleKg}
              onChange={(event) => setSampleKg(event.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm tabular-nums text-slate-950 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
            />
          </label>
        </div>

        <dl className="mt-5 space-y-2.5 border-t border-slate-100 pt-4 text-sm tabular-nums">
          <div className="flex items-center justify-between text-slate-600">
            <dt>{t('Phí cơ bản')}</dt>
            <dd>{formatVnd(simulation.base, intlLocale)}</dd>
          </div>
          <div className="flex items-center justify-between text-slate-600">
            <dt>
              {t('Khoảng cách')} ({simulation.km} km × {formatVnd(simulation.perKm, intlLocale)})
            </dt>
            <dd>{formatVnd(simulation.distanceAmount, intlLocale)}</dd>
          </div>
          <div className="flex items-center justify-between text-slate-600">
            <dt>
              {t('Phụ phí hàng nặng')}{' '}
              <span className="text-xs text-slate-400">
                ({simulation.heavyApplied ? `> ${simulation.threshold} kg` : `≤ ${simulation.threshold} kg`})
              </span>
            </dt>
            <dd>{formatVnd(simulation.bonusAmount, intlLocale)}</dd>
          </div>
          <div className="flex items-center justify-between border-t border-slate-100 pt-2.5 font-semibold text-slate-950">
            <dt>
              {t('Thực nhận dự kiến')}
              {simulation.flooredByMinimum && (
                <span className="ml-1.5 text-xs font-normal text-slate-500">({t('áp dụng tối thiểu')})</span>
              )}
            </dt>
            <dd className="text-base">{formatVnd(simulation.rounded, intlLocale)}</dd>
          </div>
        </dl>

        <p className="mt-5 rounded-lg bg-slate-50 p-3 text-xs leading-5 text-slate-600">
          {t(
            'Tiền tài xế = phí cơ bản + (khoảng cách đường bộ Goong × đơn giá) + phụ phí, sau đó áp dụng mức tối thiểu. Mỗi khoản giữ nguyên công thức lúc ghi nhận.'
          )}
        </p>
      </aside>
    </div>
  );
}
