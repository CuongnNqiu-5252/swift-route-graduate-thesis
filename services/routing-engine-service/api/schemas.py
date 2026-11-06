# api/schemas.py
"""
Pydantic request/response models for the Routing Engine API.
All coordinate fields use GPS decimal degrees (WGS-84).
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import List, Optional

from pydantic import BaseModel, Field, field_validator


class LocationPoint(BaseModel):
    lat: float = Field(..., ge=-90.0, le=90.0, description="Latitude in decimal degrees")
    lng: float = Field(..., ge=-180.0, le=180.0, description="Longitude in decimal degrees")
    address: Optional[str] = Field(None, max_length=500)


class StopItem(BaseModel):
    id: str = Field(..., description="Client-side stop identifier (e.g. order UUID)")
    name: Optional[str] = None
    location: LocationPoint
    demand_kg: float = Field(0.0, ge=0.0)
    time_window_start: Optional[str] = None
    time_window_end: Optional[str] = None


class BatchOptimizationRequest(BaseModel):
    depot: LocationPoint
    stops: List[StopItem] = Field(..., min_length=1)
    algorithm: str = Field("auto", description="auto | ortools")
    time_weight: Optional[float] = Field(0.5, ge=0.0, le=1.0, description="Weight for delivery time in multi-objective function")
    cost_weight: Optional[float] = Field(0.5, ge=0.0, le=1.0, description="Weight for distance/cost in multi-objective function")

    @field_validator("algorithm")
    @classmethod
    def validate_algorithm(cls, v: str) -> str:
        # Gracefully normalize any legacy algorithm names to ortools for backward compatibility
        if v in {"held_karp", "nearest_neighbor", "genetic"}:
            return "ortools"
        allowed = {"ortools", "auto"}
        if v not in allowed:
            raise ValueError(f"algorithm must be one of {allowed}")
        return v


class OrderedStop(BaseModel):
    stop_index: int
    stop_id: str
    location: LocationPoint
    cumulative_distance_km: float
    estimated_arrival_minutes: float


class BatchOptimizationResponse(BaseModel):
    job_id: str
    algorithm_used: str
    total_distance_km: float
    total_duration_minutes: float
    ordered_stops: List[OrderedStop]
    execution_time_ms: float
    timestamp: str


class WhatIfOptimizationRequest(BatchOptimizationRequest):
    """A dry-run comparison that never persists or dispatches a route."""

    baseline_driver_count: int = Field(3, ge=1, le=50)
    scenario_driver_count: int = Field(2, ge=1, le=50)
    max_shift_minutes: float = Field(150.0, gt=0.0, le=1_440.0)


class WhatIfScenario(BaseModel):
    driver_count: int
    total_distance_km: float
    completion_minutes: float
    late_risk_orders: int
    algorithm_used: str


class WhatIfOptimizationResponse(BaseModel):
    baseline: WhatIfScenario
    scenario: WhatIfScenario
    distance_delta_percent: float
    completion_delta_percent: float
    late_risk_delta: int


class PendingOrder(BaseModel):
    order_id: str
    tenant_id: str
    customer_id: Optional[str] = None
    pickup_lat: float
    pickup_lng: float
    pickup_address: Optional[str] = None
    delivery_lat: float
    delivery_lng: float
    delivery_address: Optional[str] = None
    weight_kg: float = 1.0
    priority: str = "NORMAL"  # NORMAL | HIGH | URGENT
    time_window_start: Optional[str] = None
    time_window_end: Optional[str] = None
    status: str = "PENDING_PAYMENT"
    shift: str = "MORNING"  # MORNING | AFTERNOON | EVENING | ALL
    received_at: str


class TenantDispatchConfig(BaseModel):
    auto_dispatch_enabled: bool = True
    batch_size_threshold: int = Field(5, ge=1, le=50)
    max_wait_time_seconds: int = Field(300, ge=30, le=3600)
    urgent_order_mode: str = Field("IMMEDIATE_SINGLE", description="IMMEDIATE_SINGLE | BATCH_IMMEDIATE")
    require_approval: bool = False
    max_orders_per_driver: int = Field(5, ge=1, le=20)
    algorithm: str = Field("auto", description="auto | ortools")
    allow_pool_drivers: bool = Field(False, description="Enable platform driver pool fallback")
    time_weight: Optional[float] = Field(0.5, ge=0.0, le=1.0)
    cost_weight: Optional[float] = Field(0.5, ge=0.0, le=1.0)


class AutoDispatchEvaluationResult(BaseModel):
    tenant_id: str
    triggered: bool
    reason: Optional[str] = None
    order_count: int = 0
    plan_id: Optional[str] = None
    dispatched: bool = False
    message: str


class TenantQueueStatus(BaseModel):
    tenant_id: str
    pending_paid_orders: int
    oldest_order_age_seconds: float
    has_urgent_order: bool
    is_optimizing: bool
    config: TenantDispatchConfig


class AutoDispatchStatusResponse(BaseModel):
    is_running: bool
    tenants: List[TenantQueueStatus]
    timestamp: str


class ShiftOptimizationRequest(BaseModel):
    tenant_id: str
    shift: str = Field("ALL", description="MORNING | AFTERNOON | EVENING | ALL")
    depot: Optional[LocationPoint] = None
    driver_ids: Optional[List[str]] = Field(
        default=None,
        description="Optional list of driver UUIDs to partition deliveries across"
    )
    algorithm: str = Field("auto", description="auto | ortools")


class DriverRoutePlan(BaseModel):
    driver_id: Optional[str] = None
    driver_name: Optional[str] = None
    total_distance_km: float
    total_duration_minutes: float
    ordered_stops: List[OrderedStop]


class RoutingPlan(BaseModel):
    plan_id: str
    tenant_id: str
    shift: str
    status: str = "DRAFT"  # DRAFT | APPROVED | DISPATCHED
    algorithm_used: str
    total_orders: int
    total_distance_km: float
    total_duration_minutes: float
    driver_routes: List[DriverRoutePlan]
    created_at: str
    dispatched_at: Optional[str] = None


class DispatchPlanResponse(BaseModel):
    plan_id: str
    status: str
    dispatched_orders_count: int
    assignments: List[dict]
    message: str


class RouteStopState(BaseModel):
    id: str = Field(..., description="Stop / order UUID")
    name: Optional[str] = None
    location: LocationPoint
    demand_kg: float = Field(0.0, ge=0.0)
    time_window_start: Optional[str] = None
    time_window_end: Optional[str] = None
    is_completed: bool = Field(False, description="True if driver already completed this stop")


class RouteInsertRequest(BaseModel):
    driver_location: LocationPoint = Field(..., description="Current GPS position of the active driver")
    current_stops: List[RouteStopState] = Field(..., description="Existing route stops with completion flags")
    new_stop: StopItem = Field(..., description="New order stop to dynamically insert into remaining route")
    max_added_duration_minutes: float = Field(30.0, gt=0.0, description="Max acceptable ETA increase in minutes")
    time_weight: Optional[float] = Field(0.5, ge=0.0, le=1.0)
    cost_weight: Optional[float] = Field(0.5, ge=0.0, le=1.0)


class RouteInsertResponse(BaseModel):
    job_id: str
    algorithm_used: str
    feasible: bool
    inserted_stop_index: int
    added_distance_km: float
    added_duration_minutes: float
    total_distance_km: float
    total_duration_minutes: float
    ordered_stops: List[OrderedStop]
    execution_time_ms: float
    timestamp: str


class RouteReoptimizeRequest(BaseModel):
    driver_location: LocationPoint = Field(..., description="Current GPS position of the active driver")
    current_stops: List[RouteStopState] = Field(..., min_length=1, description="Route stops with is_completed flags")
    reason: Optional[str] = Field("DELAY_DETECTED", description="Reason for reoptimization")
    min_improvement_km: float = Field(0.2, ge=0.0, description="Minimum distance saved to justify changing route")
    time_weight: Optional[float] = Field(0.5, ge=0.0, le=1.0)
    cost_weight: Optional[float] = Field(0.5, ge=0.0, le=1.0)


class RouteReoptimizeResponse(BaseModel):
    job_id: str
    algorithm_used: str
    stops_changed: bool
    distance_saved_km: float
    total_distance_km: float
    total_duration_minutes: float
    ordered_stops: List[OrderedStop]
    execution_time_ms: float
    timestamp: str

