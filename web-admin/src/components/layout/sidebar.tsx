'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { AlertTriangle, BarChart3, LayoutDashboard, MapPin, PackageSearch, Route, Settings, Truck, Users, WalletCards } from 'lucide-react';
import { cn } from '@/lib/utils';

const navItems = [
  { href: '/', label: 'Tổng quan', icon: LayoutDashboard },
  { href: '/orders', label: 'Đơn hàng', icon: PackageSearch },
  { href: '/dispatch', label: 'Điều phối', icon: Route },
  { href: '/operations', label: 'Sự cố & COD', icon: AlertTriangle },
  { href: '/drivers', label: 'Tài xế', icon: Users },
  { href: '/fleet', label: 'Phương tiện', icon: Truck },
  { href: '/locations', label: 'Điểm lấy hàng', icon: MapPin },
  { href: '/settlements', label: 'Đối soát tài xế', icon: WalletCards },
  { href: '/members', label: 'Nhân sự', icon: Users },
  { href: '/analytics', label: 'Phân tích', icon: BarChart3 },
  { href: '/organizations', label: 'Tổ chức', icon: Users },
  { href: '/onboarding', label: 'Thiết lập doanh nghiệp', icon: Settings },
  { href: '/settings', label: 'Cài đặt', icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();
  return (
    <aside className="flex h-dvh w-16 shrink-0 flex-col bg-[#eef1f4] lg:w-60 [&_nav_span]:hidden lg:[&_nav_span]:inline">
      <div className="flex h-16 shrink-0 items-center gap-3 px-4 lg:px-6">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600"><Truck size={18} className="text-white" /></div>
        <div className="hidden lg:block"><p className="text-sm font-bold text-gray-900">SwiftRoute</p><p className="text-xs text-gray-500">Business Workspace</p></div>
      </div>
      <nav className="flex-1 overflow-y-auto p-2 lg:p-4" aria-label="Điều hướng workspace">
        <ul className="space-y-1">{navItems.map(({ href, label, icon: Icon }) => {
          const active = href === '/' ? pathname === '/' : pathname.startsWith(href);
          return <li key={href}><Link href={href} title={label} aria-label={label} aria-current={active ? 'page' : undefined} className={cn('flex min-h-11 items-center justify-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors lg:justify-start', active ? 'bg-white text-slate-950' : 'text-slate-600 hover:bg-white/60 hover:text-slate-950')}><Icon size={18} className="shrink-0" /><span className="hidden lg:inline">{label}</span></Link></li>;
        })}</ul>
      </nav>
      <p className="hidden p-4 text-center text-xs text-slate-500 lg:block">SwiftRoute · Business Workspace</p>
    </aside>
  );
}
