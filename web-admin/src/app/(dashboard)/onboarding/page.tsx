'use client';

import { useLocale } from '@/i18n/provider';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/application/contexts/auth-context';
import { Progress, TenantProfile, tenantWorkflow } from '@/infrastructure/repositories/tenant-workflow.repository';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  formatAdminDateTime,
  TENANT_LIFECYCLE_LABEL,
  VERIFICATION_STATUS_META,
} from '@/domain/entities/platform-verification.entity';
import LocationMapPicker from '@/components/locations/location-map-picker';

const STEPS = ['BUSINESS_PROFILE', 'PICKUP_POINT', 'VERIFICATION_SUBMISSION'] as const;
type Step = typeof STEPS[number];
const LABEL: Record<Step, string> = {
  BUSINESS_PROFILE: 'Hồ sơ doanh nghiệp',
  PICKUP_POINT: 'Điểm lấy hàng',
  VERIFICATION_SUBMISSION: 'Gửi xét duyệt',
};

function recommendedStep(progress: Progress): Step {
  return STEPS.find((step) => step === progress.next_action) ?? 'VERIFICATION_SUBMISSION';
}

const READINESS_LABEL: Record<string, string> = {
  TENANT_PENDING: 'Doanh nghiệp chưa được duyệt',
  TENANT_REJECTED: 'Doanh nghiệp bị từ chối',
  TENANT_SUSPENDED: 'Doanh nghiệp đang tạm ngưng',
  NO_PICKUP_POINT: 'Chưa có điểm lấy hàng',
  DRIVER_SERVICE_UNAVAILABLE: 'Chưa thể kiểm tra đội tài xế',
  NO_ACTIVE_DRIVER: 'Chưa có tài xế đủ điều kiện hoạt động',
};

