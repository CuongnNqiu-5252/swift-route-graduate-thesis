# Kế Hoạch Chuyển Đổi Dữ Liệu Chi Tiết (Database Migration Plan)

**Giai đoạn:** Phase 2 — Phân Tích & Thiết Kế Kiến Trúc  
**Ngày cập nhật:** 31/10/2026  
**Trạng thái:** HOÀN THIỆN ĐỂ PHÊ DUYỆT CUỐI CÙNG (FINALIZED FOR APPROVAL - REVISION 2)  
**Tiêu chí hàng đầu:** Bảo toàn 100% dữ liệu hiện có (Zero Data Loss), không phá vỡ tính tương thích ngược, phân định rõ ràng thời điểm hoàn tác an toàn (Point of No Return).

---

## 1. Ma Trận Ánh Xạ Chuyển Đổi Dữ Liệu (Migration Mapping Table)

Bảng dưới đây thống kê toàn bộ các bảng hiện có, nguồn dữ liệu hiện tại, đích đến mục tiêu và chiến lược chuyển đổi trên cả **6 cơ sở dữ liệu mục tiêu** (bao gồm **5 CSDL tạo mới** và **1 CSDL giữ nguyên tại chỗ**):

| CSDL Hiện Tại | Bảng Hiện Tại | Số Dòng Thực Tế | CSDL Mục Tiêu | Bảng Mục Tiêu | Chiến Lược Chuyển Đổi & Quy Tắc Dữ Liệu (Strategy) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`postgres`** | `users` | 7 dòng | **`auth_db`** | `users` | Sao chép nguyên vẹn dữ liệu qua lệnh `pg_dump` / `psql`. Giữ nguyên `user_id` (UUID), `password_hash`, `tenant_id` (làm `active_tenant_id`). |
| **`postgres`** | `refresh_token` | 137 dòng | **`auth_db`** | `refresh_token` | Sao chép toàn bộ token hợp lệ và revoked để duy trì phiên đăng nhập không làm gián đoạn người dùng. Giữ nguyên quan hệ FK với `users`. |
| **`postgres`** | `tenant_memberships` | 0 dòng | **`auth_db`** | `tenant_memberships` | Khởi tạo bảng rỗng qua Flyway V1. Tự động chạy script tổng hợp (backfill) từ `tenants.owner_user_id` để tạo quan hệ `OWNER` với trạng thái `ACTIVE`. |
| — | *(Mới)* | 0 dòng | **`auth_db`** | `processed_events` | Khởi tạo bảng Idempotency cho Auth Service. |
| **`postgres`** | `tenants` | 7 dòng | **`order_db`** | `tenants` | Di chuyển toàn bộ 7 tenant (bao gồm tọa độ depot, `optimization_weights`, và `dispatch_config` JSONB). |
| **`postgres`** | `orders` | 18 dòng | **`order_db`** | `orders` | Di chuyển toàn bộ 18 đơn hàng. Bổ sung cột `total_amount NUMERIC(12,0) NOT NULL DEFAULT 0`. Xử lý đối soát số tiền lịch sử qua script chuyên dụng `backfill_order_amounts.py`. Ánh xạ trạng thái chặt chẽ cho `fulfillment_status` và `payment_status` (không dùng fallback mù). |
| — | *(Mới)* | 0 dòng | **`order_db`** | `order_items` | Khởi tạo bảng `order_items` trong baseline V1 của `order_db`. **Không bịa đặt dữ liệu sản phẩm lịch sử (Zero synthetic backfill)** vì nguồn cũ chỉ lưu cấp độ kiện hàng. 18 đơn cũ giữ 0 items, hoàn toàn hợp lệ. |
| — | *(Mới)* | 0 dòng | **`order_db`** | `order_status_history` | Khởi tạo bảng Audit Trail trạng thái đơn hàng. |
| **`postgres`** | `outbox_events` | 84 dòng | **`order_db`** | `outbox_events` | Toàn bộ 84 bản ghi hiện tại đều thuộc Order Service (`aggregate_type` là `ORDER` hoặc `TENANT`), chuyển trọn vẹn sang `order_db`. |
| — | *(Mới)* | 0 dòng | **`order_db`** | `processed_events` | Khởi tạo bảng Idempotency cho Order Service. |
| **`postgres`** | `drivers` | 3 dòng | **`driver_db`** | `drivers` | Di chuyển nguyên vẹn 3 hồ sơ tài xế (`driver_id`, `license_plate`, `phone`, `tenant_id`). Duy trì ràng buộc `tenant_id NOT NULL`. |
| **`postgres`** | `driver_earning` | 0 dòng | **`driver_db`** | `driver_earning` | Khởi tạo bảng cấu trúc chuẩn qua Flyway V1 của Driver Service. |
| **`postgres`** | `driver_rating` | 0 dòng | **`driver_db`** | `driver_rating` | Khởi tạo bảng cấu trúc chuẩn qua Flyway V1 của Driver Service. |
| — | *(Mới)* | 0 dòng | **`driver_db`** | `outbox_events` | Khởi tạo bảng Outbox riêng cho Driver Service để loại bỏ hoàn toàn tranh chấp lock với Order Service. |
| — | *(Mới)* | 0 dòng | **`driver_db`** | `processed_events` | Khởi tạo bảng Idempotency cho Driver Service. |
| **`payment_db`**| `payments` | 3 dòng | **`payment_db`** | `payments` | **Giữ nguyên tại chỗ (In-place Retention)**. Chạy Flyway V2 để mở rộng check constraint hỗ trợ COD (`COD_PENDING`, `COLLECTED`, `PARTIALLY_COLLECTED`), đặt `tenant_id NOT NULL`, `expires_at NULL` cho COD. |
| **`payment_db`**| `payment_webhook_logs` | 0 dòng | **`payment_db`** | `payment_webhook_logs` | Giữ nguyên tại chỗ. |
| — | *(Mới)* | 0 dòng | **`payment_db`** | `outbox_events` | Khởi tạo bảng Outbox riêng cho Payment Service qua Flyway V2 để xuất bản `payment.completed`. |
| — | *(Mới)* | 0 dòng | **`payment_db`** | `processed_events` | Khởi tạo bảng Idempotency cho Payment Service qua Flyway V2. |
| **`routing_engine.db`** (SQLite)| `pending_orders` | 8 dòng | **`routing_db`** | `pending_orders` | Trích xuất từ SQLite qua script Python và nạp vào PostgreSQL `routing_db`. Giữ nguyên snapshot tọa độ và trọng số. |
| **`routing_engine.db`** (SQLite)| `routing_plans` | 2 dòng | **`routing_db`** | `routing_plans` | Trích xuất từ SQLite, nạp `payload_json` bất biến vào PostgreSQL. Tự động bóc tách thành `driver_routes` và `route_stops`. |
| — | *(Mới)* | — | **`routing_db`** | `driver_routes` | Tự động sinh từ phân tích cấu trúc 2 kế hoạch cũ trong `routing_plans`. |
| — | *(Mới)* | — | **`routing_db`** | `route_stops` | Tự động sinh từ danh sách điểm dừng của 2 kế hoạch cũ. Là bảng hoạt động chính thức (Authoritative). |
| **`routing_engine.db`** (SQLite)| `processed_events`| 8 dòng | **`routing_db`** | `processed_events` | Di chuyển danh sách event IDs đã xử lý để tránh nhận lại các event cũ từ RabbitMQ. |
| — | *(Mới)* | 0 dòng | **`notification_db`** | `notification_preferences`, `device_tokens`, `notification_deliveries`, `processed_events` | Khởi tạo cấu trúc schema cơ sở (Baseline Schema) để sẵn sàng hạ tầng cho Notification Service. |

