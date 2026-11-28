# Tenant registration inspection and implementation plan

## Baseline mapping (before changes)
Estimated functional coverage: **35%**, using the equally weighted scores below (partial implementations receive partial credit). This is a source inspection estimate, not a runtime certification.

| Requirement group | Coverage | Evidence / gap |
|---|---:|---|
| Existing identity and email verification | 50% | AuthserviceImpl/JwtService support registration/login/refresh; V4 and EmailVerificationToken exist, but sending, consuming and resend APIs are missing. |
| Tenant creation and ownership | 45% | TenantService creates PENDING + outbox; Auth projects ownership asynchronously as PENDING. No verification check or retry key. |
| Business configuration and pickup | 80% | TenantSettingsService, BusinessTypeProfileRegistry, PickupLocationService exist. PENDING access is blocked. |
| Persistent onboarding and delivery modes | 0% | No progress model or explicit OWN_FLEET/SHARED_POOL/HYBRID lifecycle. |
| Verification lifecycle and audit | 20% | Direct tenant status transitions and optimistic version exist; no independent requests/history. CUSTOMER is incorrectly allowed to review. |
| Operational readiness | 25% | Order creation checks approval and pickup coordinates; no centralized readiness or real shared network gate. |
| Multi-tenant authorization and switching | 40% | Unique memberships and invitations exist. JWT/UI assume one tenant; several endpoints trust headers or global roles. |
| Web onboarding and admin review | 15% | Login/register/settings/locations/members pages exist; onboarding and review pages are absent. |
| Events and notifications | 40% | Order outbox and idempotent Auth consumer exist. Notification service only contains bootstrap/schema; no email delivery implementation. |
| Workflow regression coverage | 35% | Existing service tests cover portions of tenant lifecycle/membership, but not requested workflow. |

Mean = 35%. Architecture fits the requested services; ownership remains Auth for identities/memberships and Order for tenants/configuration/pickup/verification. Existing DB refactoring documents are historical; current Flyway files and code are authoritative.

## Implementation sequence
1. Extend Auth email verification (hashed single-use tokens, expiry, serialized resend), membership context/list/switch and protected internal owner provisioning. Preserve JWT/refresh formats and existing endpoints. Do not grant platform admin from membership roles.
2. Extend Order using Tenant/BusinessTypeProfileRegistry/PickupLocation and existing audit_logs/outbox. Add persistent onboarding, idempotent creation, verification attempts, role checks, readiness and operation guards. Local changes are transactional. Owner provisioning uses a retryable cross-service step after the local tenant transaction, backed by the creation outbox.
3. Add forward-only auth V5 and order V10 migrations (after existing V4/V9), notification runtime/schema wiring if needed. Retain subscription_plan. No cross-database foreign keys. Export databases before applying migrations to running instances.
4. Extend Web Admin with email verification, organization creation, resumable setup/pending dashboard, tenant switching and system review. Read local Next docs; keep existing design components and API wrapper.
5. Publish versioned events using existing outbox; retain legacy events for consumers. Add durable, idempotent notification delivery with SMTP configuration. Shared pool remains unavailable until operational integration exists.
6. Build/compile and perform static checks. Per the user's later instruction, retain no new automated testcases or testcase runners; hand off manual web verification when Browser is unavailable.

## Compatibility and rollback
Keep /api/tenants and add /api/v1/tenants aliases. Preserve snake_case API payloads with camelCase aliases for new request fields. Global ADMIN is a documented legacy platform authority; new membership ADMIN must never become global ADMIN. Legacy tenant_id remains a default context, while membership is authoritative. Existing approved tenants keep operating under existing settings; backfill progress separately from approval. Suspension blocks new operations while preserving ongoing delivery records.

Backup: C:/D/swiftroute-backups/tenant-onboarding-20261103-231555/source.zip with SHA-256 manifest, verified ZIP contents and worktree diff. Restore only changed paths from this archive, preserving unrelated work. Database rollback requires the pre-migration custom-format pg_dump, restored with pg_restore into a recovery database before switching traffic; do not downgrade by editing historical migrations.


