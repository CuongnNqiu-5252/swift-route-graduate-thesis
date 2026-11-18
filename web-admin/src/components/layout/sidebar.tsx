'use client';

import { useLocale } from '@/i18n/provider';
import { useAuth } from '@/application/contexts/auth-context';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { AlertTriangle, ChevronLeft, ChevronRight, GitBranch, LayoutDashboard, MapPin, Menu, MessageSquare, PackageSearch, Route, Settings, Truck, UserRound, Users, X, type LucideIcon } from 'lucide-react';
import { useRef, useState } from 'react';
import { cn } from '@/lib/utils';

interface NavItemConfig {
  href: string;
  label: string;
  icon: LucideIcon;
  requiredPermissions?: string[];
  tenantRoles?: string[];
}

const navGroups: { label?: string; items: NavItemConfig[] }[] = [
  {
    items: [
      { href: '/', label: 'Tổng quan', icon: LayoutDashboard },
    ],
  },
  {
    label: 'Vận hành',
    items: [
      { href: '/orders', label: 'Đơn hàng', icon: PackageSearch, requiredPermissions: ['order:read'] },
      { href: '/dispatch', label: 'Điều phối', icon: Route, requiredPermissions: ['dispatch:read', 'dispatch:execute'] },
      { href: '/operation-flow', label: 'Luồng vận hành', icon: GitBranch, requiredPermissions: ['order:create', 'dispatch:execute'] },
      { href: '/operations', label: 'Sự cố & COD', icon: AlertTriangle, requiredPermissions: ['sla:read', 'order:read'] },
      { href: '/messages', label: 'Tin nhắn', icon: MessageSquare, tenantRoles: ['OWNER', 'TENANT_ADMIN', 'OPERATOR'] },
    ],
  },
  {
    label: 'Nguồn lực giao hàng',
    items: [
      { href: '/drivers', label: 'Tài xế', icon: Users, requiredPermissions: ['driver:read', 'vehicle:read'] },
      { href: '/locations', label: 'Điểm lấy hàng', icon: MapPin, requiredPermissions: ['hub:read'] },
    ],
  },
  {
    label: 'Quản trị',
    items: [
      { href: '/members', label: 'Nhân sự', icon: Users, requiredPermissions: ['member:read'] },
      { href: '/organizations', label: 'Tổ chức', icon: Users },
      { href: '/onboarding', label: 'Thiết lập doanh nghiệp', icon: Settings, requiredPermissions: ['tenant:settings:manage'] },
      { href: '/settings', label: 'Cài đặt', icon: Settings, requiredPermissions: ['tenant:settings:read'] },
      { href: '/profile', label: 'Hồ sơ cá nhân', icon: UserRound },
    ],
  },
];

const mobilePrimary = ['/', '/orders', '/dispatch', '/operations'];

