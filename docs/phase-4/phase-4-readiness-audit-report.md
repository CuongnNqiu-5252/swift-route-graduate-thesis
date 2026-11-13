# SwiftRoute Phase 4 — Business Operations & Map Operations
## T4.0: Repository Readiness Audit Report

> **Auditor**: Senior Software Architect & Full-stack Engineer
> **Date**: 2026-11-01
> **Status**: Completed (READ-ONLY Verification)
> **Baseline**: Phase 3 Hardened Microservices Architecture
>
> **Scope update (2026-11-13):** Product Catalog, product master pricing,
> coupons and promotions were removed from the SwiftRoute delivery-operations
> scope. Orders keep package descriptions and financial snapshots without a SKU
> dependency. Historical Flyway migration `V15__drop_obsolete_products_and_product_categories.sql`
> is retained for schema-history integrity.

---

## 1. Executive Summary

This report delivers the results of the **T4.0 Repository Audit** for SwiftRoute Phase 4 (*Business Operations & Map Operations*). The audit strictly operated under **READ-ONLY** mode—inspecting database schemas, Flyway migrations, API Gateway configurations, backend service codebases, Python routing algorithms, mobile Flutter implementations, and Next.js Web Admin pages without modifying any source code or database states.

### Key Audit Conclusions:
1. **Significant Existing Capabilities Reusable**: The Python `routing-engine-service` already possesses an advanced automated dispatch engine (`auto_dispatcher.py`), route planning store (`plan_store.py`), and REST endpoints (`/plans`, `/plans/optimize-shift`, `/plans/{plan_id}/dispatch`). There is **no need** for a new Dispatch microservice or rewriting route dispatch algorithms.
2. **False Assumptions Corrected**: `driver-service` currently has **no Redis Geo** or heartbeat logic (locations are saved directly into PostgreSQL table `drivers`). Real-time tracking with Redis Geo and Redis TTL is a **new capability** to be built.
3. **Critical Domain Separation**: `orders.customer_id` historically pointed to login users (`auth_db.users`). Phase 4 introduces tenant-scoped CRM customers (`order_db.customers`), requiring an explicit backward-compatible dual-support migration strategy.
4. **Architectural Alignment Needed**: Web Admin currently calls `routing-engine:8084` directly and hardcodes depot coordinates. Both must be rerouted through the API Gateway (port 8000) and bound dynamically to `order_db.tenants` warehouse coordinates.

---

## 2. Audit Findings for the 12 Mandatory Inspection Items

