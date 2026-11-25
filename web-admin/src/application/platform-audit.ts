import type { PlatformAuditEntry, PlatformAuditFilter } from '@/infrastructure/repositories/platform.repository';

export function mergeAuditEntries(...groups: readonly PlatformAuditEntry[][]): PlatformAuditEntry[] {
  const entries = new Map<string, PlatformAuditEntry>();
  for (const group of groups) for (const row of group) entries.set(row.id, row);
  return [...entries.values()].sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at) || (a.id < b.id ? 1 : a.id > b.id ? -1 : 0));
}

export function matchesAudit(row: PlatformAuditEntry, filter: PlatformAuditFilter, now = Date.now()): boolean {
  return (!filter.category || row.category === filter.category)
    && (!filter.severity || row.severity === filter.severity)
    && Date.parse(row.created_at) >= now - filter.days * 86_400_000
    && [row.action, row.aggregate_type, row.tenant_name, row.actor_id, row.tenant_id].join(' ').toLowerCase().includes(filter.query.trim().toLowerCase());
}

export const AUDIT_CATEGORIES: Record<string, string> = {
  REVIEW: 'Xét duyệt', ACCESS: 'Vòng đời doanh nghiệp', CONFIGURATION: 'Cấu hình vùng giao',
  AUTOMATION: 'Luồng lập kế hoạch', PROVISIONING: 'Cấp workspace', OTHER: 'Hoạt động khác',
};
export const AUDIT_ACTIONS: Record<string, string> = {
  VERIFICATION_SUBMITTED: 'Gửi hồ sơ xét duyệt', VERIFICATION_APPROVED: 'Phê duyệt doanh nghiệp',
  VERIFICATION_REJECTED: 'Từ chối hồ sơ', VERIFICATION_CHANGES_REQUIRED: 'Yêu cầu bổ sung hồ sơ',
  VERIFICATION_UNDER_REVIEW: 'Bắt đầu xét duyệt', TENANT_SUSPENDED: 'Tạm ngừng doanh nghiệp',
  TENANT_REACTIVATED: 'Kích hoạt lại doanh nghiệp', OWNER_PROVISIONED: 'Hoàn tất cấp workspace',
  FAILED: 'Lập kế hoạch thất bại', PLANNED: 'Đã lập kế hoạch', NO_ORDERS: 'Không có đơn đủ điều kiện',
  NO_ROUTES: 'Chưa tạo được tuyến', DELIVERY_ZONE_CREATED: 'Tạo vùng giao', DELIVERY_ZONE_UPDATED: 'Cập nhật vùng giao',
  TENANT_CREATED: 'Tạo doanh nghiệp', PROVISIONING_FAILED: 'Cấp workspace thất bại',
};
