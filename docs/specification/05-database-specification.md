# Database specification

The detailed target schema and migration path are maintained in [database-schema-microservices.md](../database-schema-microservices.md). It is normative for data ownership, columns, constraints, indexes, outbox/inbox, migration and retention.

## Database matrix

| Owner | Database | Core tables | Logical external IDs |
|---|---|---|---|
| Auth | `auth_db` | `users`, `tenant_memberships`, `refresh_tokens`, `processed_events` | `tenant_id` -> Order |
| Order | `order_db` | `tenants`, `orders`, `order_status_history`, `outbox_events`, `processed_events` | user/customer -> Auth; driver -> Driver; route plan -> Routing |
| Driver | `driver_db` | `drivers`, `vehicles`, `driver_shifts`, availability projection, outbox/inbox | tenant -> Order; user -> Auth; route plan -> Routing |
| Routing | `routing_db` | `optimization_jobs`, `route_plans`, `route_stops`, `model_registry`, outbox/inbox | tenant/order -> Order; driver -> Driver |
| Notification | `notification_db` | preferences, device tokens, deliveries, inbox | user -> Auth |

Physical FKs are permitted only within the owner database. UUID external references are validated by authenticated command APIs and event projections. UTC `TIMESTAMPTZ`, snake_case JSON, `version` optimistic locking and tenant-first indexes are required. Current shared PostgreSQL is an implementation gap, not an approved target.

The current Driver V2 migration must not be treated as an approved schema: it targets `driver` although V1 creates `drivers`, and its earning/rating tables introduce payment/rating concepts outside stated scope. Replace it with a tested forward migration/baseline strategy after deciding the feature scope; do not patch an already-applied V1 in place.
