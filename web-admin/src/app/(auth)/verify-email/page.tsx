'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
export default function VerifyEmailPage() {
  const [token,setToken]=useState(''); const [busy,setBusy]=useState(false); const [message,setMessage]=useState('');
  useEffect(() => {const task=window.setTimeout(()=>setToken(new URLSearchParams(window.location.search).get('token') || ''),0);return ()=>window.clearTimeout(task);},[]);
  async function verify() {
    setBusy(true);try{await api.post('/auth/email/verify',{token});setToken('');window.history.replaceState(null,'','/verify-email');setMessage('Email đã được xác minh. Bạn có thể tạo tổ chức.');}
    catch(e){setMessage(e instanceof Error?e.message:'Xác minh thất bại');}finally{setBusy(false);}
  }
  return <main className="mx-auto mt-20 max-w-lg space-y-5 rounded border bg-white p-8"><h1 className="text-2xl font-semibold">Xác minh email SwiftRoute</h1><p role="status">{message || 'Xác nhận để hoàn tất xác minh email. Liên kết có hiệu lực trong 24 giờ.'}</p>{token && <Button onClick={verify} isLoading={busy}>Xác minh email</Button>}<p><Link className="text-blue-600 underline" href="/organizations">Tiếp tục đến tổ chức</Link></p></main>;
}
