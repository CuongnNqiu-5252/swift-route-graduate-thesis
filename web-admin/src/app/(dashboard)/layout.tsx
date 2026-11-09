'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/application/contexts/auth-context';
import { NotificationProvider } from '@/application/contexts/notification-context';
import { Sidebar } from '@/components/layout/sidebar';
import { Navbar } from '@/components/layout/navbar';
import { isSystemAdmin, portalHome } from '@/lib/portal-access';
import { SetupProgress } from '@/components/layout/setup-progress';

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, token, isLoading, refreshProfile, logout } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !token) {
      router.replace('/login');
    } else if (!isLoading && user && isSystemAdmin(user.role)) {
      router.replace(portalHome(user.role));
    }
  }, [isLoading, token, user, router]);

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50">
        <div className="flex flex-col items-center gap-3">
          <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
          <p className="text-sm font-medium text-gray-500">Đang khởi tạo phiên làm việc...</p>
        </div>
      </div>
    );
  }

  if (token && !user && !isLoading) {
    return (
      <div className="mx-auto mt-20 max-w-lg space-y-4 p-8">
        <p role="alert">Chưa thể xác minh tài khoản. Vui lòng thử lại.</p>
        <button onClick={() => refreshProfile()} className="rounded border px-4 py-2">Tải lại thông tin</button>
        <button onClick={logout} className="ml-3 rounded border px-4 py-2">Đăng nhập lại</button>
      </div>
    );
  }

  if (!token || !user || isSystemAdmin(user.role)) {
    return null;
  }

  return (
    <NotificationProvider>
      <div className="workspace-shell flex h-dvh overflow-hidden bg-[#eef1f4]">
        <Sidebar />
        <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
          <Navbar />
          <main id="workspace-content" className="workspace-sheet mx-2 mb-18 min-w-0 flex-1 overflow-y-auto rounded-2xl border border-slate-200 bg-white p-4 sm:p-6 lg:mb-2 lg:p-8">
            <SetupProgress />
            {children}
          </main>
        </div>
      </div>
    </NotificationProvider>
  );
}