---

## 2. Chiến Lược Đánh Phiên Bản Flyway Migration (Flyway Versioning Strategy)

Quy tắc đánh phiên bản được chuẩn hóa cho toàn bộ 6 cơ sở dữ liệu:

```
auth_db        (Mới 1/5) ──► V1__init_auth_schema.sql (users, refresh_token, tenant_memberships, processed)
order_db       (Mới 2/5) ──► V1__init_order_schema.sql (tenants, orders, order_items, order_status_history, outbox, processed)
driver_db      (Mới 3/5) ──► V1__init_driver_schema.sql (drivers, driver_earning, driver_rating, outbox, processed)
routing_db     (Mới 4/5) ──► 001_create_routing_schema.sql (jobs, plans, driver_routes, route_stops, pending, processed)
notification_db(Mới 5/5) ──► V1__init_notification_schema.sql (preferences, device_tokens, deliveries, processed)
payment_db     (Giữ cũ)  ──► Giữ nguyên V1 cũ ──► V2__add_cod_and_outbox.sql (enum COD, tenant_id, outbox, processed)
```

1. **Đối với 5 CSDL tạo mới hoàn toàn:** Mỗi CSDL được khởi tạo với một file baseline `V1` duy nhất chứa đầy đủ cấu trúc bảng, chỉ mục và ràng buộc chuẩn hóa. CSDL `order_db` tạo trực tiếp `tenants`, `orders` (có cột `total_amount`), và `order_items` với khóa ngoại vật lý nội bộ `order_id REFERENCES orders(order_id) ON DELETE CASCADE`.
2. **Đối với `payment_db` giữ nguyên tại chỗ:** Giữ nguyên lịch sử `flyway_schema_history` và file `V1__init_payment_schema.sql`. Bổ sung file migration mới **`V2__add_cod_and_outbox.sql`**:
   - Mở rộng ràng buộc `CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED', 'COD_PENDING', 'COLLECTED', 'PARTIALLY_COLLECTED'))`.
   - Cập nhật ràng buộc `payments.tenant_id SET NOT NULL`.
   - Chuyển `payments.expires_at` thành `NULL` đối với phương thức `COD`.
   - Tạo bảng `outbox_events` và `processed_events`.

