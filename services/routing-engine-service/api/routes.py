# api/routes.py
"""
FastAPI router exposing Route Optimisation, Shift Planning,
Pending Order Queries, and Dispatch endpoints.
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import List, Optional

from fastapi import APIRouter, HTTPException, Query

from api.schemas import (
    BatchOptimizationRequest,
    BatchOptimizationResponse,
    DispatchPlanResponse,
    PendingOrder,
    RoutingPlan,
    ShiftOptimizationRequest,
)
from service.optimizer_service import solve_batch
from service.order_store import order_store
from service.plan_store import plan_store
from service.shift_optimizer import dispatch_plan as run_dispatch, optimize_shift

router = APIRouter(tags=["Optimization & Planning"])


@router.get("/health", summary="Health check")
def health():
    """Returns service status and supported algorithm list."""
    return {
        "status": "ok",
        "service": "SwiftRoute Routing Engine",
        "algorithms": ["held_karp", "nearest_neighbor", "genetic", "auto"],
    }


@router.post(
    "/optimize/batch",
    response_model=BatchOptimizationResponse,
    summary="Batch route optimisation — depot + N delivery stops",
)
def optimize_batch(request: BatchOptimizationRequest) -> BatchOptimizationResponse:
    """
    Accepts a depot location and a list of delivery stops.
    Returns the optimised visitation order with cumulative distances and ETA.

    Algorithm selection:
    - auto: uses Held-Karp for n <= 14 stops, Nearest Neighbor for larger sets.
    - held_karp: exact O(n² · 2^n) DP solver — best for n <= 14.
    - nearest_neighbor: O(n²) greedy heuristic — fast approximation.
    - genetic: evolutionary algorithm — good approximation for large n.
    """
    return solve_batch(request)


# ─────────────────────────────────────────────────────────────────────────────
# Shift Planning & Order Endpoints
# ─────────────────────────────────────────────────────────────────────────────


@router.get(
    "/orders/pending",
    response_model=List[PendingOrder],
    summary="List received pending orders waiting for batch routing",
)
def get_pending_orders(
    tenant_id: Optional[str] = Query(None, description="Filter by tenant UUID"),
    shift: Optional[str] = Query(None, description="Filter by shift (MORNING, AFTERNOON, EVENING, ALL)"),
    status: Optional[str] = Query(None, description="Filter by status (e.g. PENDING_PAYMENT, PAID)"),
) -> List[PendingOrder]:
    """Retrieves deduplicated pending orders received via RabbitMQ."""
    return order_store.get_orders(tenant_id=tenant_id, shift=shift, status=status)


@router.post(
    "/plans/optimize-shift",
    response_model=RoutingPlan,
    summary="Generate optimized route plan for a specific shift",
)
def optimize_shift_endpoint(request: ShiftOptimizationRequest) -> RoutingPlan:
    """
    Groups pending orders for the specified tenant and shift, partitions them
    across available drivers, runs the TSP solver, and persists the generated
    routing plan in DRAFT status.
    """
    try:
        return optimize_shift(request)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get(
    "/plans",
    response_model=List[RoutingPlan],
    summary="List saved routing plans",
)
def list_plans(
    tenant_id: Optional[str] = Query(None, description="Filter by tenant UUID"),
    shift: Optional[str] = Query(None, description="Filter by shift"),
    status: Optional[str] = Query(None, description="Filter by status (DRAFT, APPROVED, DISPATCHED)"),
) -> List[RoutingPlan]:
    """Returns stored routing plans from SQLite persistence."""
    return plan_store.list_plans(tenant_id=tenant_id, shift=shift, status=status)


@router.get(
    "/plans/{plan_id}",
    response_model=RoutingPlan,
    summary="Get single routing plan details by ID",
)
def get_plan(plan_id: str) -> RoutingPlan:
    """Retrieves a saved routing plan by its UUID."""
    plan = plan_store.get_plan(plan_id)
    if not plan:
        raise HTTPException(status_code=404, detail=f"Routing plan {plan_id} not found.")
    return plan


@router.post(
    "/plans/{plan_id}/dispatch",
    response_model=DispatchPlanResponse,
    summary="Dispatch approved plan: assignments pushed to order-service",
)
async def dispatch_plan_endpoint(plan_id: str) -> DispatchPlanResponse:
    """
    Dispatches a routing plan by invoking Order Service's POST /api/orders/assign-batch
    endpoint. Updates the local plan and order statuses to DISPATCHED / PLANNED.
    """
    try:
        return await run_dispatch(plan_id)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Dispatch error: {str(e)}")


@router.post(
    "/orders/seed-demo",
    response_model=List[PendingOrder],
    summary="Utility: Seed sample demo orders into the local pending pool",
)
def seed_demo_orders(
    tenant_id: str = "a1111111-1111-1111-1111-111111111111",
    count: int = 6,
) -> List[PendingOrder]:
    """
    Seeds sample delivery stops around Ho Chi Minh City District 1 & 3
    for rapid end-to-end testing of shift optimization.
    """
    demo_stops = [
        {"name": "Vincom Đồng Khởi", "lat": 10.7780, "lng": 106.7020, "addr": "72 Lê Thánh Tôn, Bến Nghé, Q.1"},
        {"name": "Nhà thờ Đức Bà", "lat": 10.7797, "lng": 106.6990, "addr": "01 Công xã Paris, Bến Nghé, Q.1"},
        {"name": "Dinh Độc Lập", "lat": 10.7770, "lng": 106.6953, "addr": "135 Nam Kỳ Khởi Nghĩa, Bến Thành, Q.1"},
        {"name": "Chợ Bến Thành", "lat": 10.7725, "lng": 106.6980, "addr": "Chợ Bến Thành, Bến Thành, Q.1"},
        {"name": "Hồ Con Rùa", "lat": 10.7828, "lng": 106.6958, "addr": "Vòng xoay Công trường Quốc Tế, Q.3"},
        {"name": "Công viên Tao Đàn", "lat": 10.7742, "lng": 106.6922, "addr": "Trương Định, Bến Thành, Q.1"},
        {"name": "Bitexco Financial Tower", "lat": 10.7716, "lng": 106.7044, "addr": "02 Hải Triều, Bến Nghé, Q.1"},
        {"name": "Bảo tàng Chứng tích Chiến tranh", "lat": 10.7795, "lng": 106.6921, "addr": "28 Võ Văn Tần, P.6, Q.3"},
    ]

    seeded: List[PendingOrder] = []
    depot_lat, depot_lng = 10.7769, 106.7009
    depot_addr = "Kho Hàng Tổng Quận 1, 120 Nguyễn Huệ, Q.1, TP.HCM"

    for i in range(min(count, len(demo_stops))):
        s = demo_stops[i]
        order_id = str(uuid.uuid4())
        event_id = str(uuid.uuid4())
        p_order = PendingOrder(
            order_id=order_id,
            tenant_id=tenant_id,
            customer_id="c0000000-0000-0000-0000-000000000001",
            pickup_lat=depot_lat,
            pickup_lng=depot_lng,
            pickup_address=depot_addr,
            delivery_lat=s["lat"],
            delivery_lng=s["lng"],
            delivery_address=s["addr"],
            weight_kg=1.5 + (i * 0.5),
            time_window_start="2026-10-30T09:00:00Z",
            time_window_end="2026-10-30T12:00:00Z",
            status="PENDING_PAYMENT",
            shift="MORNING",
            received_at=datetime.now(timezone.utc).isoformat(),
        )
        order_store.save_or_update_order(p_order)
        order_store.mark_event_processed(event_id)
        seeded.append(p_order)

    return seeded
