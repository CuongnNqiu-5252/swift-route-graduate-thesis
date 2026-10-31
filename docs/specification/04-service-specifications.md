# Service and client specifications

## Auth Service — Partial

**Purpose/data:** owns users, credentials, refresh tokens and memberships. Public features are registration, login, refresh and logout. It consumes tenant lifecycle events to maintain memberships. Users need no tenant membership to register; protected access requires a valid access token and tenant-scoped endpoints additionally require active membership.

- Existing endpoints: `POST /api/auth/register`, `/login`, `/refresh?refreshToken=`, `/logout?refreshToken=`.
- Existing validation: register email/full name/phone/address fields and password minimum six characters; login currently has no bean validation.
- Security: BCrypt/hash password; store only refresh token hash; rotate refresh token; reject reused/revoked/expired token. Current JWT expiry is 15 minutes access/30 days refresh.
- Dependencies: PostgreSQL, RabbitMQ for `tenant.*`; Gateway shares the configured JWT secret today. **Proposed:** asymmetric signing/JWKS rather than shared secret.
- Tests: authentication unit tests exist; add rate-limit, rotation/reuse, token expiry and membership isolation integration tests.

## Order Service / tenant owner — Partial

**Purpose/data:** owns tenant onboarding, orders and their status. Existing controllers expose tenant create/get/status and order create/get mine/get tenant/get by ID/update status. Current implementation validates approved tenant and order window, but it does not create order events/history or verify all tenant membership paths correctly.

- Permissions: tenant create currently allows broad roles, including `CUSTOMER`; status review includes `CUSTOMER`, which is inconsistent with platform review and must be narrowed to `SYSTEM_ADMIN`/`OPS_STAFF` (or approved role policy). Order access is customer owner or authorised tenant/platform role.
- Dependencies: Auth identity propagated by Gateway; RabbitMQ tenant outbox currently exists. Future dependencies: Driver/Routing by logical UUID/event only.
- Failure: return 409 for invalid state/optimistic-lock conflict; do not assign a plan whose input order version no longer matches.
- Tests: current tenant service tests exist; add authorization matrix, event/outbox atomicity, cancellation, idempotency and pagination tests.

## Driver Service — Scaffold

Intended responsibility is driver registration, vehicle capacity, shift, availability, current route and delivery reporting. Existing code has an entity/repository/security filter but no controller/business service. It also has unexposed `DriverEarning` and `DriverRating` entities/migration; settlement/rating are not in the project plan and must remain proposed/out of scope until requirements define who creates them and how amounts are sourced. V1 creates `drivers`, while entity/V2 use `driver`, so a clean Flyway run is currently broken. No driver API is a currently implemented contract. Implement only after schema reconciliation, then require driver self-access plus tenant operator scoped access, publish availability/delivery events, and test capacity/shift/tenant isolation.

## Routing Engine — Planned scaffold

The plan requires FastAPI endpoints `/optimize/batch` and `/optimize/insert`; repository has dependency list but no source/API. It owns jobs, plan snapshots, stops and model registry. It consumes eligible order/driver facts or receives trusted snapshots, never writes Order/Driver databases. It publishes a plan proposal; Order accepts/rejects it after version/state verification. Tests must prove feasibility, determinism under seed/input, no duplicate stops, and fallback/error handling.

## Notification Service — Scaffold

The current project is only a Spring application/test. Its proposed role is consuming events, applying preferences, dispatching push/email/SMS through adapters, and retaining delivery attempts. It owns no order/user profile; recipient IDs are logical references. Provider failure must retry/DLQ without rolling back the original business event.

## Web Admin — Scaffold

Next.js currently contains default application content. Planned users are tenant operators and platform staff. It uses Gateway only, stores no source-of-truth business data, and must render tenant-scoped orders, onboarding review and route/job state. Acceptance: unauthorised routes redirect; list filters/pagination are server-driven; a route screen handles pending/failed job states.

## Mobile application — Scaffold

Flutter currently contains default content. Planned customer features are order creation/tracking; driver features are login, assigned route, GPS and delivery exception. Device receives token registration only through Notification API. Acceptance: secure token storage, offline action queue with idempotency key, role-specific navigation, and explicit sync/error indication.