| # | Inspection Item | Current Classification | Key Finding & Architectural Impact |
|---|---|---|---|
| **1** | All existing uses of `orders.customer_id` | **Requires Migration & Conflicting** | Currently `customer_id UUID NOT NULL` in `order_db.orders` is extracted directly from `X-User-Id` in `OrderController.java`. It represents the caller's Auth account ID. In Phase 4, tenant operators need to create orders for tenant CRM customers who may not have login accounts. |
| **2** | Historical customer IDs referencing Auth users | **Requires Migration** | Historical demo and test scripts (`seed_demo_orders.sql`, `seed_demo_full.sql`) populated `orders.customer_id` with `00000000-0000-0000-0000-000000000002`, which is `customer@gmail.com` in `auth_db.users`. These IDs represent platform login accounts. |
| **3** | Existing SQLite tables and `plan_store.py` methods | **Existing & Reusable (Migrate Storage)** | `plan_store.py` defines `routing_plans` table in SQLite (`data/routing_engine.db`) and provides 4 thread-safe methods: `save_plan`, `get_plan`, `list_plans`, `update_plan_status`. Migrating this to PostgreSQL `routing_db` is clean and isolated. |
| **4** | Existing `auto_dispatcher.py` workflow | **Existing & Reusable** | Implements deterministic batching thresholds, wait-time triggers, urgent-order instant dispatch, driver availability filtering, and respects tenant `require_approval` flag (saving plans in `DRAFT` status). |
| **5** | Existing routing API contracts | **Existing & Reusable** | Complete Pydantic schemas in `api/schemas.py`: `BatchOptimizationRequest/Response`, `ShiftOptimizationRequest`, `RoutingPlan`, `DispatchPlanResponse`, `PendingOrder`. Completely compatible with Phase 4 needs. |
| **6** | Existing driver GPS persistence | **Existing but Incomplete** | `driver-service` updates `current_lat` and `current_lng` directly in PostgreSQL `drivers` table via `driverRepository.save(driver)`. No location history table (`driver_location_history`) exists yet. |
| **7** | Redis configured in Driver Service | **Missing** | `driver-service/pom.xml` lacks `spring-boot-starter-data-redis` and `application.yml` has no Redis configuration. Redis Geo and heartbeat must be introduced as new infrastructure. |
| **8** | Existing frontend map components | **Existing & Reusable** | `web-admin` has `route-map.tsx` and `route-map-inner.tsx` built with `react-leaflet` and OpenStreetMap. Already renders warehouse depots, numbered order stops, and polyline routes. |
| **9** | Existing Goong Mobile App integration | **Existing & Reusable** | `mobile-app` has `GoongConfig` and `GoongMapWidget` (`maplibre_gl` with vector tiles and radar puck driver markers). Works reliably for Vietnam navigation. |
| **10** | Existing tenant warehouse API | **Existing but Incomplete** | `order_db.tenants` has `pickup_lat`, `pickup_lng`, `address`. `TenantController` supports CRUD by ID, but is **missing `GET /api/tenants/me`** to allow Web Admin to automatically load the warehouse depot of the logged-in tenant. |
| **11** | Direct Web Admin calls to Routing Engine | **Conflicting / Requires Reroute** | `web-admin/src/infrastructure/repositories/routing.repository.ts` calls `http://localhost:8084` directly with raw Axios, bypassing API Gateway security and JWT tenant validation. |
| **12** | Existing API Gateway routing configuration | **Existing & Reusable** | API Gateway already configures `/api/routing/**` -> `http://routing-engine:8084` with `StripPrefix=2`. Requires Bearer JWT token and injects `X-Tenant-Id`. |

---

## 3. Deep-Dive Gap Analysis & Technical Strategy

### 3.1 Customer Domain Distinction (T4.2)
* **The Problem**: Previously, `orders.customer_id` recorded the `user_id` of the caller from `X-User-Id`. When a merchant operator created an order in Web Admin, `customer_id` mistakenly received the operator's ID.
* **The Solution**:
  1. Create Flyway migration in `order-service` for `customers` and `customer_addresses` scoped by `tenant_id`.
  2. For historical orders where `customer_id` points to a legacy Auth user:
     * Keep existing records intact.
     * Backfill a default CRM customer in `customers` linked via `auth_user_id = legacy_customer_id` for each tenant.
  3. In `CreateOrderRequest`, add optional `customerId` (select existing CRM customer) or inline customer details (`recipientName`, `recipientPhone`, `deliveryAddress`). If inline details are provided, `OrderService` automatically creates or resolves the tenant's CRM customer record.

---

