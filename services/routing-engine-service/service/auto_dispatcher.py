# service/auto_dispatcher.py
"""
Automated Dispatch Engine for SwiftRoute.

Implements deterministic business rules for batch route optimization and automated dispatch:
  1. Threshold Trigger: Order count >= tenant batch_size_threshold
  2. Timeout Trigger: Oldest pending order wait time >= tenant max_wait_time_seconds
  3. Urgent Order Handling: Immediate single-driver assignment or priority batch trigger
  4. Driver Constraints & Availability: Queries driver-service, respects vehicle capacity
  5. Overlap Prevention: Async lock per tenant prevents concurrent optimization runs
  6. In-flight Order Safety: New orders arriving during a run remain in the pending pool
  7. Approval vs Auto-Dispatch: Automatically dispatches or saves as DRAFT based on tenant config
  8. Preserves Manual Workflow: Works alongside manual operator actions in Web Admin

No AI agents or heuristic non-determinism are introduced.
"""
from __future__ import annotations

import asyncio
import json
import logging
import math
import os
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Set, Tuple

import aio_pika
import httpx

from api.schemas import (
    AutoDispatchEvaluationResult,
    AutoDispatchStatusResponse,
    BatchOptimizationRequest,
    DriverRoutePlan,
    LocationPoint,
    PendingOrder,
    RoutingPlan,
    StopItem,
    TenantDispatchConfig,
    TenantQueueStatus,
)
from service.optimizer_service import solve_batch
from service.order_store import order_store
from service.plan_store import plan_store
from service.shift_optimizer import _partition_stops_for_drivers, dispatch_plan

logger = logging.getLogger("routing-engine.auto_dispatcher")

DRIVER_SERVICE_URL = os.getenv("DRIVER_SERVICE_URL", "http://driver-service:8083")
ORDER_SERVICE_URL = os.getenv("ORDER_SERVICE_URL", "http://order-service:8082")
RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")


