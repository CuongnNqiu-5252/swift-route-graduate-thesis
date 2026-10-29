# api/routes.py
"""
FastAPI router exposing the Route Optimisation endpoints.
"""
from fastapi import APIRouter
from api.schemas import BatchOptimizationRequest, BatchOptimizationResponse
from service.optimizer_service import solve_batch

router = APIRouter(tags=["Optimization"])


@router.get("/health", summary="Health check")
def health():
    """Returns service status and supported algorithm list."""
    return {
        "status": "ok",
        "service": "SwiftRoute Routing Engine",
        "algorithms": ["held_karp", "nearest_neighbor", "genetic", "auto"],
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

    Algorithm selection:
    - auto: uses Held-Karp for n <= 14 stops, Nearest Neighbor for larger sets.
    - held_karp: exact O(n² · 2^n) DP solver — best for n <= 14.
    - nearest_neighbor: O(n²) greedy heuristic — fast approximation.
    - genetic: evolutionary algorithm — good approximation for large n.
    """
    return solve_batch(request)
