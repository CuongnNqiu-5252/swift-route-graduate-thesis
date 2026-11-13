'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/application/contexts/auth-context';
import { AdminShell } from '@/components/admin/admin-shell';
import { Button } from '@/components/ui/button';
import { isSystemAdmin } from '@/lib/portal-access';

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { user, token, isLoading, logout, refreshProfile } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !token) {
      router.replace('/admin/login');
    }
  }, [isLoading, token, router]);

  if (isLoading) {
    return (
      <div
        role="status"
        className="flex min-h-screen items-center justify-center bg-[#eef1f4] text-sm text-slate-600"
      >
        Đang xác thực quyền quản trị hệ thống…
      </div>
    );
  }

  if (!token) {
    return null;
  }

  if (!user) {
    return (
      <main className="mx-auto mt-20 max-w-lg space-y-4 rounded-xl border border-slate-200 bg-white p-8">
        <p role="alert" className="text-sm text-slate-800">
          Chưa thể xác minh tài khoản quản trị. Vui lòng thử lại.
        </p>
        <div className="flex flex-wrap gap-3">
          <Button onClick={() => void refreshProfile()}>Tải lại thông tin</Button>
          <Button variant="outline" onClick={logout}>
            Đăng nhập lại
          </Button>
        </div>
      </main>
    );
  }

  if (!isSystemAdmin(user)) {
    return (
      <main className="mx-auto mt-20 max-w-lg space-y-4 rounded-xl border border-slate-200 bg-white p-8">
        <h1 className="text-xl font-semibold text-slate-950">Không có quyền truy cập</h1>
        <p className="text-sm leading-6 text-slate-600">
          Khu vực này chỉ dành cho quản trị viên nền tảng SwiftRoute.
        </p>
        <div className="flex flex-wrap gap-4 pt-2 text-sm">
          <Link href="/organizations" className="font-medium text-[#17766c] hover:underline">
            Về workspace doanh nghiệp
          </Link>
          <Link href="/admin/login" className="font-medium text-slate-700 hover:underline">
            Đăng nhập bằng tài khoản quản trị
          </Link>
        </div>
      </main>
    );
  }

  return (
    <AdminShell
      userName={user.name}
      userEmail={user.email}
      userRole={user.role}
      onLogout={logout}
    >
      {children}
    </AdminShell>
  );
}
