# tests/test_shift_optimization.py
"""
Unit and integration tests for shift-based route optimization,
order store idempotency, and plan persistence.
"""
import uuid
from pathlib import Path
from fastapi.testclient import TestClient
import pytest

from main import app
from api.schemas import PendingOrder, ShiftOptimizationRequest, LocationPoint
from service.order_store import OrderStore, classify_shift
from service.plan_store import PlanStore
from service.shift_optimizer import optimize_shift, _partition_stops_for_drivers
from api.schemas import StopItem


@pytest.fixture
def temp_order_store(tmp_path: Path):
    db_file = tmp_path / "test_routing.db"
    return OrderStore(db_path=db_file)


@pytest.fixture
def temp_plan_store(tmp_path: Path):
    db_file = tmp_path / "test_plans.db"
    return PlanStore(db_path=db_file)


def test_classify_shift():
    assert classify_shift("2026-10-30T02:00:00Z") == "MORNING"    # 02 UTC = 09 VN -> MORNING
    assert classify_shift("2026-10-30T07:00:00Z") == "AFTERNOON"  # 07 UTC = 14 VN -> AFTERNOON
    assert classify_shift("2026-10-30T12:00:00Z") == "EVENING"    # 12 UTC = 19 VN -> EVENING


def test_order_store_idempotency(temp_order_store: OrderStore):
    event_id = str(uuid.uuid4())
    assert not temp_order_store.is_event_processed(event_id)

    temp_order_store.mark_event_processed(event_id)
    assert temp_order_store.is_event_processed(event_id)

    # Re-marking does not crash
    temp_order_store.mark_event_processed(event_id)
    assert temp_order_store.is_event_processed(event_id)


def test_order_store_save_and_filter(temp_order_store: OrderStore):
    tenant_id = "tenant-1"
    order = PendingOrder(
        order_id="order-1",
        tenant_id=tenant_id,
        pickup_lat=10.7769,
        pickup_lng=106.7009,
        delivery_lat=10.7800,
        delivery_lng=106.7050,
        weight_kg=2.0,
        time_window_start="2026-10-30T09:00:00Z",
        status="PENDING_PAYMENT",
        shift="MORNING",
        received_at="2026-10-30T08:00:00Z",
    )
    temp_order_store.save_or_update_order(order)

    orders = temp_order_store.get_orders(tenant_id=tenant_id, shift="MORNING")
    assert len(orders) == 1
    assert orders[0].order_id == "order-1"

    # Filter for non-matching shift
    assert len(temp_order_store.get_orders(tenant_id=tenant_id, shift="AFTERNOON")) == 0


def test_partition_stops_for_drivers():
    depot = LocationPoint(lat=10.7769, lng=106.7009)
    stops = [
        StopItem(id="s1", location=LocationPoint(lat=10.7780, lng=106.7020), demand_kg=1.0),
        StopItem(id="s2", location=LocationPoint(lat=10.7790, lng=106.7030), demand_kg=1.0),
        StopItem(id="s3", location=LocationPoint(lat=10.7720, lng=106.6980), demand_kg=1.0),
        StopItem(id="s4", location=LocationPoint(lat=10.7730, lng=106.6970), demand_kg=1.0),
    ]
    drivers = ["driver-1", "driver-2"]

    partitions = _partition_stops_for_drivers(stops, depot, drivers)
    assert len(partitions) == 2
    assert partitions[0][0] == "driver-1"
    assert partitions[1][0] == "driver-2"
    assert len(partitions[0][1]) + len(partitions[1][1]) == 4


def test_api_routes_end_to_end():
    client = TestClient(app)

    # 1. Health check
    res = client.get("/api/v1/health")
    assert res.status_code == 200

    # 2. Seed demo orders
    res = client.post("/api/v1/orders/seed-demo?count=4")
    assert res.status_code == 200
    seeded = res.json()
    assert len(seeded) == 4

    # 3. Check pending orders
    res = client.get("/api/v1/orders/pending")
    assert res.status_code == 200
    pending = res.json()
    assert len(pending) >= 4

    # 4. Run shift optimization
    tenant_id = seeded[0]["tenant_id"]
    opt_payload = {
        "tenant_id": tenant_id,
        "shift": "MORNING",
        "driver_ids": ["driver-1", "driver-2"],
        "algorithm": "held_karp",
    }
    res = client.post("/api/v1/plans/optimize-shift", json=opt_payload)
    assert res.status_code == 200
    plan = res.json()
    assert plan["status"] == "DRAFT"
    assert plan["total_orders"] >= 4
    assert len(plan["driver_routes"]) == 2
    assert plan["total_distance_km"] > 0

    plan_id = plan["plan_id"]

    # 5. Get plan by ID
    res = client.get(f"/api/v1/plans/{plan_id}")
    assert res.status_code == 200
    assert res.json()["plan_id"] == plan_id

    # 6. List plans
    res = client.get(f"/api/v1/plans?tenant_id={tenant_id}")
    assert res.status_code == 200
    plans = res.json()
    assert any(p["plan_id"] == plan_id for p in plans)

