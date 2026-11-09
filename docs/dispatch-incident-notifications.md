# Dispatch incident notifications

## Ownership and storage

Order-service owns incident creation, authorization, recovery and resolution. Notification-service consumes operational events and supplies the map's active alerts through `GET /api/notifications/dispatch-alerts`. The web client does not derive alerts directly from the incident list.

Notification-service keeps its existing independent PostgreSQL database, `notification_db`. This implementation reuses `notification_deliveries.payload` and `processed_events`; it requires no new database engine, dependency, or schema migration. Notification payloads are projections of events, not the source of truth for incident resolution.

## Event flow

1. The canonical `POST /api/operations/incidents` persists an incident and the `delivery.incident` outbox event in the order transaction. Mobile operational incident reports already use this flow.
2. The outbox publisher sends the event to `swiftroute.events` with stable `messageId` and `correlationId`. Notification-service uses the identity to prevent duplicate delivery records.
3. The consumer records a tenant operator `IN_APP` notification, including incident, assigned driver, plan/order, severity and action link. The reporter is stored separately from the assigned driver.
4. The gateway forwards `/api/notifications/**` to notification-service. The notification API verifies the bearer token and operator membership for the requested tenant through auth-service; reads always constrain notification rows to that tenant.
5. The dashboard opens one authenticated notification SSE stream shared by the bell and dispatch map. On connection or reconnection it performs one bounded active-alert snapshot, then applies `dispatch-alert` and `incident-resolved` events. The map only shows an alert when its assigned driver and dispatch plan/order both match the route being displayed.
6. Incident resolution emits `incident.resolved` with tenant and incident identity. Completing the physical return also emits this event. `RESOLVED` and `CANCELLED` hide alerts; `IN_PROGRESS` and `ESCALATED` remain actionable.

Resolution events are stored independently. The active-alert query excludes an incident when a terminal resolution exists, even if RabbitMQ delivers resolution before the original alert. Failed notification writes escape the listener so the transaction, including its processed-event insert, can roll back. Existing broker requeue behavior remains in effect.

## Trying the simulation

Choose a driver with an assigned route or an in-transit order, start GPS simulation, then select **Giả lập sự cố**. This creates a real `VEHICLE_BREAKDOWN` incident marked `[Giả lập]` and attempts to stop simulated movement at the current position. The control is unavailable for an unassigned driver because incidents need an order or dispatch plan.

Wait for the outbox publisher and notification SSE event. The alert appears in the notification bell and, when it belongs to the displayed route, as **Có sự cố · Xử lý ngay** above the driver. Resolve the incident in Operations; the alert disappears when the resolution event reaches notification-service. GPS simulation is restarted manually when appropriate.

## Configuration and rollout

- Gateway: `NOTIFICATION_SERVICE_URL`, default `http://localhost:8086`; Compose uses `http://notification-service:8086`.
- Notification-service: `AUTH_SERVICE_URL`, default `http://localhost:8081`; Compose uses `http://auth-service:8081`.
- Dependencies: existing PostgreSQL notification database, RabbitMQ, auth-service, order-service and gateway.
- Rebuild/restart the affected services and web app to activate the source changes. This task does not execute a deployment or alter live data.

## Verification and limits

Targeted Java tests cover notification recipients, stable event identity, duplicate handling, write failure propagation, operator authorization, tenant access rejection, recovery payloads and return-completion events. Web validation uses ESLint and a production Next.js build.

Commands run successfully:

- `mvn -f services/notification-service/pom.xml -Dtest=OperationalAlertConsumerTest,NotificationAuthorizationTest test`: 8 tests passed. The consumer suite was rerun after the final controller adjustment: 4 tests passed.
- `mvn -f services/order-service/pom.xml -Dtest=OutboxPublisherTest,ExceptionOperationsServiceTest,RecoveryRuleEngineTest test`: 11 tests passed.
- From `web-admin`: `npm.cmd run lint`, `npm.cmd run lint -- --quiet`, focused ESLint on changed web files, and `npm.cmd run build`. No lint errors; focused checking reports two existing page effect warnings.
- `git diff --check`: passed.

The current transport uses SSE with a three-second reconnect backoff and a single snapshot after each successful connection; there is no periodic incident or location fetch. Driver GPS freshness uses a one-shot timeout, and visual positions interpolate between server coordinates while the server coordinate remains authoritative. No browser was connected in the implementation session, so interactive map rendering and a full live RabbitMQ/PostgreSQL round trip were not verified. Historical notification records with the old reporter-as-driver payload or missing terminal events are not backfilled. The legacy order-only incident endpoint is outside this integration; use the canonical Operations incident API.
