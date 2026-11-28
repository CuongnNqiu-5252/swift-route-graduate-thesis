'use client';

import { useAuth } from '@/application/contexts/auth-context';
import { useLocale } from '@/i18n/provider';

import { cn } from '@/lib/utils';
import { AlertTriangle, ChartNoAxesColumnIncreasing, ClipboardCheck, GitBranch, LayoutDashboard, MapPin, Menu, MessageSquare, PanelLeftClose, PanelLeftOpen, Route, Settings, Truck, UserRound, Users, X, type LucideIcon } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useRef, useState, useSyncExternalStore, type CSSProperties, type KeyboardEvent, type PointerEvent } from 'react';
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
      { href: '/overview', label: 'Tổng quan', icon: LayoutDashboard },
    ],
  },
  {
    label: 'Vận hành',
    items: [
      { href: '/dispatch', label: 'Điều phối', icon: Route, requiredPermissions: ['dispatch:read', 'dispatch:execute', 'order:read'] },
      { href: '/operation-flow', label: 'Luồng vận hành', icon: GitBranch, requiredPermissions: ['order:create', 'dispatch:execute'] },
      { href: '/operations', label: 'Sự cố & COD', icon: AlertTriangle, requiredPermissions: ['sla:read', 'order:read'] },
      { href: '/reports', label: 'Báo cáo giao hàng', icon: ChartNoAxesColumnIncreasing, requiredPermissions: ['order:read'], tenantRoles: ['OWNER', 'TENANT_ADMIN', 'OPERATOR'] },
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
      {
        href: '/proof-of-delivery',
        label: 'Bằng chứng giao nhận',
        icon: ClipboardCheck,
      },
    ],
  },
];

const mobilePrimary = ['/overview', '/dispatch', '/operations'];
const sidebarWidthKey = 'swiftroute.workspace.sidebar-width';
const defaultSidebarWidth = 240;
const minSidebarWidth = 216;
const maxSidebarWidth = 360;

const clampSidebarWidth = (width: number) => Math.min(maxSidebarWidth, Math.max(minSidebarWidth, width));
const getDefaultSidebarWidth = () => defaultSidebarWidth;

function readSidebarWidth() {
  try {
    const width = Number.parseInt(window.localStorage.getItem(sidebarWidthKey) ?? '', 10);
    return Number.isFinite(width) ? clampSidebarWidth(width) : defaultSidebarWidth;
  } catch {
    return defaultSidebarWidth;
  }
}

function subscribeToSidebarWidth(onChange: () => void) {
  const onStorage = (event: StorageEvent) => {
    if (event.key === sidebarWidthKey || event.key === null) onChange();
  };
  window.addEventListener('storage', onStorage);
  return () => window.removeEventListener('storage', onStorage);
}