---

## 3. Quy Trình Nạp Dữ Liệu Thanh Toán Lịch Sử (Historical Payment Backfill Procedure)

### 3.1. Hiện trạng và Thách thức
- Bảng `postgres.orders` có 18 đơn hàng lịch sử.
- Bảng `payment_db.payments` có 3 bản ghi thanh toán online. 15 đơn hàng còn lại là các đơn thử nghiệm tạo trong giai đoạn đầu trước khi tích hợp cổng thanh toán.
- Do `payment_db` và `order_db` là hai CSDL tách biệt, không thể dùng câu lệnh `UPDATE ... FROM` trực tiếp qua SQL chuẩn mà không thiết lập dblink hoặc foreign data wrapper.

### 3.2. Script Python Chuyển Đổi Xuyên CSDL (`scripts/backfill_order_amounts.py`)

Quy trình nạp dữ liệu được thực thi thông qua kịch bản Python độc lập, đảm bảo tính tất định (Deterministic), ranh giới giao dịch rõ ràng (Transaction Boundary) và xử lý ngoại lệ an toàn:

```python
#!/usr/bin/env python3
"""
scripts/backfill_order_amounts.py
Backfill historical payment amounts from payment_db to order_db with deterministic ranking.
"""

import sys
import psycopg
from psycopg.rows import dict_row

PAYMENT_DB_URI = "postgresql://postgres:postgres@localhost:5433/payment_db"
ORDER_DB_URI = "postgresql://postgres:postgres@localhost:5433/order_db"

def backfill_order_amounts():
    print("[INFO] Starting Historical Payment Backfill Procedure...")
    
    try:
        with psycopg.connect(PAYMENT_DB_URI, row_factory=dict_row) as pay_conn, \
             psycopg.connect(ORDER_DB_URI, row_factory=dict_row) as order_conn:
            
            pay_cur = pay_conn.cursor()
            order_cur = order_conn.cursor()
            
            # 1. Trích xuất bản ghi thanh toán có thẩm quyền cao nhất theo thứ tự ưu tiên
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
            
            # 2. Cập nhật các đơn hàng có bản ghi thanh toán tương ứng trong order_db
            for p in matched_payments:
                oid = str(p['order_id'])
                amount = int(p['amount']) # Làm tròn integer VND
                matched_order_ids.add(oid)
                
                # Ánh xạ trạng thái payment_status tương ứng
                mapped_p_status = 'PAID' if p['status'] == 'SUCCESS' else p['status']
                
                order_cur.execute("""
                    UPDATE orders
                    SET total_amount = %s,
                        payment_status = %s,
                        updated_at = now()
                    WHERE order_id = %s;
                """, (amount, mapped_p_status, oid))
                print(f"  -> Order {oid}: updated total_amount = {amount} VND, payment_status = {mapped_p_status}")
            
            # 3. Xử lý các đơn hàng lịch sử không có bản ghi thanh toán (15 đơn test cũ)
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
            
            # Commit transaction trên order_db
            order_conn.commit()
            print("[SUCCESS] Historical payment backfill committed successfully!")
            
    except Exception as e:
        print(f"[ERROR] Backfill failed: {str(e)}", file=sys.stderr)
        if 'order_conn' in locals() and order_conn:
            order_conn.rollback()
            print("[INFO] Rolled back order_db transaction.")
        sys.exit(1)

if __name__ == "__main__":
    backfill_order_amounts()
```

