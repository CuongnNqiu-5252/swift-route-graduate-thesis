# service/order_store.py
"""
In-memory and persisted storage for received RabbitMQ order events.
Implements idempotency checking via eventId deduping and stores
pending orders awaiting batch route optimisation.
"""
from __future__ import annotations

import logging
import sqlite3
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Set

from api.schemas import PendingOrder

logger = logging.getLogger("routing-engine.order_store")

DB_PATH = Path("data/routing_engine.db")


def classify_shift(time_window_start: Optional[str]) -> str:
    """
    Classifies an order into a delivery shift (MORNING, AFTERNOON, EVENING)
    based on its time_window_start timestamp.
    """
    if not time_window_start:
        hour = datetime.now(timezone.utc).hour + 7  # Vietnam UTC+7
        hour = hour % 24
        if 6 <= hour < 12:
            return "MORNING"
        elif 12 <= hour < 17:
            return "AFTERNOON"
        else:
            return "EVENING"

    try:
        dt = datetime.fromisoformat(time_window_start.replace("Z", "+00:00"))
        if dt.tzinfo:
            hour = (dt.hour + 7) % 24
        else:
            hour = dt.hour
        if 6 <= hour < 12:
            return "MORNING"
        elif 12 <= hour < 17:
            return "AFTERNOON"
        else:
            return "EVENING"
    except Exception:
        return "MORNING"


