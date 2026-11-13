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
    vehicle_count: int = Field(1, ge=1, le=50, description="Number of available delivery vehicles/drivers for CVRP partitioning")
    vehicle_capacities: Optional[List[float]] = Field(None, description="Optional cargo capacity (kg) per vehicle")
    max_stops_per_vehicle: Optional[int] = Field(None, ge=1, le=100, description="Optional maximum stops per vehicle")

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


class VehicleRoute(BaseModel):
    vehicle_index: int
    total_distance_km: float
    total_duration_minutes: float
    total_load_kg: float
    ordered_stops: List[OrderedStop]


class BatchOptimizationResponse(BaseModel):
    job_id: str
    algorithm_used: str
    total_distance_km: float
    total_duration_minutes: float
    ordered_stops: List[OrderedStop]
    vehicle_routes: List[VehicleRoute] = Field(default_factory=list)
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

