"""
Data schemas and models for SwiftRoute E2E security hardening tests.
"""

from typing import Any, Dict, Generic, List, Optional, TypeVar
from pydantic import BaseModel, Field

T = TypeVar("T")


class ApiError(BaseModel):
    """Standard microservice error object."""
    code: Optional[str] = None
    message: Optional[str] = None


class ApiResponse(BaseModel, Generic[T]):
    """Standard SwiftRoute response wrapper: { data, error, timestamp }."""
    data: Optional[T] = None
    error: Optional[ApiError] = None
    timestamp: Optional[str] = None


class GatewayErrorResponse(BaseModel):
    """API Gateway error format: { status, error, message, path }."""
    status: int
    error: str
    message: str
    path: Optional[str] = None


class TokenClaims(BaseModel):
    """JWT Token claims extracted or signed."""
    sub: str
    system_role: str
    tenant_id: Optional[str] = None
    tenant_role: Optional[str] = None
    iat: Optional[int] = None
    exp: Optional[int] = None


class LoginRequest(BaseModel):
    username: str
    password: str


class RefreshTokenRequest(BaseModel):
    refresh_token: str
    tenant_id: Optional[str] = None


class TokenData(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "Bearer"
    expires_in: int = 900
    tenant_id: Optional[str] = None
    tenant_role: Optional[str] = None


class MemberInviteRequest(BaseModel):
    email: str
    role: str
    full_name: Optional[str] = None


class MemberSuspendRequest(BaseModel):
    reason: Optional[str] = "Administrative suspension"


class TenantSuspendedEvent(BaseModel):
    tenant_id: str
    event_type: str
    timestamp: Optional[str] = None


class OrderCreateRequest(BaseModel):
    recipient_name: str
    recipient_phone: str
    delivery_address: str
    cod_amount: float = 0.0
    notes: Optional[str] = None


class OrderCancelRequest(BaseModel):
    reason: str


class OrderResponse(BaseModel):
    id: str
    tenant_id: str
    status: str
    assigned_driver_id: Optional[str] = None
    recipient_name: Optional[str] = None
    cod_amount: Optional[float] = 0.0


class PlanAcknowledgeRequest(BaseModel):
    notes: Optional[str] = None


class PlanDeclineRequest(BaseModel):
    reason: str


class PlanResponse(BaseModel):
    id: str
    tenant_id: str
    driver_id: str
    status: str
    stops: Optional[List[Dict[str, Any]]] = None


class IncidentReportRequest(BaseModel):
    order_id: str
    incident_type: str  # e.g., ACCIDENT, TRAFFIC_DELAY, PACKAGE_DAMAGED
    description: str


class SettlementApprovalRequest(BaseModel):
    notes: Optional[str] = "Approved by manager"


class SettlementPayoutRequest(BaseModel):
    payment_reference: str
    payment_method: str = "BANK_TRANSFER"


class SettlementResponse(BaseModel):
    id: str
    tenant_id: str
    driver_id: str
    amount: float
    status: str  # PENDING, APPROVED, PAID


class OptimizationBatchRequest(BaseModel):
    depot: Dict[str, float] = Field(default_factory=lambda: {"lat": 10.7769, "lng": 106.7009})
    orders: List[Dict[str, Any]] = Field(default_factory=list)
    vehicles: List[Dict[str, Any]] = Field(default_factory=list)


class OptimizationResponse(BaseModel):
    route_id: str
    total_distance: float
    stops: List[Dict[str, Any]] = Field(default_factory=list)


class DispatchAlert(BaseModel):
    id: str
    tenant_id: str
    severity: str
    message: str
    timestamp: Optional[str] = None
