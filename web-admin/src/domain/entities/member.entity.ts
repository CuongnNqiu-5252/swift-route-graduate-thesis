export type TenantMembershipRole = 'OWNER' | 'TENANT_ADMIN' | 'OPERATOR' | 'DRIVER' | 'VIEWER';
export type TenantMembershipStatus = 'ACTIVE' | 'PENDING' | 'SUSPENDED' | 'INACTIVE' | 'REVOKED';
export type TenantInvitationStatus = 'PENDING' | 'ACCEPTED' | 'EXPIRED' | 'REVOKED';

export interface TenantMember {
  membership_id: string;
  tenant_id: string;
  user_id: string;
  email: string;
  full_name: string;
  role: TenantMembershipRole;
  status: TenantMembershipStatus;
  created_at: string;
  updated_at: string;
}

export interface TenantInvitation {
  id: string;
  tenant_id: string;
  driver_id?: string;
  email: string;
  role: TenantMembershipRole;
  status: TenantInvitationStatus;
  invited_by: string;
  expires_at: string;
  created_at: string;
}

export interface InviteMemberResponse {
  id: string;
  tenant_id: string;
  driver_id?: string;
  email: string;
  role: TenantMembershipRole;
  status: TenantInvitationStatus;
  invite_token: string;
  expires_at: string;
  created_at: string;
}

export const MEMBER_ROLE_LABEL: Record<TenantMembershipRole, string> = {
  OWNER: 'Chủ sở hữu',
  TENANT_ADMIN: 'Quản trị viên',
  OPERATOR: 'Nhân viên vận hành',
  DRIVER: 'Tài xế',
  VIEWER: 'Người xem',
};

export function accountRoleLabel(role?: string | null): string {
  if (role === 'SYSTEM_ADMIN') return 'Quản trị viên hệ thống';
  if (role === 'USER') return 'Người dùng';
  return MEMBER_ROLE_LABEL[role as TenantMembershipRole] ?? 'Vai trò chưa xác định';
}

export const MEMBER_STATUS_LABEL: Record<TenantMembershipStatus, string> = {
  ACTIVE: 'Đang hoạt động',
  PENDING: 'Chờ kích hoạt',
  SUSPENDED: 'Đã tạm khóa',
  INACTIVE: 'Ngừng hoạt động',
  REVOKED: 'Đã thu hồi',
};

export const MEMBER_STATUS_COLOR: Record<TenantMembershipStatus, string> = {
  ACTIVE: 'bg-emerald-100 text-emerald-800 border-emerald-200',
  PENDING: 'bg-amber-100 text-amber-800 border-amber-200',
  SUSPENDED: 'bg-rose-100 text-rose-800 border-rose-200',
  INACTIVE: 'bg-gray-100 text-gray-700 border-gray-200',
  REVOKED: 'bg-gray-100 text-gray-700 border-gray-200',
};
