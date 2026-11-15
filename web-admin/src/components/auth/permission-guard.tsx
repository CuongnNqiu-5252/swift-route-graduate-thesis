'use client';

import React from 'react';
import Link from 'next/link';
import { ShieldAlert } from 'lucide-react';
import { useAuth } from '@/application/contexts/auth-context';
import { useLocale } from '@/i18n/provider';

interface PermissionGuardProps {
  permission: string | string[];
  requireAll?: boolean;
  fallback?: React.ReactNode;
  children: React.ReactNode;
}

/**
 * Renders children only when the signed-in workspace user holds the required permission(s).
 */
export function PermissionGuard({
  permission,
  requireAll = false,
  fallback = null,
  children,
}: PermissionGuardProps) {
  const { hasPermission, hasAllPermissions } = useAuth();
  const allowed =
    requireAll && Array.isArray(permission)
      ? hasAllPermissions(permission)
      : hasPermission(permission);

  return allowed ? <>{children}</> : <>{fallback}</>;
}

interface AccessDeniedNoticeProps {
  requiredPermission?: string;
}

export function AccessDeniedNotice({ requiredPermission }: AccessDeniedNoticeProps) {
  const { t } = useLocale();
  return (
    <div className="mx-auto my-12 max-w-lg rounded-2xl border border-rose-200 bg-rose-50/70 p-8 text-center">
      <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-rose-100 text-rose-600">
        <ShieldAlert className="h-6 w-6" />
      </div>
      <h2 className="text-lg font-semibold text-slate-900">
        {t('Bạn không có quyền truy cập mục này')}
      </h2>
      <p className="mt-1.5 text-sm text-slate-600">
        {t('Vui lòng liên hệ chủ doanh nghiệp hoặc quản trị viên để được cấp quyền phù hợp.')}
      </p>
      {requiredPermission && (
        <p className="mt-2 font-mono text-xs text-rose-700">{requiredPermission}</p>
      )}
      <div className="mt-5">
        <Link
          href="/"
          className="inline-flex items-center justify-center rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
        >
          {t('Quay về tổng quan')}
        </Link>
      </div>
    </div>
  );
}
