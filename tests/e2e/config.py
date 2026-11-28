"""
Configuration module for SwiftRoute E2E security hardening tests.
Provides URLs, execution modes, secrets, and deterministic UUIDs for repeatable testing.
"""

import os
from dataclasses import dataclass


# Execution Mode: "auto", "live", or "mock"
# - "auto": probe Gateway on localhost:8000; fallback to mock contract client if unavailable
# - "live": strictly require live Docker microservices stack
# - "mock": purely in-process contract verification engine
TEST_MODE = os.getenv("SWIFTROUTE_TEST_MODE", "auto").lower()

# Service URLs (configurable via environment variables)
GATEWAY_URL = os.getenv("GATEWAY_URL", "http://localhost:8000")
AUTH_SERVICE_URL = os.getenv("AUTH_SERVICE_URL", "http://localhost:8081")
ORDER_SERVICE_URL = os.getenv("ORDER_SERVICE_URL", "http://localhost:8082")
DRIVER_SERVICE_URL = os.getenv("DRIVER_SERVICE_URL", "http://localhost:8083")
ROUTING_SERVICE_URL = os.getenv("ROUTING_SERVICE_URL", "http://localhost:8084")
NOTIFICATION_SERVICE_URL = os.getenv("NOTIFICATION_SERVICE_URL", "http://localhost:8086")

# Security Secrets & Keys
JWT_SECRET = os.getenv("JWT_SECRET", "your_super_secret_key_change_in_production")
JWT_ALGORITHM = "HS256"
DRIVER_SERVICE_KEY = os.getenv("DRIVER_SERVICE_KEY", "swiftroute_driver_internal_service_key")
ROUTING_INTERNAL_KEY = os.getenv("ROUTING_INTERNAL_KEY", "swiftroute_routing_internal_key")

# Standard Timeouts
REQUEST_TIMEOUT = float(os.getenv("SWIFTROUTE_TEST_TIMEOUT", "5.0"))
SSE_READ_TIMEOUT = float(os.getenv("SWIFTROUTE_SSE_TIMEOUT", "2.0"))


@dataclass(frozen=True)
class DeterministicIDs:
    """Pre-seeded deterministic UUIDs for test isolation and consistency."""

    # Tenants
    TENANT_A_ID: str = "11111111-1111-1111-1111-111111111111"
    TENANT_B_ID: str = "22222222-2222-2222-2222-222222222222"
    TENANT_SUSPENDED_ID: str = "33333333-3333-3333-3333-333333333333"
    TENANT_PENDING_ID: str = "44444444-4444-4444-4444-444444444444"

    # Users in Tenant A
    OWNER_USER_ID: str = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    ADMIN_USER_ID: str = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    OPERATOR_USER_ID: str = "cccccccc-cccc-cccc-cccc-cccccccccccc"
    DRIVER_USER_ID: str = "dddddddd-dddd-dddd-dddd-dddddddddddd"
    DRIVER_2_USER_ID: str = "dddddddd-dddd-dddd-dddd-dddddddd2222"
    VIEWER_USER_ID: str = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"

    # Platform & Cross-Tenant Users
    SYSTEM_ADMIN_USER_ID: str = "ffffffff-ffff-ffff-ffff-ffffffffffff"
    UNSCOPED_USER_ID: str = "99999999-9999-9999-9999-999999999999"
    TENANT_B_ADMIN_USER_ID: str = "bbbbbbbb-bbbb-bbbb-bbbb-222222222222"
    TENANT_B_DRIVER_USER_ID: str = "dddddddd-dddd-dddd-dddd-222222222222"

    # Multi-tenant user (member of both Tenant A and Tenant B)
    MULTI_TENANT_USER_ID: str = "abababab-abab-abab-abab-abababababab"

    # Orders
    ORDER_1_ID: str = "aaaaaaaa-1111-4000-8000-000000000001"  # Tenant A, assigned to DRIVER_USER_ID
    ORDER_2_ID: str = "aaaaaaaa-2222-4000-8000-000000000002"  # Tenant A, unassigned or assigned to DRIVER_2
    ORDER_TENANT_B_ID: str = "aaaaaaaa-bbbb-4000-8000-000000000001"  # Tenant B order

    # Dispatch Plans
    PLAN_1_ID: str = "bbbbbbbb-1111-4000-8000-000000000001"  # Tenant A, assigned to DRIVER_USER_ID
    PLAN_2_ID: str = "bbbbbbbb-2222-4000-8000-000000000002"  # Tenant A, assigned to DRIVER_2_USER_ID
    PLAN_TENANT_B_ID: str = "bbbbbbbb-bbbb-4000-8000-000000000001"  # Tenant B plan

    # Settlements
    SETTLEMENT_1_ID: str = "cccccccc-1111-4000-8000-000000000001"
    SETTLEMENT_TENANT_B_ID: str = "cccccccc-bbbb-4000-8000-000000000001"


IDS = DeterministicIDs()
