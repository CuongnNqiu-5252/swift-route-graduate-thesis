# COD reconciliation phase 0 contract

Status: implemented through phase 4  
Scope: embedded Payment/COD Ledger in Order Service, driver workflow and Web Admin reconciliation

## Implementation profile

The Payment/COD Ledger remains a separate domain boundary but is implemented as a deep module inside `order-service`, using the existing `order_db`. It does not add a service, database, RabbitMQ producer, outbox or consumer inbox. Money-changing commands use a caller UUID `operation_id`, database uniqueness and optimistic versions only.

Runtime delivery completed:

- Phase 1: COD obligation, collection ledger and derived payment state.
- Phase 2: driver cash custody and batch handover.
- Phase 3: independent cashier count, discrepancies and controlled resolution.
- Phase 4: driver collection/handover UI and Web Admin COD dashboard.

## 1. Objective

Build one unambiguous model for the path from an order's COD obligation to customer collection, driver custody, cash handover, cashier verification, and discrepancy resolution. This contract deliberately keeps COD separate from delivery completion and driver earnings.

## 2. Ownership and seams

| Module | Owns | Does not own |
|---|---|---|
| Order | Order, expected COD snapshot, fulfillment state, payment-status projection | Actual money collected, handover verification, discrepancy balance |
| Driver | Driver identity, assignment and shift context | COD balance or driver earnings deduction caused by a discrepancy |
| Payment/COD Ledger (embedded in Order Service) | COD obligation, collection entries, cash custody, handover batch, discrepancies and adjustments | Delivery route or driver compensation |
| Mobile adapter | Driver commands and evidence capture | Financial state calculation |
| Admin adapter | Cashier counting and Ops resolution commands | Direct database status mutation |

The external write seam is the Payment/COD Ledger command interface. State transitions, totals, authorization rules and audit facts stay behind that interface rather than being duplicated in mobile, admin or Order.

## 3. Amount vocabulary

All amounts are non-negative integer VND values.

| Name | Meaning |
|---|---|
| `expected_amount` | COD obligation fixed for the order |
| `collected_amount` | Actual cash received from the payer in one collection action |
| `custody_amount` | Valid collected cash not yet included in a verified handover |
| `ledger_total` | Sum of custody amounts selected into a handover |
| `declared_total` | Amount the driver says is physically being handed over |
| `counted_total` | Amount independently counted by the cashier |
| `discrepancy_amount` | `counted_total - ledger_total`; negative is shortage, positive is overage |

`declared_total - ledger_total` is retained as an early declaration variance. It does not replace the final cashier discrepancy.

## 4. State models

### 4.1 COD obligation state

The state is derived from non-reversed collection entries rather than freely edited.

```text
COD_PENDING
  ├─ collected total = 0                  -> COD_PENDING
  ├─ 0 < collected total < expected       -> PARTIALLY_COLLECTED
  ├─ collected total = expected           -> COLLECTED
  └─ collected total > expected           -> OVER_COLLECTED
```

`DELIVERED` is a fulfillment fact and never moves the obligation to `COLLECTED` by itself.

### 4.2 Cash handover state

```text
DRAFT -> SUBMITTED -> VERIFIED
                   -> DISCREPANCY -> RESOLVED
```

- `DRAFT`: collections are selected but the driver has not declared a physical handover.
- `SUBMITTED`: driver has fixed the selected lines and `declared_total`.
- `VERIFIED`: cashier counted exactly `ledger_total`; included custody is released from the driver.
- `DISCREPANCY`: cashier count differs from `ledger_total`.
- `RESOLVED`: Ops approved an adjustment, recovery, refund, or documented write-off.

Submitted and later batches are immutable except for their controlled transition. Corrections append an adjustment; they do not rewrite the original money fact.

## 5. Roles and authority

| Actor | Allowed actions |
|---|---|
| Driver | Record collection for an assigned delivery; view own custody; submit a handover containing own eligible collections |
| Cashier | View submitted batches; enter an independent count; verify or open a discrepancy |
| Ops Staff | Investigate and resolve discrepancies with reason and evidence |
| Tenant Owner/Manager | Read reports and approve a write-off when tenant policy requires it |
| System Admin | Platform support only; no default authority to confirm tenant cash |

A driver cannot verify their own handover. Verification records both the authenticated actor and tenant.

## 6. Command interface

The initial write interface stays intentionally small:

```text
openCodObligation(orderId, tenantId, expectedAmount, currency)
recordCollection(operationId, orderId, driverId, amount, outcome, reason, evidence, occurredAt)
submitHandover(operationId, driverId, collectionIds, declaredTotal, notes)
verifyHandover(operationId, handoverId, cashierId, countedTotal, notes)
resolveDiscrepancy(operationId, discrepancyId, resolutionType, adjustmentAmount, reason, evidence)
```

Query interfaces are separate and may expose:

```text
getDriverCustody(driverId)
getHandover(handoverId)
listHandoverQueue(filters)
listDisputes(filters)
getOrderCodSummary(orderId)
```

### Required error modes

