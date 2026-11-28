"""
Pytest configuration and deterministic fixtures for SwiftRoute E2E test suite.
"""

from datetime import datetime, timezone
from typing import Callable, Optional
from jose import jwt
import pytest

from tests.e2e.client.dual_client import DualClient
from tests.e2e.config import IDS, JWT_SECRET, JWT_ALGORITHM, DeterministicIDs


def create_token(
    user_id: str,
    system_role: str = "USER",
    tenant_id: Optional[str] = None,
    tenant_role: Optional[str] = None,
    expires_in: int = 900,
    expired: bool = False,
    iat: Optional[int] = None,
    secret: str = JWT_SECRET,
    algorithm: str = JWT_ALGORITHM,
) -> str:
    """Generate a deterministic signed JWT token matching JwtVerifier claims."""
    current_time = int(datetime.now(timezone.utc).timestamp())
    token_iat = current_time if iat is None else iat
    if expired:
        exp = token_iat - 3600
    else:
        exp = token_iat + expires_in

    claims = {
        "sub": user_id,
        "system_role": system_role,
        "tenant_id": tenant_id,
        "tenant_role": tenant_role,
        "iat": token_iat,
        "exp": exp,
    }
    return jwt.encode(claims, secret, algorithm=algorithm)


@pytest.fixture(scope="session")
def test_ids() -> DeterministicIDs:
    """Provide deterministic UUID constants."""
    return IDS


@pytest.fixture(scope="function")
def client() -> DualClient:
    """Provide a fresh dual client with isolated state for every test."""
    dual = DualClient()
    dual.reset_state()
    return dual


@pytest.fixture
def token_factory() -> Callable[..., str]:
    """Provide a factory function for custom token generation."""
    return create_token


@pytest.fixture
def owner_token() -> str:
    return create_token(
        user_id=IDS.OWNER_USER_ID,
        system_role="USER",
        tenant_id=IDS.TENANT_A_ID,
        tenant_role="OWNER",
    )


@pytest.fixture
def admin_token() -> str:
    return create_token(
        user_id=IDS.ADMIN_USER_ID,
        system_role="USER",
        tenant_id=IDS.TENANT_A_ID,
        tenant_role="TENANT_ADMIN",
    )


@pytest.fixture
def operator_token() -> str:
    return create_token(
        user_id=IDS.OPERATOR_USER_ID,
        system_role="USER",
        tenant_id=IDS.TENANT_A_ID,
        tenant_role="OPERATOR",
    )


@pytest.fixture
def driver_token() -> str:
    return create_token(
        user_id=IDS.DRIVER_USER_ID,
        system_role="USER",
        tenant_id=IDS.TENANT_A_ID,
        tenant_role="DRIVER",
    )


@pytest.fixture
def driver_2_token() -> str:
    return create_token(
        user_id=IDS.DRIVER_2_USER_ID,
        system_role="USER",
        tenant_id=IDS.TENANT_A_ID,
        tenant_role="DRIVER",
    )


@pytest.fixture
def viewer_token() -> str:
    return create_token(
        user_id=IDS.VIEWER_USER_ID,
        system_role="USER",
        tenant_id=IDS.TENANT_A_ID,
        tenant_role="VIEWER",
    )


@pytest.fixture
def system_admin_token() -> str:
    return create_token(
        user_id=IDS.SYSTEM_ADMIN_USER_ID,
        system_role="SYSTEM_ADMIN",
        tenant_id=None,
        tenant_role=None,
    )


@pytest.fixture
def unscoped_token() -> str:
    return create_token(
        user_id=IDS.UNSCOPED_USER_ID,
        system_role="USER",
        tenant_id=None,
        tenant_role=None,
    )


@pytest.fixture
def tenant_b_admin_token() -> str:
    return create_token(
        user_id=IDS.TENANT_B_ADMIN_USER_ID,
        system_role="USER",
        tenant_id=IDS.TENANT_B_ID,
        tenant_role="TENANT_ADMIN",
    )


@pytest.fixture
def tenant_b_driver_token() -> str:
    return create_token(
        user_id=IDS.TENANT_B_DRIVER_USER_ID,
        system_role="USER",
        tenant_id=IDS.TENANT_B_ID,
        tenant_role="DRIVER",
    )
