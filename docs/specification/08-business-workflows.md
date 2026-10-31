# Business workflows

## Registration and onboarding

```mermaid
sequenceDiagram
  participant U as User
  participant G as Gateway
  participant A as Auth
  participant O as Order/Tenant
  participant Q as RabbitMQ
  U->>G: register/login
  G->>A: public auth request
  A-->>U: token pair
  U->>G: create tenant (Bearer token)
  G->>O: trusted identity
  O->>O: tenant=PENDING + outbox
  O->>Q: tenant.created.v1
  Q->>A: membership projection
  Note over O: reviewer approves/rejects; approval emits status event
```

Preconditions are valid user/token and required business data. Only platform reviewer approves/rejects; rejection has reason. Failure to publish does not roll back tenant write: outbox retry ensures eventual Auth projection. Final outcome is tenant `APPROVED`, `REJECTED`, or `SUSPENDED`.

## Order to route plan

1. Customer or authorised operator submits an idempotent order command. Order validates tenant state, ownership/scope, coordinates, weight and UTC window; writes order and `order.created/ready_for_planning` outbox fact.
2. Routing consumes candidate/availability facts, creates optimisation job and immutable input snapshot. It excludes stale, cancelled or infeasible candidates.
3. Routing publishes plan proposal containing source versions. Order atomically accepts only matching versions and allowable statuses, records assignment/history and emits `order.status_changed` (`PLANNED`). Stale proposal becomes `route.plan_rejected` and triggers replan.
4. Driver receives route notification/projection. Notification is asynchronous and does not gate planning.

## Delivery, cancellation and exception

Driver reports start/delivered/failed using idempotent event endpoint; Driver publishes a fact, Order authorises assignment and applies only allowed transition, then emits status event. Cancellation is permitted only through allowed pre-start transitions in current state machine; Routing removes/replans stops after cancellation event. An urgent order invokes proposed dynamic insertion; if no feasible capacity/time window exists, it remains unplanned with explicit reason and Ops notification—never reassigned by direct database update.
