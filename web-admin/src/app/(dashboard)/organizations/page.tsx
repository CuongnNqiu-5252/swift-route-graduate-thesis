'use client';
import { useEffect, useState } from 'react';
import api from '@/lib/api';
import { useAuth } from '@/application/contexts/auth-context';
import { Membership, switchTenant, tenantWorkflow } from '@/infrastructure/repositories/tenant-workflow.repository';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export default function OrganizationsPage() {
  const { user, refreshProfile } = useAuth();
  const [memberships, setMemberships] = useState<Array<Membership & { business_name?: string }>>([]);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  useEffect(() => {
    tenantWorkflow.memberships().then(async memberships => {
      const named = await Promise.all(memberships.map(async m => {
        try { const tenant = await api.get<{ business_name: string }>(`/tenants/${m.tenant_id}`);return { ...m, business_name: tenant.data.business_name }; }
        catch { return m; }
      }));
      setMemberships(named);
    }).catch(e => setError(e.message));
  }, []);
  async function create(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('');
    const form = new FormData(event.currentTarget);
    const payload = Object.fromEntries(form.entries());
    // Persist the key with its exact payload so retries after a timeout do not duplicate organizations.
    const signature = JSON.stringify(payload); const cacheKey = `tenant-create:${user?.id}`;
    const cached = sessionStorage.getItem(cacheKey); let retry: { signature: string; key: string } | null = null;
    try { retry = cached ? JSON.parse(cached) : null; } catch { /* replace invalid draft */ }
    if (retry?.signature !== signature) retry = { signature, key: crypto.randomUUID() };
    sessionStorage.setItem(cacheKey, JSON.stringify(retry));
    try {
      const result = await api.post<{ tenant_id: string }>('/tenants', payload, { headers: { 'Idempotency-Key': retry!.key } });
      sessionStorage.removeItem(cacheKey); await switchTenant(result.data.tenant_id);
    } catch (e) { setError(e instanceof Error ? e.message : 'Không thể tạo tổ chức'); } finally { setBusy(false); }
  }
  async function resend() {
    setBusy(true);setError('');
    try { await api.post('/auth/email/resend');setNotice('Đã yêu cầu gửi email. Hãy kiểm tra hộp thư.'); }
    catch(e) {setError(e instanceof Error ? e.message : 'Gửi email thất bại');}finally{setBusy(false);}
  }
  return <div className="mx-auto max-w-3xl space-y-6">
    <h1 className="text-2xl font-semibold">Tổ chức của bạn</h1>
    {error && <p role="alert" className="rounded bg-red-50 p-3 text-red-700">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {!user?.emailVerified && <Card><CardContent className="space-y-3 pt-6"><p>Xác minh email trước khi tạo tổ chức mới. Bạn vẫn có thể mở workspace hiện có.</p><Button onClick={resend} isLoading={busy}>Gửi lại email xác minh</Button> <Button variant="outline" onClick={() => refreshProfile()}>Tôi đã xác minh</Button></CardContent></Card>}
    <div className="space-y-2">{memberships.map(m => <div className="flex items-center justify-between rounded border bg-white p-4" key={m.tenant_id}><span>{m.business_name || m.tenant_id} · {m.role}</span><Button onClick={() => switchTenant(m.tenant_id).catch(e => setError(e.message))}>Mở workspace</Button></div>)}</div>
    <Card><CardHeader><CardTitle>Tạo tổ chức</CardTitle></CardHeader><CardContent>
      <form onSubmit={create} className="space-y-4">
        <Input label="Tên doanh nghiệp" name="business_name" required minLength={2} maxLength={255}/>
        <label className="block text-sm">Loại hình<select name="business_type" className="mt-1 block w-full rounded border p-2">{['RESTAURANT','CAFE','RETAIL','PHARMACY','OTHER'].map(x=><option key={x}>{x}</option>)}</select></label>
        <Input label="Số điện thoại" name="contact_phone" required minLength={9} maxLength={20}/>
        <Input label="Địa chỉ" name="address" required minLength={5} maxLength={500}/>
        <Input label="Mã quốc gia" name="country_code" defaultValue="VN" required pattern="[A-Z]{2}"/>
        <Button type="submit" disabled={!user?.emailVerified} isLoading={busy}>Tạo và bắt đầu thiết lập</Button>
      </form>
    </CardContent></Card>
  </div>;
}