### 3.3. Câu Lệnh Xác Thực Sau Khi Backfill (Verification Query)
Sau khi chạy script, kiểm tra chéo độ khớp số tiền giữa `order_db` và `payment_db`:

```sql
-- Chạy trên order_db: Kiểm tra không còn đơn nào có total_amount là NULL
SELECT count(*) AS null_amount_orders FROM orders WHERE total_amount IS NULL;
-- Kỳ vọng: 0

-- Kiểm tra 3 đơn có thanh toán trong payment_db đã nhận đúng số tiền
SELECT order_id, total_amount, payment_status, status 
FROM orders 
WHERE total_amount > 0;
-- Kỳ vọng: Đúng 3 dòng tương ứng với 3 bản ghi trong payment_db.payments
```

---

## 4. Ánh Xạ Trạng Thái Đơn Hàng Cũ Tường Minh (Explicit Legacy Order Status Mapping)

Để đảm bảo các đơn hàng chưa thanh toán hoặc thất bại **không bao giờ bị lọt vào hàng đợi gom chuyến**, câu lệnh ánh xạ SQL xử lý tường minh 100% các trạng thái legacy:

### 4.1. Câu Lệnh Cập Nhật Trạng Thái An Toàn (Fail-Fast SQL Update)

```sql
-- Cập nhật đồng bộ fulfillment_status và payment_status trong order_db
UPDATE orders SET
    fulfillment_status = CASE
        WHEN status = 'PENDING_PAYMENT' THEN 'DRAFT'
        WHEN status = 'PAYMENT_FAILED'  THEN 'CANCELLED'
        WHEN status = 'PAID'            THEN 'READY_FOR_PLANNING'
        WHEN status = 'PLANNED'         THEN 'PLANNED'
        WHEN status = 'IN_TRANSIT'      THEN 'IN_TRANSIT'
        WHEN status = 'DELIVERED'       THEN 'DELIVERED'
        WHEN status = 'FAILED'          THEN 'FAILED'
        WHEN status = 'CANCELLED'       THEN 'CANCELLED'
        ELSE NULL -- BẮT BUỘC ĐỂ NULL ĐỂ PHÁT HIỆN TRẠNG THÁI LẠ, KHÔNG DÙNG BROAD DEFAULT
    END,
    payment_status = CASE
        WHEN status = 'PENDING_PAYMENT' THEN 'PENDING'
        WHEN status = 'PAYMENT_FAILED'  THEN 'FAILED'
        WHEN status = 'PAID'            THEN 'PAID'
        WHEN status = 'PLANNED'         THEN 'PAID'
        WHEN status = 'IN_TRANSIT'      THEN 'PAID'
        WHEN status = 'DELIVERED'       THEN 'PAID'
        WHEN status = 'FAILED'          THEN 'FAILED'
        WHEN status = 'CANCELLED'       THEN 'CANCELLED'
        ELSE NULL -- BẮT BUỘC ĐỂ NULL ĐỂ PHÁT HIỆN TRẠNG THÁI LẠ, KHÔNG DÙNG BROAD DEFAULT
    END;

-- ASSERTION CHECK: Ngắt ngay lập tức nếu xuất hiện trạng thái chưa được định nghĩa
DO $$
DECLARE
    v_unmapped_count INT;
BEGIN
    SELECT COUNT(*) INTO v_unmapped_count 
    FROM orders 
    WHERE fulfillment_status IS NULL OR payment_status IS NULL;

    IF v_unmapped_count > 0 THEN
        RAISE EXCEPTION 'MIGRATION ABORTED: Phát hiện % đơn hàng có trạng thái legacy không hợp lệ!', v_unmapped_count;
    END IF;
END $$;
```

