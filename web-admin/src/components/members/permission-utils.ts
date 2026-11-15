import { PermissionCatalogItem } from '@/domain/entities/member.entity';

export function groupPermissionsByModule(
  permissions: PermissionCatalogItem[]
): Array<{ module: string; items: PermissionCatalogItem[] }> {
  const buckets = new Map<string, PermissionCatalogItem[]>();
  for (const item of permissions) {
    const key = item.module || 'other';
    const current = buckets.get(key);
    if (current) {
      current.push(item);
    } else {
      buckets.set(key, [item]);
    }
  }
  return Array.from(buckets.entries()).map(([module, items]) => ({ module, items }));
}

export function extractApiErrorMessage(err: unknown, fallback: string): string {
  const responseMsg = (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error
    ?.message;
  if (responseMsg) return responseMsg;
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}