def calculate_haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Haversine distance between two GPS coordinates in kilometers."""
    r = 6371.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    a = (
        math.sin(delta_phi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * (math.sin(delta_lambda / 2.0) ** 2)
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r * c


class AutoDispatcher:
    """
    Deterministic Automated Dispatch Engine managing tenant queues,
    optimization triggers, and plan dispatching.
    """

    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._running_tenants: Set[str] = set()
        self._tenant_configs: Dict[str, Tuple[TenantDispatchConfig, float]] = {}
        self._cache_ttl_seconds = 60.0

    async def get_tenant_config(self, tenant_id: str) -> TenantDispatchConfig:
        """
        Retrieves tenant dispatch configuration, cached for 60s.
        Falls back to default config if order-service is unreachable.
        """
        now = datetime.now(timezone.utc).timestamp()
        if tenant_id in self._tenant_configs:
            cfg, timestamp = self._tenant_configs[tenant_id]
            if now - timestamp < self._cache_ttl_seconds:
                return cfg

        url = f"{ORDER_SERVICE_URL}/api/tenants/{tenant_id}/dispatch-config"
        try:
            async with httpx.AsyncClient(timeout=4.0) as client:
                resp = await client.get(url, headers={"X-Service-Key": os.getenv("TENANT_INTERNAL_KEY", "")})
                if resp.status_code == 200:
                    data = resp.json().get("data", {})
                    config = TenantDispatchConfig(**data)
                    self._tenant_configs[tenant_id] = (config, now)
                    return config
        except Exception as exc:
            logger.debug("Could not fetch dispatch config for tenant %s from order-service: %s", tenant_id, exc)

        default_config = TenantDispatchConfig(auto_dispatch_enabled=False)
        self._tenant_configs[tenant_id] = (default_config, now)
        return default_config

    async def fetch_available_drivers(self, tenant_id: str) -> List[Dict[str, Any]]:
        """
        Queries driver-service for active drivers belonging to or available for the tenant.
        Filters by status == 'ACTIVE' and is_available == True.
        """
        url = f"{DRIVER_SERVICE_URL}/api/drivers"
        try:
            async with httpx.AsyncClient(timeout=4.0) as client:
                resp = await client.get(url, headers={"X-Service-Key": os.getenv("TENANT_INTERNAL_KEY", "")}, params={"tenant_id": tenant_id})
                if resp.status_code == 200:
                    drivers = resp.json().get("data", [])
                    if isinstance(drivers, list) and drivers:
                        # Prioritize active and available drivers
                        available = [
                            d for d in drivers
                            if (d.get("status") == "ACTIVE" or d.get("status") is None)
                            and (d.get("isAvailable") is True or d.get("is_available") is True)
                        ]
                        if available:
                            return available
                        # Fallback to active drivers if no driver has toggled online
                        active = [
                            d for d in drivers
                            if (d.get("status") == "ACTIVE" or d.get("status") is None)
                        ]
                        return active
        except Exception as exc:
            logger.error("Failed to query drivers from driver-service for tenant %s: %s", tenant_id, exc)

        return []

    async def fetch_pool_drivers(self) -> List[Dict[str, Any]]:
        """
        Queries driver-service for platform-designated pool drivers.
        Respects tenant isolation: only retrieves drivers with explicit platform-pool status.
        """
        url = f"{DRIVER_SERVICE_URL}/api/drivers"
        try:
            async with httpx.AsyncClient(timeout=4.0) as client:
                resp = await client.get(url, headers={"X-Service-Key": os.getenv("TENANT_INTERNAL_KEY", "")}, params={"is_pool": "true"})
                if resp.status_code == 200:
                    drivers = resp.json().get("data", [])
                    if isinstance(drivers, list):
                        return [d for d in drivers if d.get("status") == "ACTIVE" and (d.get("is_pool") is True or d.get("isPool") is True)]
        except Exception as exc:
            logger.debug("Platform driver pool query: %s", exc)
        return []

    async def dispatch_single_urgent_order(
        self,
        order: PendingOrder,
        drivers: List[Dict[str, Any]],
    ) -> bool:
        """
        Immediately dispatches an urgent order to the closest active driver.
        """
        best_driver = None
        min_dist = float("inf")

        for d in drivers:
            d_lat = d.get("currentLat") or d.get("current_lat")
            d_lng = d.get("currentLng") or d.get("current_lng")
            if d_lat is not None and d_lng is not None:
                dist = calculate_haversine_km(order.pickup_lat, order.pickup_lng, float(d_lat), float(d_lng))
                if dist < min_dist:
                    min_dist = dist
                    best_driver = d

        if not best_driver:
            best_driver = drivers[0]

        driver_id = (
            best_driver.get("driverId")
            or best_driver.get("driver_id")
            or best_driver.get("userId")
            or best_driver.get("user_id")
        )

        logger.info(
            "URGENT DISPATCH: Assigning order %s to closest driver %s (dist=%.2f km)",
            order.order_id,
            driver_id,
            min_dist,
        )

        connection = await aio_pika.connect_robust(RABBITMQ_URL)
        async with connection:
            channel = await connection.channel()
            exchange = await channel.declare_exchange("swiftroute.events", aio_pika.ExchangeType.TOPIC, durable=True)
            event_payload = {
                "eventId": str(uuid.uuid4()),
                "orderId": order.order_id,
                "driverId": str(driver_id),
                "status": "PLANNED",
                "dispatchedAt": datetime.now(timezone.utc).isoformat(),
            }
            await exchange.publish(
                aio_pika.Message(
                    body=json.dumps(event_payload).encode("utf-8"),
                    content_type="application/json",
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                ),
                routing_key="order.dispatched",
            )

        order_store.update_order_status(order.order_id, "PLANNED")
        return True

    async def evaluate_tenant(
        self,
        tenant_id: str,
        trigger_reason: Optional[str] = None,
        force: bool = False,
    ) -> AutoDispatchEvaluationResult:
        """
        Evaluates automated dispatch business rules for a single tenant.
        Thread-safe and concurrency-locked per tenant.
        """
        # 1. Concurrency Check: prevent duplicate or overlapping optimization runs
        async with self._lock:
            if tenant_id in self._running_tenants:
                return AutoDispatchEvaluationResult(
                    tenant_id=tenant_id,
                    triggered=False,
                    reason="CONCURRENT_RUN_IN_PROGRESS",
                    message=f"Optimization already in progress for tenant {tenant_id}. Overlap prevented.",
                )
            self._running_tenants.add(tenant_id)

        try:
            return await self._execute_evaluation(tenant_id, trigger_reason, force)
        finally:
            async with self._lock:
                self._running_tenants.discard(tenant_id)

    async def _execute_evaluation(
        self,
        tenant_id: str,
        trigger_reason: Optional[str],
        force: bool,
    ) -> AutoDispatchEvaluationResult:
        """Internal execution under tenant lock."""
        # 2. Fetch tenant dispatch preferences
        config = await self.get_tenant_config(tenant_id)

        if not config.auto_dispatch_enabled and not force:
            return AutoDispatchEvaluationResult(
                tenant_id=tenant_id,
                triggered=False,
                reason="AUTO_DISPATCH_DISABLED",
                message="Automated dispatch is disabled for this tenant. Orders awaiting manual dispatch.",
            )

        # 3. Pull pending orders in PAID status
        pending_orders = order_store.get_pending_paid_orders(tenant_id)
        if not pending_orders:
            return AutoDispatchEvaluationResult(
                tenant_id=tenant_id,
                triggered=False,
                reason="NO_PAID_ORDERS",
                message="No pending paid orders found for this tenant.",
            )

        now_utc = datetime.now(timezone.utc)
        urgent_orders = [o for o in pending_orders if o.priority == "URGENT"]
        has_urgent = len(urgent_orders) > 0

        # Compute wait time of oldest pending order
        oldest_order = pending_orders[0]
        try:
            oldest_dt = datetime.fromisoformat(oldest_order.received_at.replace("Z", "+00:00"))
            wait_time_seconds = (now_utc - oldest_dt).total_seconds()
        except Exception:
            wait_time_seconds = 0.0

        # 4. Evaluate Business Rules
        rule_triggered = False
        decision_reason = trigger_reason or "THRESHOLD_EVALUATION"

        # Rule A: Urgent Order Handling
        if has_urgent:
            if config.urgent_order_mode == "IMMEDIATE_SINGLE":
                drivers = await self.fetch_available_drivers(tenant_id)
                if drivers:
                    for urgent_order in urgent_orders:
                        await self.dispatch_single_urgent_order(urgent_order, drivers)
                    # Remaining non-urgent orders continue to normal evaluation
                    pending_orders = [o for o in pending_orders if o.priority != "URGENT"]
                    if not pending_orders:
                        return AutoDispatchEvaluationResult(
                            tenant_id=tenant_id,
                            triggered=True,
                            reason="URGENT_ORDER_IMMEDIATE_SINGLE",
                            order_count=len(urgent_orders),
                            dispatched=True,
                            message=f"Dispatched {len(urgent_orders)} urgent order(s) immediately to nearest drivers.",
                        )
            else:
                # BATCH_IMMEDIATE mode: trigger batch optimization immediately
                rule_triggered = True
                decision_reason = "URGENT_ORDER_BATCH_IMMEDIATE"

        # Rule B: Threshold Trigger
        if len(pending_orders) >= config.batch_size_threshold:
            rule_triggered = True
            decision_reason = f"BATCH_SIZE_THRESHOLD_REACHED ({len(pending_orders)} >= {config.batch_size_threshold})"

        # Rule C: Max Wait Time Trigger
        elif wait_time_seconds >= config.max_wait_time_seconds:
            rule_triggered = True
            decision_reason = f"MAX_WAIT_TIME_EXCEEDED ({int(wait_time_seconds)}s >= {config.max_wait_time_seconds}s)"

        if not rule_triggered and not force:
            return AutoDispatchEvaluationResult(
                tenant_id=tenant_id,
                triggered=False,
                reason="CRITERIA_NOT_MET",
                order_count=len(pending_orders),
                message=(
                    f"Thresholds not met: {len(pending_orders)}/{config.batch_size_threshold} orders, "
                    f"wait time {int(wait_time_seconds)}s/{config.max_wait_time_seconds}s."
                ),
            )

        # 5. Fetch available drivers and verify fleet capacity
        drivers = await self.fetch_available_drivers(tenant_id)
        if not drivers and config.allow_pool_drivers:
            logger.info("Internal fleet empty for tenant %s; querying platform driver pool as configured", tenant_id)
            drivers = await self.fetch_pool_drivers()

        if not drivers:
            logger.warning(
                "Automated dispatch deferred for tenant %s: no active drivers available (allow_pool=%s).",
                tenant_id,
                config.allow_pool_drivers,
            )
            return AutoDispatchEvaluationResult(
                tenant_id=tenant_id,
                triggered=False,
                reason="NO_DRIVERS_AVAILABLE",
                order_count=len(pending_orders),
                message="No active or available drivers found in driver-service. Deferred to next evaluation.",
            )

        # 6. Snapshot candidate orders for this batch run
        max_batch_capacity = config.max_orders_per_driver * len(drivers)
        candidate_orders = pending_orders[:max_batch_capacity]
        driver_ids = [
            str(d.get("driverId") or d.get("driver_id") or d.get("userId") or d.get("user_id"))
            for d in drivers
        ]

        logger.info(
            "Triggering batch optimization for tenant %s (%s): %d candidate orders across %d drivers (Algorithm=%s)",
            tenant_id,
            decision_reason,
            len(candidate_orders),
            len(driver_ids),
            config.algorithm,
        )

        # 7. Convert candidate orders to StopItem objects
        depot = LocationPoint(
            lat=candidate_orders[0].pickup_lat,
            lng=candidate_orders[0].pickup_lng,
            address=candidate_orders[0].pickup_address or "Trung tâm Tiếp nhận Hàng hóa",
        )

        stops: List[StopItem] = [
            StopItem(
                id=o.order_id,
                name=f"Order {o.order_id[:8]}",
                location=LocationPoint(lat=o.delivery_lat, lng=o.delivery_lng, address=o.delivery_address),
                demand_kg=o.weight_kg,
                time_window_start=o.time_window_start,
                time_window_end=o.time_window_end,
            )
            for o in candidate_orders
        ]

        # 8. Partition stops across available drivers and run TSP solver
        partitions = _partition_stops_for_drivers(stops, depot, driver_ids)

        driver_routes: List[DriverRoutePlan] = []
        total_distance_km = 0.0
        total_duration_minutes = 0.0
        applied_algorithm = config.algorithm

        for d_id, d_stops in partitions:
            batch_req = BatchOptimizationRequest(
                depot=depot,
                stops=d_stops,
                algorithm=config.algorithm,
                time_weight=config.time_weight if config.time_weight is not None else 0.5,
                cost_weight=config.cost_weight if config.cost_weight is not None else 0.5,
            )
            batch_res = solve_batch(batch_req)
            applied_algorithm = batch_res.algorithm_used

            driver_routes.append(
                DriverRoutePlan(
                    driver_id=d_id,
                    driver_name=f"Driver {d_id[:8]}" if d_id else "Tài xế phân công sau",
                    total_distance_km=batch_res.total_distance_km,
                    total_duration_minutes=batch_res.total_duration_minutes,
                    ordered_stops=batch_res.ordered_stops,
                )
            )
            total_distance_km += batch_res.total_distance_km
            total_duration_minutes += batch_res.total_duration_minutes

        # 9. Create and persist RoutingPlan
        plan = RoutingPlan(
            plan_id=str(uuid.uuid4()),
            tenant_id=tenant_id,
            shift=candidate_orders[0].shift,
            status="DRAFT",
            algorithm_used=applied_algorithm,
            total_orders=len(candidate_orders),
            total_distance_km=round(total_distance_km, 2),
            total_duration_minutes=round(total_duration_minutes, 1),
            driver_routes=driver_routes,
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        plan_store.save_plan(plan)

        # 10. Auto-Dispatch vs Manual Approval check
        if config.require_approval:
            # Plan remains in DRAFT for dispatcher review in Web Admin
            logger.info("Plan %s generated in DRAFT mode for tenant %s awaiting dispatcher approval.", plan.plan_id, tenant_id)
            return AutoDispatchEvaluationResult(
                tenant_id=tenant_id,
                triggered=True,
                reason=decision_reason,
                order_count=len(candidate_orders),
                plan_id=plan.plan_id,
                dispatched=False,
                message=f"Plan {plan.plan_id} generated in DRAFT mode. Awaiting operator approval.",
            )

        # Auto-dispatch immediately
        dispatch_res = await dispatch_plan(plan.plan_id)
        logger.info(
            "Auto-dispatched plan %s for tenant %s: %d assignments published to RabbitMQ.",
            plan.plan_id,
            tenant_id,
            dispatch_res.dispatched_orders_count,
        )

        return AutoDispatchEvaluationResult(
            tenant_id=tenant_id,
            triggered=True,
            reason=decision_reason,
            order_count=len(candidate_orders),
            plan_id=plan.plan_id,
            dispatched=True,
            message=f"Plan {plan.plan_id} generated and auto-dispatched to {len(driver_routes)} drivers.",
        )

    async def evaluate_all_tenants(self) -> List[AutoDispatchEvaluationResult]:
        """
        Periodic evaluation loop: scans all tenants with pending PAID orders
        and evaluates threshold and wait-time triggers.
        """
        tenant_ids = order_store.get_distinct_tenants_with_paid_orders()
        results: List[AutoDispatchEvaluationResult] = []

        for t_id in tenant_ids:
            try:
                res = await self.evaluate_tenant(t_id, trigger_reason="PERIODIC_TIMER_EVALUATION")
                results.append(res)
            except Exception as err:
                logger.error("Error during automated evaluation for tenant %s: %s", t_id, err, exc_info=True)

        return results

    async def get_system_status(self) -> AutoDispatchStatusResponse:
        """Returns diagnostic status of all tenant pending queues and lock states."""
        tenant_ids = order_store.get_distinct_tenants_with_paid_orders()
        # Also include default tenant if not in list
        default_tenant = "a1111111-1111-1111-1111-111111111111"
        all_tenants = list(set(tenant_ids + [default_tenant]))

        tenants_status: List[TenantQueueStatus] = []
        now_utc = datetime.now(timezone.utc)

        for t_id in all_tenants:
            orders = order_store.get_pending_paid_orders(t_id)
            config = await self.get_tenant_config(t_id)

            oldest_age = 0.0
            if orders:
                try:
                    dt = datetime.fromisoformat(orders[0].received_at.replace("Z", "+00:00"))
                    oldest_age = (now_utc - dt).total_seconds()
                except Exception:
                    pass

            tenants_status.append(
                TenantQueueStatus(
                    tenant_id=t_id,
                    pending_paid_orders=len(orders),
                    oldest_order_age_seconds=round(oldest_age, 1),
                    has_urgent_order=any(o.priority == "URGENT" for o in orders),
                    is_optimizing=t_id in self._running_tenants,
                    config=config,
                )
            )

        return AutoDispatchStatusResponse(
            is_running=True,
            tenants=tenants_status,
            timestamp=datetime.now(timezone.utc).isoformat(),
        )


# Global singleton instance
auto_dispatcher = AutoDispatcher()

