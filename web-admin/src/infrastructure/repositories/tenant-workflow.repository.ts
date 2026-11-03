import api from '@/lib/api';
export interface Membership { tenant_id: string; role: string; status: string }
export interface Verification { request_id: string; tenant_id: string; status: string; reason?: string; version: number; snapshot: string; submitted_at?: string; reviewed_at?: string; reviewer_id?: string; business_name?: string }
export interface TenantProfile { business_name: string; business_type: string; contact_phone: string; address: string; country_code: string; delivery_mode: string; delivery_settings: { preparation_enabled: boolean; preparation_minutes: number; delivery_verification_required: boolean; sensitive_handling: boolean } }
export interface Progress { tenant_id: string; status: string; completed_steps: string[]; incomplete_steps: string[]; next_action: string; profile: TenantProfile; tenant: { status: string; business_name: string }; verification_requests: Verification[]; readiness: { ready_for_operations: boolean; missing_requirements: string[]; shared_network_available: boolean } }
export async function switchTenant(id: string) {
  const response = await api.post<{ access_token: string }>(`/auth/tenants/${id}/switch`);
  localStorage.setItem('access_token', response.data.access_token);
  localStorage.setItem('tenant_id', id);
  // A full navigation discards mounted tables, hooks and pending tenant-specific state.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination -- discard every mounted tenant cache on switching
  window.location.assign('/onboarding');
}
export const tenantWorkflow = {
  memberships: async () => (await api.get<Membership[]>('/auth/tenants')).data,
  progress: async (id: string) => (await api.get<Progress>(`/tenants/${id}/onboarding`)).data,
  save: async (id: string, profile: Partial<TenantProfile>) => (await api.put<Progress>(`/tenants/${id}/onboarding`, profile)).data,
  submit: async (id: string, key: string) => (await api.post(`/tenants/${id}/verification-requests`, null, { headers: { 'Idempotency-Key': key } })).data,
  reviews: async () => (await api.get<Verification[]>('/tenants/verification-requests')).data,
  review: async (request: Verification, status: string, reason: string) => (await api.post(`/tenants/verification-requests/${request.request_id}/review`, { status, reason, version: request.version })).data,
};
