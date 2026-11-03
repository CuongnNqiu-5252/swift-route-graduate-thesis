'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LayoutDashboard,
  PackageSearch,
  MapPin,
  Route,
  Truck,
  Users,
  BarChart3,
  Settings,
  AlertTriangle,
  WalletCards,
} from 'lucide-react';
import { cn } from '@/lib/utils';

const navItems = [
  { href: '/organizations', label: 'Tổ chức', icon: Users },
  { href: '/onboarding', label: 'Thiết lập doanh nghiệp', icon: Settings },
  { href: '/', label: 'Tổng quan', icon: LayoutDashboard },
  { href: '/orders', label: 'Đơn hàng', icon: PackageSearch },
  { href: '/locations', label: 'Điểm lấy hàng', icon: MapPin },
  { href: '/dispatch', label: 'Điều phối', icon: Route },
  { href: '/drivers', label: 'Tài xế', icon: Truck },
  { href: '/fleet', label: 'Phương tiện', icon: Truck },
  { href: '/operations', label: 'Exception & COD', icon: AlertTriangle },
  { href: '/settlements', label: 'Đối soát tài xế', icon: WalletCards },
  { href: '/members', label: 'Nhân sự', icon: Users },
  { href: '/analytics', label: 'Phân tích', icon: BarChart3 },
  { href: '/settings', label: 'Cài đặt', icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="flex h-screen w-64 flex-col border-r border-gray-200 bg-white">
      {/* Logo */}
      <div className="flex h-16 items-center gap-3 border-b border-gray-200 px-6">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600">
          <Truck size={18} className="text-white" />
        </div>
        <div>
          <p className="text-sm font-bold text-gray-900">SwiftRoute</p>
          <p className="text-xs text-gray-500">Business Workspace</p>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto p-4">
        <ul className="space-y-1">
          {navItems.map(({ href, label, icon: Icon }) => {
            const isActive = href === '/' ? pathname === '/' : pathname.startsWith(href);
            return (
              <li key={href}>
                <Link
                  href={href}
                  className={cn(
                    'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-blue-50 text-blue-700'
                      : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                  )}
                >
                  <Icon size={18} className={isActive ? 'text-blue-600' : 'text-gray-400'} />
                  {label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {/* Footer */}
      <div className="border-t border-gray-200 p-4">
        <p className="text-xs text-gray-400 text-center">SwiftRoute v1.0 — Thesis Demo</p>
      </div>
    </aside>
  );
}
