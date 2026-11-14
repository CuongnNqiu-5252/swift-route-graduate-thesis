'use client';

import { useLocale } from '@/i18n/provider';

import { useEffect, useState } from 'react';
import { BUSINESS_TYPE_LABEL } from '@/domain/entities/platform-verification.entity';
import { MEMBER_ROLE_LABEL, MEMBER_STATUS_LABEL } from '@/domain/entities/member.entity';
import type { TenantMembershipRole, TenantMembershipStatus } from '@/domain/entities/member.entity';
import { useAuth } from '@/application/contexts/auth-context';
import {
  Membership,
  ProvisioningState,
  switchTenant,
  tenantWorkflow,
} from '@/infrastructure/repositories/tenant-workflow.repository';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

const PENDING_TENANT_KEY = 'pending_tenant_id';

export default function OrganizationsPage() {
  const { t } = useLocale();
  const { user, refreshProfile } = useAuth();
  const [memberships, setMemberships] = useState<Array<Membership & { business_name?: string }>>([]);
  const [provisioning, setProvisioning] = useState<ProvisioningState | null>(null);
  const [pendingTenantId, setPendingTenantId] = useState<string | null>(() => typeof window === 'undefined' ? null : sessionStorage.getItem(PENDING_TENANT_KEY));
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    tenantWorkflow.memberships().then(async (items) => {
      const named = await Promise.all(items.map(async (membership) => {
        try {
          const response = await api.get<{ business_name: string }>(`/tenants/${membership.tenant_id}`);
          return { ...membership, business_name: response.data.business_name };
        } catch { return membership; }
      }));
      setMemberships(named);
    }).catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    const tenantId = pendingTenantId;
    if (!tenantId) return;
    let cancelled = false;
    let timer: number | undefined;

    const refresh = async () => {
      try {
        const state = await tenantWorkflow.provisioning(tenantId);
        if (cancelled) return;
        setProvisioning(state);
        if (state.status === 'READY') {
          sessionStorage.removeItem(PENDING_TENANT_KEY);
          setPendingTenantId(null);
          await switchTenant(tenantId);
          return;
        }
        timer = window.setTimeout(refresh, 3000);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : t("Không thể kiểm tra trạng thái thiết lập doanh nghiệp"));
      }
    };

    void refresh();
    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [pendingTenantId, t]);

  async function create(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setNotice('');
    const form = new FormData(event.currentTarget);
    const payload = Object.fromEntries(form.entries());
    const signature = JSON.stringify(payload);
    const cacheKey = `tenant-create:${user?.id}`;
    let retry: { signature: string; key: string } | null = null;
    try { retry = JSON.parse(sessionStorage.getItem(cacheKey) || 'null'); } catch { /* replace invalid state */ }
    if (retry?.signature !== signature) retry = { signature, key: crypto.randomUUID() };
    sessionStorage.setItem(cacheKey, JSON.stringify(retry));

    try {
      const result = await tenantWorkflow.create(payload, retry.key);
      sessionStorage.removeItem(cacheKey);
      if (result.workspace_status === 'READY') {
        await switchTenant(result.tenant_id);
      } else {
        sessionStorage.setItem(PENDING_TENANT_KEY, result.tenant_id);
        setPendingTenantId(result.tenant_id);
        setProvisioning({
          tenant_id: result.tenant_id,
          status: result.workspace_status,
          attempts: 1,
          updated_at: new Date().toISOString(),
          next_action: result.next_action,
        });
        setNotice(t("Doanh nghiệp đã được ghi nhận. Hệ thống đang cấp quyền chủ sở hữu doanh nghiệp."));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : t("Không thể tạo tổ chức"));
    } finally {
      setBusy(false);
    }
  }

  async function resendVerification() {
    setBusy(true);
    setError('');
    try {
      await api.post('/auth/email/resend');
      setNotice(t("Đã yêu cầu gửi email. Hãy kiểm tra hộp thư."));
    } catch (e) {
      setError(e instanceof Error ? e.message : t("Gửi email thất bại"));
    } finally {
      setBusy(false);
    }
  }

  async function retryProvisioning() {
    if (!provisioning) return;
    setBusy(true);
    setError('');
    try {
      const state = await tenantWorkflow.retryProvisioning(provisioning.tenant_id);
      setProvisioning(state);
      if (state.status === 'READY') {
        sessionStorage.removeItem(PENDING_TENANT_KEY);
        setPendingTenantId(null);
        await switchTenant(state.tenant_id);
      } else {
        setNotice(t("Doanh nghiệp vẫn đang được thiết lập. Bạn có thể rời trang và quay lại sau."));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : t("Không thể thử cấp quyền lại"));
    } finally {
      setBusy(false);
    }
  }

  const waitingForWorkspace = provisioning && provisioning.status !== 'READY';

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header className="overflow-hidden rounded-2xl bg-slate-950 p-6 text-white sm:p-8">
        <div className="flex flex-wrap items-end justify-between gap-6"><div><p className="text-sm text-slate-400">SwiftRoute</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">{t("Không gian vận hành của bạn")}</h1><p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300">{t("Mỗi doanh nghiệp quản lý riêng đơn hàng, tài xế, điểm lấy hàng và đối soát. Chọn doanh nghiệp để làm việc hoặc tạo doanh nghiệp mới.")}</p></div><div className="rounded-xl bg-white/10 px-5 py-4"><p className="text-xs text-slate-400">{t("Doanh nghiệp đang tham gia")}</p><p className="mt-1 text-3xl font-semibold tabular-nums">{memberships.length}</p></div></div>
      </header>

      {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {notice && <p role="status" className="rounded-lg bg-blue-50 p-3 text-sm text-blue-800">{notice}</p>}

      {!user?.emailVerified && (
        <Card>
          <CardContent className="space-y-3 pt-6">
            <p className="font-medium text-gray-950">{t("Xác minh email trước khi tạo tổ chức mới")}</p>
            <p className="text-sm text-gray-600">{t("Bạn vẫn có thể mở các doanh nghiệp đã tham gia.")}</p>
            <div className="flex flex-wrap gap-2">
              <Button onClick={resendVerification} isLoading={busy}>{t("Gửi lại email xác minh")}</Button>
              <Button variant="outline" onClick={() => refreshProfile()}>{t("Tôi đã xác minh")}</Button>
            </div>
          </CardContent>
        </Card>
      )}

      {waitingForWorkspace && (
        <Card>
          <CardContent className="space-y-4 pt-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-semibold text-gray-950">{t("Đang thiết lập doanh nghiệp")}</p>
                <p className="mt-1 text-sm text-gray-600">{t("Dữ liệu doanh nghiệp đã được lưu. Quyền OWNER đang được đồng bộ, lần thử")}{" "}{provisioning.attempts}.</p>
              </div>
              <span className="rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-800">
                {provisioning.status === 'RETRY_REQUIRED' ? t("Cần thử lại") : t("Đang xử lý")}
              </span>
            </div>
            <Button variant="outline" onClick={retryProvisioning} isLoading={busy}>{t("Thử cấp quyền lại")}</Button>
          </CardContent>
        </Card>
      )}

      <section><div className="mb-3 flex items-center justify-between"><h2 className="font-semibold text-slate-950">{t("Doanh nghiệp của tôi")}</h2><span className="text-xs text-slate-500">{t("Chọn doanh nghiệp để làm việc")}</span></div><div className="grid gap-3 sm:grid-cols-2">
        {memberships.length === 0 && <p className="p-5 text-sm text-gray-600">{t("Bạn chưa tham gia doanh nghiệp nào.")}</p>}
        {memberships.map((membership) => (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4" key={membership.tenant_id}>
            <div className="min-w-0">
              <p className="truncate font-medium text-gray-950">{membership.business_name || `${t("Doanh nghiệp")} ${membership.tenant_id.slice(0, 8)}`}</p>
              <p className="mt-1 text-xs text-gray-500">{t(MEMBER_ROLE_LABEL[membership.role as TenantMembershipRole] ?? t("Vai trò chưa xác định"))} · {t(MEMBER_STATUS_LABEL[membership.status as TenantMembershipStatus] ?? t("Trạng thái chưa xác định"))}</p>
            </div>
            <Button variant="outline" onClick={() => switchTenant(membership.tenant_id).catch((e) => setError(e.message))}>{t("Mở doanh nghiệp")}</Button>
          </div>
        ))}
      </div></section>

      <Card>
        <CardHeader><CardTitle>{t("Tạo doanh nghiệp")}</CardTitle><p className="text-sm text-slate-600">{t("Thông tin này dùng để lập hồ sơ doanh nghiệp và có thể bổ sung ở bước tiếp theo.")}</p></CardHeader>
        <CardContent>
          <form onSubmit={create} className="space-y-4">
            <Input label={t("Tên doanh nghiệp")} name="business_name" required minLength={2} maxLength={255}/>
            <label className="block text-sm font-medium text-gray-800">{t("Loại hình")}
              <select name="business_type" className="mt-1 block min-h-11 w-full rounded-lg border border-gray-300 bg-white px-3 py-2">
                {['RESTAURANT', 'CAFE', 'RETAIL', 'PHARMACY', 'OTHER'].map((type) => <option key={type} value={type}>{t(BUSINESS_TYPE_LABEL[type])}</option>)}
              </select>
            </label>
            <Input label={t("Số điện thoại")} name="contact_phone" required minLength={9} maxLength={20}/>
            <Input label={t("Địa chỉ")} name="address" required minLength={5} maxLength={500}/>
            <Input label={t("Mã quốc gia")} name="country_code" defaultValue="VN" required pattern="[A-Z]{2}"/>
            <Button type="submit" disabled={!user?.emailVerified || Boolean(waitingForWorkspace)} isLoading={busy}>{t("Tạo và bắt đầu thiết lập")}</Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
