# tests/test_auto_dispatcher.py
"""
Unit tests for the Automated Dispatch Engine and deterministic business rules.
Tests cover:
  1. Threshold Trigger (batch_size_threshold)
  2. Timeout Trigger (max_wait_time_seconds)
  3. Urgent Order Handling (IMMEDIATE_SINGLE vs BATCH_IMMEDIATE)
  4. Concurrency Guard (prevent overlapping runs)
  5. Approval vs Auto-Dispatch flag
"""
from __future__ import annotations

import asyncio
from datetime import datetime, timezone, timedelta
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from api.schemas import PendingOrder, TenantDispatchConfig
from service.auto_dispatcher import AutoDispatcher


@pytest.fixture
def dispatcher():
    """Returns a freshly initialized AutoDispatcher instance."""
    return AutoDispatcher()


def create_sample_order(
    order_id: str,
    tenant_id: str = "t1",
    status: str = "PAID",
    priority: str = "NORMAL",
    age_seconds: int = 10,
) -> PendingOrder:
    received = datetime.now(timezone.utc) - timedelta(seconds=age_seconds)
    return PendingOrder(
        order_id=order_id,
        tenant_id=tenant_id,
        pickup_lat=10.0371,
        pickup_lng=105.7828,
        pickup_address="Can Tho Depot",
        delivery_lat=10.0299,
        delivery_lng=105.7706,
        delivery_address="Can Tho Univ",
        weight_kg=2.0,
        priority=priority,
        status=status,
        shift="MORNING",
        received_at=received.isoformat(),
    )


def test_threshold_trigger(dispatcher):
    """Optimization triggers when pending orders count reaches batch_size_threshold."""
    async def _run():
        tenant_id = "tenant-thresh"
        config = TenantDispatchConfig(
            auto_dispatch_enabled=True,
            batch_size_threshold=3,
            max_wait_time_seconds=600,
            require_approval=False,
        )
        orders = [
            create_sample_order(f"ord-{i}", tenant_id=tenant_id, age_seconds=5)
            for i in range(3)
        ]
        sample_drivers = [{"driverId": "d1", "currentLat": 10.03, "currentLng": 105.78, "status": "ACTIVE", "isAvailable": True}]

        with patch.object(dispatcher, "get_tenant_config", new_callable=AsyncMock, return_value=config), \
             patch.object(dispatcher, "fetch_available_drivers", new_callable=AsyncMock, return_value=sample_drivers), \
             patch("service.auto_dispatcher.order_store.get_pending_paid_orders", return_value=orders), \
             patch("service.auto_dispatcher.dispatch_plan", new_callable=AsyncMock) as mock_dispatch, \
             patch("service.auto_dispatcher.plan_store.save_plan"):

            mock_dispatch.return_value = MagicMock(dispatched_orders_count=3)
            res = await dispatcher.evaluate_tenant(tenant_id)

            assert res.triggered is True
            assert "BATCH_SIZE_THRESHOLD_REACHED" in (res.reason or "")
            assert res.dispatched is True
            assert res.order_count == 3

    asyncio.run(_run())


def test_timeout_trigger(dispatcher):
    """Optimization triggers when oldest order exceeds max_wait_time_seconds even if count < threshold."""
    async def _run():
        tenant_id = "tenant-timeout"
        config = TenantDispatchConfig(
            auto_dispatch_enabled=True,
            batch_size_threshold=10,
            max_wait_time_seconds=120,
            require_approval=False,
        )
        orders = [create_sample_order("ord-old", tenant_id=tenant_id, age_seconds=150)]
        sample_drivers = [{"driverId": "d1", "currentLat": 10.03, "currentLng": 105.78, "status": "ACTIVE", "isAvailable": True}]

        with patch.object(dispatcher, "get_tenant_config", new_callable=AsyncMock, return_value=config), \
             patch.object(dispatcher, "fetch_available_drivers", new_callable=AsyncMock, return_value=sample_drivers), \
             patch("service.auto_dispatcher.order_store.get_pending_paid_orders", return_value=orders), \
             patch("service.auto_dispatcher.dispatch_plan", new_callable=AsyncMock) as mock_dispatch, \
             patch("service.auto_dispatcher.plan_store.save_plan"):

            mock_dispatch.return_value = MagicMock(dispatched_orders_count=1)
            res = await dispatcher.evaluate_tenant(tenant_id)

            assert res.triggered is True
            assert "MAX_WAIT_TIME_EXCEEDED" in (res.reason or "")
            assert res.dispatched is True

    asyncio.run(_run())