- Tenant mismatch or unauthorized actor: reject without revealing another tenant's record.
- Unknown or non-COD order: reject.
- Collection before successful delivery: reject unless a future explicit Ops override is designed.
- Negative amount or non-VND fractional amount: reject.
- Empty or mixed-driver handover: reject.
- Collection already locked in another submitted batch: reject.
- Verify a non-`SUBMITTED` batch: reject.
- Resolve a non-`DISCREPANCY` item: reject.
- Concurrent stale transition: return a conflict and current version.

## 7. Minimal idempotency scope

Phase 1 will not build a general idempotency platform, Redis key store, generic middleware, or `processed_events` inbox.

Only money-changing commands carry a caller-generated UUID `operation_id`. The owning aggregate table enforces a unique `(tenant_id, operation_id)` constraint and returns the previously created result when the same operation is retried. Updates also use an optimistic `version`.

Additional natural constraints remain:

- One active COD obligation per `(tenant_id, order_id)`.
- A collection line can belong to at most one submitted or verified handover.
- A verified batch cannot be verified again or edited.

Read operations and draft-only UI actions require no idempotency mechanism.

## 8. Deferred event contract

The implementation does not publish these events and does not create an outbox/inbox. The names below are reserved only for a future external accounting integration or extraction of the embedded module.

Every event uses this envelope:

```json
{
  "event_id": "uuid",
  "event_type": "name.v1",
  "occurred_at": "ISO-8601 timestamp",
  "tenant_id": "uuid",
  "aggregate_id": "uuid",
  "payload": {}
}
```

| Event | Producer | Required payload |
|---|---|---|
| `order.cod_obligation_created.v1` | Order | `order_id`, `expected_amount`, `currency` |
| `driver.cod_collected.v1` | Driver | `operation_id`, `order_id`, `driver_id`, `amount_collected`, `outcome`, `occurred_at` |
| `payment.cod_collection_recorded.v1` | Payment/COD Ledger | `payment_id`, `collection_id`, `order_id`, `driver_id`, `amount_collected`, `payment_status` |
| `cod.handover_submitted.v1` | Payment/COD Ledger | `handover_id`, `driver_id`, `ledger_total`, `declared_total` |
| `cod.handover_verified.v1` | Payment/COD Ledger | `handover_id`, `driver_id`, `cashier_id`, `counted_total` |
| `cod.discrepancy_opened.v1` | Payment/COD Ledger | `discrepancy_id`, `handover_id`, `expected_total`, `counted_total`, `difference` |
| `cod.discrepancy_resolved.v1` | Payment/COD Ledger | `discrepancy_id`, `resolution_type`, `adjustment_amount`, `resolved_by` |

Event publishing and consumer deduplication remain deferred; the synchronous embedded workflow is authoritative today.

## 9. Edge-case decisions

1. Delivered but no cash recorded remains `COD_PENDING`; it is visible as uncollected debt.
2. Partial collection is accepted and may be followed by another collection entry until the obligation is satisfied.
3. Zero collection is recorded only with outcome `NOT_COLLECTED` and a reason; it creates no driver custody amount.
4. Over-collection is recorded exactly as observed, marked `OVER_COLLECTED`, and enters an Ops review instead of being silently rejected or truncated.
5. Reassigning the order after collection does not transfer cash custody; the collecting driver remains responsible until a verified handover or explicit custody transfer exists.
6. Cancelling or returning an order before collection cancels the obligation according to policy. After collection it requires a refund or adjustment entry; historical collection is not deleted.
7. An offline mobile action keeps both `occurred_at` and server `received_at`; financial ordering uses server acceptance plus aggregate version, not the phone clock alone.
8. A submitted handover may declare a value different from `ledger_total`, but requires a note and is visibly flagged before cashier counting.
9. Cashier count determines final handover discrepancy. A mismatch cannot be confirmed as `VERIFIED` through a note-only override.
10. Unresolved collection or handover discrepancies prevent shift closure once shift enforcement is implemented.

## 10. Acceptance scenarios for implementation

1. Completing a COD delivery without recording cash leaves payment `COD_PENDING`.
2. Recording 70,000 VND against a 100,000 VND obligation produces `PARTIALLY_COLLECTED` and 70,000 VND in that driver's custody.
3. A later 30,000 VND collection moves the obligation to `COLLECTED` and custody to 100,000 VND.
4. Retrying the same `operation_id` returns the original collection without increasing any total.
5. Two different valid collection operations remain separate immutable entries.
6. A driver cannot submit another driver's collection or a collection locked in another submitted batch.
7. A 100,000 VND ledger total, 100,000 VND declaration and 100,000 VND cashier count becomes `VERIFIED` and releases custody.
8. A 100,000 VND ledger total and 90,000 VND cashier count becomes `DISCREPANCY` of -10,000 VND and does not release the disputed balance as reconciled.
9. Resolution appends an adjustment with actor, reason and evidence; it does not edit the original collection or handover totals.
10. No COD amount contributes to driver earnings or driver settlement totals.
11. Cross-tenant reads and commands are rejected.
12. Concurrent verification accepts one version and returns conflict for the stale request.

## 11. Deferred beyond phase 4

- Extracting a standalone `payment-service` and separate database.
- RabbitMQ publishing, outbox, retries and consumer inboxes.
- Shift clock-out enforcement, report export, alerts and external accounting integration.
