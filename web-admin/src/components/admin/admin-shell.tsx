'use client';

import { useLocale } from '@/i18n/provider';
import { LanguageSelect } from '@/components/layout/language-select';

import { accountRoleLabel } from '@/domain/entities/member.entity';
import { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ClipboardCheck, LogOut, Menu, ShieldCheck, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface AdminShellProps {
  userName?: string | null;
  userEmail?: string | null;
  userRole?: string | null;
  onLogout: () => void;
  children: React.ReactNode;
}

const ADMIN_NAV_ITEMS = [
  {
    href: '/admin/tenant-verifications',
    label: 'Xét duyệt doanh nghiệp',
    icon: ClipboardCheck,
  },
];

export function AdminShell({
  userName,
  userEmail,
  userRole,
  onLogout,
  children,
}: AdminShellProps) {
  const { t } = useLocale();
  const pathname = usePathname();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <div className="flex h-dvh overflow-hidden bg-[#eef1f4] text-slate-900">
      <aside className="hidden w-60 shrink-0 flex-col bg-[#eef1f4] lg:flex">
        <div className="flex h-16 shrink-0 items-center gap-3 px-6">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#17766c] text-white">
            <ShieldCheck className="h-4 w-4" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-slate-950">SwiftRoute</p>
            <p className="truncate text-xs text-slate-500">{t("Quản trị nền tảng")}</p>
          </div>
        </div>

        <nav aria-label={t("Điều hướng quản trị hệ thống")} className="flex-1 px-3 py-4">
          <ul className="space-y-1">
            {ADMIN_NAV_ITEMS.map(({ href, label, icon: Icon }) => {
              const active = pathname.startsWith(href);
              return (
                <li key={href}>
                  <Link
                    href={href}
                    aria-current={active ? 'page' : undefined}
                    className={cn(
                      'flex min-h-11 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                      active
                        ? 'border border-slate-200 bg-white text-slate-950'
                        : 'text-slate-600 hover:bg-white/60 hover:text-slate-950',
                    )}
                  >
                    <Icon className="h-4 w-4 shrink-0 text-[#17766c]" aria-hidden="true" />
                    <span className="truncate">{t(label)}</span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="px-6 py-4 text-xs text-slate-500">
          {t("Khu vực kiểm duyệt & vòng đời doanh nghiệp")}
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <header className="flex h-16 shrink-0 items-center justify-between gap-4 px-4 sm:px-6">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setMobileMenuOpen((prev) => !prev)}
              aria-label={mobileMenuOpen ? t("Đóng điều hướng") : t("Mở điều hướng")}
              className="flex h-10 w-10 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-700 lg:hidden"
            >
              {mobileMenuOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
            </button>
            <div className="flex items-center gap-2 lg:hidden">
              <ShieldCheck className="h-4 w-4 text-[#17766c]" aria-hidden="true" />
              <span className="text-sm font-semibold text-slate-900">SwiftRoute Platform</span>
            </div>
          </div>

          <div className="flex min-w-0 items-center gap-2 sm:gap-4">
            <LanguageSelect />
            <div className="hidden min-w-0 text-right leading-tight sm:block">
              <p className="text-sm font-medium text-slate-900">{userName || userEmail || t("Quản trị viên")}</p>
              {userRole && <p className="text-xs text-slate-500">{t(accountRoleLabel(userRole))}</p>}
            </div>
            <Button variant="outline" size="sm" onClick={onLogout} aria-label={t("Đăng xuất")} className="bg-white">
              <LogOut className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">{t("Đăng xuất")}</span>
            </Button>
          </div>
        </header>

        {mobileMenuOpen && (
          <nav
            aria-label={t("Điều hướng di động")}
            className="mx-3 mb-2 rounded-xl border border-slate-200 bg-white p-2 lg:hidden"
          >
            {ADMIN_NAV_ITEMS.map(({ href, label, icon: Icon }) => {
              const active = pathname.startsWith(href);
              return (
                <Link
                  key={href}
                  href={href}
                  onClick={() => setMobileMenuOpen(false)}
                  className={cn(
                    'flex min-h-11 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium',
                    active ? 'bg-slate-100 text-slate-950' : 'text-slate-600',
                  )}
                >
                  <Icon className="h-4 w-4 text-[#17766c]" />
                  <span>{t(label)}</span>
                </Link>
              );
            })}
          </nav>
        )}

        <main
          id="admin-workspace-content"
          className="workspace-sheet mx-2 mb-2 min-w-0 flex-1 overflow-y-auto rounded-2xl border border-slate-200 bg-white p-4 sm:p-6 lg:p-8"
        >
          {children}
        </main>
      </div>
    </div>
  );
}
