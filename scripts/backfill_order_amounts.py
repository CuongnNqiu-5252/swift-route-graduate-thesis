#!/usr/bin/env python3
"""
scripts/backfill_order_amounts.py
Backfill historical payment amounts from payment_db to order_db with deterministic ranking.
"""

import os
import sys
import psycopg
from psycopg.rows import dict_row

DEFAULT_PAYMENT_URI = "postgresql://openpg:openpgpwd@localhost:5432/payment_db"
DEFAULT_ORDER_URI = "postgresql://openpg:openpgpwd@localhost:5432/order_db"

PAYMENT_DB_URI = os.getenv("PAYMENT_DB_URI", DEFAULT_PAYMENT_URI)
ORDER_DB_URI = os.getenv("ORDER_DB_URI", DEFAULT_ORDER_URI)


def backfill_order_amounts():
    print("[INFO] Starting Historical Payment Backfill Procedure...")
    print(f"  Payment DB: {PAYMENT_DB_URI}")
    print(f"  Order DB:   {ORDER_DB_URI}")

    order_conn = None
    try:
        with psycopg.connect(PAYMENT_DB_URI, row_factory=dict_row) as pay_conn, \
             psycopg.connect(ORDER_DB_URI, row_factory=dict_row) as order_conn:

            pay_cur = pay_conn.cursor()
            order_cur = order_conn.cursor()

            # 1. Extract authoritative payment records partitioned by order_id
            pay_cur.execute("""
                WITH ranked_payments AS (
                    SELECT 
                        order_id,
                        amount,
                        status,
                        mode,
                        gateway,
                        paid_at,
                        created_at,
                        ROW_NUMBER() OVER (
                            PARTITION BY order_id 
                            ORDER BY 
                                CASE 
                                    WHEN status IN ('SUCCESS', 'COLLECTED') THEN 1
                                    WHEN status IN ('PENDING', 'COD_PENDING') THEN 2
                                    WHEN status = 'REFUNDED' THEN 3
                                    WHEN status = 'FAILED' THEN 4
                                    WHEN status = 'CANCELLED' THEN 5
                                    ELSE 6
                                END ASC,
                                COALESCE(paid_at, created_at) DESC,
                                created_at DESC
                        ) AS rank_num
                    FROM payments
                )
                SELECT order_id, amount, status, mode, gateway, paid_at
                FROM ranked_payments
                WHERE rank_num = 1;
            """)

            matched_payments = pay_cur.fetchall()
            print(f"[INFO] Found {len(matched_payments)} authoritative payment records in payment_db.")

            matched_order_ids = set()

            # 2. Update orders with matching payments
            for p in matched_payments:
                oid = str(p['order_id'])
                amount = int(p['amount'])  # Round to integer VND
                matched_order_ids.add(oid)

                mapped_p_status = 'PAID' if p['status'] == 'SUCCESS' else p['status']

                order_cur.execute("""
                    UPDATE orders
                    SET total_amount = %s,
                        payment_status = %s,
                        updated_at = now()
                    WHERE order_id = %s;
                """, (amount, mapped_p_status, oid))
                print(f"  -> Order {oid}: updated total_amount = {amount} VND, payment_status = {mapped_p_status}")

            # 3. Handle legacy orders without payment record
            order_cur.execute("SELECT order_id FROM orders;")
            all_orders = [str(r['order_id']) for r in order_cur.fetchall()]
            unmatched_orders = [oid for oid in all_orders if oid not in matched_order_ids]

            print(f"[INFO] Handling {len(unmatched_orders)} legacy orders without payment records...")
            for oid in unmatched_orders:
                order_cur.execute("""
                    UPDATE orders
                    SET total_amount = 0,
                        notes = COALESCE(notes || ' | ', '') || 'Legacy order migrated without payment record',
                        updated_at = now()
                    WHERE order_id = %s;
                """, (oid,))
                print(f"  -> Legacy Order {oid}: marked total_amount = 0 VND (Audited with migration note)")

            order_conn.commit()
            print("[SUCCESS] Historical payment backfill committed successfully!")

    except Exception as e:
        print(f"[ERROR] Backfill failed: {str(e)}", file=sys.stderr)
        if order_conn:
            order_conn.rollback()
            print("[INFO] Rolled back order_db transaction.")
        sys.exit(1)


if __name__ == "__main__":
    backfill_order_amounts()