### 3.3 Routing Plan Storage & Dispatch Alignment (T4.6 & T4.7)
* **The Problem**: `routing-engine-service` holds all the route optimization and auto-dispatch logic, but stores plans in a local SQLite file (`data/routing_engine.db`). Meanwhile, Docker Compose already provides PostgreSQL `routing_db`.
* **The Solution**:
  1. Upgrade `plan_store.py` to use `psycopg` / `asyncpg` or SQLAlchemy with `ROUTING_DATABASE_URL` (falling back to SQLite for local lightweight unit tests).
  2. Table structure in PostgreSQL `routing_db`:
     ```sql
     CREATE TABLE IF NOT EXISTS routing_plans (
         plan_id UUID PRIMARY KEY,
         tenant_id UUID NOT NULL,
         shift VARCHAR(20) NOT NULL,
         status VARCHAR(20) NOT NULL, -- DRAFT, APPROVED, DISPATCHED, REJECTED
         algorithm_used VARCHAR(50) NOT NULL,
         total_orders INT NOT NULL,
         total_distance_km NUMERIC(10, 2) NOT NULL,
         total_duration_minutes NUMERIC(10, 2) NOT NULL,
         payload_json JSONB NOT NULL,
         approved_by UUID,
         approved_at TIMESTAMPTZ,
         dispatched_at TIMESTAMPTZ,
         created_at TIMESTAMPTZ NOT NULL DEFAULT now()
     );
     ```
  3. **Preserve API contract**: `GET /plans`, `POST /plans/optimize-shift`, `POST /plans/{plan_id}/dispatch` remain completely compatible.
  4. Add `POST /plans/{plan_id}/approve` and `POST /plans/{plan_id}/reject` in `routing-engine` to record approval decisions before `dispatch` can be invoked.

---

### 3.4 Driver GPS Tracking: Redis Geo & Realtime Polling (T4.5)
* **The Problem**: Prompt assumed Redis Geo already existed. In reality, `driver-service` only performs SQL updates to `drivers.current_lat` and `drivers.current_lng`.
* **The Solution**:
  1. Add `spring-boot-starter-data-redis` to `services/driver-service/pom.xml`.
  2. Implement Redis Geo key per tenant: `tenant:{tenantId}:driver_locations` (`GEOADD`, `GEOPOS`, `GEORADIUS`).
  3. Implement Redis Heartbeat: `driver:{driverId}:heartbeat` with TTL (e.g., 60 seconds). If expired, the driver is marked as `OFFLINE` or stale.
  4. For high-frequency GPS pings from mobile: write to Redis Geo + refresh TTL. Persist to PostgreSQL `drivers` / `driver_location_history` only on significant movement or periodically (throttled) to protect the database.
  5. **Web Admin Realtime**: Implement `GET /api/drivers/locations` (tenant-scoped) and consume via React Query polling every 3–5 seconds on the Operations Map page. No heavy WebSocket cluster required.

---

### 3.5 Map Provider Abstraction (T4.4)
* **The Problem**: Web Admin hardcoded depot coordinates `{ lat: 10.0371, lng: 105.7828 }` and uses Leaflet with raw OSM tile URLs directly inside component files.
* **The Solution**:
  1. Create a clean Map Provider Abstraction in `web-admin/src/infrastructure/maps/`:
     * `IMapProvider`: defines `getTileUrl()`, `getAttribution()`, `geocode(address)`, `reverseGeocode(lat, lng)`.
     * `OsmMapProvider`: OpenStreetMap tiles + Nominatim geocoding.
     * `GoongMapProvider`: Goong vector/raster tiles + Goong REST geocoding for Vietnam.
  2. Add endpoint `GET /api/tenants/me` in `order-service` returning `pickup_lat`, `pickup_lng`, `address`.
  3. Update `useDispatch.ts` to dynamically fetch and display the authenticated tenant's warehouse depot.

---

## 4. Exact Files Requiring Changes

### A. Backend Services
1. **`services/order-service`**:
   * `src/main/java/com/swiftroute/orderservice/entity/Customer.java` (NEW)
   * `src/main/java/com/swiftroute/orderservice/entity/CustomerAddress.java` (NEW)
   * `src/main/java/com/swiftroute/orderservice/repository/CustomerRepository.java` (NEW)
   * `src/main/java/com/swiftroute/orderservice/controller/CustomerController.java` (NEW)
   * `src/main/java/com/swiftroute/orderservice/controller/TenantController.java` (Add `GET /api/tenants/me`)
   * `src/main/java/com/swiftroute/orderservice/service/TenantService.java` (Support `getTenantMe`)
   * `src/main/java/com/swiftroute/orderservice/service/OrderService.java` (Integrate CRM customer validation)
