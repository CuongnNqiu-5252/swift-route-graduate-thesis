/** ADMIN is the existing legacy platform authority; tenant ADMIN maps to TENANT_ADMIN. */
export function isSystemAdmin(role?: string | null): boolean {
  return role === 'SYSTEM_ADMIN' || role === 'ADMIN';
}

export function portalHome(role?: string | null): string {
  return isSystemAdmin(role) ? '/admin/tenant-verifications' : '/organizations';
}
