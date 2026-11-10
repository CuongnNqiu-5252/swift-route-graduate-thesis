import { Order, OrderStatus } from '@/domain/entities/order.entity';
import { DispatchPlanRecord } from '@/domain/entities/routing.entity';
import api from '@/lib/api';

export interface OrdersResponse {
  content: Order[];
  total_elements: number;
  total_pages: number;
  page: number;
  size: number;
}

export interface OrderFilters {
  status?: OrderStatus;
  page?: number;
  size?: number;
}

export interface OperationsQueue {
  ready_for_dispatch: Order[];
  active: Order[];
  intervention: Order[];
}

export interface CreateOrderPayload {
  recipientName:string; recipientPhone:string; pickupLocationId:string; pickupLat:number; pickupLng:number; pickupAddress:string;
  deliveryLat:number; deliveryLng:number; deliveryAddress:string; timeWindowStart:string; timeWindowEnd:string; weightKg:number;
  packageCount:number; packageCategory:string; priority:string; codAmount:number;
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
  time_window_start: string;
  time_window_end: string;
  weight_kg: number;
  package_count: number;
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
    package_count: payload.packageCount,
    package_category: payload.packageCategory,
    priority: payload.priority,
    cod_amount: payload.codAmount,
  };
}

function fromApiOrderStatus(status: unknown): OrderStatus {
  return status === 'PAID' ? 'READY_FOR_DISPATCH' : status as OrderStatus;
}

function toApiOrderStatus(status: OrderStatus): string {
  return status === 'READY_FOR_DISPATCH' ? 'PAID' : status;
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
    status: fromApiOrderStatus(raw.status),
    notes: raw.notes || raw.note || undefined,
    time_window_start: raw.timeWindowStart || raw.time_window_start,
    time_window_end: raw.timeWindowEnd || raw.time_window_end,
    assigned_driver_id: raw.assignedDriverId || raw.assigned_driver_id,
    assigned_driver_name: raw.assignedDriverName || raw.assigned_driver_name,
    cod_amount: Number(raw.codAmount ?? raw.cod_amount ?? 0),
    payment_status: raw.paymentStatus || raw.payment_status || 'PAID',
    created_at: raw.createdAt || raw.created_at || '',
    updated_at: raw.updatedAt || raw.updated_at || '',
  };
}

export const orderRepository = {
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

  async getOrders(filters: OrderFilters = {}): Promise<Order[]> {
    const params = new URLSearchParams();
    if (filters.status) params.set('status', toApiOrderStatus(filters.status));
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

  async updateStatus(orderId: string, status: OrderStatus): Promise<Order> {
    const response = await api.patch<Order>(`/orders/${orderId}/status`, JSON.stringify(toApiOrderStatus(status)), {
      headers: { 'Content-Type': 'application/json' },
    });
    return mapOrder((response.data as { data?: unknown })?.data ?? response.data);
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

  async triggerDispatchPlanning(): Promise<void> {
    await api.post('/orders/dispatch/trigger');
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

  async reoptimizeDispatchPlan(planId: string): Promise<DispatchPlanRecord> {
    const response = await api.post<{ data?: DispatchPlanRecord }>(`/orders/dispatch/plans/${planId}/reoptimize`);
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
