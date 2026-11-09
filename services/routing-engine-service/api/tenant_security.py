"""Live membership and operation readiness checks for public routing APIs."""
import os
from urllib.parse import urlencode
import httpx
from fastapi import HTTPException, Request

async def require_tenant_access(request: Request):
    path = request.url.path.removeprefix("/api/v1")
    if path == "/health" or request.method == "OPTIONS":
        return

    # Allow internal service-to-service calls (e.g., from order-service via X-Internal-Key)
    internal_key = os.getenv("TENANT_INTERNAL_KEY", "")
    req_internal_key = request.headers.get("x-internal-key")
    if req_internal_key is not None:
        if not internal_key or req_internal_key == internal_key:
            return

    authorization = request.headers.get("authorization", "")
    if not authorization.startswith("Bearer "):
        raise HTTPException(401, "Authentication required")
    tenant = request.headers.get("x-tenant-id")
    body = {}
    if request.method == "POST":
        try:
            body = await request.json()
        except ValueError:
            pass
    requested = body.get("tenant_id") or request.path_params.get("tenant_id") or request.query_params.get("tenant_id")
    if requested and tenant and requested != tenant:
        raise HTTPException(403, "Cross-tenant routing request denied")
    tenant = requested or tenant
    if "plan_id" in request.path_params:
        from service.plan_store import plan_store
        plan = plan_store.get_plan(request.path_params["plan_id"])
        if not plan:
            raise HTTPException(404, "Plan not found")
        if tenant and plan.tenant_id != tenant:
            raise HTTPException(403, "Cross-tenant plan access denied")
        tenant = plan.tenant_id
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            url = os.getenv("AUTH_SERVICE_URL", "http://auth-service:8081") + "/api/auth/context"
            response = await client.get(url, params={"tenantId": tenant} if tenant else {}, headers={"Authorization": authorization})
            if response.status_code != 200:
                raise HTTPException(response.status_code if response.status_code in (401,403) else 503, "Membership check failed")
            context = response.json()["data"]
            admin = context.get("system_role") in ("SYSTEM_ADMIN", "ADMIN")
            if path in ("/dispatch/auto/evaluate", "/dispatch/auto/status", "/simulate-orders", "/orders/seed-demo") and not admin:
                raise HTTPException(403, "System administrator required")
            if path in ("/orders/pending", "/plans") and not requested and not admin:
                raise HTTPException(400, "Explicit tenant_id filter required")
            if request.method != "GET" and context.get("membership_role") == "VIEWER":
                raise HTTPException(403, "Read-only membership")
            if request.method == "POST" and ("/dispatch" in path or path == "/plans/optimize-shift") and tenant:
                readiness = await client.get(os.getenv("ORDER_SERVICE_URL", "http://order-service:8082") + f"/api/tenants/{tenant}/operational-readiness", headers={"Authorization": authorization})
                if readiness.status_code != 200 or not readiness.json().get("data", {}).get("ready_for_operations"):
                    raise HTTPException(409, "Tenant is not ready for delivery operations")
    except httpx.RequestError:
        raise HTTPException(503, "Authorization service unavailable")