export default function OnboardingPage() {
  const { t, intlLocale } = useLocale();
  const { user } = useAuth();
  const tenantId = user?.tenantId;
  const [progress, setProgress] = useState<Progress | null>(null);
  const [profile, setProfile] = useState<TenantProfile | null>(null);
  const [step, setStep] = useState<Step>('BUSINESS_PROFILE');
  const [pickup, setPickup] = useState({ id: '', name: '', address: '', lat: '', lng: '' });
  const [owner, setOwner] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const initializedTenant = useRef<string | null>(null);

  const load = useCallback(async () => {
    if (!tenantId) return;
    setError('');
    try {
      const current = await tenantWorkflow.progress(tenantId);
      setProgress(current);
      setProfile(current.profile);
      const memberships = await tenantWorkflow.memberships();
      setOwner(memberships.some((membership) => membership.tenant_id === tenantId && membership.role === 'OWNER'));
      if (initializedTenant.current !== tenantId) {
        initializedTenant.current = tenantId;
        setStep(recommendedStep(current));
        const locations = await pickupLocationRepository.getPickupLocations('ACTIVE');
        const depot = locations.find((location) => location.is_default) || locations[0];
        if (depot) {
          setPickup({ id: depot.id, name: depot.name, address: depot.address, lat: String(depot.lat), lng: String(depot.lng) });
        } else if (current.profile.business_name) {
          setPickup((existing) => ({
            ...existing,
            name: existing.name || `${current.profile.business_name} - Kho chính`,
          }));
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : t("Không tải được tiến độ thiết lập"));
    }
  }, [t, tenantId]);

  useEffect(() => {
    let active = true;
    if (!tenantId) return;
    tenantWorkflow.progress(tenantId)
      .then(async (current) => {
        if (!active) return;
        setProgress(current);
        setProfile(current.profile);
        const memberships = await tenantWorkflow.memberships();
        if (!active) return;
        setOwner(memberships.some((membership) => membership.tenant_id === tenantId && membership.role === 'OWNER'));
        if (initializedTenant.current !== tenantId) {
          initializedTenant.current = tenantId;
          setStep(recommendedStep(current));
          const locations = await pickupLocationRepository.getPickupLocations('ACTIVE');
          const depot = locations.find((location) => location.is_default) || locations[0];
          if (depot && active) {
            setPickup({ id: depot.id, name: depot.name, address: depot.address, lat: String(depot.lat), lng: String(depot.lng) });
          } else if (active && current.profile.business_name) {
            setPickup((existing) => ({
              ...existing,
              name: existing.name || `${current.profile.business_name} - Kho chính`,
            }));
          }
        }
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e.message : t("Không tải được tiến độ thiết lập"));
      });
    return () => { active = false; };
  }, [t, tenantId]);

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId || !profile) return;
    setBusy(true);
    setError('');
    try {
      if (step === 'PICKUP_POINT') {
        if (!pickup.lat || !pickup.lng) throw new Error(t("Hãy chọn điểm lấy hàng trên bản đồ để xác định tọa độ."));
        const payload = { name: pickup.name, address: pickup.address, lat: Number(pickup.lat), lng: Number(pickup.lng), contactPhone: profile.contact_phone, isDefault: true };
        const saved = pickup.id
          ? await pickupLocationRepository.updatePickupLocation(pickup.id, payload)
          : await pickupLocationRepository.createPickupLocation(payload);
        setPickup((current) => ({ ...current, id: saved.id, name: saved.name, address: saved.address, lat: String(saved.lat), lng: String(saved.lng) }));
      } else if (step === 'BUSINESS_PROFILE') {
        await tenantWorkflow.save(tenantId, {
          business_name: profile.business_name,
          contact_phone: profile.contact_phone,
          country_code: profile.country_code,
        });
        if (!pickup.id && profile.business_name) {
          setPickup((current) => ({
            ...current,
            name: current.name || `${profile.business_name} - Kho chính`,
          }));
        }
      }
      await load();
      setStep(STEPS[Math.min(STEPS.indexOf(step) + 1, STEPS.length - 1)]);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("Không thể lưu bước thiết lập"));
    } finally {
      setBusy(false);
    }
  }

  async function submit() {
    if (!tenantId) return;
    setBusy(true);
    setError('');
    const storageKey = `verification-key:${tenantId}`;
    const current = progress?.verification_requests[0];
    const intent = `${current?.request_id || 'initial'}:${current?.status || 'DRAFT'}`;
    let cached: { intent: string; key: string } | null = null;
    try { cached = JSON.parse(sessionStorage.getItem(storageKey) || 'null'); } catch { /* replace invalid state */ }
    const key = cached?.intent === intent ? cached.key : crypto.randomUUID();
    sessionStorage.setItem(storageKey, JSON.stringify({ intent, key }));
    try {
      await tenantWorkflow.submit(tenantId, key);
      sessionStorage.removeItem(storageKey);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : t("Không thể gửi hồ sơ xét duyệt"));
    } finally {
      setBusy(false);
    }
  }

  if (!tenantId) return <p>{t("Chọn hoặc tạo tổ chức tại")}{" "}<Link href="/organizations" className="font-medium text-blue-700 underline underline-offset-4">{t("Tổ chức của bạn")}</Link>.</p>;
  if (!progress || !profile) return <div className="space-y-3"><p role="status">{t("Đang tải tiến độ thiết lập…")}</p>{error && <p role="alert" className="text-red-700">{error}</p>}<Button variant="outline" onClick={load}>{t("Thử lại")}</Button></div>;

  const latest = progress.verification_requests[0];
  const steps = STEPS;
  const index = steps.indexOf(step);
  const isReviewLocked = latest && ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED'].includes(latest.status);
  const stepLocked = Boolean(isReviewLocked);
  const submissionReady = progress.completed_steps.includes('BUSINESS_PROFILE')
    && progress.completed_steps.includes('PICKUP_POINT');
  const completedCount = steps.filter((item) => progress.completed_steps.includes(item)).length;
  const field = (name: keyof Pick<TenantProfile, 'business_name' | 'contact_phone' | 'country_code'>, label: string, minLength: number) => (
    <Input label={label} required minLength={minLength} value={profile[name] || ''} onChange={(event) => setProfile({ ...profile, [name]: event.target.value })}/>
  );

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div><h1 className="text-2xl font-semibold text-gray-950">{t("Thiết lập")}{" "}{progress.tenant.business_name}</h1><p className="mt-1 text-sm text-gray-600">{t("Hoàn thành hồ sơ vận hành trước khi gửi quản trị viên xét duyệt.")}</p></div>
        <Button variant="outline" onClick={load}>{t("Cập nhật trạng thái")}</Button>
      </div>

      {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      <div className="grid gap-4 rounded-xl border border-gray-200 bg-white p-5 sm:grid-cols-3">
        <div><p className="text-xs font-medium uppercase tracking-wide text-gray-500">{t("Doanh nghiệp")}</p><p className="mt-1 font-semibold text-gray-950">{t(TENANT_LIFECYCLE_LABEL[progress.tenant.status]?.label ?? t("Chưa xác định"))}</p></div>
        <div><p className="text-xs font-medium uppercase tracking-wide text-gray-500">{t("Hồ sơ xét duyệt")}</p><p className="mt-1 font-semibold text-gray-950">{latest ? (t(VERIFICATION_STATUS_META[latest.status]?.label ?? t("Chưa xác định"))) : t("Chưa gửi")}</p></div>
        <div><p className="text-xs font-medium uppercase tracking-wide text-gray-500">{t("Tiến độ")}</p><p className="mt-1 font-semibold text-gray-950">{completedCount}/{steps.length}{" "}{t("bước")}</p></div>
        {latest?.reason && <p className="sm:col-span-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">{t("Phản hồi từ quản trị viên:")}{" "}{latest.reason}</p>}
        <p className="sm:col-span-3 text-sm text-gray-600">{progress.readiness.ready_for_operations ? t("Doanh nghiệp đã sẵn sàng vận hành.") : `${t("Chưa thể vận hành.")} ${progress.readiness.missing_requirements.map((code) => t(READINESS_LABEL[code] ?? t("Cần kiểm tra thêm điều kiện vận hành"))).join(' · ')}`}</p>
      </div>

      <nav aria-label={t("Tiến độ thiết lập doanh nghiệp")} className="flex gap-2 overflow-x-auto pb-1">
        {steps.map((item, itemIndex) => (
          <button key={item} onClick={() => setStep(item)} className={`min-h-11 shrink-0 rounded-lg border px-3 py-2 text-sm font-medium ${step === item ? 'border-blue-600 bg-blue-50 text-blue-800' : 'border-gray-200 bg-white text-gray-700'}`}>
            {progress.completed_steps.includes(item) ? t("Đã xong · ") : `${itemIndex + 1}. `}{t(LABEL[item])}
          </button>
        ))}
      </nav>

      {!owner && <p className="rounded-lg bg-gray-100 p-3 text-sm text-gray-700">{t("Bạn có thể xem tiến độ. Chỉ chủ sở hữu mới có thể thay đổi thông tin hoặc gửi hồ sơ.")}</p>}

      <Card>
        <CardHeader><CardTitle>{t(LABEL[step])}</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={save} className="space-y-5">
            <fieldset disabled={!owner || busy || (stepLocked && step !== 'VERIFICATION_SUBMISSION')} className="space-y-4 disabled:opacity-60">
              {step === 'BUSINESS_PROFILE' && <>{field('business_name', t("Tên doanh nghiệp"), 2)}{field('contact_phone', t("Số điện thoại"), 9)}{field('country_code', t("Mã quốc gia"), 2)}</>}

              {step === 'PICKUP_POINT' && <>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm text-gray-600">{t("Chọn đúng vị trí kho xuất phát mặc định trên bản đồ. SĐT liên hệ tự động kế thừa từ hồ sơ doanh nghiệp.")}</p>
                  {pickup.id && (
                    <Link href="/locations" className="text-sm font-medium text-blue-700 underline underline-offset-4">
                      {t("Quản lý nhiều kho / điểm lấy hàng")}
                    </Link>
                  )}
                </div>
                <Input label={t("Tên điểm lấy hàng / kho mặc định")} required value={pickup.name} onChange={(event) => setPickup({ ...pickup, name: event.target.value })}/>
                <Input label={t("Địa chỉ kho")} required value={pickup.address} onChange={(event) => setPickup({ ...pickup, address: event.target.value })}/>
                <LocationMapPicker lat={pickup.lat ? Number(pickup.lat) : 10.0452} lng={pickup.lng ? Number(pickup.lng) : 105.7469} address={pickup.address} onChange={(lat, lng, address) => setPickup((current) => ({ ...current, lat: String(lat), lng: String(lng), address: address || current.address }))}/>
                <p className="text-xs text-slate-600 tabular-nums">
                  {pickup.lat && pickup.lng
                    ? `${t("Tọa độ đã chọn:")} ${Number(pickup.lat).toFixed(6)}, ${Number(pickup.lng).toFixed(6)}`
                    : t("Hãy tìm địa chỉ hoặc bấm lên bản đồ để xác định tọa độ kho.")}
                </p>
              </>}

              {step === 'VERIFICATION_SUBMISSION' && <><p className="text-sm text-gray-600">{t("Khi gửi, hệ thống lưu hồ sơ, cấu hình và các điểm lấy hàng đang hoạt động. Các chỉnh sửa sau đó không cập nhật bản đã gửi.")}</p><dl className="grid gap-3 rounded-lg bg-gray-50 p-4 sm:grid-cols-2"><div><dt className="text-xs text-gray-500">{t("Doanh nghiệp")}</dt><dd className="font-medium">{profile.business_name}</dd></div><div><dt className="text-xs text-gray-500">{t("Số điện thoại")}</dt><dd className="font-medium tabular-nums">{profile.contact_phone}</dd></div><div className="sm:col-span-2"><dt className="text-xs text-gray-500">{t("Điểm lấy hàng mặc định")}</dt><dd className="font-medium">{pickup.address ? `${pickup.name} — ${pickup.address}` : (progress.completed_steps.includes('PICKUP_POINT') ? t("Đã cấu hình") : t("Chưa hoàn tất"))}</dd></div></dl>{!submissionReady && <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">{t("Hoàn tất hồ sơ doanh nghiệp và điểm lấy hàng trước khi gửi xét duyệt.")}</p>}<Button type="button" onClick={submit} isLoading={busy} disabled={!owner || Boolean(isReviewLocked) || !submissionReady}>{t("Gửi hồ sơ xét duyệt")}</Button></>}
            </fieldset>

            <div className="flex justify-between border-t border-gray-100 pt-4"><Button variant="outline" type="button" disabled={index <= 0} onClick={() => setStep(steps[index - 1])}>{t("Quay lại")}</Button>{step !== 'VERIFICATION_SUBMISSION' && <Button type="submit" disabled={!owner || stepLocked} isLoading={busy}>{t("Lưu và tiếp tục")}</Button>}</div>
          </form>
        </CardContent>
      </Card>

      <section className="rounded-xl border border-gray-200 bg-white p-5"><h2 className="font-semibold text-gray-950">{t("Lịch sử xét duyệt")}</h2>{progress.verification_requests.length === 0 ? <p className="mt-3 text-sm text-gray-600">{t("Chưa có hồ sơ. Hoàn tất thông tin và gửi xét duyệt.")}</p> : <div className="mt-3 divide-y">{progress.verification_requests.map((request) => <div key={request.request_id} className="py-3"><p className="font-medium text-gray-900">{t(VERIFICATION_STATUS_META[request.status]?.label ?? t("Chưa xác định"))}</p><p className="mt-1 text-sm text-gray-600">{request.submitted_at ? formatAdminDateTime(request.submitted_at, intlLocale) : t("Chưa ghi nhận thời gian")}{request.reason ? ` · ${request.reason}` : ''}</p></div>)}</div>}</section>

      {progress.tenant.status === 'APPROVED' && !progress.readiness.ready_for_operations && <p className="rounded-lg bg-blue-50 p-4 text-sm text-blue-900">{t("Doanh nghiệp đã được duyệt. Tiếp tục")}{" "}<Link href="/members" className="font-semibold underline underline-offset-4">{t("mời tài xế")}</Link>{" "}{t("và")}{" "}<Link href="/drivers" className="font-semibold underline underline-offset-4">{t("hoàn thiện hồ sơ vận hành")}</Link>.</p>}
    </div>
  );
}
