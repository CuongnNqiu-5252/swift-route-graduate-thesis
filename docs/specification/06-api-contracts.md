# API contracts

## Common contract

All public APIs are gateway paths and JSON. Existing services use `ApiResponse<T>`; target shape is `{ "success": true, "data": {}, "message": null }` and error `{ "success": false, "error": {"code":"...","message":"...","trace_id":"..."} }`. Gateway derives identity from bearer JWT and injects trusted headers; services reject direct untrusted header use. Collection endpoints use `limit` (1–100, default 20) and opaque `cursor`/`next_cursor`—current list endpoints lack pagination and must be upgraded.

## Implemented/partial endpoints

| Method/path | Auth | Request / validation | Result |
|---|---|---|---|
| `POST /api/auth/register` | Public | email, password min 6, full_name, phone_number, address/city/state/zip/country required | `201 ApiResponse<void>`; 409 duplicate email. |
| `POST /api/auth/login` | Public | `{username,password}`; validation to be added | `200` access/refresh token response; 401 invalid credentials. |
| `POST /api/auth/refresh?refreshToken=` | Public | refresh token | `200` rotated token pair; 401 expired/reused. |
| `POST /api/auth/logout?refreshToken=` | Public | refresh token | `200`; revokes token. |
| `POST /api/tenants` | Authenticated | business name 2–255, type, address, lat/lng ranges, weights summing 1 | `201 tenant`; role policy needs correction. |
| `GET /api/tenants/{tenant_id}` | owner/platform role | UUID | `200 tenant`; 403/404. |
| `PATCH /api/tenants/{tenant_id}/status` | platform reviewer | `{status,reason?}`; rejection requires reason | `200 tenant`; 409 invalid transition. |
| `POST /api/orders` | authenticated/authorised | pickup/delivery coords, address <=500, ordered time window, weight >0, priority | `201 order`; add `Idempotency-Key` and remove client tenant override. |
| `GET /api/orders/me` | customer | none | `200 order[]`; add pagination. |
| `GET /api/orders/{order_id}` | owner or tenant/platform authority | UUID | `200 order`; current membership check needs correction. |
| `GET /api/orders/tenant/{tenant_id}` | authorised tenant/platform | UUID | `200 order[]`; add pagination. |
| `PATCH /api/orders/{order_id}/status` | authorised actor | target `OrderStatus` currently raw body | `200`; replace with `{status,reason,version}`. |

## Proposed API contracts

| Method/path | Purpose | Required role | Response |
|---|---|---|---|
| `POST /api/drivers` | register driver/vehicle | tenant operator | `201 driver` |
| `PATCH /api/drivers/{id}/availability` | update availability | self/operator | `200 driver` |
| `POST /api/drivers/{id}/delivery-events` | report delivery outcome | assigned driver | `202 accepted` |
| `POST /api/routing/optimizations` | start batch/insert job; `Idempotency-Key` | operator | `202 {job_id,status}` |
| `GET /api/routing/optimizations/{job_id}` | job/result status | tenant operator | `200 job` |
| `GET /api/route-plans/{id}` | route plan/stops | assigned driver/operator | `200 plan` |

`POST /api/routing/optimizations` payload contains `tenant_id` only from trusted context, order/driver IDs and expected versions or a server-built snapshot; it never accepts arbitrary cross-tenant IDs. 400 is malformed input, 401 no/invalid token, 403 scope violation, 404 resource absent, 409 stale state/version, 422 infeasible optimisation, 429 rate limit, and 503 dependency unavailable. Retried creates always use the same idempotency key.