export function Sidebar() {
  const { t } = useLocale();
  const { hasPermission, user } = useAuth();
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const moreButton = useRef<HTMLButtonElement>(null);
  const groups = navGroups.map((group) => ({
    ...group,
    items: group.items.filter((item) =>
      (!item.requiredPermissions || hasPermission(item.requiredPermissions))
      && (!item.tenantRoles || item.tenantRoles.includes(user?.tenantRole ?? '')),
    ),
  })).filter(({ items }) => items.length > 0);
  const items = groups.flatMap((group) => group.items);
  const mobileItems = items.filter(({ href }) => mobilePrimary.includes(href));
  const moreGroups = groups.map((group) => ({
    ...group, items: group.items.filter(({ href }) => !mobilePrimary.includes(href)),
  })).filter(({ items: links }) => links.length > 0);

  const isActive = (href: string) => pathname === href || (href !== '/' && pathname.startsWith(href + '/'));
  const moreActive = moreGroups.some((group) => group.items.some(({ href }) => isActive(href)));
  const focusClass = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-600';

  function renderLink({ href, label, icon: Icon }: NavItemConfig, mobile = false) {
    const active = isActive(href);
    return (
      <li key={href}>
        <Link
          href={href}
          title={t(label)}
          aria-label={t(label)}
          aria-current={active ? 'page' : undefined}
          onClick={() => setMenuOpen(false)}
          className={cn(
            'flex min-h-11 items-center gap-2.5 rounded-lg px-3 text-sm font-medium transition-colors',
            focusClass,
            !mobile && collapsed && 'justify-center',
            active ? 'bg-white font-semibold text-slate-950' : 'text-slate-700 hover:bg-white/70',
            mobile && (active ? 'bg-slate-100' : 'hover:bg-slate-100'),
          )}
        >
          <Icon size={17} className="shrink-0" aria-hidden="true" />
          {(mobile || !collapsed) && <span>{t(label)}</span>}
        </Link>
      </li>
    );
  }

  return (
    <aside
      className={cn('fixed inset-x-0 bottom-0 z-40 flex h-16 border-t border-slate-200 bg-white transition-[width] motion-reduce:transition-none lg:static lg:h-dvh lg:shrink-0 lg:flex-col lg:border-0 lg:bg-[#eef1f4]', collapsed ? 'lg:w-20' : 'lg:w-60')}
      onKeyDown={(event) => {
        if (event.key === 'Escape' && menuOpen) {
          setMenuOpen(false);
          moreButton.current?.focus();
        }
      }}
    >
      <div className="hidden h-16 shrink-0 items-center gap-3 px-6 lg:flex">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-600">
          <Truck size={18} className="text-white" aria-hidden="true" />
        </div>
        {!collapsed && (
          <div>
            <p className="text-sm font-bold text-slate-950">SwiftRoute</p>
            <p className="text-xs text-slate-600">{t('Quản lý doanh nghiệp')}</p>
          </div>
        )}
      </div>

      <nav className="hidden min-h-0 flex-1 overflow-y-auto px-3 pb-2 lg:block" aria-label={t('Điều hướng doanh nghiệp')}>
        {groups.map(({ label, items }, index) => (
          <div key={label ?? 'overview'} className={cn(index > 0 && 'mt-2 border-t border-slate-200 pt-2')}>
            {label && !collapsed && <p className="px-3 pb-1 text-xs font-medium text-slate-600">{t(label)}</p>}
            <ul aria-label={label ? t(label) : undefined}>{items.map((item) => renderLink(item))}</ul>
          </div>
        ))}
      </nav>

      <nav className="flex min-w-0 flex-1 lg:hidden" aria-label={t('Điều hướng doanh nghiệp')}>
        <ul className="flex min-w-0 flex-1 items-center justify-evenly px-1">
          {mobileItems.map(({ href, label, icon: Icon }) => (
            <li key={href} className="min-w-0 flex-1">
              <Link href={href} onClick={() => setMenuOpen(false)} aria-current={isActive(href) ? 'page' : undefined}
                className={cn('mx-0.5 flex min-h-12 flex-col items-center justify-center gap-1 rounded-lg px-0.5 text-center text-[11px] leading-tight', focusClass, isActive(href) ? 'bg-slate-100 font-semibold text-slate-950' : 'text-slate-600')}>
                <Icon size={18} className="shrink-0" aria-hidden="true" />
                <span>{t(label)}</span>
              </Link>
            </li>
          ))}
          {moreGroups.length > 0 && (
            <li className="min-w-0 flex-1">
              <button ref={moreButton} type="button" aria-label={menuOpen ? t('Đóng menu') : t('Mở thêm mục')}
                aria-expanded={menuOpen} aria-controls={menuOpen ? 'mobile-navigation' : undefined}
                onClick={() => setMenuOpen((open) => !open)}
                className={cn('mx-0.5 flex min-h-12 w-full flex-col items-center justify-center gap-1 rounded-lg text-[11px]', focusClass, moreActive || menuOpen ? 'bg-slate-100 font-semibold text-slate-950' : 'text-slate-600')}>
                {menuOpen ? <X size={18} aria-hidden="true" /> : <Menu size={18} aria-hidden="true" />}
                <span>{t('Thêm')}</span>
              </button>
            </li>
          )}
        </ul>
        {menuOpen && (
          <div id="mobile-navigation" className="absolute inset-x-0 bottom-full max-h-[65dvh] space-y-3 overflow-y-auto rounded-t-xl border-t border-slate-200 bg-white px-3 py-3 shadow-[0_-6px_24px_-15px_#0f172a]">
            {moreGroups.map(({ label, items }) => (
              <div key={label} className="space-y-1">
                {label && <p className="px-3 text-xs font-medium text-slate-600">{t(label)}</p>}
                <ul aria-label={label ? t(label) : undefined} className="grid grid-cols-2">
                  {items.map((item) => renderLink(item, true))}
                </ul>
              </div>
            ))}
          </div>
        )}
      </nav>

      <div className="hidden p-3 lg:block">
        <button type="button" onClick={() => setCollapsed((value) => !value)}
          aria-label={t(collapsed ? 'Mở rộng sidebar' : 'Thu gọn sidebar')}
          className={cn('flex min-h-10 w-full items-center justify-center gap-2 rounded-lg text-sm text-slate-600 hover:bg-white', focusClass)}>
          {collapsed ? <ChevronRight size={17} aria-hidden="true" /> : <><ChevronLeft size={17} aria-hidden="true" /><span>{t('Thu gọn')}</span></>}
        </button>
      </div>
    </aside>
  );
}