def test_criteria_not_met_no_trigger(dispatcher):
    """Does not trigger if order count < threshold and wait time < max_wait_time_seconds."""
    async def _run():
        tenant_id = "tenant-wait"
        config = TenantDispatchConfig(
            auto_dispatch_enabled=True,
            batch_size_threshold=5,
            max_wait_time_seconds=300,
        )
        orders = [
            create_sample_order("ord-1", tenant_id=tenant_id, age_seconds=20),
            create_sample_order("ord-2", tenant_id=tenant_id, age_seconds=10),
        ]

        with patch.object(dispatcher, "get_tenant_config", new_callable=AsyncMock, return_value=config), \
             patch("service.auto_dispatcher.order_store.get_pending_paid_orders", return_value=orders):

            res = await dispatcher.evaluate_tenant(tenant_id)
            assert res.triggered is False
            assert res.reason == "CRITERIA_NOT_MET"

    asyncio.run(_run())


def test_urgent_order_immediate_single(dispatcher):
    """Urgent order with IMMEDIATE_SINGLE mode dispatches to closest driver immediately."""
    async def _run():
        tenant_id = "tenant-urgent-single"
        config = TenantDispatchConfig(
            auto_dispatch_enabled=True,
            batch_size_threshold=5,
            urgent_order_mode="IMMEDIATE_SINGLE",
        )
        orders = [
            create_sample_order("ord-urgent", tenant_id=tenant_id, priority="URGENT", age_seconds=5)
        ]
        sample_drivers = [{"driverId": "d1", "currentLat": 10.03, "currentLng": 105.78, "status": "ACTIVE", "isAvailable": True}]

        with patch.object(dispatcher, "get_tenant_config", new_callable=AsyncMock, return_value=config), \
             patch.object(dispatcher, "fetch_available_drivers", new_callable=AsyncMock, return_value=sample_drivers), \
             patch.object(dispatcher, "dispatch_single_urgent_order", new_callable=AsyncMock, return_value=True) as mock_single, \
             patch("service.auto_dispatcher.order_store.get_pending_paid_orders", return_value=orders):

            res = await dispatcher.evaluate_tenant(tenant_id)
            assert res.triggered is True
            assert res.reason == "URGENT_ORDER_IMMEDIATE_SINGLE"
            assert res.dispatched is True
            mock_single.assert_awaited_once()

    asyncio.run(_run())


def test_require_approval_saves_draft(dispatcher):
    """When require_approval is True, plan is generated in DRAFT mode and not auto-dispatched."""
    async def _run():
        tenant_id = "tenant-approval"
        config = TenantDispatchConfig(
            auto_dispatch_enabled=True,
            batch_size_threshold=2,
            require_approval=True,
        )
        orders = [
            create_sample_order(f"ord-{i}", tenant_id=tenant_id, age_seconds=10)
            for i in range(2)
        ]
        sample_drivers = [{"driverId": "d1", "currentLat": 10.03, "currentLng": 105.78, "status": "ACTIVE", "isAvailable": True}]

        with patch.object(dispatcher, "get_tenant_config", new_callable=AsyncMock, return_value=config), \
             patch.object(dispatcher, "fetch_available_drivers", new_callable=AsyncMock, return_value=sample_drivers), \
             patch("service.auto_dispatcher.order_store.get_pending_paid_orders", return_value=orders), \
             patch("service.auto_dispatcher.dispatch_plan", new_callable=AsyncMock) as mock_dispatch, \
             patch("service.auto_dispatcher.plan_store.save_plan") as mock_save:

            res = await dispatcher.evaluate_tenant(tenant_id)

            assert res.triggered is True
            assert res.dispatched is False
            assert "DRAFT mode" in res.message
            mock_save.assert_called_once()
            mock_dispatch.assert_not_called()

    asyncio.run(_run())


def test_concurrency_lock_prevents_overlap(dispatcher):
    """Second concurrent evaluation for the same tenant is blocked by lock."""
    async def _run():
        tenant_id = "tenant-locked"
        dispatcher._running_tenants.add(tenant_id)

        res = await dispatcher.evaluate_tenant(tenant_id)
        assert res.triggered is False
        assert res.reason == "CONCURRENT_RUN_IN_PROGRESS"

    asyncio.run(_run())