export function Sidebar() {
  const { t } = useLocale();
  const { hasPermission, user } = useAuth();
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const storedWidth = useSyncExternalStore(subscribeToSidebarWidth, readSidebarWidth, getDefaultSidebarWidth);
  const [customWidth, setCustomWidth] = useState<number>();
  const [isResizing, setIsResizing] = useState(false);
  const resize = useRef<{ pointerId: number; startX: number; startWidth: number; width: number } | null>(null);
  const moreButton = useRef<HTMLButtonElement>(null);
  const expandedWidth = customWidth ?? storedWidth;
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

  const isActive = (href: string) => pathname === href || pathname.startsWith(href + '/');
  const moreActive = moreGroups.some((group) => group.items.some(({ href }) => isActive(href)));
  const focusClass = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-600';
  const toggleLabel = collapsed ? t('Mở rộng sidebar') : t('Thu gọn sidebar');
  const resizeLabel = t('Điều chỉnh độ rộng sidebar');
  const labelMotion = 'truncate motion-safe:transition-[opacity,translate] motion-safe:duration-[280ms] motion-safe:ease-out';
  const collapsedLabel = collapsed && '-translate-x-3 opacity-0';

  function saveWidth(width: number) {
    setCustomWidth(width);
    try {
      window.localStorage.setItem(sidebarWidthKey, String(width));
    } catch {
      // Resizing still works when browser storage is unavailable.
    }
  }

  function startResize(event: PointerEvent<HTMLDivElement>) {
    if (event.button !== 0 || !event.isPrimary || resize.current) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    resize.current = { pointerId: event.pointerId, startX: event.clientX, startWidth: expandedWidth, width: expandedWidth };
    setIsResizing(true);
  }

  function moveResize(event: PointerEvent<HTMLDivElement>) {
    const drag = resize.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    drag.width = clampSidebarWidth(Math.round(drag.startWidth + event.clientX - drag.startX));
    setCustomWidth(drag.width);
  }

  function finishResize(event: PointerEvent<HTMLDivElement>) {
    const drag = resize.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    resize.current = null;
    setIsResizing(false);
    saveWidth(drag.width);
  }

  function resizeWithKeyboard(event: KeyboardEvent<HTMLDivElement>) {
    const widths: Record<string, number> = {
      ArrowLeft: expandedWidth - 16,
      ArrowRight: expandedWidth + 16,
      Home: minSidebarWidth,
      End: maxSidebarWidth,
    };
    if (!Object.hasOwn(widths, event.key)) return;
    event.preventDefault();
    saveWidth(clampSidebarWidth(widths[event.key]));
  }

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
            'flex min-h-11 items-center gap-2.5 overflow-hidden rounded-sm text-sm font-medium transition-colors',
            focusClass,
            mobile ? 'px-3' : 'px-[19px]',
            active ? 'bg-white font-semibold text-slate-950' : 'text-slate-700 hover:bg-white/70',
            mobile && (active ? 'bg-slate-100' : 'hover:bg-slate-100'),
          )}
        >
          <Icon size={17} className="shrink-0" aria-hidden="true" />
          <span className={mobile ? undefined : cn(labelMotion, collapsedLabel)} aria-hidden={!mobile && collapsed}>
            {t(label)}
          </span>
        </Link>
      </li>
    );
  }

  return (
    <aside
      id="workspace-sidebar"
      style={{ '--workspace-sidebar-width': `${collapsed ? 80 : expandedWidth}px` } as CSSProperties}
      className={cn(
        'fixed inset-x-0 bottom-0 z-40 flex h-16 border-t border-slate-200 bg-white lg:relative lg:inset-auto lg:z-auto lg:h-dvh lg:w-[var(--workspace-sidebar-width)] lg:shrink-0 lg:flex-col lg:border-0 lg:bg-[#eef1f4]',
        isResizing ? 'select-none' : 'motion-safe:lg:transition-[width] motion-safe:lg:duration-[380ms] motion-safe:lg:ease-[cubic-bezier(0.4,0,0.2,1)]',
      )}
      onKeyDown={(event) => {
        if (event.key === 'Escape' && menuOpen) {
          setMenuOpen(false);
          moreButton.current?.focus();
        }
      }}
    >
      <div className="hidden h-16 shrink-0 items-center gap-3 overflow-hidden pl-6 pr-3 lg:flex">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-sm bg-blue-600">
          <Truck size={18} className="text-white" aria-hidden="true" />
        </div>
        <div className={cn(labelMotion, collapsedLabel)} aria-hidden={collapsed}>
          <p className="truncate text-sm font-bold text-slate-950">SwiftRoute</p>
          <p className="truncate text-xs text-slate-600">{t('Quản lý doanh nghiệp')}</p>
        </div>
      </div>

      <nav
        className="workspace-sidebar-nav hidden min-h-0 flex-1 overflow-y-auto overscroll-y-contain px-3 pb-2 focus-visible:outline-offset-[-2px] lg:block"
        aria-label={t('Điều hướng doanh nghiệp')}
        tabIndex={0}
      >
        {groups.map(({ label, items }, index) => (
          <div key={label ?? 'overview'} className={cn(index > 0 && 'mt-2 border-t border-slate-200 pt-2')}>
            {label && (
              <div className={cn('grid motion-safe:transition-[grid-template-rows] motion-safe:duration-[280ms] motion-safe:ease-out', collapsed ? 'grid-rows-[0fr]' : 'grid-rows-[1fr]')} aria-hidden={collapsed}>
                <div className="overflow-hidden">
                  <p className={cn('px-[19px] pb-1 text-xs font-medium text-slate-600', labelMotion, collapsedLabel)}>{t(label)}</p>
                </div>
              </div>
            )}
            <ul aria-label={label ? t(label) : undefined}>{items.map((item) => renderLink(item))}</ul>
          </div>
        ))}
      </nav>

      <nav className="flex min-w-0 flex-1 lg:hidden" aria-label={t('Điều hướng doanh nghiệp')}>
        <ul className="flex min-w-0 flex-1 items-center justify-evenly px-1">
          {mobileItems.map(({ href, label, icon: Icon }) => (
            <li key={href} className="min-w-0 flex-1">
              <Link href={href} onClick={() => setMenuOpen(false)} aria-current={isActive(href) ? 'page' : undefined}
                className={cn('mx-0.5 flex min-h-12 flex-col items-center justify-center gap-1 rounded-sm px-0.5 text-center text-[11px] leading-tight', focusClass, isActive(href) ? 'bg-slate-100 font-semibold text-slate-950' : 'text-slate-600')}>
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
                className={cn('mx-0.5 flex min-h-12 w-full flex-col items-center justify-center gap-1 rounded-sm text-[11px]', focusClass, moreActive || menuOpen ? 'bg-slate-100 font-semibold text-slate-950' : 'text-slate-600')}>
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

      <div className="hidden py-3 pl-[18px] lg:block">
        <button type="button" onClick={() => setCollapsed((value) => !value)}
          aria-label={toggleLabel} title={toggleLabel} aria-expanded={!collapsed}
          className={cn('grid size-11 cursor-pointer place-items-center rounded-sm text-slate-600 transition-colors hover:bg-white hover:text-slate-950', focusClass)}>
          {collapsed ? <PanelLeftOpen size={19} aria-hidden="true" /> : <PanelLeftClose size={19} aria-hidden="true" />}
        </button>
      </div>

      {!collapsed && (
        <div
          role="separator"
          tabIndex={0}
          aria-label={resizeLabel}
          aria-controls="workspace-sidebar"
          aria-orientation="vertical"
          aria-valuemin={minSidebarWidth}
          aria-valuemax={maxSidebarWidth}
          aria-valuenow={expandedWidth}
          title={resizeLabel}
          onPointerDown={startResize}
          onPointerMove={moveResize}
          onLostPointerCapture={finishResize}
          onKeyDown={resizeWithKeyboard}
          className={cn(
            'group absolute inset-y-2 right-0 hidden w-2 touch-none cursor-ew-resize items-center justify-center rounded-sm focus-visible:outline-offset-0 lg:flex',
            focusClass,
          )}
        >
          <span aria-hidden="true" className={cn('h-7 w-0.5 rounded-full transition-colors group-hover:bg-slate-600 group-focus-visible:bg-slate-600', isResizing ? 'bg-slate-600' : 'bg-slate-400')} />
        </div>
      )}
    </aside>
  );
}
