import {
  TenantMember,
  TenantInvitation,
  InviteMemberResponse,
  TenantMembershipRole,
  TenantMembershipStatus,
  PermissionCatalog,
  PermissionCatalogItem,
} from '@/domain/entities/member.entity';
import api from '@/lib/api';

type ApiRecord = Record<string, unknown>;

const value = (raw: ApiRecord, camel: string, snake: string) => raw[camel] ?? raw[snake];

function mapStringArray(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
    .map((item) => item.trim());
}

function mapMember(raw: ApiRecord): TenantMember {
  return {
    membership_id: String(value(raw, 'membershipId', 'membership_id') ?? ''),
    tenant_id: String(value(raw, 'tenantId', 'tenant_id') ?? ''),
    user_id: String(value(raw, 'userId', 'user_id') ?? ''),
    email: String(raw.email ?? ''),
    full_name: String(value(raw, 'fullName', 'full_name') ?? ''),
    role: raw.role as TenantMembershipRole,
    status: raw.status as TenantMembershipStatus,
    created_at: String(value(raw, 'createdAt', 'created_at') ?? ''),
    updated_at: String(value(raw, 'updatedAt', 'updated_at') ?? ''),
    granted_permissions: mapStringArray(value(raw, 'grantedPermissions', 'granted_permissions')),
    revoked_permissions: mapStringArray(value(raw, 'revokedPermissions', 'revoked_permissions')),
    effective_permissions: mapStringArray(value(raw, 'effectivePermissions', 'effective_permissions')),
    permissions_version: Number(value(raw, 'permissionsVersion', 'permissions_version') ?? 1),
  };
}

function mapPermissionCatalogItem(raw: ApiRecord): PermissionCatalogItem {
  return {
    code: String(raw.code ?? ''),
    module: String(raw.module ?? ''),
    display_name: String(value(raw, 'displayName', 'display_name') ?? raw.code ?? ''),
    description: String(raw.description ?? ''),
    driver_only: Boolean(value(raw, 'driverOnly', 'driver_only')),
  };
}

function mapRoleMap(raw: unknown): Record<string, string[]> {
  if (!raw || typeof raw !== 'object') return {};
  const result: Record<string, string[]> = {};
  for (const [role, list] of Object.entries(raw as Record<string, unknown>)) {
    result[role] = mapStringArray(list);
  }
  return result;
}

function mapInvitation(raw: ApiRecord): TenantInvitation {
  return {
    id: String(raw.id ?? ''),
    tenant_id: String(value(raw, 'tenantId', 'tenant_id') ?? ''),
    driver_id: String(value(raw, 'driverId', 'driver_id') ?? '') || undefined,
    email: String(raw.email ?? ''),
    role: raw.role as TenantMembershipRole,
    status: raw.status as TenantInvitation['status'],
    invited_by: String(value(raw, 'invitedBy', 'invited_by') ?? ''),
    expires_at: String(value(raw, 'expiresAt', 'expires_at') ?? ''),
    created_at: String(value(raw, 'createdAt', 'created_at') ?? ''),
  };
}

function mapInviteResponse(raw: ApiRecord): InviteMemberResponse {
  return {
    ...mapInvitation(raw),
    invite_token: String(value(raw, 'inviteToken', 'invite_token') ?? ''),
  };
}

export interface InviteMemberPayload {
  email: string;
  role: TenantMembershipRole;
  driverId?: string;
}

export interface AcceptInvitationPayload {
  token: string;
  password?: string;
  fullName?: string;
}

export interface UpdateMemberPermissionsPayload {
  grantedPermissions: string[];
  revokedPermissions: string[];
}

export function getCurrentTenantId(): string {
  if (typeof window === 'undefined') return '';
  const explicit = localStorage.getItem('tenant_id');
  if (explicit) return explicit;
  try {
    const user = JSON.parse(localStorage.getItem('auth_user') || '{}');
    return user.tenantId || user.tenant_id || '';
  } catch {
    return '';
  }
}

