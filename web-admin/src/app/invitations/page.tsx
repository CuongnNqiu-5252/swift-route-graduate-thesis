'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useAuth } from '@/application/contexts/auth-context';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

interface Invitation {
  email: string;
  tenant_id: string;
  business_name?: string;
  role: string;
  expires_at: string;
  status: string;
}

export default function InvitationPage() {
  const { user, login, refreshProfile, isLoading } = useAuth();
  const [invitation, setInvitation] = useState<Invitation | null>(null);
  const [token, setToken] = useState('');
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [accepted, setAccepted] = useState(false);

  useEffect(() => {
    let active = true;
    const raw = new URLSearchParams(window.location.search).get('token')
      || sessionStorage.getItem('driver_invitation_token') || '';
    if (!raw) {
      queueMicrotask(() => { if (active) setError('Liên kết không đầy đủ. Hãy mở lại lời mời trong email.'); });
      return () => { active = false; };
    }
    sessionStorage.setItem('driver_invitation_token', raw);
    window.history.replaceState(null, '', '/invitations');
    api.post<Invitation>('/auth/invitations/preview', { token: raw })
      .then(({ data }) => { if (active) { setToken(raw); setInvitation(data); } })
      .catch((err) => { if (active) setError(err instanceof Error ? err.message : 'Không thể tải lời mời.'); });
    return () => { active = false; };
  }, []);

  async function authenticate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!invitation) return;
    const fields = new FormData(event.currentTarget);
    setBusy(true); setError(''); setNotice('');
    try {
      const password = String(fields.get('password'));
      if (mode === 'register') {
        await api.post('/auth/register', {
          email: invitation.email, password,
          full_name: String(fields.get('fullName')).trim(),
          phone_number: String(fields.get('phoneNumber')).trim(),
        });
        setMode('login');
        setNotice('Tài khoản đã được tạo. Mở email để xác minh, sau đó quay lại đây và đăng nhập.');
        return;
      }
      await login(invitation.email, password);
    } catch (err) { setError(err instanceof Error ? err.message : 'Chưa thể đăng nhập.'); }
    finally { setBusy(false); }
  }

  async function act(action: 'accept' | 'verify' | 'refresh') {
    setBusy(true); setError(''); setNotice('');
    try {
      if (action === 'verify') {
        await api.post('/auth/email/resend');
        setNotice('Đã yêu cầu gửi email xác minh. Kiểm tra hộp thư và thư rác.');
      } else if (action === 'refresh') {
        const profile = await refreshProfile();
        if (!profile?.emailVerified) setNotice('Email chưa được xác minh. Hãy mở liên kết trong email rồi thử lại.');
      } else {
        await api.post('/auth/invitations/accept', { token });
        setAccepted(true);
        sessionStorage.removeItem('driver_invitation_token');
        await refreshProfile();
      }
    } catch (err) { setError(err instanceof Error ? err.message : 'Không thể thực hiện. Vui lòng thử lại.'); }
    finally { setBusy(false); }
  }

  const matchingEmail = user?.email.toLowerCase() === invitation?.email.toLowerCase();
  const valid = invitation?.status === 'PENDING';
  return (
    <main className="flex min-h-dvh items-center justify-center bg-slate-50 p-4">
      <section className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-6 sm:p-9">
        <p className="mb-8 text-base font-semibold text-slate-900">SwiftRoute</p>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-950">{accepted ? 'Đã tham gia đội ngũ' : 'Lời mời tham gia giao hàng'}</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">{accepted
          ? 'Tài khoản đã được liên kết với doanh nghiệp. Đăng nhập lại ứng dụng tài xế, hoàn thiện hồ sơ và gửi duyệt trước khi nhận đơn.'
          : 'Thiết lập tài khoản và xác nhận tham gia trên trình duyệt. Bạn có thể cài ứng dụng tài xế sau.'}</p>
        {error && <p role="alert" className="mt-5 rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</p>}
        {notice && <p role="status" className="mt-5 rounded-lg bg-blue-50 p-3 text-sm text-blue-800">{notice}</p>}
        {!accepted && invitation && <>
          <dl className="my-6 space-y-3 rounded-lg bg-slate-50 p-4 text-sm">
            <div><dt className="text-slate-600">Doanh nghiệp</dt><dd className="mt-1 font-medium">{invitation.business_name || 'SwiftRoute Business'}</dd></div>
            <div><dt className="text-slate-600">Email được mời</dt><dd className="mt-1 break-all font-medium">{invitation.email}</dd></div>
            <div><dt className="text-slate-600">Vai trò</dt><dd className="mt-1">{invitation.role === 'DRIVER' ? 'Tài xế' : 'Thành viên doanh nghiệp'}</dd></div>
            <div><dt className="text-slate-600">Hạn nhận lời mời</dt><dd className="mt-1">{new Date(invitation.expires_at).toLocaleString('vi-VN')}</dd></div>
          </dl>
          {!valid ? <p role="status" className="text-sm leading-6 text-slate-700">{invitation.status === 'ACCEPTED' ? 'Lời mời đã được chấp nhận.' : 'Lời mời đã hết hạn hoặc bị thu hồi. Hãy liên hệ doanh nghiệp để nhận lời mời mới.'}</p>
            : isLoading ? <p role="status" className="text-sm text-slate-600">Đang kiểm tra tài khoản…</p>
            : !user ? <form onSubmit={authenticate} className="space-y-4">
              <div className="flex gap-4 text-sm"><button type="button" onClick={() => setMode('login')} aria-pressed={mode === 'login'} className={mode === 'login' ? 'font-semibold underline underline-offset-4' : 'text-slate-600'}>Đã có tài khoản</button><button type="button" onClick={() => setMode('register')} aria-pressed={mode === 'register'} className={mode === 'register' ? 'font-semibold underline underline-offset-4' : 'text-slate-600'}>Tạo tài khoản</button></div>
              <Input label="Email" type="email" value={invitation.email} readOnly autoComplete="username" />
              {mode === 'register' && <><Input label="Họ và tên" name="fullName" required autoComplete="name" /><Input label="Số điện thoại" name="phoneNumber" required type="tel" autoComplete="tel" pattern="\+?[0-9]{9,15}" /></>}
              <Input label="Mật khẩu" name="password" type="password" required minLength={6} autoComplete={mode === 'register' ? 'new-password' : 'current-password'} />
              <Button type="submit" isLoading={busy} className="min-h-11 w-full">{mode === 'register' ? 'Tạo tài khoản và xác minh email' : 'Đăng nhập để tiếp tục'}</Button>
            </form>
            : !matchingEmail ? <p role="alert" className="text-sm leading-6 text-red-800">Bạn đang đăng nhập bằng {user.email}. Hãy đăng xuất và mở lại lời mời bằng đúng email {invitation.email}.</p>
            : !user.emailVerified ? <div className="space-y-3"><p className="text-sm leading-6 text-slate-700">Xác minh email trước khi xác nhận tham gia. Mở liên kết trong email rồi quay lại đây.</p><Button disabled={busy} onClick={() => act('verify')} variant="outline" className="min-h-11 w-full">Gửi lại email xác minh</Button><Button disabled={busy} onClick={() => act('refresh')} className="min-h-11 w-full">Tôi đã xác minh, kiểm tra lại</Button></div>
            : <Button isLoading={busy} onClick={() => act('accept')} className="min-h-11 w-full">Xác nhận tham gia doanh nghiệp</Button>}
        </>}
        {!invitation && !error && <p role="status" className="mt-6 text-sm text-slate-600">Đang tải lời mời…</p>}
      </section>
    </main>
  );
}
