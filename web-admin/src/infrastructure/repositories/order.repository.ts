import { Order, OrderStatus } from '@/domain/entities/order.entity';
import { DispatchPlanRecord } from '@/domain/entities/routing.entity';
import api, { streamSse, type StreamOptions } from '@/lib/api';

export interface OrderFilters {
  status?: OrderStatus;
  page?: number;
  size?: number;
}

interface DispatchPlanningResult {
  outcome: 'PLANNED' | 'NO_ORDERS' | 'NO_ROUTES' | 'IN_PROGRESS';
  plan_ids: string[];
}

export interface OrderListPage {
  orders: {
    content: Order[];
    total_elements: number;
    total_pages: number;
  };
  status_counts: Record<string, number>;
}

export type OrderSortField = 'created_at' | 'updated_at' | 'time_window_start';
export type OrderSortDirection = 'asc' | 'desc';

export interface OrderPageFilters {
  page: number;
  size: number;
  status?: OrderStatus;
  pickupLocationId?: string;
  query?: string;
  driverIds?: string[];
  sortBy?: OrderSortField;
  sortDir?: OrderSortDirection;
}

export interface OperationsQueue {
  ready_for_dispatch: Order[];
  active: Order[];
  intervention: Order[];
}

export interface CreateOrderPayload {
  recipientName:string; recipientPhone:string; pickupLocationId:string; pickupLat:number; pickupLng:number; pickupAddress:string;
  deliveryLat:number; deliveryLng:number; deliveryAddress:string; timeWindowStart:string | null; timeWindowEnd:string | null; weightKg:number;
  packageCategory:string; priority:string; codAmount:number;
}

interface CreateOrderRequest {
  recipient_name: string;
  recipient_phone: string;
  pickup_location_id: string;
  pickup_lat: number;
  pickup_lng: number;
  pickup_address: string;
  delivery_lat: number;
  delivery_lng: number;
  delivery_address: string;
  time_window_start: string | null;
  time_window_end: string | null;
  weight_kg: number;
  package_category: string;
  priority: string;
  cod_amount: number;
}

