# service/plan_store.py
"""
Storage repository for generated routing plans.
Persists plan results into SQLite and provides query capabilities.
"""
from __future__ import annotations

import json
import logging
import sqlite3
import threading
from pathlib import Path
from typing import Dict, List, Optional

from api.schemas import RoutingPlan

logger = logging.getLogger("routing-engine.plan_store")

DB_PATH = Path("data/routing_engine.db")


class PlanStore:
    """Thread-safe store for generated route optimisation plans."""

    def __init__(self, db_path: Path = DB_PATH) -> None:
        self.db_path = db_path
        self._lock = threading.Lock()
        self._plans: Dict[str, RoutingPlan] = {}
        self._init_db()
        self._load_from_db()

    def _init_db(self) -> None:
        """Creates the routing_plans table if it does not exist."""
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                CREATE TABLE IF NOT EXISTS routing_plans (
                    plan_id TEXT PRIMARY KEY,
                    tenant_id TEXT NOT NULL,
                    shift TEXT NOT NULL,
                    status TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """
            )
            conn.commit()

    def _load_from_db(self) -> None:
        """Loads saved plans into memory."""
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT payload_json FROM routing_plans ORDER BY created_at DESC")
            for row in cursor.fetchall():
                try:
                    data = json.loads(row[0])
                    plan = RoutingPlan.model_validate(data)
                    self._plans[plan.plan_id] = plan
                except Exception as e:
                    logger.warning("Failed to deserialize routing plan from DB: %s", e)
        logger.info("Loaded %d routing plans from SQLite.", len(self._plans))

    def save_plan(self, plan: RoutingPlan) -> RoutingPlan:
        """Saves a plan in memory and SQLite."""
        with self._lock:
            self._plans[plan.plan_id] = plan
            try:
                with sqlite3.connect(self.db_path) as conn:
                    conn.cursor().execute(
                        """
                        INSERT OR REPLACE INTO routing_plans (
                            plan_id, tenant_id, shift, status, payload_json, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        (
                            plan.plan_id,
                            plan.tenant_id,
                            plan.shift,
                            plan.status,
                            plan.model_dump_json(),
                            plan.created_at,
                        ),
                    )
                    conn.commit()
            except Exception as e:
                logger.error("Failed to persist routing plan %s: %s", plan.plan_id, e)
            return plan

    def get_plan(self, plan_id: str) -> Optional[RoutingPlan]:
        """Retrieves a single plan by its ID."""
        with self._lock:
            return self._plans.get(plan_id)

    def list_plans(
        self,
        tenant_id: Optional[str] = None,
        shift: Optional[str] = None,
        status: Optional[str] = None,
    ) -> List[RoutingPlan]:
        """Returns all plans matching the given filters."""
        with self._lock:
            plans = list(self._plans.values())
            if tenant_id:
                plans = [p for p in plans if p.tenant_id == tenant_id]
            if shift and shift != "ALL":
                plans = [p for p in plans if p.shift == shift]
            if status:
                plans = [p for p in plans if p.status == status]
            return plans

    def update_plan_status(
        self, plan_id: str, status: str, dispatched_at: Optional[str] = None
    ) -> Optional[RoutingPlan]:
        """Updates plan status (e.g. DRAFT -> DISPATCHED)."""
        with self._lock:
            plan = self._plans.get(plan_id)
            if not plan:
                return None
            updates = {"status": status}
            if dispatched_at:
                updates["dispatched_at"] = dispatched_at
            updated = plan.model_copy(update=updates)
            self._plans[plan_id] = updated
            try:
                with sqlite3.connect(self.db_path) as conn:
                    conn.cursor().execute(
                        """
                        UPDATE routing_plans
                        SET status = ?, payload_json = ?
                        WHERE plan_id = ?
                        """,
                        (status, updated.model_dump_json(), plan_id),
                    )
                    conn.commit()
            except Exception as e:
                logger.error("Failed to update status for plan %s: %s", plan_id, e)
            return updated


# Global singleton instance
plan_store = PlanStore()

