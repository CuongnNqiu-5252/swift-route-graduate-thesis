'use client';

import { useMemo, useState } from 'react';
import { Check, Lock, RotateCcw, Shield, Sparkles, X } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/application/contexts/auth-context';
import { useLocale } from '@/i18n/provider';
import { Button } from '@/components/ui/button';
import {
  MEMBER_ROLE_LABEL,
  PERMISSION_MODULE_LABEL,
  PermissionCatalog,
  TenantMember,
  TenantMembershipRole,
} from '@/domain/entities/member.entity';
import { memberRepository } from '@/infrastructure/repositories/member.repository';
import { extractApiErrorMessage, groupPermissionsByModule } from '@/components/members/permission-utils';

const ASSIGNABLE_ROLES: TenantMembershipRole[] = ['TENANT_ADMIN', 'OPERATOR', 'DRIVER', 'VIEWER'];

interface MemberPermissionsModalProps {
  member: TenantMember | null;
  catalog: PermissionCatalog | null;
  tenantId: string;
  onClose: () => void;
  onSaved: () => Promise<void> | void;
}

export function MemberPermissionsModal({
  member,
  catalog,
  tenantId,
  onClose,
  onSaved,
}: MemberPermissionsModalProps) {
  const { t } = useLocale();
  const { user, hasPermission } = useAuth();

  const [selectedRole, setSelectedRole] = useState<TenantMembershipRole>(
    () => member?.role ?? 'OPERATOR'
  );
  const [selectedEffective, setSelectedEffective] = useState<Set<string>>(
    () => new Set(member?.effective_permissions ?? [])
  );
  const [isSaving, setIsSaving] = useState(false);

  const actorIsOwner = (user?.tenantRole ?? user?.role ?? '').toUpperCase() === 'OWNER';

  const templateForSelectedRole = useMemo(() => {
    const template =
      catalog?.tenant_role_templates?.[selectedRole] ??
      catalog?.system_role_defaults?.[selectedRole] ??
      [];
    return new Set(template);
  }, [catalog, selectedRole]);

  const groupedModules = useMemo(
    () => groupPermissionsByModule(catalog?.permissions ?? []),
    [catalog]
  );

  const { grantedList, revokedList } = useMemo(() => {
    const granted: string[] = [];
    const revoked: string[] = [];
    const allCodes = catalog?.permissions.map((p) => p.code) ?? Array.from(selectedEffective);

    for (const code of allCodes) {
      const inTemplate = templateForSelectedRole.has(code);
      const isEnabled = selectedEffective.has(code);
      if (isEnabled && !inTemplate) {
        granted.push(code);
      } else if (!isEnabled && inTemplate) {
        revoked.push(code);
      }
    }
    return { grantedList: granted, revokedList: revoked };
  }, [catalog, selectedEffective, templateForSelectedRole]);

  if (!member) return null;

  const canAssignRole = (roleOption: TenantMembershipRole) => {
    if (actorIsOwner) return true;
    return roleOption !== 'TENANT_ADMIN';
  };

  const handleRoleChange = (nextRole: TenantMembershipRole) => {
    setSelectedRole(nextRole);
    const nextTemplate =
      catalog?.tenant_role_templates?.[nextRole] ??
      catalog?.system_role_defaults?.[nextRole] ??
      [];
    setSelectedEffective(new Set(nextTemplate));
  };

  const togglePermission = (code: string) => {
    if (!hasPermission(code)) return;
    setSelectedEffective((prev) => {
      const next = new Set(prev);
      if (next.has(code)) {
        next.delete(code);
      } else {
        next.add(code);
      }
      return next;
    });
  };

  const handleResetToTemplate = () => {
    setSelectedEffective(new Set(templateForSelectedRole));
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      if (selectedRole !== member.role) {
        await memberRepository.updateMemberRole(member.membership_id, selectedRole, tenantId);
      }
      await memberRepository.updateMemberPermissions(
        member.membership_id,
        {
          grantedPermissions: grantedList,
          revokedPermissions: revokedList,
        },
        tenantId
      );
      toast.success(t('Đã cập nhật vai trò và phân quyền thành viên. Hệ thống đang đồng bộ tức thì.'));
      await onSaved();
      onClose();
    } catch (err) {
      toast.error(extractApiErrorMessage(err, t('Không thể cập nhật quyền thành viên.')));
    } finally {
      setIsSaving(false);
    }
  };

  const totalCatalog = catalog?.permissions.length ?? selectedEffective.size;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-[1px]">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={t('Phân quyền thành viên')}
        className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-start justify-between border-b border-slate-200 px-6 py-4">
          <div>
            <div className="flex items-center gap-2">
              <Shield className="h-5 w-5 text-blue-600" />
              <h2 className="text-lg font-bold text-slate-900">
                {t('Phân quyền chi tiết thành viên')}
              </h2>
            </div>
            <p className="mt-1 text-sm text-slate-500">
              <span className="font-medium text-slate-800">{member.full_name || member.email}</span>
              {' · '}
              <span>{member.email}</span>
              {' · '}
              <span className="font-mono text-xs text-slate-500">
                v{member.permissions_version}
              </span>
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('Đóng')}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-1 space-y-5 overflow-y-auto px-6 py-5">
          {/* Role selector + summary strip */}
          <div className="grid gap-4 rounded-xl border border-slate-200 bg-slate-50/70 p-4 sm:grid-cols-2">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wide text-slate-600">
                {t('Vai trò trong doanh nghiệp')}
              </label>
              <select
                value={selectedRole}
                onChange={(e) => handleRoleChange(e.target.value as TenantMembershipRole)}
                className="mt-1.5 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-900 focus:border-blue-600 focus:outline-none"
              >
                {ASSIGNABLE_ROLES.map((roleOpt) => (
                  <option key={roleOpt} value={roleOpt} disabled={!canAssignRole(roleOpt)}>
                    {t(MEMBER_ROLE_LABEL[roleOpt])} ({roleOpt})
                  </option>
                ))}
              </select>
              <p className="mt-1 text-xs text-slate-500">
                {t('Đổi vai trò sẽ nạp lại bộ quyền mẫu của vai trò mới để bạn tinh chỉnh thêm.')}
              </p>
            </div>

            <div className="flex flex-col justify-between gap-2">
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <span className="rounded-full bg-slate-900 px-2.5 py-1 font-semibold text-white">
                  {selectedEffective.size}/{totalCatalog} {t('quyền hiệu lực')}
                </span>
                {grantedList.length > 0 && (
                  <span className="rounded-full bg-emerald-100 px-2.5 py-1 font-medium text-emerald-800">
                    +{grantedList.length} {t('cấp thêm')}
                  </span>
                )}
                {revokedList.length > 0 && (
                  <span className="rounded-full bg-rose-100 px-2.5 py-1 font-medium text-rose-800">
                    -{revokedList.length} {t('thu hồi')}
                  </span>
                )}
              </div>
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs text-slate-500">
                  {t('Lưu thay đổi sẽ tự động đồng bộ xuống mọi dịch vụ.')}
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleResetToTemplate}
                  className="shrink-0 gap-1.5 text-xs"
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                  {t('Mặc định vai trò')}
                </Button>
              </div>
            </div>
          </div>

          {/* Granular permission matrix */}
          <div className="space-y-4">
            {groupedModules.map(({ module, items }) => (
              <div key={module} className="overflow-hidden rounded-xl border border-slate-200">
                <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-4 py-2.5">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
                    {t(PERMISSION_MODULE_LABEL[module] || module)}
                  </h3>
                  <span className="text-xs text-slate-500">
                    {items.filter((i) => selectedEffective.has(i.code)).length}/{items.length}
                  </span>
                </div>
                <div className="divide-y divide-slate-100">
                  {items.map((perm) => {
                    const checked = selectedEffective.has(perm.code);
                    const inTemplate = templateForSelectedRole.has(perm.code);
                    const isGrantedOverride = checked && !inTemplate;
                    const isRevokedOverride = !checked && inTemplate;
                    const actorCanEdit = hasPermission(perm.code);

                    return (
                      <label
                        key={perm.code}
                        className={`flex items-start gap-3 px-4 py-3 transition-colors ${
                          actorCanEdit
                            ? 'cursor-pointer hover:bg-slate-50/80'
                            : 'cursor-not-allowed bg-slate-50/50 opacity-65'
                        }`}
                        title={
                          actorCanEdit
                            ? perm.description
                            : t('Bạn không thể cấp hoặc thu hồi quyền mà chính tài khoản của bạn không sở hữu.')
                        }
                      >
                        <button
                          type="button"
                          role="checkbox"
                          aria-checked={checked}
                          disabled={!actorCanEdit}
                          onClick={() => togglePermission(perm.code)}
                          className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border transition-colors ${
                            checked
                              ? 'border-blue-600 bg-blue-600 text-white'
                              : 'border-slate-300 bg-white text-transparent'
                          } disabled:cursor-not-allowed`}
                        >
                          <Check className="h-3.5 w-3.5" />
                        </button>
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="text-sm font-medium text-slate-900">
                              {t(perm.display_name)}
                            </span>
                            <code className="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-600">
                              {perm.code}
                            </code>
                            {isGrantedOverride && (
                              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-medium text-emerald-800">
                                <Sparkles className="h-2.5 w-2.5" />
                                {t('Cấp thêm')}
                              </span>
                            )}
                            {isRevokedOverride && (
                              <span className="rounded-full bg-rose-100 px-2 py-0.5 text-[11px] font-medium text-rose-800">
                                {t('Đã thu hồi')}
                              </span>
                            )}
                            {!actorCanEdit && (
                              <span className="inline-flex items-center gap-1 rounded-full bg-slate-200 px-2 py-0.5 text-[11px] text-slate-600">
                                <Lock className="h-2.5 w-2.5" />
                                {t('Không đủ quyền chỉnh')}
                              </span>
                            )}
                          </div>
                          {perm.description && (
                            <p className="mt-0.5 text-xs text-slate-500">{t(perm.description)}</p>
                          )}
                        </div>
                      </label>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="flex items-center justify-end gap-3 border-t border-slate-200 bg-slate-50 px-6 py-4">
          <Button type="button" variant="outline" onClick={onClose} disabled={isSaving}>
            {t('Hủy')}
          </Button>
          <Button
            type="button"
            onClick={handleSave}
            isLoading={isSaving}
            className="bg-blue-600 hover:bg-blue-700"
          >
            {t('Lưu phân quyền')}
          </Button>
        </div>
      </div>
    </div>
  );
}
