'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/application/contexts/auth-context';
import { Progress, TenantProfile, tenantWorkflow } from '@/infrastructure/repositories/tenant-workflow.repository';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import LocationMapPicker from '@/components/locations/location-map-picker';

const STEPS = ['BUSINESS_PROFILE', 'PICKUP_POINT', 'DELIVERY_MODE', 'DELIVERY_SETTINGS', 'VERIFICATION_SUBMISSION'] as const;
type Step = typeof STEPS[number];
const LABEL: Record<Step, string> = {
  BUSINESS_PROFILE: 'Hồ sơ doanh nghiệp',
  PICKUP_POINT: 'Điểm lấy hàng',
  DELIVERY_MODE: 'Phương thức giao hàng',
  DELIVERY_SETTINGS: 'Cấu hình giao hàng',
  VERIFICATION_SUBMISSION: 'Gửi xét duyệt',
};

export default function OnboardingPage() {
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
        const recommended = current.next_action === 'VIEW_VERIFICATION' ? 'VERIFICATION_SUBMISSION' : current.next_action;
        if (STEPS.includes(recommended as Step)) setStep(recommended as Step);
        const locations = await pickupLocationRepository.getPickupLocations('ACTIVE');
        const depot = locations.find((location) => location.is_default) || locations[0];
        if (depot) setPickup({ id: depot.id, name: depot.name, address: depot.address, lat: String(depot.lat), lng: String(depot.lng) });
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không tải được tiến độ thiết lập');
    }
  }, [tenantId]);

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
          const recommended = current.next_action === 'VIEW_VERIFICATION' ? 'VERIFICATION_SUBMISSION' : current.next_action;
          if (STEPS.includes(recommended as Step)) setStep(recommended as Step);
          const locations = await pickupLocationRepository.getPickupLocations('ACTIVE');
          const depot = locations.find((location) => location.is_default) || locations[0];
          if (depot && active) setPickup({ id: depot.id, name: depot.name, address: depot.address, lat: String(depot.lat), lng: String(depot.lng) });
        }
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e.message : 'Không tải được tiến độ thiết lập');
      });
    return () => { active = false; };
  }, [tenantId]);

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId || !profile) return;
    setBusy(true);
    setError('');
    try {
      if (step === 'PICKUP_POINT') {
        if (!pickup.lat || !pickup.lng) throw new Error('Hãy chọn điểm lấy hàng trên bản đồ để xác định tọa độ.');
        const payload = { name: pickup.name, address: pickup.address, lat: Number(pickup.lat), lng: Number(pickup.lng), contactPhone: profile.contact_phone, isDefault: true };
        const saved = pickup.id
          ? await pickupLocationRepository.updatePickupLocation(pickup.id, payload)
          : await pickupLocationRepository.createPickupLocation(payload);
        setPickup((current) => ({ ...current, id: saved.id }));
      } else if (step === 'BUSINESS_PROFILE') {
        await tenantWorkflow.save(tenantId, {
          business_name: profile.business_name,
          business_type: profile.business_type,
          contact_phone: profile.contact_phone,
          address: profile.address,
          country_code: profile.country_code,
        });
      } else if (step === 'DELIVERY_MODE') {
        await tenantWorkflow.save(tenantId, { delivery_mode: profile.delivery_mode });
      } else if (step === 'DELIVERY_SETTINGS') {
        await tenantWorkflow.save(tenantId, { delivery_settings: profile.delivery_settings });
      }
      await load();
      const index = STEPS.indexOf(step);
      setStep(STEPS[Math.min(index + 1, STEPS.length - 1)]);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không thể lưu bước thiết lập');
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
      setError(e instanceof Error ? e.message : 'Không thể gửi hồ sơ xét duyệt');
    } finally {
      setBusy(false);
    }
  }

  if (!tenantId) return <p>Chọn hoặc tạo tổ chức tại <Link href="/organizations" className="font-medium text-blue-700 underline underline-offset-4">Tổ chức của bạn</Link>.</p>;
  if (!progress || !profile) return <div className="space-y-3"><p role="status">Đang tải workspace…</p>{error && <p role="alert" className="text-red-700">{error}</p>}<Button variant="outline" onClick={load}>Thử lại</Button></div>;

  const latest = progress.verification_requests[0];
  const index = STEPS.indexOf(step);
  const isReviewLocked = latest && ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED'].includes(latest.status);
  const field = (name: keyof Pick<TenantProfile, 'business_name' | 'contact_phone' | 'address' | 'country_code'>, label: string, minLength: number) => (
    <Input label={label} required minLength={minLength} value={profile[name] || ''} onChange={(event) => setProfile({ ...profile, [name]: event.target.value })}/>
  );

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div><h1 className="text-2xl font-semibold text-gray-950">Thiết lập {progress.tenant.business_name}</h1><p className="mt-1 text-sm text-gray-600">Hoàn thành hồ sơ vận hành trước khi gửi quản trị viên xét duyệt.</p></div>
        <Button variant="outline" onClick={load}>Cập nhật trạng thái</Button>
      </div>

      {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      <div className="grid gap-4 rounded-xl border border-gray-200 bg-white p-5 sm:grid-cols-3">
        <div><p className="text-xs font-medium uppercase tracking-wide text-gray-500">Doanh nghiệp</p><p className="mt-1 font-semibold text-gray-950">{progress.tenant.status}</p></div>
        <div><p className="text-xs font-medium uppercase tracking-wide text-gray-500">Hồ sơ xét duyệt</p><p className="mt-1 font-semibold text-gray-950">{latest?.status || 'CHƯA GỬI'}</p></div>
        <div><p className="text-xs font-medium uppercase tracking-wide text-gray-500">Tiến độ</p><p className="mt-1 font-semibold text-gray-950">{progress.completed_steps.length}/{STEPS.length} bước</p></div>
        {latest?.reason && <p className="sm:col-span-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">Phản hồi từ quản trị viên: {latest.reason}</p>}
        <p className="sm:col-span-3 text-sm text-gray-600">{progress.readiness.ready_for_operations ? 'Doanh nghiệp đã sẵn sàng vận hành.' : `Điều kiện còn thiếu: ${progress.readiness.missing_requirements.join(', ')}`}</p>
      </div>

      <nav aria-label="Tiến độ onboarding" className="flex gap-2 overflow-x-auto pb-1">
        {STEPS.map((item, itemIndex) => (
          <button key={item} onClick={() => setStep(item)} className={`min-h-11 shrink-0 rounded-lg border px-3 py-2 text-sm font-medium ${step === item ? 'border-blue-600 bg-blue-50 text-blue-800' : 'border-gray-200 bg-white text-gray-700'}`}>
            {progress.completed_steps.includes(item) ? 'Đã xong · ' : `${itemIndex + 1}. `}{LABEL[item]}
          </button>
        ))}
      </nav>

      {!owner && <p className="rounded-lg bg-gray-100 p-3 text-sm text-gray-700">Bạn có thể xem tiến độ. Chỉ OWNER mới có thể thay đổi thông tin hoặc gửi hồ sơ.</p>}

      <Card>
        <CardHeader><CardTitle>{LABEL[step]}</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={save} className="space-y-5">
            <fieldset disabled={!owner || busy || Boolean(isReviewLocked && step !== 'VERIFICATION_SUBMISSION')} className="space-y-4 disabled:opacity-60">
              {step === 'BUSINESS_PROFILE' && <>{field('business_name', 'Tên doanh nghiệp', 2)}{field('contact_phone', 'Số điện thoại', 9)}{field('address', 'Địa chỉ', 5)}{field('country_code', 'Mã quốc gia', 2)}<label className="block text-sm font-medium text-gray-800">Loại hình<select className="mt-1 min-h-11 w-full rounded-lg border border-gray-300 bg-white px-3" value={profile.business_type} onChange={(event) => setProfile({ ...profile, business_type: event.target.value })}>{['RESTAURANT', 'CAFE', 'RETAIL', 'PHARMACY', 'OTHER'].map((type) => <option key={type}>{type}</option>)}</select></label></>}

              {step === 'PICKUP_POINT' && <><p className="text-sm text-gray-600">Chọn đúng vị trí trên bản đồ. Địa chỉ và tọa độ này sẽ được đóng băng trong snapshot xét duyệt.</p><Input label="Tên điểm lấy hàng" required value={pickup.name} onChange={(event) => setPickup({ ...pickup, name: event.target.value })}/><Input label="Địa chỉ" required value={pickup.address} onChange={(event) => setPickup({ ...pickup, address: event.target.value })}/><LocationMapPicker lat={pickup.lat ? Number(pickup.lat) : 10.0452} lng={pickup.lng ? Number(pickup.lng) : 105.7469} address={pickup.address} onChange={(lat, lng, address) => setPickup((current) => ({ ...current, lat: String(lat), lng: String(lng), address: address || current.address }))}/><div className="grid gap-3 sm:grid-cols-2"><Input label="Vĩ độ" required type="number" step="any" min={-90} max={90} value={pickup.lat} onChange={(event) => setPickup({ ...pickup, lat: event.target.value })}/><Input label="Kinh độ" required type="number" step="any" min={-180} max={180} value={pickup.lng} onChange={(event) => setPickup({ ...pickup, lng: event.target.value })}/></div></>}

              {step === 'DELIVERY_MODE' && <><label className="block text-sm font-medium text-gray-800">Phương thức giao hàng<select className="mt-1 min-h-11 w-full rounded-lg border border-gray-300 bg-white px-3" value={profile.delivery_mode} onChange={(event) => setProfile({ ...profile, delivery_mode: event.target.value })}>{['OWN_FLEET', 'SHARED_POOL', 'HYBRID'].map((mode) => <option key={mode}>{mode}</option>)}</select></label><div className="space-y-2 text-sm text-gray-600"><p><strong className="text-gray-900">OWN_FLEET:</strong> sử dụng tài xế riêng của doanh nghiệp.</p><p><strong className="text-gray-900">HYBRID:</strong> hiện vận hành bằng đội xe riêng; Shared Pool chưa khả dụng.</p><p><strong className="text-gray-900">SHARED_POOL:</strong> có thể lưu lựa chọn nhưng chưa thể phát đơn qua mạng dùng chung.</p></div></>}

              {step === 'DELIVERY_SETTINGS' && <><label className="flex min-h-11 items-center gap-3"><input type="checkbox" checked={profile.delivery_settings.preparation_enabled} onChange={(event) => setProfile({ ...profile, delivery_settings: { ...profile.delivery_settings, preparation_enabled: event.target.checked } })}/>Có bước chuẩn bị hàng</label><Input label="Thời gian chuẩn bị (phút)" type="number" min={0} max={240} required value={profile.delivery_settings.preparation_minutes} onChange={(event) => setProfile({ ...profile, delivery_settings: { ...profile.delivery_settings, preparation_minutes: Number(event.target.value) } })}/><label className="flex min-h-11 items-center gap-3"><input type="checkbox" checked={profile.delivery_settings.delivery_verification_required} onChange={(event) => setProfile({ ...profile, delivery_settings: { ...profile.delivery_settings, delivery_verification_required: event.target.checked } })}/>Yêu cầu xác nhận giao hàng</label><label className="flex min-h-11 items-center gap-3"><input type="checkbox" checked={profile.delivery_settings.sensitive_handling} onChange={(event) => setProfile({ ...profile, delivery_settings: { ...profile.delivery_settings, sensitive_handling: event.target.checked } })}/>Xử lý hàng nhạy cảm</label></>}

              {step === 'VERIFICATION_SUBMISSION' && <><p className="text-sm text-gray-600">Khi gửi, hệ thống lưu một snapshot bất biến gồm hồ sơ, cấu hình và các điểm lấy hàng đang hoạt động.</p><dl className="grid gap-3 rounded-lg bg-gray-50 p-4 sm:grid-cols-2"><div><dt className="text-xs text-gray-500">Doanh nghiệp</dt><dd className="font-medium">{profile.business_name}</dd></div><div><dt className="text-xs text-gray-500">Phương thức giao</dt><dd className="font-medium">{profile.delivery_mode}</dd></div><div><dt className="text-xs text-gray-500">Địa chỉ</dt><dd className="font-medium">{profile.address}</dd></div><div><dt className="text-xs text-gray-500">Điểm lấy hàng</dt><dd className="font-medium">{progress.completed_steps.includes('PICKUP_POINT') ? 'Đã cấu hình' : 'Chưa hoàn tất'}</dd></div></dl><Button type="button" onClick={submit} isLoading={busy} disabled={!owner || Boolean(isReviewLocked)}>Gửi hồ sơ xét duyệt</Button></>}
            </fieldset>

            <div className="flex justify-between border-t border-gray-100 pt-4"><Button variant="outline" type="button" disabled={index === 0} onClick={() => setStep(STEPS[index - 1])}>Quay lại</Button>{step !== 'VERIFICATION_SUBMISSION' && <Button type="submit" disabled={!owner || Boolean(isReviewLocked)} isLoading={busy}>Lưu và tiếp tục</Button>}</div>
          </form>
        </CardContent>
      </Card>

      <section className="rounded-xl border border-gray-200 bg-white p-5"><h2 className="font-semibold text-gray-950">Lịch sử xét duyệt</h2>{progress.verification_requests.length === 0 ? <p className="mt-3 text-sm text-gray-600">Chưa có hồ sơ. Hoàn tất thông tin và gửi xét duyệt.</p> : <div className="mt-3 divide-y">{progress.verification_requests.map((request) => <div key={request.request_id} className="py-3"><p className="font-medium text-gray-900">{request.status}</p><p className="mt-1 text-sm text-gray-600">{request.submitted_at || 'Chưa ghi nhận thời gian'}{request.reason ? ` · ${request.reason}` : ''}</p></div>)}</div>}</section>

      {progress.tenant.status === 'APPROVED' && !progress.readiness.ready_for_operations && <p className="rounded-lg bg-blue-50 p-4 text-sm text-blue-900">Doanh nghiệp đã được duyệt. Nếu dùng đội xe riêng, tiếp tục <Link href="/members" className="font-semibold underline underline-offset-4">mời tài xế</Link> và <Link href="/drivers" className="font-semibold underline underline-offset-4">hoàn thiện hồ sơ vận hành</Link>.</p>}
    </div>
  );
}
