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


def test_empty_internal_key_blocks_x_internal_key_header(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("TENANT_INTERNAL_KEY", "")
    client = TestClient(_make_app())
    res = client.post("/api/v1/optimize/batch", headers={"x-internal-key": "any-attempted-key"})
    assert res.status_code == 401
    assert "Invalid internal key" in res.json()["detail"]


def test_valid_internal_key_allows_access(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("TENANT_INTERNAL_KEY", "expected-secret-key")
    client = TestClient(_make_app())
    res = client.post("/api/v1/optimize/batch", headers={"x-internal-key": "expected-secret-key"})
    assert res.status_code == 200
    assert res.json() == {"ok": True}


def test_invalid_internal_key_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("TENANT_INTERNAL_KEY", "expected-secret-key")
    client = TestClient(_make_app())
    res = client.post("/api/v1/optimize/batch", headers={"x-internal-key": "wrong-secret-key"})
    assert res.status_code == 401
    assert "Invalid internal key" in res.json()["detail"]


def test_viewer_role_blocked_from_optimization() -> None:
    token = jwt.encode(
        {
            "sub": "viewer-user",
            "system_role": "USER",
            "tenant_id": "tenant-1",
            "tenant_role": "VIEWER",
        },
        DEFAULT_JWT_SECRET,
        algorithm="HS256",
    )
    client = TestClient(_make_app())
    res = client.post(
        "/api/v1/optimize/batch",
        headers={"Authorization": f"Bearer {token}"},
        json={"tenant_id": "tenant-1"},
    )
    assert res.status_code == 403
    assert "Read-only membership" in res.json()["detail"]


def test_driver_role_allowed_for_optimization() -> None:
    token = jwt.encode(
        {
            "sub": "driver-user",
            "system_role": "USER",
            "tenant_id": "tenant-1",
            "tenant_role": "DRIVER",
        },
        DEFAULT_JWT_SECRET,
        algorithm="HS256",
    )
    client = TestClient(_make_app())
    res = client.post(
        "/api/v1/optimize/batch",
        headers={"Authorization": f"Bearer {token}"},
        json={"tenant_id": "tenant-1"},
    )
    assert res.status_code == 200
    assert res.json() == {"ok": True}

