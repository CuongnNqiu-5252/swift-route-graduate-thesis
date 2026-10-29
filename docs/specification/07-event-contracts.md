# Event contracts

RabbitMQ exchange is durable topic `swiftroute.events`; routing keys are `<domain>.<event>.v<version>` (the existing code emits unversioned `tenant.created`/`tenant.approved`; migrate compatibly to versioned keys). All events include:

```json
{"event_id":"uuid","event_type":"order.created.v1","schema_version":1,"occurred_at":"2026-10-09T00:00:00Z","producer":"order-service","correlation_id":"uuid","causation_id":"uuid|null","aggregate_id":"uuid","tenant_id":"uuid","payload":{}}
```

| Event owner | Consumers | Required payload |
|---|---|---|
| `tenant.created.v1`, `tenant.status_changed.v1` — Order | Auth, Driver | tenant_id, owner_user_id, status, occurred_at |
| `order.created.v1`, `order.ready_for_planning.v1`, `order.cancelled.v1`, `order.status_changed.v1` — Order | Routing, Notification | order ID/version, tenant ID, status, candidate snapshot where needed |
| `driver.availability_changed.v1`, `driver.delivery_reported.v1` — Driver | Routing, Order, Notification | driver ID/version, tenant ID, availability/report, time |
| `route.plan_created.v1`, `route.plan_failed.v1`, `route.plan_rejected.v1` — Routing | Order, Driver, Notification | plan/job ID, input versions, assignments/reason |

Producer writes outbox record in the same transaction as its aggregate change. Publisher claims rows, obtains broker confirm, then marks published. Consumer records `(consumer_name,event_id)` in its own transaction before/with processing; duplicate messages are successful no-ops. Retry transient errors with exponential backoff; malformed/permanent messages go to a service-owned DLQ with alert and replay after repair. Event payloads are immutable, versioned and contain no password, raw token or unnecessary PII.
