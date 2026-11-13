export type RoleContext =
  | string
  | {
      role?: string | null;
      systemRole?: string | null;
      accountRole?: string | null;
    }
  | null
  | undefined;

/** Platform authority check using canonical SYSTEM_ADMIN role. */
export function isSystemAdmin(roleOrUser?: RoleContext): boolean {
  if (!roleOrUser) return false;
  if (typeof roleOrUser === 'string') {
    return roleOrUser === 'SYSTEM_ADMIN';
  }
  return (roleOrUser.systemRole ?? roleOrUser.accountRole ?? roleOrUser.role) === 'SYSTEM_ADMIN';
}

export function portalHome(roleOrUser?: RoleContext): string {
  return isSystemAdmin(roleOrUser) ? '/admin/tenant-verifications' : '/organizations';
}
