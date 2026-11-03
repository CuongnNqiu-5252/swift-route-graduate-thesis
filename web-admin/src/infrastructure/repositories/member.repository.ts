import {
  TenantMember,
  TenantInvitation,
  InviteMemberResponse,
  TenantMembershipRole,
  TenantMembershipStatus,
} from '@/domain/entities/member.entity';
import api from '@/lib/api';

type ApiRecord = Record<string, unknown>;

const value = (raw: ApiRecord, camel: string, snake: string) => raw[camel] ?? raw[snake];

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
  };
}

function mapInvitation(raw: ApiRecord): TenantInvitation {
  return {
    id: String(raw.id ?? ''),
    tenant_id: String(value(raw, 'tenantId', 'tenant_id') ?? ''),
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
}

export interface AcceptInvitationPayload {
  token: string;
  password?: string;
  fullName?: string;
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

  async inviteMember(payload: InviteMemberPayload, tenantId?: string): Promise<InviteMemberResponse> {
    const tid = tenantId || getCurrentTenantId();
    const res = await api.post(`/tenants/${tid}/members/invite`, payload);
    return mapInviteResponse(res.data as ApiRecord);
  },

  async updateMemberStatus(membershipId: string, status: TenantMembershipStatus, tenantId?: string): Promise<TenantMember> {
    const tid = tenantId || getCurrentTenantId();
    const res = await api.patch(`/tenants/${tid}/members/${membershipId}/status`, { status });
    return mapMember(res.data as ApiRecord);
  },

  async acceptInvitation(payload: AcceptInvitationPayload): Promise<TenantMember> {
    const res = await api.post('/auth/invitations/accept', payload);
    return mapMember(res.data as ApiRecord);
  },
};
