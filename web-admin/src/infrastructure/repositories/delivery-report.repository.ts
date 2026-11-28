import api from '@/lib/api';

export interface DeliveryReportFilters {
  from: string;
  to: string;
  warehouse?: string;
}

export interface DeliveryReportDay {
  date: string;
  delivered: number;
  with_deadline: number;
  on_time: number;
  first_attempts: number;
  first_successes: number;
  failures: number;
}

export interface DeliveryReport {
  days: DeliveryReportDay[];
  failures: { reason: string; count: number }[];
  overdue: number;
  warehouses: { id: string; name: string }[];
}

export type DeliveryReportDetailFilter = { kind: 'LATE' } | { kind: 'FAILURE'; reason: string };

interface DeliveryReportDetails {
  content: {
    order_id: string;
    order_code: string;
    completed_at: string;
    deadline: string | null;
    attempt_number: number;
  }[];
  total_pages: number;
}

export const deliveryReportRepository = {
  async overview(params: DeliveryReportFilters, signal: AbortSignal) {
    const { data } = await api.get<DeliveryReport>('/orders/reports/delivery', { params, signal });
    return data;
  },

  async details(filters: DeliveryReportFilters, detail: DeliveryReportDetailFilter, page: number, signal: AbortSignal) {
    const { data } = await api.get<DeliveryReportDetails>('/orders/reports/delivery/details', {
      params: { ...filters, ...detail, page },
      signal,
    });
    return data;
  },
};
