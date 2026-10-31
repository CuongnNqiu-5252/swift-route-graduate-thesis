# Functional requirements

## Requirements and acceptance criteria

| ID | Requirement | Status | Acceptance criteria |
|---|---|---|---|
| FR-01 | Account registration and authentication | Partial | Unique valid email; password is hashed; login returns short-lived access token and rotating refresh token; invalid credentials return a consistent error. |
| FR-02 | Tenant onboarding | Partial | Authenticated requester creates a `PENDING` tenant; authorised platform reviewer can approve/reject/suspend; every change creates an outbox event. |
| FR-03 | Create and view order | Partial | Only an approved tenant accepts an order; coordinates, positive weight and ordered time window are validated; customer sees only own orders, operator only authorised tenant data. |
| FR-04 | Order lifecycle | Partial | State transitions obey the state machine in workflow 08; status change writes audit history and event (the current audit/event are still planned). |
| FR-05 | Driver management and availability | Planned | Authorised tenant staff can manage a driver, vehicle and shift; only active available drivers appear in planning input. |
| FR-06 | Batch route optimisation | Planned | Operator submits idempotent request, receives job ID, and can retrieve accepted/failure result; result is reproducible from input snapshot and algorithm version. |
| FR-07 | Urgent insertion/exception handling | Planned (phase 4) | Urgent order re-plans an affected route or returns an explicit rejection/fallback; no silently lost order. |
| FR-08 | Driver execution | Planned | Driver sees only assigned route, reports start/delivered/failed and optionally location; invalid status changes are rejected. |
| FR-09 | Notifications | Planned | A business event creates at-most-one delivery attempt per recipient/channel/event; retryable failure does not block source transaction. |
| FR-10 | Reporting/dashboard | Planned | Tenant dashboard shows tenant-scoped orders, route outcomes and basic on-time/cost metrics from a read model; it must not query other service databases. |

## Business rules

- Tenant state: `PENDING -> APPROVED|REJECTED|SUSPENDED`; `APPROVED -> SUSPENDED`; `SUSPENDED -> APPROVED`; rejected is terminal in current code.
- Current order state machine: `PENDING_PAYMENT -> PAID|PAYMENT_FAILED|CANCELLED`, `PAYMENT_FAILED -> PENDING_PAYMENT|CANCELLED`, `PAID -> PLANNED|CANCELLED`, `PLANNED -> IN_TRANSIT|CANCELLED`, `IN_TRANSIT -> DELIVERED|FAILED`. Payment transitions are placeholders until payment scope is decided.
- A planning candidate must belong to an approved tenant, be not cancelled/delivered, have a future delivery window and positive weight.
- Driver capacity, shift, tenant, availability and geographic/temporal constraints must be satisfied before assignment.
- All client retries of create/order optimise commands require an idempotency key.

## Normal and exceptional behaviour

Normal operations return the shared `ApiResponse` envelope. Validation errors return 400, unauthenticated 401, unauthorised 403, missing resource 404, state/version conflict 409, and idempotency replay returns the original result. A broker, routing or notification outage may make a command eventually consistent; its source write succeeds only if its local transaction succeeds, and failed asynchronous work retries/DLQs with operator visibility.
