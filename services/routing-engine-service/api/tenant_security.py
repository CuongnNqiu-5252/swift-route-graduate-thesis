"""Stateless JWT verification and tenant scoping for public routing APIs."""
import os

import jwt
from fastapi import HTTPException, Request

DEFAULT_JWT_SECRET = "your_super_secret_key_change_in_production"


def _decode_jwt(authorization: str) -> dict:
    if not authorization.startswith("Bearer "):
        raise HTTPException(401, "Authentication required")
    token = authorization[7:].strip()
    secret = os.getenv("JWT_SECRET", DEFAULT_JWT_SECRET)
    try:
        return jwt.decode(token, secret, algorithms=["HS256", "HS384", "HS512"])
    except jwt.PyJWTError:
        raise HTTPException(401, "Invalid or expired token")


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

    claims = _decode_jwt(request.headers.get("authorization", ""))
    system_role = str(claims.get("system_role") or "").upper()
    admin = system_role == "SYSTEM_ADMIN"
    membership_role = str(claims.get("tenant_role") or "").upper()
    token_tenant = claims.get("tenant_id")

    tenant = request.headers.get("x-tenant-id") or token_tenant
    body = {}
    if request.method == "POST":
        try:
            body = await request.json()
        except ValueError:
            pass
    requested = body.get("tenant_id") or request.path_params.get("tenant_id") or request.query_params.get("tenant_id")
    if requested and token_tenant and requested != token_tenant and not admin:
        raise HTTPException(403, "Cross-tenant routing request denied")
    if requested and tenant and requested != tenant and not admin:
        raise HTTPException(403, "Cross-tenant routing request denied")
    tenant = requested or tenant

    if request.method != "GET" and membership_role == "VIEWER" and not admin:
        raise HTTPException(403, "Read-only membership")