export const memberRepository = {
  async getMembers(tenantId?: string): Promise<TenantMember[]> {
    const tid = tenantId || getCurrentTenantId();
    if (!tid) return [];
    const res = await api.get(`/tenants/${tid}/members`);
    return Array.isArray(res.data) ? res.data.map((item) => mapMember(item as ApiRecord)) : [];
  },

  async getInvitations(tenantId?: string): Promise<TenantInvitation[]> {
    const tid = tenantId || getCurrentTenantId();
    if (!tid) return [];
    const res = await api.get(`/tenants/${tid}/members/invitations`);
    return Array.isArray(res.data) ? res.data.map((item) => mapInvitation(item as ApiRecord)) : [];
  },

  async getPermissionsCatalog(tenantId?: string): Promise<PermissionCatalog> {
    const tid = tenantId || getCurrentTenantId();
    if (!tid) {
      return { permissions: [], system_role_defaults: {}, tenant_role_templates: {} };
    }
    const res = await api.get(`/tenants/${tid}/members/permissions-catalog`);
    const raw = (res.data ?? {}) as ApiRecord;
    const rawPermissions = Array.isArray(raw.permissions) ? raw.permissions : [];
    return {
      permissions: rawPermissions.map((item) => mapPermissionCatalogItem(item as ApiRecord)),
      system_role_defaults: mapRoleMap(value(raw, 'systemRoleDefaults', 'system_role_defaults')),
      tenant_role_templates: mapRoleMap(value(raw, 'tenantRoleTemplates', 'tenant_role_templates')),
    };
  },

  async inviteMember(payload: InviteMemberPayload, tenantId?: string): Promise<InviteMemberResponse> {
    const tid = tenantId || getCurrentTenantId();
    const res = await api.post(`/tenants/${tid}/members/invite`, {
      email: payload.email,
      role: payload.role,
      driver_id: payload.driverId,
    });
    return mapInviteResponse(res.data as ApiRecord);
  },

  async updateMemberStatus(membershipId: string, status: TenantMembershipStatus, tenantId?: string): Promise<TenantMember> {
    const tid = tenantId || getCurrentTenantId();
    const res = await api.patch(`/tenants/${tid}/members/${membershipId}/status`, { status });
    return mapMember(res.data as ApiRecord);
  },

  async updateMemberRole(membershipId: string, role: TenantMembershipRole, tenantId?: string): Promise<TenantMember> {
    const tid = tenantId || getCurrentTenantId();
    const res = await api.patch(`/tenants/${tid}/members/${membershipId}/role`, { role });
    return mapMember(res.data as ApiRecord);
  },

  async updateMemberPermissions(
    membershipId: string,
    payload: UpdateMemberPermissionsPayload,
    tenantId?: string
  ): Promise<TenantMember> {
    const tid = tenantId || getCurrentTenantId();
    const res = await api.put(`/tenants/${tid}/members/${membershipId}/permissions`, {
      granted_permissions: payload.grantedPermissions,
      revoked_permissions: payload.revokedPermissions,
    });
    return mapMember(res.data as ApiRecord);
  },

  async updateRolePermissionsTemplate(
    role: TenantMembershipRole,
    permissions: string[],
    tenantId?: string
  ): Promise<Record<string, string[]>> {
    const tid = tenantId || getCurrentTenantId();
    const res = await api.put(`/tenants/${tid}/members/role-permissions/${role}`, {
      permissions,
    });
    return mapRoleMap(res.data);
  },

  async revokeInvitation(invitationId: string, tenantId?: string): Promise<void> {
    const tid = tenantId || getCurrentTenantId();
    await api.post(`/tenants/${tid}/members/invitations/${invitationId}/revoke`);
  },

  async acceptInvitation(payload: AcceptInvitationPayload): Promise<TenantMember> {
    const res = await api.post('/auth/invitations/accept', {
      token: payload.token,
      password: payload.password,
      full_name: payload.fullName,
      fullName: payload.fullName,
    });
    return mapMember(res.data as ApiRecord);
  },
};
