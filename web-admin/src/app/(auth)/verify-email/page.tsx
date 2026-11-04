'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';

export default function VerifyEmailPage() {
  const [token, setToken] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [verified, setVerified] = useState(false);

  useEffect(() => { setToken(new URLSearchParams(window.location.search).get('token') || ''); }, []);

  async function verify() {
    setBusy(true);
    setMessage('');
    try {
      await api.post('/auth/email/verify', { token });
      setToken('');
      setVerified(true);
      window.history.replaceState(null, '', '/verify-email');
      setMessage('Email đã được xác minh. Bạn có thể tạo tổ chức mới.');
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Không thể xác minh email');
    } finally { setBusy(false); }
  }

  return <main className="mx-auto mt-20 max-w-lg space-y-5 rounded-xl border border-gray-200 bg-white p-8"><h1 className="text-2xl font-semibold text-gray-950">Xác minh email SwiftRoute</h1><p role="status" className="text-gray-700">{message || 'Xác nhận để hoàn tất email. Liên kết có hiệu lực trong 24 giờ và chỉ được sử dụng một lần.'}</p>{token && <Button onClick={verify} isLoading={busy}>Xác minh email</Button>}{!token && !verified && <p className="text-sm text-gray-500">Liên kết không có token. Hãy yêu cầu gửi lại từ trang tổ chức.</p>}<p><Link className="font-medium text-blue-700 underline underline-offset-4" href="/organizations">Tiếp tục đến tổ chức</Link></p></main>;
}
