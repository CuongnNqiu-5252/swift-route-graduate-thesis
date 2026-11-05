# api/routes.py
"""
FastAPI router exposing Route Optimisation and Health Check endpoints.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from api.tenant_security import require_tenant_access

from api.schemas import (
    BatchOptimizationRequest,
    BatchOptimizationResponse,
    RouteInsertRequest,
    RouteInsertResponse,
    RouteReoptimizeRequest,
    RouteReoptimizeResponse,
    WhatIfOptimizationRequest,
    WhatIfOptimizationResponse,
)
from service.optimizer_service import (
    simulate_what_if,
    solve_batch,
    solve_insert,
    solve_reoptimize,
)

router = APIRouter(tags=["Optimization & Planning"], dependencies=[Depends(require_tenant_access)])


@router.get("/health", summary="Health check")
def health():
    """Returns service status and supported algorithm list."""
    return {
        "status": "ok",
        "service": "SwiftRoute Routing Engine",
        "algorithms": ["ortools", "auto"],
    }


@router.post(
    "/optimize/batch",
    response_model=BatchOptimizationResponse,
    summary="Batch route optimisation — depot + N delivery stops",
)
def optimize_batch(request: BatchOptimizationRequest) -> BatchOptimizationResponse:
    """
    Accepts a depot location and a list of delivery stops.
    Returns the optimised visitation order with cumulative distances and ETA.
    """
    return solve_batch(request)


@router.post(
    "/optimize/what-if",
    response_model=WhatIfOptimizationResponse,
    summary="Stateless What-If staffing comparison",
)
def optimize_what_if(request: WhatIfOptimizationRequest) -> WhatIfOptimizationResponse:
    """Compares two routing scenarios entirely in memory; no plan is saved."""
    return simulate_what_if(request)


@router.post(
    "/optimize/insert",
    response_model=RouteInsertResponse,
    summary="Dynamically insert a new order stop into an active route",
)
def optimize_insert(request: RouteInsertRequest) -> RouteInsertResponse:
    """
    Preserves completed stops and finds the optimal insertion index for a new
    order stop among remaining uncompleted stops using Google OR-Tools.
    """
    return solve_insert(request)


@router.post(
    "/optimize/reoptimize",
    response_model=RouteReoptimizeResponse,
    summary="Safely reoptimize remaining stops of an active route",
)
def optimize_reoptimize(request: RouteReoptimizeRequest) -> RouteReoptimizeResponse:
    """
    Preserves completed stops and reoptimizes remaining stops from the driver's
    current GPS position when a delay or disruption is detected.
    """
    return solve_reoptimize(request)
