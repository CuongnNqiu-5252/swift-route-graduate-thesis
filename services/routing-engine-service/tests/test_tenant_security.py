from __future__ import annotations

import jwt
import pytest
from fastapi import FastAPI, Depends
from fastapi.testclient import TestClient

from api.tenant_security import DEFAULT_JWT_SECRET, require_tenant_access


def _make_app() -> FastAPI:
    app = FastAPI()

    @app.get("/api/v1/plans", dependencies=[Depends(require_tenant_access)])
    def list_plans(tenant_id: str | None = None):
        return {"ok": True, "tenant_id": tenant_id}

    @app.post("/api/v1/optimize/batch", dependencies=[Depends(require_tenant_access)])
    def optimize_batch():
        return {"ok": True}

    return app


def test_stateless_jwt_allows_matching_tenant() -> None:
    token = jwt.encode(
        {
            "sub": "user-1",
            "system_role": "USER",
            "tenant_id": "tenant-1",
            "tenant_role": "OWNER",
        },
        DEFAULT_JWT_SECRET,
        algorithm="HS256",
    )
    client = TestClient(_make_app())
    res = client.get("/api/v1/plans?tenant_id=tenant-1", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 200


def test_stateless_jwt_blocks_cross_tenant() -> None:
    token = jwt.encode(
        {
            "sub": "user-1",
            "system_role": "USER",
            "tenant_id": "tenant-1",
            "tenant_role": "OWNER",
        },
        DEFAULT_JWT_SECRET,
        algorithm="HS256",
    )
    client = TestClient(_make_app())
    res = client.get("/api/v1/plans?tenant_id=tenant-2", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 403


def test_stateless_jwt_rejects_invalid_signature() -> None:
    token = jwt.encode({"sub": "user-1"}, "wrong-secret", algorithm="HS256")
    client = TestClient(_make_app())
    res = client.post("/api/v1/optimize/batch", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 401
