# service/shift_optimizer.py
"""
Service layer for shift-based batch route optimisation.
Bridges pending order data with BatchOptimizationRequest and the TSP solvers,
partitions deliveries across available drivers, and persists resulting routing plans.
"""
from __future__ import annotations

import json
import logging
import math
import os
import uuid
from datetime import datetime, timezone
from typing import List, Optional

try:
    import aio_pika
except ImportError:
    aio_pika = None
import httpx

from api.schemas import (
    BatchOptimizationRequest,
    DispatchPlanResponse,
    DriverRoutePlan,
    LocationPoint,
    PendingOrder,
    RoutingPlan,
    ShiftOptimizationRequest,
    StopItem,
)
from service.optimizer_service import solve_batch
from service.order_store import order_store
from service.plan_store import plan_store

logger = logging.getLogger("routing-engine.shift_optimizer")

ORDER_SERVICE_URL = os.getenv("ORDER_SERVICE_URL", "http://order-service:8082")
DRIVER_SERVICE_URL = os.getenv("DRIVER_SERVICE_URL", "http://driver-service:8083")


def fetch_real_driver_ids(tenant_id: str) -> List[str]:
    """
    Queries driver-service for real active and available driver UUIDs for the given tenant.
    Never uses hardcoded IDs or mocks.
    """
    url = f"{DRIVER_SERVICE_URL}/api/drivers"
    try:
        with httpx.Client(timeout=4.0) as client:
            resp = client.get(url, params={"tenant_id": tenant_id}, headers={"X-Service-Key": os.getenv("TENANT_INTERNAL_KEY", "")})
            if resp.status_code == 200:
                data = resp.json().get("data", [])
                if isinstance(data, list):
                    # Filter for active and available drivers
                    active = [
                        str(d.get("driverId") or d.get("driver_id") or d.get("userId") or d.get("user_id"))
                        for d in data
                        if (d.get("status") == "ACTIVE" or d.get("status") is None)
                    ]
                    valid_ids = [d_id for d_id in active if d_id and d_id != "None"]
                    if valid_ids:
                        return valid_ids
    except Exception as exc:
        logger.error("Could not fetch real drivers from driver-service for tenant %s: %s", tenant_id, exc)
    return []


def _partition_stops_for_drivers(
    stops: List[StopItem],
    depot: LocationPoint,
    driver_ids: Optional[List[str]],
) -> List[tuple[Optional[str], List[StopItem]]]:
    """
    Partitions stops among drivers using an angular sweep algorithm
    around the depot to create geometrically cohesive delivery zones.
    """
    if not driver_ids or len(driver_ids) <= 1:
        single_driver = driver_ids[0] if driver_ids else None
        return [(single_driver, stops)]

    # Sort stops by bearing from depot (sweep heuristic)
    def stop_bearing(stop: StopItem) -> float:
        d_lat = stop.location.lat - depot.lat
        d_lng = stop.location.lng - depot.lng
        angle = math.atan2(d_lat, d_lng)
        return (angle + 2 * math.pi) % (2 * math.pi)

    sorted_stops = sorted(stops, key=stop_bearing)
    n_drivers = len(driver_ids)
    stops_per_driver = math.ceil(len(sorted_stops) / n_drivers)

    partitions: List[tuple[Optional[str], List[StopItem]]] = []
    for i, driver_id in enumerate(driver_ids):
        start_idx = i * stops_per_driver
        end_idx = min(start_idx + stops_per_driver, len(sorted_stops))
        chunk = sorted_stops[start_idx:end_idx]
        if chunk:
            partitions.append((driver_id, chunk))

    return partitions


