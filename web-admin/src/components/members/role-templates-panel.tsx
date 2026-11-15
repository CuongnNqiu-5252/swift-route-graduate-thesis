'use client';

import { useMemo, useState } from 'react';
import { Check, Info, RotateCcw, Save, ShieldCheck } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/application/contexts/auth-context';
import { useLocale } from '@/i18n/provider';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  MEMBER_ROLE_LABEL,
  PERMISSION_MODULE_LABEL,
  PermissionCatalog,
  TenantMembershipRole,
} from '@/domain/entities/member.entity';
import { memberRepository } from '@/infrastructure/repositories/member.repository';
import { extractApiErrorMessage, groupPermissionsByModule } from '@/components/members/permission-utils';

const EDITABLE_TEMPLATE_ROLES: TenantMembershipRole[] = [
  'TENANT_ADMIN',
  'OPERATOR',
  'DRIVER',
  'VIEWER',
];

interface RoleTemplatesPanelProps {
  catalog: PermissionCatalog | null;
  tenantId: string;
  onUpdated: () => Promise<void> | void;
}

export function RoleTemplatesPanel({ catalog, tenantId, onUpdated }: RoleTemplatesPanelProps) {
  const { t } = useLocale();
  const { user } = useAuth();
  const isOwner = (user?.tenantRole ?? user?.role ?? '').toUpperCase() === 'OWNER';

  const [selectedRole, setSelectedRole] = useState<TenantMembershipRole>('OPERATOR');
  const [draftOverride, setDraftOverride] = useState<Set<string> | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const draftPerms = useMemo(() => {
    if (draftOverride) return draftOverride;
    const initial =
      catalog?.tenant_role_templates?.[selectedRole] ??
      catalog?.system_role_defaults?.[selectedRole] ??
      [];
    return new Set(initial);
  }, [catalog, draftOverride, selectedRole]);

  const groupedModules = useMemo(
    () => groupPermissionsByModule(catalog?.permissions ?? []),
    [catalog]
  );

  const toggleCode = (code: string) => {
    if (!isOwner) return;
    const next = new Set(draftPerms);
    if (next.has(code)) {
      next.delete(code);
    } else {
      next.add(code);
    }
    setDraftOverride(next);
  };

  const handleResetSystemDefault = () => {
    if (!isOwner) return;
    const defaults = catalog?.system_role_defaults?.[selectedRole] ?? [];
    setDraftOverride(new Set(defaults));
  };

  const handleSaveTemplate = async () => {
    if (!isOwner) return;
    setIsSaving(true);
    try {
      await memberRepository.updateRolePermissionsTemplate(
        selectedRole,
        Array.from(draftPerms),
        tenantId
      );
      toast.success(
        t('Đã lưu mẫu quyền mặc định cho vai trò. Tất cả thành viên giữ vai trò này đã được đồng bộ.')
      );
      setDraftOverride(null);
      await onUpdated();
    } catch (err) {
      toast.error(extractApiErrorMessage(err, t('Không thể lưu mẫu quyền vai trò.')));
    } finally {
      setIsSaving(false);
    }
  };

  if (!catalog) {
    return (
      <Card>
        <CardContent className="py-12 text-center text-sm text-slate-400">
          {t('Đang tải danh mục quyền hạn...')}
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {!isOwner && (
        <div className="flex items-start gap-2.5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
          <p>
            {t(
              'Chỉ Chủ sở hữu doanh nghiệp (OWNER) mới được chỉnh sửa bộ quyền mẫu theo vai trò. Bạn đang xem ở chế độ chỉ đọc.'
            )}
          </p>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-3">
        <div className="flex flex-wrap gap-2">
          {EDITABLE_TEMPLATE_ROLES.map((roleKey) => {
            const active = selectedRole === roleKey;
            const count = (catalog.tenant_role_templates?.[roleKey] ?? []).length;
            return (
              <button
                key={roleKey}
                type="button"
                onClick={() => {
                  setSelectedRole(roleKey);
                  setDraftOverride(null);
                }}
                className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-2 text-sm font-medium transition-colors ${
                  active
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                }`}
              >
                <ShieldCheck className="h-4 w-4" />
                <span>{t(MEMBER_ROLE_LABEL[roleKey])}</span>
                <span
                  className={`rounded-full px-1.5 py-0.5 text-xs ${
                    active ? 'bg-white/20 text-white' : 'bg-white text-slate-600'
                  }`}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {isOwner && (
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleResetSystemDefault}
              className="gap-1.5"
            >
              <RotateCcw className="h-3.5 w-3.5" />
              {t('Khôi phục mặc định')}
            </Button>
            <Button
              type="button"
              size="sm"
              onClick={handleSaveTemplate}
              isLoading={isSaving}
              className="gap-1.5 bg-blue-600 hover:bg-blue-700"
            >
              <Save className="h-3.5 w-3.5" />
              {t('Lưu mẫu vai trò')}
            </Button>
          </div>
        )}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {groupedModules.map(({ module, items }) => (
          <Card key={module} className="overflow-hidden">
            <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-4 py-2.5">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
                {t(PERMISSION_MODULE_LABEL[module] || module)}
              </h3>
              <span className="text-xs text-slate-500">
                {items.filter((i) => draftPerms.has(i.code)).length}/{items.length}
              </span>
            </div>
            <CardContent className="divide-y divide-slate-100 p-0">
              {items.map((perm) => {
                const checked = draftPerms.has(perm.code);
                return (
                  <label
                    key={perm.code}
                    className={`flex items-start gap-3 px-4 py-3 ${
                      isOwner ? 'cursor-pointer hover:bg-slate-50/70' : 'cursor-default'
                    }`}
                  >
                    <button
                      type="button"
                      role="checkbox"
                      aria-checked={checked}
                      disabled={!isOwner}
                      onClick={() => toggleCode(perm.code)}
                      className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border transition-colors ${
                        checked
                          ? 'border-blue-600 bg-blue-600 text-white'
                          : 'border-slate-300 bg-white text-transparent'
                      } disabled:opacity-75`}
                    >
                      <Check className="h-3.5 w-3.5" />
                    </button>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-medium text-slate-900">
                          {t(perm.display_name)}
                        </span>
                        <code className="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-500">
                          {perm.code}
                        </code>
                      </div>
                      <p className="mt-0.5 text-xs text-slate-500">{t(perm.description)}</p>
                    </div>
                  </label>
                );
              })}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
