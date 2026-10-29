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
    algorithm: str = Field("auto", description="held_karp | nearest_neighbor | genetic | auto")

    @field_validator("algorithm")
    @classmethod
    def validate_algorithm(cls, v: str) -> str:
        allowed = {"held_karp", "nearest_neighbor", "genetic", "auto"}
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
