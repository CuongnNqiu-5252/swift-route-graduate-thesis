# Testing strategy

| Level | Scope | Required examples |
|---|---|---|
| Unit | Service/domain logic | tenant/order state machine, weights/window validation, JWT expiry/rotation, route objective/constraint functions. |
| Repository/migration | Real PostgreSQL container | Flyway clean migrate, constraints/indexes, tenant-filtered queries, outbox/inbox idempotency. |
| API integration | Gateway + service | auth errors, RBAC matrix, forged header rejection, pagination, idempotency replay, uniform `ApiResponse`. |
| Messaging | RabbitMQ container | publish confirm, duplicate delivery, retry/DLQ/replay, schema compatibility and correlation propagation. |
| End-to-end | Gateway + services | register -> tenant approval -> order -> plan -> delivery -> notification; cancellation and routing outage. |
| Routing experiments | Fixtures/benchmarks | DP ground truth, feasibility/property tests, baseline/ML comparison at 10/30/50/100. |
| Non-functional/security | CI/staging | load, chaos dependency outage, SAST/dependency scan, secret scan, tenant penetration tests, backup restore. |

Use Testcontainers for PostgreSQL/RabbitMQ/Redis. Contract test REST/OpenAPI and event schemas in producer/consumer CI. Seed deterministic clocks/UUIDs for repeatability. Each bug fixing a state/security/event issue requires a regression test. The current repository has application-context and selected Auth/Tenant tests; it has no demonstrated E2E, routing, message reliability, UI or load test suite.
