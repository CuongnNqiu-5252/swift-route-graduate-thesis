# SwiftRoute — Project overview and gap analysis

## Purpose and scope

SwiftRoute is a multi-tenant SaaS for businesses that need to collect delivery orders, allocate them to drivers, and optimise delivery routes. Its research core is a hybrid pipeline: clustering/assignment, dynamic-programming route optimisation, and optional ML pruning. The product scope in `ke-hoach-trien-khai.md` includes a web operations console and Flutter applications for drivers and customers.

The in-scope outcome is an auditable flow from tenant onboarding and order intake to an optimised route, driver action, delivery status, and notification. Payments, subscription billing, maps/geocoding providers, and live push delivery are **not specified or implemented**; order payment states exist in code but no payment integration may be inferred.

## Actors

| Actor | Intended capability |
|---|---|
| Customer | Register/login, create and view own order, track delivery (planned). |
| Tenant owner/operator | Request tenant onboarding, manage tenant orders, trigger/view planning (partly planned). |
| Driver | View route, publish location, report delivery/failure (planned). |
| Platform admin/ops staff | Review tenant onboarding and operate exceptional cases (tenant status API partly implemented). |

## Evidence-based implementation status

| Component | Status | Evidence |
|---|---|---|
| API Gateway | Partly implemented | Spring Cloud Gateway routes Auth, Order and Tenant requests; verifies JWT and injects headers. |
| Auth | Partly implemented | Register/login/refresh/logout, JWT, refresh token persistence, tenant membership event consumer. |
| Order/Tenant | Partly implemented | Tenant onboarding/status, order CRUD subset, state machine, PostgreSQL migrations and tenant outbox. |
| Driver | Scaffold/inconsistent | Spring project, entity/repository/migration exist; no controller or business service and mapping conflicts with migration. |
| Routing Engine | Planned scaffold | Python requirements only; no FastAPI source or optimisation API exists. |
| Notification | Scaffold | Spring Boot application/test only. |
| Web Admin | Scaffold | Default Next.js page. |
| Mobile | Scaffold | Default Flutter app. |

## Gaps and contradictions requiring resolution

1. The plan says Order/Driver are NestJS, but the repository uses Spring Boot. This specification adopts the repository implementation (Spring Boot) unless a deliberate rewrite is approved.
2. The plan states database per service, while Compose and all current service DSNs point to one PostgreSQL database. The target must be database-per-service.
3. Auth/Order disable Flyway and use Hibernate `ddl-auto: update`; this conflicts with migration-controlled schema. Target: Flyway enabled, `validate` only.
4. Driver V1 creates `drivers` while the entity and V2 use `driver`; V2 therefore attempts to alter a non-existent table on a clean migration. V2 also introduces earnings/ratings although neither the plan nor a driver API defines their workflow. Driver functionality must not be exposed until reconciled by a new forward migration and scope decision.
5. Existing order windows are `LocalTime`, which has no date/timezone. The target contract uses UTC ISO-8601 `delivery_window_start/end`.
6. Order authorization incorrectly compares a tenant UUID with requester user UUID for membership. Tenant membership must be resolved from authenticated claims/projection or a service API, never by UUID equality.
7. The create-order comment says tenant comes from a trusted header, yet code accepts `tenant_id` in the body. The body field must be removed/deprecated or restricted to platform staff after explicit tenant authorization.

## Glossary and conventions

`tenant` is a business organisation. `order` is one delivery demand. `route plan` is an immutable optimisation result with a version. `assignment` is Order's accepted reference to a Driver/Routing plan. All IDs are UUID; JSON uses `snake_case`; timestamps are UTC ISO-8601. Labels used below are **Implemented**, **Partial**, **Planned**, or **Proposed**.