### 4.2. Câu Lệnh Kiểm Tra Tổ Hợp Trạng Thái Bất Hợp Lệ (Invalid Status Combinations Check)

```sql
-- 1. Đơn chưa thanh toán hoặc thanh toán thất bại nhưng lại được xếp lịch/giao hàng
SELECT order_id, status, fulfillment_status, payment_status 
FROM orders 
WHERE fulfillment_status IN ('READY_FOR_PLANNING', 'PLANNED', 'IN_TRANSIT') 
  AND payment_status NOT IN ('PAID', 'COLLECTED', 'COD_PENDING');
-- Kỳ vọng: 0 dòng

-- 2. Đơn đã hủy nhưng giao vận vẫn tiếp diễn
SELECT order_id, status, fulfillment_status, payment_status 
FROM orders 
WHERE fulfillment_status IN ('READY_FOR_PLANNING', 'PLANNED', 'IN_TRANSIT', 'DELIVERED') 
  AND payment_status IN ('FAILED', 'CANCELLED');
-- Kỳ vọng: 0 dòng

-- 3. Đơn DRAFT nhưng lại đánh dấu đã thanh toán
SELECT order_id, status, fulfillment_status, payment_status 
FROM orders 
WHERE fulfillment_status = 'DRAFT' 
  AND payment_status = 'PAID';
-- Kỳ vọng: 0 dòng
```

---

## 5. Quy Trình Chuyển Đổi Hệ Thống An Toàn (Cutover Procedure)

Quá trình chuyển đổi dữ liệu được thực thi trong một khung giờ bảo trì có kiểm soát (**Maintenance Window 15 - 30 phút**).

```
[1. Kích hoạt Khóa Ghi (Write Freeze)] ──► [2. Dừng Outbox & Consumers] ──► [3. Xả Hàng Đợi RabbitMQ]
                                                                                       │
[6. Khởi tạo 5 CSDL Mới & Schema] ◄── [5. Sao lưu Nguội & Test Restore] ◄── [4. Đợi Txn Hoàn Tất]
                  │
[7. Nạp Dữ Liệu & Chạy Backfill] ──► [8. Đổi Kết Nối & Restart] ──► [9. Chạy Validation & Smoke Test]
                                                                                       │
                                                        [10. ĐIỂM PONR: Mở Cổng Ghi (Unfreeze)]
```

### Bước 5.1: Danh Mục Kiểm Tra Chuẩn Bị Trước Chuyển Đổi (Pre-Cutover Checklist)
- [ ] Dừng toàn bộ các tiến trình Outbox Publisher trên `order-service`, `driver-service`, `payment-service`.
- [ ] Tạm dừng toàn bộ các Consumer lắng nghe RabbitMQ trên các service.
- [ ] Kiểm tra không còn giao dịch cơ sở dữ liệu nào đang treo dở dang:
  ```sql
  SELECT pid, usename, client_addr, state, query 
  FROM pg_stat_activity 
  WHERE state = 'active' AND query NOT LIKE '%pg_stat_activity%';
  ```
- [ ] Xả sạch các message còn đọng trên RabbitMQ (Drain RabbitMQ Backlog).
- [ ] Kích hoạt chế độ bảo trì (Write Freeze) trên API Gateway: Trả về HTTP 503 cho toàn bộ các phương thức ghi (`POST`, `PUT`, `DELETE`, `PATCH`).

### Bước 5.2: Tạo Bản Sao Lưu Nguội & Kiểm Tra Phục Hồi Thử Nghiệm (Cold Backup & Test Restore)

> [!WARNING]
> **Khắc phục bẫy mã hóa trên Windows PowerShell:** Tuyệt đối không dùng toán tử pipe `| Out-File` trong PowerShell để hứng kết quả của `pg_dumpall`, vì PowerShell sẽ tự ý encode file sang định dạng UTF-16LE có BOM làm hỏng tệp sao lưu. Phải dùng tham số `-f` bên trong container rồi sao chép nhị phân ra máy host qua `docker cp`.

