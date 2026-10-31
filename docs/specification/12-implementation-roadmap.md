# Implementation roadmap

## Phase 0 — stabilise foundations (first)

Provision per-service databases/roles, enable Flyway and `ddl-auto=validate`, reconcile Driver V1/V2/entity naming (including clean-migration verification), decide whether earning/rating is in scope, remove default secrets and complete Compose services. Correct tenant/role authorization and body-supplied tenant ID before expanding APIs. DoD: clean environment migrates and Auth/Tenant/Order flow passes integration tests.

## Phase 1 — demonstrable core workflow

Implement Driver profile/vehicle/shift/availability and minimal FastAPI Routing batch API; add order outbox/history/inbox and route plan acceptance. Add Gateway routes, OpenAPI/contract tests and web minimal operations view. DoD: approved tenant -> eligible order -> plan -> authorised order/driver view works through gateway.

## Phase 2 — execution and notifications

Implement driver mobile role/API, idempotent delivery reports, Notification event consumer and delivery audit; add customer tracking read model. DoD: planned route is delivered/failed and both actor visibility/notifications are tested under duplicate events.

## Phase 3 — research and resilience

Add DP baselines, evaluation corpus, ML pruning, clustering and dynamic insertion; implement model registry, metric dashboard, DLQ/replay/runbooks, observability and performance tests. DoD: reproducible experiment tables and failure recovery evidence meet thesis/demo requirements.

## Phase 4 — optional enhancements

Implement watchdog/advisor only after core flow is stable. Payment/billing, external map/geocoding provider and full customer live tracking remain open requirements; no code should assume them.

## Change locations and risks

Expected changes: `docker-compose.yml`, per-service `application.yml`, Flyway directories, service controllers/domain/event configuration, `routing-engine-service` source, `notification-service`, `web-admin`, `mobile-app`, and this specification. Primary risks are a service boundary migration from shared DB, algorithm scale/quality, ambiguous RBAC/tenant policy, and mobile scope. Mitigate with phased cutover, contract tests, benchmark gates and explicit stakeholder decisions.
