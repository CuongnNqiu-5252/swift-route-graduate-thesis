'use client';

import { useEffect, useState } from 'react';
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
  const { user, refreshProfile } = useAuth();
  const [memberships, setMemberships] = useState<Array<Membership & { business_name?: string }>>([]);
  const [provisioning, setProvisioning] = useState<ProvisioningState | null>(null);
  const [pendingTenantId, setPendingTenantId] = useState<string | null>(null);
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

  useEffect(() => { setPendingTenantId(sessionStorage.getItem(PENDING_TENANT_KEY)); }, []);

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
        if (!cancelled) setError(e instanceof Error ? e.message : 'Không thể kiểm tra trạng thái workspace');
      }
    };

    void refresh();
    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [pendingTenantId]);

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
        setNotice('Doanh nghiệp đã được ghi nhận. Hệ thống đang cấp quyền chủ sở hữu cho workspace.');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không thể tạo tổ chức');
    } finally {
      setBusy(false);
    }
  }

  async function resendVerification() {
    setBusy(true);
    setError('');
    try {
      await api.post('/auth/email/resend');
      setNotice('Đã yêu cầu gửi email. Hãy kiểm tra hộp thư.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Gửi email thất bại');
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
        setNotice('Workspace vẫn đang được chuẩn bị. Bạn có thể rời trang và quay lại sau.');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không thể thử cấp quyền lại');
    } finally {
      setBusy(false);
    }
  }

  const waitingForWorkspace = provisioning && provisioning.status !== 'READY';

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-950">Tổ chức của bạn</h1>
        <p className="mt-1 text-sm text-gray-600">Chọn workspace đang có hoặc tạo doanh nghiệp mới để bắt đầu thiết lập.</p>
      </div>

      {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {notice && <p role="status" className="rounded-lg bg-blue-50 p-3 text-sm text-blue-800">{notice}</p>}

      {!user?.emailVerified && (
        <Card>
          <CardContent className="space-y-3 pt-6">
            <p className="font-medium text-gray-950">Xác minh email trước khi tạo tổ chức mới</p>
            <p className="text-sm text-gray-600">Bạn vẫn có thể mở các workspace đã tham gia.</p>
            <div className="flex flex-wrap gap-2">
              <Button onClick={resendVerification} isLoading={busy}>Gửi lại email xác minh</Button>
              <Button variant="outline" onClick={() => refreshProfile()}>Tôi đã xác minh</Button>
            </div>
          </CardContent>
        </Card>
      )}

      {waitingForWorkspace && (
        <Card>
          <CardContent className="space-y-4 pt-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-semibold text-gray-950">Đang chuẩn bị workspace</p>
                <p className="mt-1 text-sm text-gray-600">Dữ liệu doanh nghiệp đã được lưu. Quyền OWNER đang được đồng bộ, lần thử {provisioning.attempts}.</p>
              </div>
              <span className="rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-800">
                {provisioning.status === 'RETRY_REQUIRED' ? 'Cần thử lại' : 'Đang xử lý'}
              </span>
            </div>
            <Button variant="outline" onClick={retryProvisioning} isLoading={busy}>Thử cấp quyền lại</Button>
          </CardContent>
        </Card>
      )}

      <div className="divide-y rounded-xl border border-gray-200 bg-white">
        {memberships.length === 0 && <p className="p-5 text-sm text-gray-600">Bạn chưa tham gia workspace nào.</p>}
        {memberships.map((membership) => (
          <div className="flex flex-wrap items-center justify-between gap-3 p-4" key={membership.tenant_id}>
            <div className="min-w-0">
              <p className="truncate font-medium text-gray-950">{membership.business_name || `Workspace ${membership.tenant_id.slice(0, 8)}`}</p>
              <p className="mt-1 text-xs text-gray-500">{membership.role} · {membership.status}</p>
            </div>
            <Button variant="outline" onClick={() => switchTenant(membership.tenant_id).catch((e) => setError(e.message))}>Mở workspace</Button>
          </div>
        ))}
      </div>

      <Card>
        <CardHeader><CardTitle>Tạo tổ chức</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={create} className="space-y-4">
            <Input label="Tên doanh nghiệp" name="business_name" required minLength={2} maxLength={255}/>
            <label className="block text-sm font-medium text-gray-800">Loại hình
              <select name="business_type" className="mt-1 block min-h-11 w-full rounded-lg border border-gray-300 bg-white px-3 py-2">
                {['RESTAURANT', 'CAFE', 'RETAIL', 'PHARMACY', 'OTHER'].map((type) => <option key={type}>{type}</option>)}
              </select>
            </label>
            <Input label="Số điện thoại" name="contact_phone" required minLength={9} maxLength={20}/>
            <Input label="Địa chỉ" name="address" required minLength={5} maxLength={500}/>
            <Input label="Mã quốc gia" name="country_code" defaultValue="VN" required pattern="[A-Z]{2}"/>
            <Button type="submit" disabled={!user?.emailVerified || Boolean(waitingForWorkspace)} isLoading={busy}>Tạo và bắt đầu thiết lập</Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
