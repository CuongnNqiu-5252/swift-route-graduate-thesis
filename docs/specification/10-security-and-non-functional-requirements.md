# Security and non-functional requirements

## Security

- TLS at ingress; bearer JWT access token target 15 minutes, refresh token target 30 days (current config). Refresh tokens are hashed, rotated and revoke family on reuse.
- Gateway validates JWT and may inject signed/trusted identity context; services do not trust client-provided `X-User-*` headers. Enforce RBAC and tenant membership on every resource lookup.
- Tenant isolation: tenant ID derives from token/membership, all repositories tenant-scope queries, and integration tests attempt cross-tenant UUID access.
- Validate body/path/query, allow-list state transitions, parameterised ORM/SQL, output encoding in web, CORS allow-list, rate limits for login and write endpoints, request-size limits, and secrets only through secret manager/environment (never repository/default production secret).
- Database roles least privilege per service; encrypt backups and PII at rest; redact PII/tokens from logs. Dependency scans and SAST run in CI.

## Measurable targets requiring confirmation

These are proposed engineering targets, not promises from the current plan: 99.5% monthly availability for gateway/core command APIs excluding scheduled maintenance; p95 authenticated read <300 ms and simple write <500 ms under agreed load; 95% notification enqueue <60 seconds; outbox p95 age <30 seconds; route batch SLA defined by input size with benchmark evidence; RPO <=15 minutes and documented RTO <=4 hours. Validate target load/size and provider SLA with stakeholders before acceptance.

Health endpoints must distinguish liveness/readiness, use timeout/circuit breakers for synchronous calls, and fail independently. Backups, restore drill, schema migration rehearsal, queue DLQ alert, trace correlation, metrics and audit logs are release requirements.