**Lệnh thực thi trên Windows PowerShell:**
```powershell
# 1. Tạo thư mục sao lưu
New-Item -ItemType Directory -Force -Path ".\backup\pre_migration"

# 2. Dump nhị phân an toàn từ bên trong container
docker exec swiftroute-postgres pg_dumpall -U postgres -f /tmp/postgres_backup.sql
docker cp swiftroute-postgres:/tmp/postgres_backup.sql .\backup\pre_migration\postgres_backup.sql
docker exec swiftroute-postgres rm /tmp/postgres_backup.sql

# 3. Sao lưu file SQLite của Routing Engine
Copy-Item ".\services\routing-engine-service\data\routing_engine.db" -Destination ".\backup\pre_migration\routing_engine_backup.db"

# 4. Kiểm tra kích thước file sao lưu hợp lệ (Không được bằng 0 byte)
Get-Item .\backup\pre_migration\* | Select-Object Name, Length, LastWriteTime
```

**Lệnh thực thi trên Linux / Docker Bash:**
```bash
mkdir -p ./backup/pre_migration
docker exec swiftroute-postgres pg_dumpall -U postgres -f /tmp/postgres_backup.sql
docker cp swiftroute-postgres:/tmp/postgres_backup.sql ./backup/pre_migration/postgres_backup.sql
docker exec swiftroute-postgres rm /tmp/postgres_backup.sql
cp ./services/routing-engine-service/data/routing_engine.db ./backup/pre_migration/routing_engine_backup.db
```

### Bước 5.3: Khởi Tạo 5 Cơ Sở Dữ Liệu Mới Trong PostgreSQL Container
Thực thi lệnh SQL tạo **5 CSDL mới** (CSDL `payment_db` giữ nguyên tại chỗ):

```sql
CREATE DATABASE auth_db WITH OWNER postgres ENCODING 'UTF8';
CREATE DATABASE order_db WITH OWNER postgres ENCODING 'UTF8';
CREATE DATABASE driver_db WITH OWNER postgres ENCODING 'UTF8';
CREATE DATABASE routing_db WITH OWNER postgres ENCODING 'UTF8';
CREATE DATABASE notification_db WITH OWNER postgres ENCODING 'UTF8';
```

### Bước 5.4: Nạp Dữ Liệu và Thực Thi Scripts Chuyển Đổi

**Thực thi trên Windows PowerShell:**
```powershell
# 1. Chuyển dữ liệu sang auth_db
docker exec -i swiftroute-postgres pg_dump -U postgres -d postgres --data-only -t users -t refresh_token `
    | docker exec -i swiftroute-postgres psql -U postgres -d auth_db

# Backfill quyền OWNER cho bảng tenant_memberships trong auth_db
docker exec -i swiftroute-postgres psql -U postgres -d auth_db -c "
    INSERT INTO tenant_memberships (membership_id, tenant_id, user_id, membership_role, status, created_at, updated_at)
    SELECT gen_random_uuid(), u.tenant_id, u.user_id, 'OWNER', 'ACTIVE', u.created_at, now()
    FROM users u
    WHERE u.tenant_id IS NOT NULL AND u.role IN ('TENANT_OWNER', 'ADMIN')
    ON CONFLICT (tenant_id, user_id) DO NOTHING;
"

# 2. Chuyển dữ liệu sang order_db
docker exec -i swiftroute-postgres pg_dump -U postgres -d postgres --data-only -t tenants -t orders -t outbox_events `
    | docker exec -i swiftroute-postgres psql -U postgres -d order_db

# Ánh xạ trạng thái chuẩn trong order_db
docker exec -i swiftroute-postgres psql -U postgres -d order_db -c "
    UPDATE orders SET
        fulfillment_status = CASE
            WHEN status = 'PENDING_PAYMENT' THEN 'DRAFT'
            WHEN status = 'PAYMENT_FAILED'  THEN 'CANCELLED'
            WHEN status = 'PAID'            THEN 'READY_FOR_PLANNING'
            WHEN status = 'PLANNED'         THEN 'PLANNED'
            WHEN status = 'IN_TRANSIT'      THEN 'IN_TRANSIT'
            WHEN status = 'DELIVERED'       THEN 'DELIVERED'
            WHEN status = 'FAILED'          THEN 'FAILED'
            WHEN status = 'CANCELLED'       THEN 'CANCELLED'
            ELSE NULL
        END,
        payment_status = CASE
            WHEN status = 'PENDING_PAYMENT' THEN 'PENDING'
            WHEN status = 'PAYMENT_FAILED'  THEN 'FAILED'
            WHEN status = 'PAID'            THEN 'PAID'
            WHEN status = 'PLANNED'         THEN 'PAID'
            WHEN status = 'IN_TRANSIT'      THEN 'PAID'
            WHEN status = 'DELIVERED'       THEN 'PAID'
            WHEN status = 'FAILED'          THEN 'FAILED'
            WHEN status = 'CANCELLED'       THEN 'CANCELLED'
            ELSE NULL
        END;
"

# 3. Nạp tiền đơn hàng lịch sử từ payment_db sang order_db bằng script Python
python scripts/backfill_order_amounts.py

# 4. Chuyển dữ liệu sang driver_db
docker exec -i swiftroute-postgres pg_dump -U postgres -d postgres --data-only -t drivers -t driver_earning -t driver_rating `
    | docker exec -i swiftroute-postgres psql -U postgres -d driver_db

