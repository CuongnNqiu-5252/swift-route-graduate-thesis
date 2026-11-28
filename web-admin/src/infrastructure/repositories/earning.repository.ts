import api from '@/lib/api';
import { DriverEarning, DriverSettlement, EarningPolicy } from '@/domain/entities/earning.entity';

export const earningRepository = {
  async listEarnings(): Promise<DriverEarning[]> {
    return (await api.get<DriverEarning[]>('/earnings')).data;
  },
  async listSettlements(): Promise<DriverSettlement[]> {
    return (await api.get<DriverSettlement[]>('/settlements')).data;
  },
  async getPolicy(): Promise<EarningPolicy> {
    return (await api.get<EarningPolicy>('/earnings/policy')).data;
  },
  async updatePolicy(policy: Omit<EarningPolicy, 'tenant_id' | 'currency'>): Promise<EarningPolicy> {
    return (await api.put<EarningPolicy>('/earnings/policy', policy)).data;
  },
  async createSettlement(driverId: string, periodStart: string, periodEnd: string): Promise<DriverSettlement> {
    return (await api.post<DriverSettlement>('/settlements', {
      driver_id: driverId,
      period_start: periodStart,
      period_end: periodEnd,
    })).data;
  },
  async approve(settlementId: string): Promise<DriverSettlement> {
    return (await api.post<DriverSettlement>(`/settlements/${settlementId}/approve`)).data;
  },
  async markPaid(settlementId: string, paymentReference: string): Promise<DriverSettlement> {
    return (await api.post<DriverSettlement>(`/settlements/${settlementId}/mark-paid`, {
      payment_reference: paymentReference,
    })).data;
  },
};