2. **`services/driver-service`**:
   * `pom.xml` (Add `spring-boot-starter-data-redis`)
   * `src/main/resources/application.yml` (Configure Redis host, port, timeout)
   * `src/main/java/com/swiftroute/driverservice/service/DriverLocationRedisService.java` (NEW: Redis Geo & Heartbeat)
   * `src/main/java/com/swiftroute/driverservice/controller/DriverLocationController.java` (NEW or update `updateMyLocation` and `getActiveDriverLocations`)
   * `src/main/resources/db/migration/V3__create_driver_location_history.sql` (NEW: Sampled location history)
3. **`services/routing-engine-service`**:
   * `service/plan_store.py` (Add PostgreSQL `routing_db` persistence with fallback)
   * `api/routes.py` (Add `POST /plans/{plan_id}/approve` and `POST /plans/{plan_id}/reject`)
   * `service/auto_dispatcher.py` (Respect manual approval workflow seamlessly)

### B. Frontend (`web-admin`)
1. **API & Repositories**:
   * `src/infrastructure/repositories/routing.repository.ts` (Reroute from `localhost:8084` to API Gateway `http://localhost:8000/api/routing/...` using shared `api.ts`)
   * `src/infrastructure/repositories/tenant.repository.ts` (NEW: call `/api/tenants/me`)
   * `src/infrastructure/repositories/customer.repository.ts` (NEW)
2. **State & Hooks**:
   * `src/application/hooks/use-dispatch.ts` (Replace hardcoded `DEFAULT_DEPOT` with dynamic tenant warehouse)
   * `src/application/hooks/use-driver-tracking.ts` (NEW: React Query polling every 3s)
3. **Map & UI Components**:
   * `src/infrastructure/maps/map-provider.ts` (NEW: Map abstraction interface)
   * `src/infrastructure/maps/osm-provider.ts` (NEW: OSM implementation)
   * `src/components/dispatch/route-map.tsx` & `route-map-inner.tsx` (Connect dynamic depot & live driver pucks)
   * `src/app/(dashboard)/customers/page.tsx` (NEW: Customer CRM management)
   * `src/app/(dashboard)/dispatch/page.tsx` (Integrate approval & plan publishing buttons)

---

## 5. Safest Recommended Implementation Sequence

To avoid breaking existing functionality and allow continuous verification at each step, Phase 4 should be executed in the following sequential order:

1. **Step 1: T4.2 — Customer Management** (Create `customers`, update order creation with backward-compatibility for legacy Auth users).
2. **Step 2: T4.4 — Map Foundation & Tenant Depot** (Expose `GET /api/tenants/me`, remove hardcoded depot from Web Admin, implement Map provider abstraction).
3. **Step 3: T4.5 — Driver Live Tracking** (Integrate Redis Geo & TTL heartbeat in `driver-service`, React Query 3s polling in Web Admin).
4. **Step 4: T4.6 — Route Visualization & Gateway Integration** (Reroute `web-admin` routing calls through API Gateway, render planning stops and polylines on Leaflet).
5. **Step 5: T4.7 — Route Approval & Publishing** (PostgreSQL `routing_db` migration for `plan_store.py`, connect operator approval buttons to `dispatch_plan`).
6. **Step 6: T4.8 — Map Intelligence** (Sampled GPS location history and breadcrumb trail replay).

---

## 6. Audit Sign-off

- [x] All 12 mandatory inspection items fully investigated.
- [x] No code, migrations, or database schemas modified during this task.
- [x] Database migration and compatibility strategies documented.
- [x] Service ownership boundaries preserved.
- [x] 6 architectural conflicts identified and concrete resolutions defined.

**Status: READY FOR USER APPROVAL TO BEGIN TASK T4.1.**