export function toCreateOrderRequest(payload: CreateOrderPayload): CreateOrderRequest {
  return {
    recipient_name: payload.recipientName.trim(),
    recipient_phone: payload.recipientPhone.trim(),
    pickup_location_id: payload.pickupLocationId,
    pickup_lat: payload.pickupLat,
    pickup_lng: payload.pickupLng,
    pickup_address: payload.pickupAddress.trim(),
    delivery_lat: payload.deliveryLat,
    delivery_lng: payload.deliveryLng,
    delivery_address: payload.deliveryAddress.trim(),
    time_window_start: payload.timeWindowStart,
    time_window_end: payload.timeWindowEnd,
    weight_kg: payload.weightKg,
    package_category: payload.packageCategory,
    priority: payload.priority,
    cod_amount: payload.codAmount,
  };
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function mapOrder(raw: any): Order {
  const rawId = raw.orderId || raw.order_id || raw.id || '';
  return {
    id: rawId,
    order_code: raw.externalOrderCode || raw.order_code || rawId,
    tenant_id: raw.tenantId || raw.tenant_id || '',
    tenant_name: raw.tenantName || raw.tenant_name,
    customer_id: raw.customerId || raw.customer_id || '',
    customer_name: raw.recipientName || raw.customer_name,
    sender_name: raw.senderName ?? raw.sender_name,
    sender_phone: raw.senderPhone ?? raw.sender_phone,
    recipient_phone: raw.recipientPhone ?? raw.recipient_phone,
    pickup_location_id: raw.pickupLocationId || raw.pickup_location_id,
    pickup_location_name: raw.pickupLocationName || raw.pickup_location_name,
    pickup_address: raw.pickupAddress || raw.pickup_address || '',
    delivery_address: raw.deliveryAddress || raw.delivery_address || '',
    pickup_lat: raw.pickupLat ?? raw.pickup_lat ?? 0,
    pickup_lng: raw.pickupLng ?? raw.pickup_lng ?? 0,
    delivery_lat: raw.deliveryLat ?? raw.delivery_lat ?? 0,
    delivery_lng: raw.deliveryLng ?? raw.delivery_lng ?? 0,
    package_category: raw.packageCategory || raw.package_category || 'PARCEL',
    weight_kg: raw.weightKg ?? raw.weight_kg ?? 0,
    status: raw.status as OrderStatus,
    notes: raw.notes || raw.note || undefined,
    time_window_start: raw.timeWindowStart || raw.time_window_start,
    time_window_end: raw.timeWindowEnd || raw.time_window_end,
    assigned_driver_id: raw.assignedDriverId || raw.assigned_driver_id,
    assigned_driver_name: raw.assignedDriverName || raw.assigned_driver_name,
    cod_amount: Number(raw.codAmount ?? raw.cod_amount ?? 0),
    cod_status: raw.codStatus ?? raw.cod_status,
    cod_collected_at: raw.codCollectedAt ?? raw.cod_collected_at,
    declared_value: raw.declaredValue ?? raw.declared_value,
    delivery_fee: raw.deliveryFee ?? raw.delivery_fee,
    total_amount: raw.totalAmount ?? raw.total_amount,
    payer: raw.payer,
    created_at: raw.createdAt || raw.created_at || '',
    updated_at: raw.updatedAt || raw.updated_at || '',
  };
}

export const orderRepository = {
  streamOrderTracking(orderId: string, options: StreamOptions) {
    return streamSse(`/orders/${orderId}/tracking/stream`, options);
  },

  streamDispatchTracking(target: { driverId: string } | { planId: string }, options: StreamOptions) {
    const path = 'driverId' in target ? `drivers/${target.driverId}` : `plans/${target.planId}`;
    return streamSse(`/orders/dispatch/${path}/tracking/stream`, options);
  },

  streamFleetTracking(options: StreamOptions) {
    return streamSse('/orders/dispatch/drivers/tracking/stream', options);
  },

  async startDriverSimulation(
    driverId: string,
    waypoints: { lat: number; lng: number }[],
    pauseAtIndex?: number | null,
  ): Promise<void> {
    await api.post(`/orders/dispatch/drivers/${driverId}/tracking/simulate`, {
      waypoints, interval_ms: 1000, pause_at_index: pauseAtIndex,
    });
  },

  async stopDriverSimulation(driverId: string): Promise<void> {
    await api.post(`/orders/dispatch/drivers/${driverId}/tracking/simulate/stop`);
  },

  async resumeDriverSimulation(driverId: string): Promise<void> {
    await api.post(`/orders/dispatch/drivers/${driverId}/tracking/simulate/resume`);
  },

  async getOrder(orderId: string, signal: AbortSignal): Promise<Order> {
    const { data } = await api.get<unknown>(`/orders/${encodeURIComponent(orderId)}`, { signal });
    return mapOrder(data);
  },

  async createOrder(payload: CreateOrderPayload): Promise<Order> {
    return mapOrder((await api.post<unknown>('/orders', toCreateOrderRequest(payload))).data);
  },
  async getOperationsQueue(): Promise<OperationsQueue> {
    const response = await api.get<unknown>('/orders/operations/queue');
    const responseBody = response.data as { data?: unknown };
    const data = responseBody.data ?? responseBody;
    if (!data || typeof data !== 'object') {
      throw new Error('Không thể tải hàng đợi vận hành');
    }
    const queue = data as Record<string, unknown>;
    const readyForDispatch = queue.readyForDispatch ?? queue.ready_for_dispatch;
    const active = queue.active;
    const intervention = queue.intervention;
    if (!Array.isArray(readyForDispatch) || !Array.isArray(active) || !Array.isArray(intervention)) {
      throw new Error('Dữ liệu hàng đợi vận hành không hợp lệ');
    }
    return {
      ready_for_dispatch: readyForDispatch.map(mapOrder),
      active: active.map(mapOrder),
      intervention: intervention.map(mapOrder),
    };
  },

  async getOrderPage(filters: OrderPageFilters, signal?: AbortSignal): Promise<OrderListPage> {
    const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) });
    if (filters.status) params.set('status', filters.status);
    if (filters.pickupLocationId) params.set('pickupLocationId', filters.pickupLocationId);
    if (filters.query) params.set('query', filters.query);
    if (filters.driverIds?.length) params.set('driverIds', filters.driverIds.join(','));
    if (filters.sortBy) params.set('sortBy', filters.sortBy);
    if (filters.sortDir) params.set('sortDir', filters.sortDir);
    const { data } = await api.get<OrderListPage>(`/orders/page?${params}`, { signal });
    return { ...data, orders: { ...data.orders, content: data.orders.content.map(mapOrder) } };
  },

  async getOrders(filters: OrderFilters = {}): Promise<Order[]> {
    const params = new URLSearchParams();
    if (filters.status) params.set('status', filters.status);
    if (filters.page !== undefined) params.set('page', String(filters.page));
    if (filters.size !== undefined) params.set('size', String(filters.size));

    try {
      const response = await api.get<unknown>(`/orders?${params.toString()}`);
      const responseBody = response.data as { data?: unknown };
      const data = responseBody?.data ?? responseBody;
      if (Array.isArray(data)) {
        return data.map(mapOrder);
      }
      const paged = data as { content?: unknown };
      if (Array.isArray(paged?.content)) {
        return paged.content.map(mapOrder);
      }
      throw new Error('Không thể tải dữ liệu vận hành');
    } catch (error) {
      throw error instanceof Error ? error : new Error('Không thể tải dữ liệu vận hành');
    }
  },

  async cancelOrder(orderId: string, reason?: string): Promise<Order> {
    const response = await api.patch<Order>(`/orders/${orderId}/cancel`, {
      reason: reason || 'Điều phối viên huỷ đơn từ Web Admin',
    });
    return mapOrder((response.data as { data?: unknown })?.data ?? response.data);
  },

  async assignBatch(payload: {
    driver_id?: string;
    order_ids?: string[];
    assignments?: Array<{ order_id: string; driver_id: string; plan_id?: string | null }>;
  }): Promise<void> {
    const assignments =
      payload.assignments ||
      (payload.driver_id && payload.order_ids
        ? payload.order_ids.map((orderId) => ({
            order_id: orderId,
            driver_id: payload.driver_id!,
            plan_id: null,
          }))
        : []);

    const body = {
      assignments: assignments.map((assignment) => ({
        order_id: assignment.order_id,
        driver_id: assignment.driver_id,
        plan_id: assignment.plan_id ?? null,
      })),
      driver_id: payload.driver_id || (assignments[0]?.driver_id ?? null),
      order_ids: payload.order_ids || assignments.map((assignment) => assignment.order_id),
    };
    await api.post('/orders/assign-batch', body);
  },

  async getDispatchPlans(): Promise<DispatchPlanRecord[]> {
    const response = await api.get<DispatchPlanRecord[] | { data?: DispatchPlanRecord[] }>('/orders/dispatch/plans');
    if (Array.isArray(response.data)) {
      return response.data;
    }
    return response.data?.data ?? [];
  },

  async triggerDispatchPlanning(): Promise<DispatchPlanningResult> {
    const response = await api.post<DispatchPlanningResult>('/orders/dispatch/trigger');
    return response.data;
  },

  async reorderDraftStops(planId: string, version: number, orderedStopIds: string[]): Promise<DispatchPlanRecord> {
    const response = await api.post<DispatchPlanRecord>(`/orders/dispatch/plans/${planId}/stop-order`, {
      expected_version: version,
      ordered_stop_ids: orderedStopIds,
    });
    return response.data;
  },

  async moveDraftStop(input: {
    sourcePlan: DispatchPlanRecord;
    targetPlan: DispatchPlanRecord;
    orderId: string;
    targetIndex: number;
  }): Promise<{ source_plan: DispatchPlanRecord | null; target_plan: DispatchPlanRecord }> {
    const response = await api.post<{ source_plan: DispatchPlanRecord | null; target_plan: DispatchPlanRecord }>(
      '/orders/dispatch/plans/move-stop',
      {
        source_plan_id: input.sourcePlan.plan_id,
        target_plan_id: input.targetPlan.plan_id,
        order_id: input.orderId,
        target_index: input.targetIndex,
        expected_source_version: input.sourcePlan.version,
        expected_target_version: input.targetPlan.version,
      },
    );
    return response.data;
  },

  async approveDispatchPlan(planId: string, driverId?: string | null, notes?: string): Promise<DispatchPlanRecord> {
    const response = await api.post<{ data?: DispatchPlanRecord }>(`/orders/dispatch/plans/${planId}/approve`, {
      driver_id: driverId ?? null,
      notes: notes || 'Phê duyệt kế hoạch từ Web Admin',
    });
    return (response.data?.data ?? response.data) as DispatchPlanRecord;
  },

  async dispatchApprovedPlan(planId: string): Promise<DispatchPlanRecord> {
    const response = await api.post<{ data?: DispatchPlanRecord }>(`/orders/dispatch/plans/${planId}/dispatch`);
    return (response.data?.data ?? response.data) as DispatchPlanRecord;
  },

  async cancelDispatchPlan(planId: string, reason: string): Promise<DispatchPlanRecord> {
    const response = await api.post<{ data?: DispatchPlanRecord }>(`/orders/dispatch/plans/${planId}/cancel`, {
      reason: reason || 'Huỷ kế hoạch điều phối từ Web Admin',
    });
    return (response.data?.data ?? response.data) as DispatchPlanRecord;
  },

  async insertOrderIntoPlan(planId: string, orderId: string): Promise<DispatchPlanRecord> {
    const response = await api.post<{ data?: DispatchPlanRecord }>(
      `/orders/dispatch/plans/${planId}/insert/${orderId}`,
    );
    return (response.data?.data ?? response.data) as DispatchPlanRecord;
  },

  async startOrderSimulation(orderId: string, waypoints: Array<{ lat: number; lng: number }>, intervalMs = 1000): Promise<void> {
    await api.post(`/orders/${orderId}/tracking/simulate`, {
      waypoints,
      interval_ms: intervalMs,
    });
  },

  async stopOrderSimulation(orderId: string): Promise<void> {
    await api.post(`/orders/${orderId}/tracking/simulate/stop`);
  },

  async startPlanSimulation(planId: string, waypoints: Array<{ lat: number; lng: number }>, intervalMs = 1000): Promise<void> {
    await api.post(`/orders/dispatch/plans/${planId}/tracking/simulate`, {
      waypoints,
      interval_ms: intervalMs,
    });
  },

  async stopPlanSimulation(planId: string): Promise<void> {
    await api.post(`/orders/dispatch/plans/${planId}/tracking/simulate/stop`);
  },
};
