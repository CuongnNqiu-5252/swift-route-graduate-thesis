'use client';

import { useLocale } from '@/i18n/provider';

import { FormEvent, useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/application/contexts/auth-context';
import { authRepository, type InvitationPreview } from '@/infrastructure/repositories/auth.repository';
import { memberRepository } from '@/infrastructure/repositories/member.repository';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export default function InvitationPage() {
  const { t, intlLocale } = useLocale();
  const { user, login, refreshProfile, isLoading } = useAuth();
  const [invitation, setInvitation] = useState<InvitationPreview | null>(null);
  const [token, setToken] = useState('');
  const [manualToken, setManualToken] = useState('');
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [accepted, setAccepted] = useState(false);

  const loadInvitation = useCallback(async (cleanToken: string) => {
    if (!cleanToken) return;
    setBusy(true);
    setError('');
    try {
      const data = await authRepository.previewInvitation(cleanToken);
      setToken(cleanToken);
      setInvitation(data);
      if (typeof window !== 'undefined') {
        sessionStorage.setItem('driver_invitation_token', cleanToken);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tải thông tin lời mời. Vui lòng kiểm tra lại mã token.');
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    const raw =
      new URLSearchParams(window.location.search).get('token') ||
      sessionStorage.getItem('driver_invitation_token') ||
      '';
    if (raw) {
      authRepository
        .previewInvitation(raw.trim())
        .then((data) => {
          if (!active) return;
          setToken(raw.trim());
          setInvitation(data);
          sessionStorage.setItem('driver_invitation_token', raw.trim());
        })
        .catch((err) => {
          if (!active) return;
          setError(err instanceof Error ? err.message : 'Không thể tải thông tin lời mời.');
        });
    }
    return () => {
      active = false;
    };
  }, []);

  function handleManualTokenSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!manualToken.trim()) {
      setError('Vui lòng nhập mã kích hoạt lời mời.');
      return;
    }
    void loadInvitation(manualToken.trim());
  }

  async function authenticate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!invitation) return;
    const fields = new FormData(event.currentTarget);
    setBusy(true); setError(''); setNotice('');
    try {
      const password = String(fields.get('password'));
      if (mode === 'register') {
        await authRepository.register({
          email: invitation.email, password,
          full_name: String(fields.get('fullName')).trim(),
          phone_number: String(fields.get('phoneNumber')).trim(),
        });
        setMode('login');
        setNotice(t("Tài khoản đã được tạo. Mở email để xác minh, sau đó quay lại đây và đăng nhập."));
        return;
      }
      await login(invitation.email, password);
    } catch (err) { setError(err instanceof Error ? err.message : t("Chưa thể đăng nhập.")); }
    finally { setBusy(false); }
  }

  async function act(action: 'accept' | 'verify' | 'refresh') {
    setBusy(true); setError(''); setNotice('');
    try {
      if (action === 'verify') {
        await authRepository.resendVerification();
        setNotice(t("Đã yêu cầu gửi email xác minh. Kiểm tra hộp thư và thư rác."));
      } else if (action === 'refresh') {
        const profile = await refreshProfile();
        if (!profile?.emailVerified) setNotice(t("Email chưa được xác minh. Hãy mở liên kết trong email rồi thử lại."));
      } else {
        await memberRepository.acceptInvitation({ token });
        setAccepted(true);
        sessionStorage.removeItem('driver_invitation_token');
        await refreshProfile();
      }
    } catch (err) { setError(err instanceof Error ? err.message : t("Không thể thực hiện. Vui lòng thử lại.")); }
    finally { setBusy(false); }
  }

  const matchingEmail = user?.email.toLowerCase() === invitation?.email.toLowerCase();
  const valid = invitation?.status === 'PENDING';
  const isDriver = invitation?.role === 'DRIVER';

  const headingText = accepted
    ? isDriver
      ? t('Đã tham gia đội ngũ giao hàng')
      : t('Đã tham gia doanh nghiệp')
    : isDriver
      ? t('Lời mời tham gia giao hàng')
      : t('Lời mời tham gia doanh nghiệp');

  const subheadingText = accepted
    ? isDriver
      ? t('Tài khoản đã được liên kết với doanh nghiệp. Đăng nhập lại ứng dụng tài xế, hoàn thiện hồ sơ và gửi duyệt trước khi nhận đơn.')
      : t('Tài khoản đã được liên kết với doanh nghiệp. Bạn có thể truy cập hệ thống quản trị để bắt đầu làm việc.')
    : isDriver
      ? t('Thiết lập tài khoản và xác nhận tham gia trên trình duyệt. Bạn có thể cài ứng dụng tài xế sau.')
      : t('Thiết lập tài khoản và xác nhận tham gia vận hành cùng doanh nghiệp trên SwiftRoute.');

  return (
    <main className="flex min-h-dvh items-center justify-center bg-slate-50 p-4">
      <section className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-6 sm:p-9 shadow-sm">
        <p className="mb-6 text-base font-semibold text-slate-900">SwiftRoute</p>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-950">{headingText}</h1>
        <p className="mt-2 text-sm leading-6 text-slate-600">{subheadingText}</p>

        {error && <p role="alert" className="mt-5 rounded-lg bg-red-50 p-3 text-sm text-red-800">{t(error)}</p>}
        {notice && <p role="status" className="mt-5 rounded-lg bg-blue-50 p-3 text-sm text-blue-800">{notice}</p>}

        {!accepted && !invitation && (
          <div className="mt-6 space-y-4">
            <div className="rounded-xl border border-slate-200 bg-slate-50/80 p-4">
              <p className="text-sm font-semibold text-slate-900">
                {t('Nhập mã kích hoạt lời mời (Token)')}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {t('Nếu bạn nhận được mã kích hoạt từ người quản trị, hãy dán mã vào đây để mở thông tin lời mời:')}
              </p>
              <form onSubmit={handleManualTokenSubmit} className="mt-3 space-y-3">
                <Input
                  label={t('Mã token kích hoạt')}
                  placeholder="acb8333024fd..."
                  value={manualToken}
                  onChange={(e) => setManualToken(e.target.value)}
                  required
                  autoComplete="off"
                  className="font-mono text-xs"
                />
                <Button type="submit" isLoading={busy} className="min-h-11 w-full bg-blue-600 hover:bg-blue-700">
                  {t('Kiểm tra và mở lời mời')}
                </Button>
              </form>
            </div>
          </div>
        )}

        {!accepted && invitation && (
          <>
            <dl className="my-6 space-y-3 rounded-lg bg-slate-50 p-4 text-sm">
              <div>
                <dt className="text-slate-600">{t('Doanh nghiệp')}</dt>
                <dd className="mt-1 font-medium">{invitation.business_name || 'SwiftRoute Business'}</dd>
              </div>
              <div>
                <dt className="text-slate-600">{t('Email được mời')}</dt>
                <dd className="mt-1 break-all font-medium">{invitation.email}</dd>
              </div>
              <div>
                <dt className="text-slate-600">{t('Vai trò')}</dt>
                <dd className="mt-1 font-medium">
                  {invitation.role === 'DRIVER'
                    ? t('Tài xế')
                    : invitation.role === 'OPERATOR'
                      ? t('Điều phối viên (OPERATOR)')
                      : t('Thành viên quản trị')}
                </dd>
              </div>
              <div>
                <dt className="text-slate-600">{t('Hạn nhận lời mời')}</dt>
                <dd className="mt-1">{new Date(invitation.expires_at).toLocaleString(intlLocale)}</dd>
              </div>
            </dl>

            {!valid ? (
              <p role="status" className="text-sm leading-6 text-slate-700">
                {invitation.status === 'ACCEPTED'
                  ? t('Lời mời đã được chấp nhận.')
                  : t('Lời mời đã hết hạn hoặc bị thu hồi. Hãy liên hệ doanh nghiệp để nhận lời mời mới.')}
              </p>
            ) : isLoading ? (
              <p role="status" className="text-sm text-slate-600">{t('Đang kiểm tra tài khoản…')}</p>
            ) : !user ? (
              <form onSubmit={authenticate} className="space-y-4">
                <div className="flex gap-4 text-sm">
                  <button
                    type="button"
                    onClick={() => setMode('login')}
                    aria-pressed={mode === 'login'}
                    className={mode === 'login' ? 'font-semibold underline underline-offset-4 text-slate-900' : 'text-slate-600'}
                  >
                    {t('Đã có tài khoản')}
                  </button>
                  <button
                    type="button"
                    onClick={() => setMode('register')}
                    aria-pressed={mode === 'register'}
                    className={mode === 'register' ? 'font-semibold underline underline-offset-4 text-slate-900' : 'text-slate-600'}
                  >
                    {t('Tạo tài khoản')}
                  </button>
                </div>
                <Input label="Email" type="email" value={invitation.email} readOnly autoComplete="username" />
                {mode === 'register' && (
                  <>
                    <Input label={t('Họ và tên')} name="fullName" required autoComplete="name" />
                    <Input label={t('Số điện thoại')} name="phoneNumber" required type="tel" autoComplete="tel" pattern="\+?[0-9]{9,15}" />
                  </>
                )}
                <Input
                  label={t('Mật khẩu')}
                  name="password"
                  type="password"
                  required
                  minLength={6}
                  autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
                />
                <Button type="submit" isLoading={busy} className="min-h-11 w-full bg-blue-600 hover:bg-blue-700">
                  {mode === 'register' ? t('Tạo tài khoản và xác minh email') : t('Đăng nhập để tiếp tục')}
                </Button>
              </form>
            ) : !matchingEmail ? (
              <p role="alert" className="text-sm leading-6 text-red-800">
                {t('Bạn đang đăng nhập bằng')} {user.email}
                {t('. Hãy đăng xuất và mở lại lời mời bằng đúng email')} {invitation.email}.
              </p>
            ) : !user.emailVerified ? (
              <div className="space-y-3">
                <p className="text-sm leading-6 text-slate-700">
                  {t('Xác minh email trước khi xác nhận tham gia. Mở liên kết trong email rồi quay lại đây.')}
                </p>
                <Button disabled={busy} onClick={() => act('verify')} variant="outline" className="min-h-11 w-full">
                  {t('Gửi lại email xác minh')}
                </Button>
                <Button disabled={busy} onClick={() => act('refresh')} className="min-h-11 w-full">
                  {t('Tôi đã xác minh, kiểm tra lại')}
                </Button>
              </div>
            ) : (
              <Button isLoading={busy} onClick={() => act('accept')} className="min-h-11 w-full bg-emerald-600 hover:bg-emerald-700 text-white font-medium">
                {t('Xác nhận tham gia doanh nghiệp')}
              </Button>
            )}
          </>
        )}

        {accepted && (
          <div className="mt-6">
            <Link href={isDriver ? '/drivers' : '/orders'} className="block w-full">
              <Button className="min-h-11 w-full bg-blue-600 hover:bg-blue-700">
                {t('Bắt đầu làm việc')} &rarr;
              </Button>
            </Link>
          </div>
        )}
      </section>
    </main>
  );
}