# 5. Chạy script chuyển đổi Routing Engine từ SQLite sang routing_db
python scripts/migrate_sqlite_to_pg.py
```

### Bước 5.5: Cập Nhật Cấu Hình Kết Nối & Khởi Động Lại Services
Cập nhật biến môi trường `SPRING_DATASOURCE_URL` và `ROUTING_DATABASE_URL` trong `docker-compose.yml`. Khởi động lại container theo đúng thứ tự phụ thuộc topo:
1. `swiftroute-postgres`
2. `swiftroute-rabbitmq` & `swiftroute-redis`
3. `swiftroute-auth-service`
4. `swiftroute-driver-service`
5. `swiftroute-order-service`
6. `swiftroute-payment-service`
7. `swiftroute-routing-engine`
8. `swiftroute-api-gateway`

---

## 6. Các Câu Lệnh Kiểm Thử Tính Nhất Quán Dữ Liệu (Data Validation Queries)

Sau khi chuyển đổi, chạy bộ truy vấn đối soát dữ liệu:

```sql
-- ============================================================================
-- 1. KIỂM TRA ĐỐI SOÁT SỐ LƯỢNG DÒNG (ROW COUNT PARITY)
-- ============================================================================
\c auth_db
SELECT 'users' AS table_name, count(*) AS count FROM users
UNION ALL
SELECT 'refresh_token', count(*) FROM refresh_token
UNION ALL
SELECT 'tenant_memberships', count(*) FROM tenant_memberships;
-- Kỳ vọng: users = 7, refresh_token = 137, tenant_memberships >= 1

\c order_db
SELECT 'tenants' AS table_name, count(*) AS count FROM tenants
UNION ALL
SELECT 'orders', count(*) FROM orders
UNION ALL
SELECT 'order_items', count(*) FROM order_items
UNION ALL
SELECT 'outbox_events', count(*) FROM outbox_events;
-- Kỳ vọng: tenants = 7, orders = 18, order_items = 0, outbox_events = 84

\c driver_db
SELECT 'drivers' AS table_name, count(*) AS count FROM drivers;
-- Kỳ vọng: drivers = 3

\c payment_db
SELECT 'payments' AS table_name, count(*) AS count FROM payments;
-- Kỳ vọng: payments = 3

\c routing_db
SELECT 'pending_orders' AS table_name, count(*) AS count FROM pending_orders
UNION ALL
SELECT 'routing_plans', count(*) FROM routing_plans
UNION ALL
SELECT 'processed_events', count(*) FROM processed_events;
-- Kỳ vọng: pending_orders = 8, routing_plans = 2, processed_events = 8

-- ============================================================================
-- 2. KIỂM TRA TÍNH TOÀN VẸN VÀ RÀNG BUỘC NGHIỆP VỤ (INTEGRITY CHECKS)
-- ============================================================================
-- Kiểm tra token mồ côi trong auth_db
\c auth_db
SELECT count(*) AS orphan_tokens FROM refresh_token rt 
LEFT JOIN users u ON rt.user_id = u.user_id WHERE u.user_id IS NULL;
-- Kỳ vọng: 0

-- Kiểm tra order_items mồ côi trong order_db
\c order_db
SELECT count(*) AS orphan_items FROM order_items oi 
LEFT JOIN orders o ON oi.order_id = o.order_id WHERE o.order_id IS NULL;
-- Kỳ vọng: 0