## Implemented modules
- **Auth:** `EmailVerificationService`/`EmailVerificationController` reuse the existing token entity/table. Tokens are random, SHA-256 hashed, expire after 24 hours and are consumed with a conditional SQL update. Resend is serialized and limited to once per 60 seconds. Delivery payloads are encrypted at rest. `TenantIdentityService`/`TenantIdentityController` expose live membership context, organization listing and scoped tenant switching. Existing-user invitation acceptance now requires that user's authenticated identity and preserves their password/global authority.
- **Order:** `TenantWorkflowService`/`TenantWorkflowController` extend the existing tenant model, presets, pickup locations, audit logs and outbox. Local registration data commits atomically; protected Auth owner provisioning follows the commit. A remote failure returns a retryable error, and retrying the original registration key recovers the same tenant; the durable creation event also provisions membership. `OperationalReadinessService` guards order creation and driver assignments, including event-driven assignment. `TenantRequestInterceptor` verifies JWT identity/live membership before legacy controllers and scopes order/task access.
- **Driver/Routing:** Driver membership checks verify current identity and tenant roles through Auth, with tenant guards on driver/vehicle access. Routing API checks tenant membership and readiness for real dispatch/planning; internal background reads use the private service credential and configuration failures disable automatic dispatch rather than defaulting to enabled.
- **Notification:** the existing service now has a durable RabbitMQ consumer, `processed_events` deduplication, encrypted pending email content, retryable SMTP delivery and a Docker build/service definition. Producer startup also declares the durable notification queue. Transport/email delivery is at-least-once; provider-level exactly-once SMTP delivery is not claimed.
- **Web Admin:** `/organizations`, `/verify-email`, `/onboarding`, `/admin/tenant-verifications`; navbar organization switching; saved organization context restored after refresh; a full navigation discards mounted tenant-specific state on switching. Onboarding resumes at its next incomplete step and edits an existing default pickup point. Review actions require confirmation and negative decisions require feedback.

No subscription, entitlement, billing, quota or payment-plan module was added. Existing subscription metadata remains intact. Verification documents are not mandatory in the current domain; no ungrounded document requirement or upload infrastructure was introduced.

## New and extended APIs

All workflow responses use the existing `{data,error,timestamp}` envelope. Existing snake_case requests remain the convention; tenant registration also accepts the camelCase fields from the supplied prompt.

| API | Purpose |
|---|---|
| `POST /api/auth/email/verify` | Consume a verification token once |
| `POST /api/auth/email/resend` | Authenticated, rate-limited resend |
| `GET /api/auth/me` | Adds `email_verified` |
| `GET /api/auth/context?tenantId=...` | Authoritative live identity/membership context |
| `GET /api/auth/tenants` | Active memberships for the authenticated account |
| `POST /api/auth/tenants/{id}/switch` | Issue a tenant-scoped access token; refresh format is unchanged |
| `POST /api/tenants` | Verified registration; requires `Idempotency-Key` |
| `GET /api/tenants/{id}` | Tenant visible to active members or explicit platform authority |
| `GET/PUT /api/tenants/{id}/onboarding` | Persistent, server-derived progress and OWNER configuration |
| `GET /api/tenants/{id}/operational-readiness` | Current business prerequisites |
| `GET/POST /api/tenants/{id}/verification-requests` | History and idempotent OWNER submission |
| `GET /api/tenants/verification-requests` | System review queue |
| `POST /api/tenants/verification-requests/{id}/review` | Version-checked administrative decision |
| `PATCH /api/tenants/{id}/status` | Controlled suspension/reactivation; initial approval goes through review |

The tenant workflow controller also serves `/api/v1/tenants`. Existing pickup, settings, member, login and refresh endpoints are retained. Creating a tenant now requires verified email, phone/country information and a retry key; direct initial status approval is intentionally replaced by the verification workflow. Internal owner provisioning and fleet lookup are service-key protected and are not routed by the public gateway.

## Migrations and rollout prerequisites

- Auth `V5__tenant_identity_and_verification_delivery.sql`: encrypted delivery outbox, legacy role/membership mapping, active workspace access for legacy PENDING OWNER memberships. Explicit suspended/revoked memberships are preserved.
- Order `V10__tenant_onboarding_and_verification.sql`: additive profile/mode/settings fields, registration retry keys, progress and verification requests, a partial unique index preventing duplicate open requests. Review versions prevent concurrent decisions; historical snapshots/reasons are retained.
- Notification `V2__tenant_notification_delivery.sql`: SMTP recipient, encrypted delivery body and retry scheduling. An empty notification database first runs the existing V1 schema.

This machine's `auth_db` and `order_db` contain schemas without Flyway history. Automatic baselining is now disabled, and Hibernate validates schemas rather than changing them. **Before starting the updated services against these databases**, confirm the current schema matches Auth V4 and Order V9, then perform a deliberate one-time baseline with `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`, `AUTH_FLYWAY_BASELINE_VERSION=4`, and `ORDER_FLYWAY_BASELINE_VERSION=9`. These values are passed through Compose. Remove the baseline-on-migrate override once history exists. Clean databases run their full migration chain without this override. Do not baseline an older database at these versions.

Set the same private `TENANT_INTERNAL_KEY` for Auth, Order, Driver and Routing; set a consistent JWT secret for signing/validation and encrypted email delivery. Configure `WEB_APP_URL` and `SMTP_*`. The notification service must be running for actual email delivery. Shared Pool is explicitly unavailable; OWN_FLEET requires a suitable active dedicated driver for assignment, and HYBRID can use its eligible owned fleet. Existing legacy depot coordinates are accepted as a pickup source.

