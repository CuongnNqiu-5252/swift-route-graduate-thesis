'use client';

import { useLocale } from '@/i18n/provider';

import { accountRoleLabel } from '@/domain/entities/member.entity';
import { BadgeCheck, CalendarDays, Mail, Shield, UserRound } from 'lucide-react';
import { FormEvent, useState } from 'react';
import { useAuth } from '@/application/contexts/auth-context';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export default function ProfilePage() {
  const { t, intlLocale } = useLocale();
  const { user, refreshProfile } = useAuth();
  const [busy, setBusy] = useState(false); const [message, setMessage] = useState('');

  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setMessage('');
    const data=new FormData(event.currentTarget as HTMLFormElement);
    try { await api.put('/auth/me',{full_name:String(data.get('fullName')).trim(),phone_number:String(data.get('phoneNumber')).trim()}); await refreshProfile(); setMessage(t("Đã cập nhật hồ sơ.")); }
    catch(e){ setMessage(e instanceof Error?e.message:t("Không thể cập nhật hồ sơ")); }
    finally{ setBusy(false); }
  }

  return <div className="mx-auto max-w-5xl space-y-8"><header><h1 className="text-3xl font-semibold tracking-tight text-slate-950">{t("Hồ sơ cá nhân")}</h1><p className="mt-2 text-sm text-slate-600">{t("Thông tin tài khoản của bạn.")}</p></header>
    <div className="grid gap-6 lg:grid-cols-[0.72fr_1.28fr]"><aside className="rounded-2xl bg-slate-950 p-6 text-white"><div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white/10"><UserRound size={26}/></div><p className="mt-5 text-xl font-semibold">{user?.name || t("Quản trị viên")}</p><p className="mt-1 text-sm text-slate-300">{user?.email}</p><dl className="mt-8 space-y-4 border-t border-white/10 pt-5 text-sm"><div className="flex items-center gap-3"><Shield size={17}/><span>{t(accountRoleLabel(user?.role))}</span></div><div className="flex items-center gap-3"><BadgeCheck size={17}/><span>{user?.emailVerified?t("Email đã xác minh"):t("Email chưa xác minh")}</span></div><div className="flex items-center gap-3"><CalendarDays size={17}/><span>{t("Tham gia")}{" "}{user?.createdAt?new Date(user.createdAt).toLocaleDateString(intlLocale):'—'}</span></div></dl></aside>
      <form key={user?.id} onSubmit={save} className="rounded-2xl border border-slate-200 p-6 sm:p-8"><h2 className="text-lg font-semibold text-slate-950">{t("Thông tin liên hệ")}</h2><p className="mt-1 text-sm text-slate-600">{t("Tên và số điện thoại được dùng khi trao đổi về vận hành.")}</p><div className="mt-7 space-y-5"><Input name="fullName" label={t("Họ và tên")} defaultValue={user?.name||''} minLength={2} maxLength={120} required/><Input name="phoneNumber" label={t("Số điện thoại")} type="tel" defaultValue={user?.phone||''} minLength={9} maxLength={20} required/><div className="rounded-xl bg-slate-50 p-4"><div className="flex items-center gap-3"><Mail size={18}/><div><p className="text-sm font-medium text-slate-900">{user?.email}</p><p className="text-xs text-slate-500">{t("Email đăng nhập không thể thay đổi tại đây.")}</p></div></div></div>{message&&<p role="status" className="text-sm text-slate-700">{message}</p>}<Button type="submit" isLoading={busy}>{t("Lưu thay đổi")}</Button></div></form></div>
  </div>;
}
