"""
Unit tests for atomic order event idempotency and transaction safety in OrderStore.
"""
import tempfile
import uuid
from pathlib import Path
from api.schemas import PendingOrder
from service.order_store import OrderStore


def test_order_store_atomic_save_and_duplicate_deduplication():
    test_id = uuid.uuid4().hex
    db_file = Path(tempfile.gettempdir()) / f"test_store_{test_id}.db"

    try:
        store = OrderStore(db_path=db_file)

        order = PendingOrder(
            order_id="ord-001",
            tenant_id="ten-001",
            customer_id="cust-001",
            pickup_lat=10.0,
            pickup_lng=105.0,
            pickup_address="A",
            delivery_lat=10.1,
            delivery_lng=105.1,
            delivery_address="B",
            weight_kg=1.0,
            status="PENDING_PAYMENT",
            shift="MORNING",
            received_at="2026-10-31T00:00:00Z",
            priority="NORMAL",
        )

        event_id = "evt-12345"

        # 1. First save must succeed
        first_result = store.save_order_with_event(order, event_id)
        assert first_result is True
        assert store.is_event_processed(event_id) is True
        assert store.get_order("ord-001") is not None

        # 2. Duplicate delivery of the same event must be rejected
        second_result = store.save_order_with_event(order, event_id)
        assert second_result is False

        # 3. Create a fresh instance reading from SQLite to verify persistence
        new_store = OrderStore(db_path=db_file)
        assert new_store.is_event_processed(event_id) is True
        assert new_store.get_order("ord-001") is not None
        assert new_store.save_order_with_event(order, event_id) is False
    finally:
        try:
            if db_file.exists():
                db_file.unlink()
        except Exception:
            pass
