import api from '@/lib/api';

export interface IncidentDto {
  id: string;
  order_id: string | null;
  order_code: string | null;
  dispatch_plan_id: string | null;
  driver_id: string | null;
  reported_by: string;
  incident_type: string;
  notes: string | null;
  evidence_urls: string[];
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';
  assigned_operator_id: string | null;
  resolution_type: string | null;
  resolved_by: string | null;
  resolution_notes: string | null;
  created_at: string;
  resolved_at: string | null;
}

export interface ReturningOrderDto {
  order_id: string;
  order_code: string | null;
  status: 'FAILED' | 'RETURNING' | 'RETURNED';
  assigned_driver_id: string | null;
  recipient_name: string | null;
  recipient_phone: string | null;
  delivery_address: string;
  pickup_location_name: string | null;
  pickup_address: string | null;
  cod_amount: number | null;
  latest_reason: string | null;
  updated_at: string;
}

export interface ResolveIncidentParams {
  resolution_type: 'RETRY_DELIVERY' | 'REASSIGN_DRIVER' | 'REQUEST_RETURN' | 'RESOLVED_NO_ACTION';
  notes?: string;
  assigned_operator_id?: string;
  reschedule_time?: string;
  replacement_driver_id?: string;
}

export interface FileUploadResult {
  url: string;
  public_id: string;
  format: string;
  bytes: number;
  original_filename: string;
  uploaded_at: string;
}

export interface CodHandoverLineDto {
  collection_id: string;
  order_id: string;
  order_code: string | null;
  amount: number;
  outcome: string;
}

export interface CodHandoverDto {
  id: string;
  driver_id: string;
  status: 'SUBMITTED' | 'SETTLED' | 'SETTLED_WITH_VARIANCE';
  ledger_total: number;
  declared_total: number;
  counted_total: number | null;
  discrepancy_amount: number | null;
  variance_resolution: 'DRIVER_RESPONSIBLE' | 'BUSINESS_ADJUSTMENT' | 'OTHER' | null;
  notes: string | null;
  submitted_at: string;
  verified_by: string | null;
  verified_at: string | null;
  lines: CodHandoverLineDto[];
}

export interface CodDashboardDto {
  outstanding_from_customers: number;
  in_driver_custody: number;
  awaiting_cashier_count: number;
  open_discrepancy_amount: number;
  pending_payment_count: number;
  custody_collection_count: number;
  pending_handover_count: number;
  open_dispute_count: number;
  handovers: CodHandoverDto[];
  disputes?: unknown[];
}

export const operationsRepository = {
  async getReviewQueue() {
    return (await api.get<unknown>('/operations/review')).data;
  },

  async getIncidents(params?: { status?: string; driver_id?: string; order_id?: string }): Promise<IncidentDto[]> {
    const res = await api.get<IncidentDto[]>('/operations/incidents', { params });
    return res.data;
  },

  async resolveIncident(id: string, params: ResolveIncidentParams): Promise<IncidentDto> {
    const res = await api.patch<IncidentDto>(`/operations/incidents/${id}/resolve`, params);
    return res.data;
  },

  async getReturningOrders(params?: { status?: string }): Promise<ReturningOrderDto[]> {
    const res = await api.get<ReturningOrderDto[]>('/operations/returning-orders', { params });
    return res.data;
  },

  async confirmOrderReturn(orderId: string, notes?: string): Promise<ReturningOrderDto> {
    const res = await api.patch<ReturningOrderDto>(`/operations/orders/${orderId}/confirm-return`, { notes });
    return res.data;
  },

  async requeueFailedOrder(orderId: string, notes?: string): Promise<ReturningOrderDto> {
    const res = await api.patch<ReturningOrderDto>(`/operations/orders/${orderId}/requeue-dispatch`, { notes });
    return res.data;
  },

  async uploadImage(file: File, folder = 'evidence'): Promise<FileUploadResult> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('folder', folder);
    const res = await api.post<FileUploadResult>('/uploads/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data;
  },

  async getCodDashboard(): Promise<CodDashboardDto> {
    const res = await api.get<CodDashboardDto>('/cod/dashboard');
    return res.data;
  },

  async settleCodReceipt(id: string, params: {
    counted_total: number;
    variance_resolution?: 'DRIVER_RESPONSIBLE' | 'BUSINESS_ADJUSTMENT' | 'OTHER';
    notes?: string;
  }): Promise<unknown> {
    const res = await api.post(`/cod/handovers/${id}/verify`, {
      operation_id: crypto.randomUUID(),
      ...params,
    });
    return res.data;
  },

  async verifyCodHandover(id: string, params: {
    operation_id: string;
    counted_total: number;
    variance_resolution?: 'DRIVER_RESPONSIBLE' | 'BUSINESS_ADJUSTMENT' | 'OTHER';
    notes?: string;
  }): Promise<CodHandoverDto> {
    const res = await api.post<CodHandoverDto>(`/cod/handovers/${id}/verify`, params);
    return res.data;
  },
};
