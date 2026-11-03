import { redirect } from 'next/navigation';

/** Preserve old bookmarks while moving reviews out of the tenant portal. */
export default function LegacyTenantVerificationsPage() {
  redirect('/admin/tenant-verifications');
}
