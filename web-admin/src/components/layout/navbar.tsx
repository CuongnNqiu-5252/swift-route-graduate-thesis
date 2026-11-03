'use client';

import Link from 'next/link';
import { Bell, LogOut, User } from 'lucide-react';
import { useAuth } from '@/application/contexts/auth-context';
import { useRouter } from 'next/navigation';

export function Navbar() {
  const { user, logout } = useAuth();
  const router = useRouter();

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  return (
    <header className="flex h-16 items-center justify-between border-b border-gray-200 bg-white px-6">
      <Link href="/organizations" className="text-sm font-medium text-blue-600">Tổ chức / Đổi workspace</Link>
      <div className="flex items-center gap-3">
        <button className="relative rounded-lg p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600">
          <Bell size={18} />
        </button>
        <div className="flex items-center gap-2.5 rounded-lg border border-gray-200 px-3 py-1.5">
          <div className="flex h-7 w-7 items-center justify-center rounded-full bg-blue-100">
            <User size={14} className="text-blue-600" />
          </div>
          <div>
            <p className="text-xs font-medium text-gray-900">{user?.name || user?.email || 'Admin'}</p>
            <p className="text-xs text-gray-500">{user?.role}</p>
          </div>
          <button
            onClick={handleLogout}
            className="ml-2 rounded p-1 text-gray-400 hover:text-red-500"
            title="Đăng xuất"
          >
            <LogOut size={14} />
          </button>
        </div>
      </div>
    </header>
  );
}

