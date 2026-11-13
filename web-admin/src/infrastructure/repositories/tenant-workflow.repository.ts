import api from '@/lib/api';
export interface Membership { tenant_id: string; role: string; status: string }
export interface Verification { request_id: string; tenant_id: string; status: string; reason?: string; version: number; snapshot?: string; submitted_at?: string; reviewed_at?: string; reviewer_id?: string; business_name?: string }
export interface ProvisioningState { tenant_id: string; status: 'PROVISIONING' | 'READY' | 'RETRY_REQUIRED'; attempts: number; updated_at: string; next_action: string }
export interface CreateOrganizationResult { tenant_id: string; workspace_status: ProvisioningState['status']; next_action: string }
export interface PageResult<T> { content: T[]; page_number: number; page_size: number; total_elements: number; total_pages: number; is_first: boolean; is_last: boolean }
export interface VerificationDetail {
  request: Verification;
  tenant: {
    tenant_id: string;
    business_name: string;
    status: string;
    approval_reason?: string | null;
    address?: string | null;
  };
  history: Verification[];
  timeline: Array<{ action: string; actor_id: string; created_at: string; details: string }>;
}
export interface TenantProfile { business_name: string; business_type: string; contact_phone: string; address: string; country_code: string; delivery_mode: string; delivery_settings: { preparation_enabled: boolean; preparation_minutes: number; delivery_verification_required: boolean; sensitive_handling: boolean } }
export interface Progress { tenant_id: string; status: string; completed_steps: string[]; incomplete_steps: string[]; next_action: string; profile: TenantProfile; tenant: { status: string; business_name: string }; verification_requests: Verification[]; readiness: { ready_for_operations: boolean; missing_requirements: string[]; shared_network_available: boolean } }
export async function switchTenant(id: string) {
  const response = await api.post<{ access_token: string }>(`/auth/tenants/${id}/switch`);
  localStorage.setItem('access_token', response.data.access_token);
  localStorage.setItem('tenant_id', id);

  try {
    const progress = await tenantWorkflow.progress(id);
    if (progress?.readiness?.ready_for_operations) {
      const currentPath = window.location.pathname;
      if (currentPath === '/onboarding' || currentPath.startsWith('/organizations')) {
        // eslint-disable-next-line @next/next/no-location-assign-relative-destination
        window.location.assign('/');
      } else {
        window.location.reload();
      }
      return;
    }
  } catch {
    // Fallback if progress check is unavailable
  }

  // A full navigation discards mounted tables, hooks and pending tenant-specific state.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.assign('/onboarding');
}
export const tenantWorkflow = {
  memberships: async () => (await api.get<Membership[]>('/auth/tenants')).data,
  create: async (payload: Record<string, FormDataEntryValue>, key: string) =>
    (await api.post<CreateOrganizationResult>('/tenants', payload, { headers: { 'Idempotency-Key': key } })).data,
  provisioning: async (id: string) => (await api.get<ProvisioningState>(`/tenants/${id}/provisioning`)).data,
  retryProvisioning: async (id: string) => (await api.post<ProvisioningState>(`/tenants/${id}/provisioning/retry`)).data,
  progress: async (id: string) => (await api.get<Progress>(`/tenants/${id}/onboarding`)).data,
  save: async (id: string, profile: Partial<TenantProfile>) => (await api.put<Progress>(`/tenants/${id}/onboarding`, profile)).data,
  submit: async (id: string, key: string) => (await api.post(`/tenants/${id}/verification-requests`, null, { headers: { 'Idempotency-Key': key } })).data,
  reviews: async () => (await api.get<Verification[]>('/tenants/verification-requests')).data,
  reviewQueue: async (status: string, search: string, page: number, size = 20) =>
    (await api.get<PageResult<Verification>>('/tenants/verification-requests/page', { params: { status: status || undefined, search: search || undefined, page, size } })).data,
  reviewDetail: async (id: string) => (await api.get<VerificationDetail>(`/tenants/verification-requests/${id}`)).data,
  review: async (request: Verification, status: string, reason: string) => (await api.post(`/tenants/verification-requests/${request.request_id}/review`, { status, reason, version: request.version })).data,
  updateStatus: async (tenantId: string, status: 'SUSPENDED' | 'APPROVED', reason: string) =>
    (await api.patch(`/tenants/${tenantId}/status`, { status, reason })).data,
};
