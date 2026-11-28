import api from '@/lib/api';

const draftsPath = '/orders/operation-flow/drafts';
const activationPath = `${draftsPath}/planning/activation`;
const runsPath = `${draftsPath}/planning/runs`;

type DraftKind = 'import' | 'planning';
type DraftPayload = { revision: number; draft: Record<string, unknown> };

export interface PlanningActivation {
  schedule: { mode: 'daily' | 'interval'; time: string; hours: number };
  revision: number | null;
  next_run_at: string;
  pickup_location_id?: string | null;
}

interface PlanningRun {
  id: string;
  started_at: string;
  source: 'MANUAL' | 'SCHEDULED';
  outcome: 'PLANNED' | 'NO_ORDERS' | 'NO_DRIVERS' | 'PICKUP_UNAVAILABLE' | 'NO_ROUTES' | 'FAILED';
  scheduled_for?: string | null;
  pickup_location_id?: string | null;
  eligible_orders: number;
  plan_ids: string[];
}

export interface PlanningRunPage {
  content: PlanningRun[];
  has_more: boolean;
}

export const operationFlowRepository = {
  async getDraft(kind: DraftKind, signal: AbortSignal) {
    return (await api.get<DraftPayload | null>(`${draftsPath}/${kind}`, { signal })).data;
  },
  async saveDraft(kind: DraftKind, payload: Omit<DraftPayload, 'revision'> & { revision: number | null }) {
    return (await api.put<{ revision: number }>(`${draftsPath}/${kind}`, payload)).data;
  },
  async getPlanningActivation(signal: AbortSignal) {
    return (await api.get<PlanningActivation | null>(activationPath, { signal })).data;
  },
  async activatePlanning(revision: number | null) {
    return (await api.put<PlanningActivation>(activationPath, { revision })).data;
  },
  async deactivatePlanning() {
    await api.delete(activationPath);
  },
  async getPlanningRuns(page: number, signal: AbortSignal) {
    return (await api.get<PlanningRunPage>(runsPath, { params: { page }, signal })).data;
  },
  async runPlanning(pickupLocationId: string | null) {
    return (await api.post<PlanningRun>(runsPath, { pickup_location_id: pickupLocationId })).data;
  },
};