class OrderStore:
    """
    Thread-safe store for deduplicated order events and pending orders.
    Persists processed event IDs and order states to SQLite for crash resilience.
    """

    def __init__(self, db_path: Path = DB_PATH) -> None:
        self.db_path = db_path
        self._lock = threading.Lock()
        self._processed_events: Set[str] = set()
        self._orders: Dict[str, PendingOrder] = {}
        self._init_db()
        self._load_from_db()

    def _init_db(self) -> None:
        """Initialises SQLite tables for processed events and pending orders."""
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                CREATE TABLE IF NOT EXISTS processed_events (
                    event_id TEXT PRIMARY KEY,
                    processed_at TEXT NOT NULL
                )
                """
            )
            cursor.execute(
                """
                CREATE TABLE IF NOT EXISTS pending_orders (
                    order_id TEXT PRIMARY KEY,
                    tenant_id TEXT NOT NULL,
                    customer_id TEXT,
                    pickup_lat REAL NOT NULL,
                    pickup_lng REAL NOT NULL,
                    pickup_address TEXT,
                    delivery_lat REAL NOT NULL,
                    delivery_lng REAL NOT NULL,
                    delivery_address TEXT,
                    weight_kg REAL NOT NULL,
                    time_window_start TEXT,
                    time_window_end TEXT,
                    status TEXT NOT NULL,
                    shift TEXT NOT NULL,
                    received_at TEXT NOT NULL,
                    priority TEXT DEFAULT 'NORMAL'
                )
                """
            )
            # Migration check: add priority column if table existed without it
            try:
                cursor.execute("ALTER TABLE pending_orders ADD COLUMN priority TEXT DEFAULT 'NORMAL'")
            except sqlite3.OperationalError:
                pass  # Column already exists
            conn.commit()

    def _load_from_db(self) -> None:
        """Loads existing state from SQLite into memory on startup."""
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT event_id FROM processed_events")
            for row in cursor.fetchall():
                self._processed_events.add(row[0])

            cursor.execute(
                """
                SELECT order_id, tenant_id, customer_id, pickup_lat, pickup_lng,
                       pickup_address, delivery_lat, delivery_lng, delivery_address,
                       weight_kg, time_window_start, time_window_end, status, shift,
                       received_at, COALESCE(priority, 'NORMAL')
                FROM pending_orders
                """
            )
            for row in cursor.fetchall():
                order = PendingOrder(
                    order_id=row[0],
                    tenant_id=row[1],
                    customer_id=row[2],
                    pickup_lat=row[3],
                    pickup_lng=row[4],
                    pickup_address=row[5],
                    delivery_lat=row[6],
                    delivery_lng=row[7],
                    delivery_address=row[8],
                    weight_kg=row[9],
                    time_window_start=row[10],
                    time_window_end=row[11],
                    status=row[12],
                    shift=row[13],
                    received_at=row[14],
                    priority=row[15] if len(row) > 15 else "NORMAL",
                )
                self._orders[order.order_id] = order
        logger.info(
            "Loaded %d processed events and %d orders from SQLite storage.",
            len(self._processed_events),
            len(self._orders),
        )

    def save_order_with_event(self, order: PendingOrder, event_id: str) -> bool:
        """
        Atomically records event_id in processed_events and saves order in pending_orders
        within a single database transaction.
        Returns True if newly inserted, False if event_id has already been processed (duplicate).
        """
        with self._lock:
            if event_id in self._processed_events:
                return False

            try:
                with sqlite3.connect(self.db_path) as conn:
                    cursor = conn.cursor()
                    cursor.execute("BEGIN IMMEDIATE")
                    cursor.execute(
                        "INSERT INTO processed_events (event_id, processed_at) VALUES (?, ?)",
                        (event_id, datetime.now(timezone.utc).isoformat()),
                    )
                    cursor.execute(
                        """
                        INSERT OR REPLACE INTO pending_orders (
                            order_id, tenant_id, customer_id, pickup_lat, pickup_lng,
                            pickup_address, delivery_lat, delivery_lng, delivery_address,
                            weight_kg, time_window_start, time_window_end, status, shift,
                            received_at, priority
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        (
                            order.order_id,
                            order.tenant_id,
                            order.customer_id,
                            order.pickup_lat,
                            order.pickup_lng,
                            order.pickup_address,
                            order.delivery_lat,
                            order.delivery_lng,
                            order.delivery_address,
                            order.weight_kg,
                            order.time_window_start,
                            order.time_window_end,
                            order.status,
                            order.shift,
                            order.received_at,
                            order.priority,
                        ),
                    )
                    conn.commit()
                self._processed_events.add(event_id)
                self._orders[order.order_id] = order
                return True
            except sqlite3.IntegrityError:
                self._processed_events.add(event_id)
                return False
            except Exception as e:
                logger.error("Failed transactional save for order %s event %s: %s", order.order_id, event_id, e)
                raise

    def is_event_processed(self, event_id: str) -> bool:
        """Idempotency check: returns True if event has already been handled."""
        with self._lock:
            return event_id in self._processed_events

    def mark_event_processed(self, event_id: str) -> None:
        """Records event_id in memory and SQLite."""
        with self._lock:
            self._processed_events.add(event_id)
            try:
                with sqlite3.connect(self.db_path) as conn:
                    conn.cursor().execute(
                        "INSERT OR IGNORE INTO processed_events (event_id, processed_at) VALUES (?, ?)",
                        (event_id, datetime.now(timezone.utc).isoformat()),
                    )
                    conn.commit()
            except Exception as e:
                logger.error("Failed to persist processed event %s: %s", event_id, e)

    def save_or_update_order(self, order: PendingOrder) -> None:
        """Inserts or updates an order in memory and SQLite."""
        with self._lock:
            self._orders[order.order_id] = order
            try:
                with sqlite3.connect(self.db_path) as conn:
                    conn.cursor().execute(
                        """
                        INSERT OR REPLACE INTO pending_orders (
                            order_id, tenant_id, customer_id, pickup_lat, pickup_lng,
                            pickup_address, delivery_lat, delivery_lng, delivery_address,
                            weight_kg, time_window_start, time_window_end, status, shift,
                            received_at, priority
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        (
                            order.order_id,
                            order.tenant_id,
                            order.customer_id,
                            order.pickup_lat,
                            order.pickup_lng,
                            order.pickup_address,
                            order.delivery_lat,
                            order.delivery_lng,
                            order.delivery_address,
                            order.weight_kg,
                            order.time_window_start,
                            order.time_window_end,
                            order.status,
                            order.shift,
                            order.received_at,
                            order.priority,
                        ),
                    )
                    conn.commit()
            except Exception as e:
                logger.error("Failed to persist order %s: %s", order.order_id, e)

    def get_order(self, order_id: str) -> Optional[PendingOrder]:
        """Retrieves an order by its ID."""
        with self._lock:
            return self._orders.get(order_id)

    def get_orders(
        self,
        tenant_id: Optional[str] = None,
        shift: Optional[str] = None,
        status: Optional[str] = None,
    ) -> List[PendingOrder]:
        """Filters orders by tenant, shift, and status."""
        with self._lock:
            orders = list(self._orders.values())
            if tenant_id:
                orders = [o for o in orders if o.tenant_id == tenant_id]
            if shift and shift != "ALL":
                orders = [o for o in orders if o.shift == shift]
            if status:
                orders = [o for o in orders if o.status == status]
            return orders

    def get_pending_paid_orders(self, tenant_id: Optional[str] = None) -> List[PendingOrder]:
        """
        Retrieves pending orders eligible for dispatch (status == 'PAID'),
        sorted by received_at ascending (FIFO: oldest orders first).
        """
        with self._lock:
            orders = [o for o in self._orders.values() if o.status == "PAID"]
            if tenant_id:
                orders = [o for o in orders if o.tenant_id == tenant_id]
            orders.sort(key=lambda o: o.received_at)
            return orders

    def get_distinct_tenants_with_paid_orders(self) -> List[str]:
        """Returns distinct tenant IDs that currently have PAID orders waiting."""
        with self._lock:
            return list({o.tenant_id for o in self._orders.values() if o.status == "PAID"})

    def update_order_status(self, order_id: str, new_status: str) -> None:
        """Updates the status of an existing order."""
        with self._lock:
            if order_id in self._orders:
                order = self._orders[order_id]
                updated = order.model_copy(update={"status": new_status})
                self._orders[order_id] = updated
                try:
                    with sqlite3.connect(self.db_path) as conn:
                        conn.cursor().execute(
                            "UPDATE pending_orders SET status = ? WHERE order_id = ?",
                            (new_status, order_id),
                        )
                        conn.commit()
                except Exception as e:
                    logger.error("Failed to update status for order %s: %s", order_id, e)


# Global singleton instance
order_store = OrderStore()
