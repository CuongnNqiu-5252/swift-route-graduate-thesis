import api from '@/lib/api';

export interface IncidentDto {
  id: string;
  order_id: string | null;
  order_code: string | null;
  dispatch_plan_id: string | null;
  driver_id: string | null;
  reported_by: string;
  incident_type: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  notes: string | null;
  evidence_urls: string[];
  can_continue_delivery: boolean;
  needs_immediate_assistance: boolean;
  status: 'OPEN' | 'IN_PROGRESS' | 'ESCALATED' | 'RESOLVED' | 'CANCELLED';
  assigned_operator_id: string | null;
  resolution_type: string | null;
  resolved_by: string | null;
  resolution_notes: string | null;
  created_at: string;
  resolved_at: string | null;
}

export interface ReturnRequestDto {
  id: string;
  order_id: string;
  order_code: string | null;
  incident_id: string | null;
  request_type: 'RETURN_TO_ORIGIN' | 'RETURN_TO_DEPOT' | 'REDELIVERY';
  reason: string;
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'ASSIGNED' | 'IN_EXECUTION' | 'ARRIVED' | 'AWAITING_RECEIPT' | 'COMPLETED' | 'DISCREPANCY' | 'CANCELLED';
  return_location_id: string | null;
  return_location_name: string | null;
  return_address: string | null;
  current_holder_id: string | null;
  package_count: number;
  requested_by: string;
  approved_by: string | null;
  return_task_id: string | null;
  received_by: string | null;
  received_at: string | null;
  completion_notes: string | null;
  discrepancy_type: string | null;
  actual_quantity: number | null;
  package_condition: string | null;
  discrepancy_notes: string | null;
  discrepancy_evidence_url: string | null;
  created_at: string;
  updated_at: string;
}

export interface ResolveIncidentParams {
  resolution_type: 'CONTINUE_DELIVERY' | 'RETRY_DELIVERY' | 'REASSIGN_DRIVER' | 'REQUEST_RETURN' | 'ESCALATED' | 'RESOLVED_NO_ACTION';
  notes?: string;
  assigned_operator_id?: string;
  reschedule_time?: string;
  replacement_driver_id?: string;
}

export interface CreateReturnParams {
  order_id: string;
  incident_id?: string;
  request_type?: string;
  reason: string;
  return_location_id?: string;
  package_count?: number;
  notes?: string;
  evidence_url?: string;
}

export interface ReportDiscrepancyParams {
  discrepancy_type: string;
  actual_quantity?: number;
  discrepancy_notes?: string;
  evidence_url?: string;
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
  status: 'SUBMITTED' | 'VERIFIED' | 'DISCREPANCY' | 'RESOLVED';
  ledger_total: number;
  declared_total: number;
  counted_total: number | null;
  discrepancy_amount: number | null;
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
  async reportIncident(params: {
    order_id?: string;
    dispatch_plan_id?: string;
    incident_type: string;
    severity: string;
    notes: string;
    can_continue_delivery: boolean;
    needs_immediate_assistance: boolean;
  }): Promise<IncidentDto> {
    const res = await api.post<IncidentDto>('/operations/incidents', params);
    return res.data;
  },

  async getIncidents(params?: { status?: string; severity?: string; driver_id?: string; order_id?: string }): Promise<IncidentDto[]> {
    const res = await api.get<IncidentDto[]>('/operations/incidents', { params });
    return res.data;
  },

  async getIncidentById(id: string): Promise<IncidentDto> {
    const res = await api.get<IncidentDto>(`/operations/incidents/${id}`);
    return res.data;
  },

  async resolveIncident(id: string, params: ResolveIncidentParams): Promise<IncidentDto> {
    const res = await api.patch<IncidentDto>(`/operations/incidents/${id}/resolve`, params);
    return res.data;
  },

  async getReturns(params?: { status?: string; driver_id?: string }): Promise<ReturnRequestDto[]> {
    const res = await api.get<ReturnRequestDto[]>('/operations/returns', { params });
    return res.data;
  },

  async getReturnById(id: string): Promise<ReturnRequestDto> {
    const res = await api.get<ReturnRequestDto>(`/operations/returns/${id}`);
    return res.data;
  },

  async createReturn(params: CreateReturnParams): Promise<ReturnRequestDto> {
    const res = await api.post<ReturnRequestDto>('/operations/returns', params);
    return res.data;
  },

  async approveReturn(id: string, params?: { return_location_id?: string; assigned_driver_id?: string; notes?: string }): Promise<ReturnRequestDto> {
    const res = await api.patch<ReturnRequestDto>(`/operations/returns/${id}/approve`, params ?? {});
    return res.data;
  },

  async confirmReturnReceipt(id: string, notes?: string): Promise<ReturnRequestDto> {
    const res = await api.patch<ReturnRequestDto>(`/operations/returns/${id}/confirm-receipt`, { notes });
    return res.data;
  },

  async reportDiscrepancy(id: string, params: ReportDiscrepancyParams): Promise<ReturnRequestDto> {
    const res = await api.patch<ReturnRequestDto>(`/operations/returns/${id}/discrepancy`, params);
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

  async verifyCod(id: string, notes?: string): Promise<unknown> {
    const res = await api.patch(`/operations/cod-collections/${id}/verify`, { notes });
    return res.data;
  },

  async verifyCodHandover(id: string, params: {
    operation_id: string;
    counted_total: number;
    notes?: string;
  }): Promise<CodHandoverDto> {
    const res = await api.post<CodHandoverDto>(`/cod/handovers/${id}/verify`, params);
    return res.data;
  },
};
