#!/usr/bin/env python3
"""
scripts/migrate_sqlite_to_pg.py
Migrate Routing Engine data from SQLite (routing_engine.db) to PostgreSQL (routing_db).
"""

import json
import os
import sqlite3
import sys
import uuid
from datetime import datetime, timezone
import psycopg
from psycopg.rows import dict_row

SQLITE_PATH = os.getenv("SQLITE_PATH", "services/routing-engine-service/data/routing_engine.db")
DEFAULT_ROUTING_URI = "postgresql://openpg:openpgpwd@localhost:5432/routing_db"
ROUTING_DB_URI = os.getenv("ROUTING_DATABASE_URL", os.getenv("ROUTING_DB_URI", DEFAULT_ROUTING_URI))


def migrate_sqlite_to_pg():
    print("[INFO] Starting SQLite to PostgreSQL Routing DB Migration...")
    print(f"  SQLite Source: {SQLITE_PATH}")
    print(f"  PostgreSQL Target: {ROUTING_DB_URI}")

    if not os.path.exists(SQLITE_PATH):
        print(f"[WARN] SQLite file not found at {SQLITE_PATH}. Nothing to migrate.")
        return

    sqlite_conn = sqlite3.connect(SQLITE_PATH)
    sqlite_conn.row_factory = sqlite3.Row
    s_cur = sqlite_conn.cursor()

    pg_conn = None
    try:
        pg_conn = psycopg.connect(ROUTING_DB_URI, row_factory=dict_row)
        p_cur = pg_conn.cursor()

        # 1. Migrate processed_events
        s_cur.execute("SELECT event_id, processed_at FROM processed_events;")
        events = s_cur.fetchall()
        print(f"[INFO] Migrating {len(events)} processed_events...")
        for row in events:
            raw_eid = row["event_id"]
            try:
                eid = str(uuid.UUID(raw_eid))
            except ValueError:
                eid = str(uuid.uuid5(uuid.NAMESPACE_DNS, raw_eid))

            p_cur.execute("""
                INSERT INTO processed_events (event_id, consumer_name, processed_at)
                VALUES (%s, %s, %s)
                ON CONFLICT (event_id, consumer_name) DO NOTHING;
            """, (eid, "routing-engine-consumer", row["processed_at"]))

        # 2. Migrate pending_orders
        s_cur.execute("""
            SELECT order_id, tenant_id, customer_id, pickup_lat, pickup_lng, pickup_address,
                   delivery_lat, delivery_lng, delivery_address, weight_kg, time_window_start,
                   time_window_end, status, shift, received_at
            FROM pending_orders;
        """)
        pending_orders = s_cur.fetchall()
        print(f"[INFO] Migrating {len(pending_orders)} pending_orders...")
        for o in pending_orders:
            p_cur.execute("""
                INSERT INTO pending_orders (
                    order_id, tenant_id, customer_id, pickup_lat, pickup_lng, pickup_address,
                    delivery_lat, delivery_lng, delivery_address, weight_kg, priority,
                    time_window_start, time_window_end, status, shift, received_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (order_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    shift = EXCLUDED.shift;
            """, (
                o["order_id"], o["tenant_id"], o["customer_id"],
                o["pickup_lat"], o["pickup_lng"], o["pickup_address"],
                o["delivery_lat"], o["delivery_lng"], o["delivery_address"],
                o["weight_kg"], "NORMAL",
                o["time_window_start"], o["time_window_end"],
                o["status"] or "PAID", o["shift"] or "MORNING", o["received_at"]
            ))

        # 3. Migrate routing_plans, driver_routes, route_stops
        s_cur.execute("SELECT plan_id, tenant_id, shift, status, payload_json, created_at FROM routing_plans;")
        plans = s_cur.fetchall()
        print(f"[INFO] Migrating {len(plans)} routing_plans...")
        for p in plans:
            plan_id = p["plan_id"]
            payload = json.loads(p["payload_json"])

            algo = payload.get("algorithm_used", "held_karp")
            tot_orders = payload.get("total_orders", 0)
            tot_dist = payload.get("total_distance_km", 0.0)
            tot_dur = payload.get("total_duration_minutes", 0.0)
            dispatched_at = payload.get("dispatched_at")

            p_cur.execute("""
                INSERT INTO routing_plans (
                    plan_id, tenant_id, shift, status, algorithm_used,
                    total_orders, total_distance_km, total_duration_minutes,
                    payload_json, created_at, dispatched_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (plan_id) DO NOTHING;
            """, (
                plan_id, p["tenant_id"], p["shift"], p["status"],
                algo, tot_orders, tot_dist, tot_dur,
                json.dumps(payload), p["created_at"], dispatched_at
            ))

            # Partition into driver_routes and route_stops
            driver_routes = payload.get("driver_routes", [])
            for r_idx, dr in enumerate(driver_routes):
                raw_driver_id = dr.get("driver_id")
                try:
                    driver_uuid = str(uuid.UUID(raw_driver_id)) if raw_driver_id else "00000000-0000-0000-0000-000000000003"
                except (ValueError, TypeError):
                    driver_uuid = "00000000-0000-0000-0000-000000000003"

                route_id = str(uuid.uuid4())
                p_cur.execute("""
                    INSERT INTO driver_routes (
                        route_id, plan_id, driver_id, driver_name,
                        total_distance_km, total_duration_minutes, status, created_at
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s);
                """, (
                    route_id, plan_id, driver_uuid, dr.get("driver_name"),
                    dr.get("total_distance_km", 0.0), dr.get("total_duration_minutes", 0.0),
                    "ASSIGNED", p["created_at"]
                ))

                # Route stops
                stops = dr.get("ordered_stops", [])
                for stop in stops:
                    raw_stop_id = stop.get("stop_id", "")
                    try:
                        order_uuid = str(uuid.UUID(raw_stop_id))
                    except (ValueError, TypeError):
                        order_uuid = str(uuid.uuid5(uuid.NAMESPACE_DNS, raw_stop_id))

                    loc = stop.get("location", {})
                    p_cur.execute("""
                        INSERT INTO route_stops (
                            stop_id, route_id, order_id, stop_index,
                            pickup_lat, pickup_lng, pickup_address,
                            delivery_lat, delivery_lng, delivery_address,
                            demand_kg, cumulative_distance_km, estimated_arrival_minutes,
                            status, created_at
                        ) VALUES (
                            gen_random_uuid(), %s, %s, %s,
                            %s, %s, %s,
                            %s, %s, %s,
                            %s, %s, %s,
                            %s, %s
                        ) ON CONFLICT (route_id, stop_index) DO NOTHING;
                    """, (
                        route_id, order_uuid, stop.get("stop_index", 0),
                        10.776889, 106.700806, "Depot",
                        loc.get("lat", 0.0), loc.get("lng", 0.0), loc.get("address"),
                        1.0, stop.get("cumulative_distance_km", 0.0),
                        stop.get("estimated_arrival_minutes", 0.0),
                        "PENDING", p["created_at"]
                    ))

        pg_conn.commit()
        print("[SUCCESS] SQLite to PostgreSQL migration completed successfully!")

    except Exception as e:
        print(f"[ERROR] Migration failed: {e}", file=sys.stderr)
        if pg_conn:
            pg_conn.rollback()
        sys.exit(1)
    finally:
        sqlite_conn.close()
        if pg_conn:
            pg_conn.close()


if __name__ == "__main__":
    migrate_sqlite_to_pg()