-- Kiểm tra không có đơn hàng nào bị âm tiền
\c order_db
SELECT count(*) AS negative_total_count FROM orders WHERE total_amount < 0;
-- Kỳ vọng: 0

-- Kiểm tra điểm dừng mồ côi và trùng lặp index trong routing_db
\c routing_db
SELECT count(*) AS orphan_stops FROM route_stops s 
LEFT JOIN driver_routes r ON s.route_id = r.route_id WHERE r.route_id IS NULL;
-- Kỳ vọng: 0

SELECT route_id, stop_index, count(*) 
FROM route_stops 
GROUP BY route_id, stop_index 
HAVING count(*) > 1;
-- Kỳ vọng: 0
```

---

## 7. Chiến Lược Hoàn Tác Thực Tế & Điểm Không Thể Đảo Ngược (Realistic Rollback Plan & PONR)

> [!WARNING]
> **Bác bỏ giả định "Hoàn tác dưới 60 giây không mất dữ liệu bất kỳ lúc nào":**
> Thao tác hoàn tác nhanh bằng cách trỏ lại cấu hình về CSDL cũ `postgres` **chỉ an toàn tuyệt đối khi hệ thống vẫn đang trong Khung giờ Bảo trì (Write Freeze) TRƯỚC ĐIỂM PONR**. Một khi API Gateway đã mở khóa (Unfreeze) và tiếp nhận các đơn hàng hoặc giao dịch mới từ người dùng trên các CSDL mục tiêu, việc khôi phục lại database cũ chắc chắn sẽ xóa sạch toàn bộ các đơn hàng mới đó.

```
[Bắt đầu Cutover] ──► [Khóa ghi] ──► [Di chuyển dữ liệu] ──► [Kiểm thử] ──► [ĐIỂM PONR: Mở cổng Unfreeze] ──► [Vận hành mới]
         │                                              │                                 │
         └───────────── Giai đoạn 1: Rollback An toàn ──┘                                 └──── Giai đoạn 2: Sửa tiến (Forward Fix)
                        (Khôi phục lại trong < 60s)                                            (Không thể Rollback về CSDL cũ)
```

### 7.1. Giai đoạn 1: Sự cố xảy ra trước khi Mở cổng Unfreeze (Trước Điểm PONR)
- **Điều kiện kích hoạt:** Phát hiện lỗi nghiêm trọng trong quá trình nạp dữ liệu, kiểm tra row count không khớp, hoặc Smoke Test thất bại.
- **Thao tác hoàn tác (Fast Rollback < 60 giây):**
  1. Giữ nguyên trạng thái Khóa ghi (Write Freeze).
  2. Đổi lại chuỗi kết nối datasource của các service trong `docker-compose.yml` trỏ về database cũ: `jdbc:postgresql://postgres:5432/postgres`.
  3. Đổi cấu hình `ROUTING_DATABASE_URL` trỏ lại file SQLite `data/routing_engine.db`.
  4. Chạy lệnh: `docker compose up -d` để khởi động lại các service với database cũ.
  5. Mở khóa API Gateway.
- **Kết quả:** Hệ thống quay về trạng thái hoạt động cũ bình thường ngay lập tức, **bảo toàn 100% dữ liệu, zero data loss**.

### 7.2. Giai đoạn 2: Sự cố xảy ra sau khi đã Mở cổng Unfreeze (Sau Điểm PONR)
- **Điều kiện:** Hệ thống đã tiếp nhận các đơn hàng, giao dịch thanh toán hoặc vị trí tài xế mới trên `order_db` và `payment_db`.
- **Nguyên tắc xử lý:** **Tuyệt đối không khôi phục (restore) đè database `postgres` cũ.**
- **Phương án giải quyết:**
  - Áp dụng chiến lược **Sửa tiến (Forward Fix / Hotfix Patch)**: Xác định lỗi mã nguồn hoặc lỗi cấu hình và triển khai bản vá nóng trực tiếp trên service tương ứng.
  - Trong trường hợp lỗi quá nghiêm trọng buộc phải dừng CSDL mới: Tạm dừng Gateway, viết script đối soát trích xuất các đơn hàng và thanh toán mới phát sinh từ `order_db` gộp ngược lại vào `postgres` trước khi chuyển hướng kết nối.
