'use client';

import Link from 'next/link';
import { LogOut, User } from 'lucide-react';
import { useAuth } from '@/application/contexts/auth-context';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { Membership, switchTenant, tenantWorkflow } from '@/infrastructure/repositories/tenant-workflow.repository';
import api from '@/lib/api';

export function Navbar() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [workspaces, setWorkspaces] = useState<Array<Membership & { businessName?: string }>>([]);

  useEffect(() => { tenantWorkflow.memberships().then(items=>Promise.all(items.map(async item=>{try{const response=await api.get<{business_name:string}>(`/tenants/${item.tenant_id}`);return {...item,businessName:response.data.business_name};}catch{return item;}}))).then(setWorkspaces).catch(() => setWorkspaces([])); }, []);

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  return (
    <header className="flex h-16 shrink-0 items-center justify-between gap-2 bg-[#eef1f4] px-3 sm:px-6">
      <div className="flex min-w-0 items-center gap-2"><span className="hidden text-xs font-medium text-slate-500 sm:inline">Workspace</span>{workspaces.length > 0 ? <select aria-label="Đổi workspace" value={user?.tenantId || ''} onChange={event=>void switchTenant(event.target.value)} className="max-w-52 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-900"><option value="" disabled>Chọn workspace</option>{workspaces.map(item=><option key={item.tenant_id} value={item.tenant_id}>{item.businessName||`Workspace ${item.tenant_id.slice(0,8)}`}</option>)}</select> : <Link href="/organizations" className="text-sm font-medium text-blue-700 underline underline-offset-4">Tạo workspace</Link>}</div>
      <div className="flex items-center gap-3">
        <div className="flex items-center rounded-lg border border-gray-200 bg-white px-2 py-1.5">
          <Link href="/profile" className="flex items-center gap-2.5 px-1" aria-label="Mở hồ sơ cá nhân">
          <div className="flex h-7 w-7 items-center justify-center rounded-full bg-blue-100">
            <User size={14} className="text-blue-600" />
          </div>
          <div className="hidden max-w-48 truncate sm:block">
            <p className="text-xs font-medium text-gray-900">{user?.name || user?.email || 'Admin'}</p>
            <p className="text-xs text-gray-500">{user?.role}</p>
          </div>
          </Link>
          <button
            onClick={handleLogout}
            className="ml-2 rounded p-1 text-gray-400 hover:text-red-500"
            title="Đăng xuất"
            aria-label="Đăng xuất"
          >
            <LogOut size={14} />
          </button>
        </div>
      </div>
    </header>
  );
}

