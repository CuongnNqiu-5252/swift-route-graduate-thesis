'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { ClipboardCheck, LogOut, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/application/contexts/auth-context';
import { Button } from '@/components/ui/button';
import { isSystemAdmin } from '@/lib/portal-access';

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { user, token, isLoading, logout, refreshProfile } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!isLoading && !token) router.replace('/admin/login');
  }, [isLoading, token, router]);

  if (isLoading) return <div role="status" className="flex min-h-screen items-center justify-center bg-slate-50">Đang xác thực quyền quản trị…</div>;
  if (!token) return null;
  if (!user) return <main className="mx-auto mt-20 max-w-lg space-y-4 p-8"><p role="alert">Chưa thể xác minh tài khoản. Vui lòng thử lại.</p><Button onClick={() => refreshProfile()}>Tải lại thông tin tài khoản</Button><Button variant="outline" onClick={logout}>Đăng nhập lại</Button></main>;
  if (!isSystemAdmin(user?.role)) return <main className="mx-auto mt-20 max-w-lg space-y-4 rounded border bg-white p-8">
    <h1 className="text-2xl font-semibold">Không có quyền truy cập</h1>
    <p>Khu vực này chỉ dành cho quản trị viên hệ thống SwiftRoute.</p>
    <Link href="/organizations" className="text-blue-600 underline">Về workspace khách thuê</Link>
    <p><Link href="/admin/login" className="text-blue-600 underline">Đăng nhập bằng tài khoản quản trị</Link></p>
  </main>;

  return <div className="flex h-screen overflow-hidden bg-slate-50">
    <aside className="flex w-64 shrink-0 flex-col bg-slate-950 text-slate-100">
      <div className="flex h-20 items-center gap-3 border-b border-slate-800 px-5"><ShieldCheck className="text-indigo-400"/><div><p className="font-semibold">SwiftRoute</p><p className="text-xs text-slate-400">Platform Administration</p></div></div>
      <nav aria-label="Quản trị hệ thống" className="flex-1 p-4">
        <Link href="/admin/tenant-verifications" aria-current={pathname.startsWith('/admin/tenant-verifications') ? 'page' : undefined} className="flex items-center gap-3 rounded-lg bg-indigo-600 px-3 py-3 text-sm"><ClipboardCheck size={18}/>Xét duyệt doanh nghiệp</Link>
      </nav>
      <p className="border-t border-slate-800 p-5 text-xs text-slate-400">Quản trị nền tảng SwiftRoute</p>
    </aside>
    <div className="flex min-w-0 flex-1 flex-col">
      <header className="flex h-20 shrink-0 items-center justify-between border-b bg-white px-6">
        <h2 className="font-semibold text-slate-800">Quản trị hệ thống</h2>
        <div className="flex items-center gap-4"><div className="text-right"><p className="text-sm">{user?.name || user?.email}</p><p className="text-xs text-slate-500">{user?.role}</p></div><Button variant="outline" onClick={logout}><LogOut size={16}/>Đăng xuất</Button></div>
      </header>
      <main className="flex-1 overflow-y-auto p-6">{children}</main>
    </div>
  </div>;
}
