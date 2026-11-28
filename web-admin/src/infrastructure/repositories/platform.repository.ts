import api, { streamSse } from '@/lib/api';

export interface PlatformAuditEntry {
  id: string; tenant_id: string; tenant_name: string; aggregate_type: string; aggregate_id: string;
  action: string; actor_id: string | null; created_at: string; category: string; severity: string;
  details: Record<string, string | number | boolean>;
}
export interface PlatformAuditPage { entries: PlatformAuditEntry[]; has_more: boolean }
export interface PlatformOverview {
  tenant_statuses: Array<{ status: string; total: number }>;
  pending_reviews: number;
  review_priority: Array<{ request_id: string; tenant_id: string; business_name: string; status: string; submitted_at: string | null; waiting_hours: number }>;
  provisioning_retries: number;
  provisioning_attention: Array<{ tenant_id: string; business_name: string; attempts: number; last_error_code: string | null; updated_at: string }>;
  activity_days: Array<{ day: string; total: number }>;
}
export interface PlatformSnapshot { as_of: string; audit: PlatformAuditPage; overview: PlatformOverview }
export interface PlatformAuditFilter { query: string; category: string; severity: string; days: number }

export const platformRepository = {
  stream(signal: AbortSignal, receive: (name: string, value: unknown) => void) {
    return streamSse('/notifications/platform/stream', { signal, headers: { Accept: 'text/event-stream' },
      onEvent(name, data) {
        if (name === 'ping') receive('heartbeat', null);
        else if (name === 'connect' || name === 'platform-audit' || name === 'platform-resync') receive(name, JSON.parse(data));
      },
    });
  },
  async snapshot(signal: AbortSignal) {
    return (await api.get<PlatformSnapshot>('/tenants/platform/snapshot', { signal })).data;
  },
  async history(filter: PlatformAuditFilter, signal: AbortSignal, before?: PlatformAuditEntry) {
    const { data } = await api.get<PlatformAuditPage>('/tenants/platform/audit', { signal,
      params: { ...filter, ...(before ? { beforeTime: before.created_at, beforeId: before.id } : {}) },
    });
    return data;
  },
};