def optimize_shift(request: ShiftOptimizationRequest) -> RoutingPlan:
    """
    Runs batch optimization for pending orders within a specific shift and tenant.

    Workflow:
      1. Pulls pending orders from OrderStore matching tenant_id and shift.
      2. Converts pending orders into StopItem objects.
      3. Determines depot location (from request, or first order's pickup).
      4. Partitions deliveries across available drivers.
      5. Runs solver (Google OR-Tools) for each partition.
      6. Aggregates results into a durable RoutingPlan (status: DRAFT).
      7. Persists plan into PlanStore.
    """
    # 1. Fetch pending orders
    orders = order_store.get_orders(
        tenant_id=request.tenant_id,
        shift=request.shift,
        status="PENDING_PAYMENT",  # Or confirmed/paid orders
    )

    if not orders:
        # Also include any orders in other non-delivered states if PENDING_PAYMENT is empty
        orders = [
            o for o in order_store.get_orders(tenant_id=request.tenant_id, shift=request.shift)
            if o.status not in ("DELIVERED", "CANCELLED")
        ]

    if not orders:
        raise ValueError(
            f"No pending orders found for tenant {request.tenant_id} in shift {request.shift}."
        )

    # 2. Determine Depot location
    if request.depot:
        depot = request.depot
    else:
        # Use first order pickup or default HCM hub
        first_order = orders[0]
        depot = LocationPoint(
            lat=first_order.pickup_lat,
            lng=first_order.pickup_lng,
            address=first_order.pickup_address or "Chi nhánh chính",
        )

    # 3. Convert orders into StopItems
    stops: List[StopItem] = [
        StopItem(
            id=order.order_id,
            name=f"Order {order.order_id[:8]}",
            location=LocationPoint(
                lat=order.delivery_lat,
                lng=order.delivery_lng,
                address=order.delivery_address,
            ),
            demand_kg=order.weight_kg,
            time_window_start=order.time_window_start,
            time_window_end=order.time_window_end,
        )
        for order in orders
    ]

    # 4. Resolve real drivers if not explicitly specified
    driver_ids = request.driver_ids
    if not driver_ids:
        driver_ids = fetch_real_driver_ids(request.tenant_id)
        if driver_ids:
            logger.info("Dynamically resolved %d active drivers from driver-service for tenant %s", len(driver_ids), request.tenant_id)
        else:
            logger.warning("No active drivers found from driver-service for tenant %s. Routes will be created unassigned.", request.tenant_id)

    # Partition stops across drivers
    partitions = _partition_stops_for_drivers(stops, depot, driver_ids)

    # 5. Solve routes for each driver
    driver_routes: List[DriverRoutePlan] = []
    total_distance_km = 0.0
    total_duration_minutes = 0.0
    last_algorithm_used = request.algorithm

    for driver_id, driver_stops in partitions:
        batch_req = BatchOptimizationRequest(
            depot=depot,
            stops=driver_stops,
            algorithm=request.algorithm,
        )
        batch_res = solve_batch(batch_req)
        last_algorithm_used = batch_res.algorithm_used

        driver_routes.append(
            DriverRoutePlan(
                driver_id=driver_id,
                driver_name=f"Driver {driver_id[:8]}" if driver_id else "Tài xế phân công sau",
                total_distance_km=batch_res.total_distance_km,
                total_duration_minutes=batch_res.total_duration_minutes,
                ordered_stops=batch_res.ordered_stops,
            )
        )
        total_distance_km += batch_res.total_distance_km
        total_duration_minutes += batch_res.total_duration_minutes

    # 6. Build and save RoutingPlan
    plan = RoutingPlan(
        plan_id=str(uuid.uuid4()),
        tenant_id=request.tenant_id,
        shift=request.shift,
        status="DRAFT",
        algorithm_used=last_algorithm_used,
        total_orders=len(orders),
        total_distance_km=round(total_distance_km, 2),
        total_duration_minutes=round(total_duration_minutes, 1),
        driver_routes=driver_routes,
        created_at=datetime.now(timezone.utc).isoformat(),
    )

    plan_store.save_plan(plan)
    logger.info(
        "Generated plan %s for tenant %s (shift: %s, orders: %d, dist: %.2f km)",
        plan.plan_id,
        plan.tenant_id,
        plan.shift,
        plan.total_orders,
        plan.total_distance_km,
    )
    return plan


async def dispatch_plan(plan_id: str) -> DispatchPlanResponse:
    """
    Dispatches an approved RoutingPlan to Order Service:
      1. Collects all (orderId, driverId, planId) assignments from the plan.
      2. Calls POST /api/orders/assign-batch on order-service.
      3. On success, updates plan status to DISPATCHED.
    """
    plan = plan_store.get_plan(plan_id)
    if not plan:
        raise ValueError(f"Routing plan {plan_id} not found.")

    # Build assignments payload using real assigned drivers
    assignments: List[dict] = []
    available_drivers_pool: Optional[List[str]] = None

    for route in plan.driver_routes:
        effective_driver = route.driver_id
        if not effective_driver:
            if available_drivers_pool is None:
                available_drivers_pool = fetch_real_driver_ids(plan.tenant_id)
            if available_drivers_pool:
                effective_driver = available_drivers_pool.pop(0)
                route.driver_id = effective_driver
            else:
                raise ValueError(
                    f"Cannot dispatch plan {plan.plan_id}: Route has no assigned driver and no active drivers available from driver-service for tenant {plan.tenant_id}"
                )
        for stop in route.ordered_stops:
            assignments.append(
                {
                    "orderId": stop.stop_id,
                    "driverId": effective_driver,
                    "planId": plan.plan_id,
                }
            )

    if not assignments:
        raise ValueError(f"Cannot dispatch plan {plan.plan_id}: Plan contains no stop assignments")

    # Publish batch dispatch event to RabbitMQ topic exchange
    rabbit_url = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
    try:
        connection = await aio_pika.connect_robust(rabbit_url)
        async with connection:
            channel = await connection.channel()
            exchange = await channel.declare_exchange(
                "swiftroute.events",
                aio_pika.ExchangeType.TOPIC,
                durable=True,
            )
            batch_event = {
                "eventId": str(uuid.uuid4()),
                "planId": plan.plan_id,
                "assignments": assignments,
                "dispatchedAt": datetime.now(timezone.utc).isoformat(),
            }
            await exchange.publish(
                aio_pika.Message(
                    body=json.dumps(batch_event).encode("utf-8"),
                    content_type="application/json",
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                ),
                routing_key="order.batch.dispatched",
            )
            logger.info("Published batch dispatch event to RabbitMQ for plan %s (%d assignments)", plan_id, len(assignments))
    except Exception as exc:
        logger.error("Failed to publish batch dispatch event to RabbitMQ: %s", exc)
        raise RuntimeError(f"Failed to publish batch dispatch event to RabbitMQ: {exc}") from exc

    # Update local states
    dispatched_time = datetime.now(timezone.utc).isoformat()
    plan_store.update_plan_status(plan_id, "DISPATCHED", dispatched_at=dispatched_time)

    for item in assignments:
        order_store.update_order_status(item["orderId"], "PLANNED")

    return DispatchPlanResponse(
        plan_id=plan_id,
        status="DISPATCHED",
        dispatched_orders_count=len(assignments),
        assignments=assignments,
        message="Routing plan successfully dispatched and assigned to drivers.",
    )