Legacy `users.tenant_id` remains a default, not authorization. Auth backfills known legacy role mappings; unknown legacy CUSTOMER memberships become VIEWER. If historical data lacks an OWNER membership, reconcile it against Order's canonical `owner_user_id` through an explicit administrative migration rather than granting arbitrary users OWNER authority. Legacy global ADMIN remains a platform authority for compatibility; newly invited tenant ADMIN maps to TENANT_ADMIN and cannot perform system review.

## Verification and handoff

- Source archive and checksums were verified. Auth, Order and Notification database dumps were restored successfully to isolated databases; candidate migrations ran there, not against application databases. Backup artifacts and restore metadata are in the approved backup directory above.
- A previous HTTP smoke attempt started Auth/Order against restored databases, then stopped on a registration fixture missing legacy required fields; it did not establish end-to-end success. The temporary services were stopped.
- The user subsequently requested **no new automated testcases**. Newly created testcases/runners were removed and edited existing test files restored from the initial backup. Earlier automated results are intermediate evidence only.
- Browser connection was attempted using the Browser skill; no connected browser was available. Interactive UI and actual SMTP/RabbitMQ end-to-end delivery remain unverified.
- Final build/static-check results are recorded in the task ledger and backup logs. The updated application services and migrations have **not** been deployed to the databases/services currently serving localhost.

## Manual web verification after local rollout

1. Open `/register`, register an account, and sign in. Open `/organizations`: creating an organization must be disabled until email verification. Follow the email link at `/verify-email`; reusing it must fail. An immediate resend should be rate-limited.
2. Create an organization. It should appear as PENDING with an active OWNER workspace. Retry a timed-out creation with the same retry key: there must be one tenant and one membership.
3. Open `/onboarding`; confirm business details, select a pickup point, choose a delivery mode and save settings. Leave and sign in again: saved settings and progress should remain, with the recommended incomplete step selected.
4. Submit for review. Use a separate System Admin account at `/admin/tenant-verifications` to request changes with a reason. The owner should see the feedback and be able to edit/resubmit; both snapshots must remain in history.
5. Approve the resubmission. The tenant should become APPROVED. Complete driver setup for OWN_FLEET and try the normal order/assignment flow. No subscription/payment activation should be required.
6. Select SHARED_POOL: readiness must report unavailable shared capacity without `NO_ACTIVE_DRIVER`. Suspend an approved tenant: new operations must fail while existing delivery records remain.
7. Use two accounts and two tenants to check isolation, role-specific owner actions and switching. An OWNER/OPERATOR of one tenant must not review businesses or access the other's private data. Check that switching replaces the previous workspace's displayed data.

Final build status: all five affected Java services packaged successfully with tests skipped; Web Admin production build and targeted ESLint passed; Routing syntax and Compose configuration checks passed. No connected browser was available for UI verification.


## Separate portal entry points (2026-11-04)

Tenant users sign in at `/login` and use the Business Workspace. Platform users sign in at `/admin/login` and use `/admin/tenant-verifications` with a separate administration layout/menu. System administrators are redirected out of tenant pages; tenant users cannot render the protected admin console. `/tenant-verifications` redirects old bookmarks to the new URL. Backend review authorization continues to use Auth's actual database system role, independent of tenant membership.

Both entry points reuse the existing Auth Service and browser session. They are distinct application areas, not two authentication systems; use separate browser profiles/incognito for simultaneously testing two accounts. The platform console has no public signup page and does not require an organization membership. New System Admin accounts are provisioned manually by the operator, as described in `docs/platform-admin-setup.md`.


## Local runtime repair and rollout — 2026-11-04

Task 019 resolved the admin review UUID error. The running Order image lacked TenantWorkflowController and treated the static review URL as /{tenantId}. The local images were rebuilt and deployed after fresh database exports and restoration checks. Auth/Order/Notification now include Boot's managed Flyway starter so startup actually runs migrations. Auth was explicitly baselined at V4 and migrated through V6, Order baselined at V9 and migrated to V10, and the empty notification schema migrated through V2. Driver's existing V4 history was retained. One-time baseline mode was disabled after adoption.

The current Auth model/database had already removed users.tenant_id; V5 now checks for the legacy column before backfilling instead of recreating it. An invalid duplicate tenant accessor in Driver's update DTO path was removed, retaining the check against the persisted driver record. The private internal service credential was configured without exposing its value. Backup/rollback artifacts: C:/D/swiftroute-backups/verification-route-fix-20261104-001942.

Gateway checks of /api/tenants/verification-requests and /api/v1/tenants/verification-requests returned HTTP 200 for platform authority, HTTP 403 for a customer and HTTP 401 without authentication. Lists were empty; no account, role or request was created/changed for those checks. Full interactive onboarding/approval still requires the user's manual web verification. Actual email delivery remains pending SMTP configuration. No automated testcase files were added or executed.
