# main.py
"""
SwiftRoute Routing Engine — FastAPI application entry point.
Runs on port 8084. API Gateway forwards /api/routing/** to this service.
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.routes import router

app = FastAPI(
    title="SwiftRoute Routing Engine",
    version="1.0.0",
    description=(
        "TSP/VRP route optimisation engine with Held-Karp Bitmask DP, "
        "Nearest Neighbor heuristic and Genetic Algorithm. "
        "Accepts depot + delivery stops, returns an optimised route with distances and ETA."
    ),
    docs_url="/docs",
    redoc_url="/redoc",
)

# Allow calls from API Gateway, Swagger UI, and mobile app during local development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router, prefix="/api/v1")


@app.get("/health", tags=["Health"])
def root_health():
    """Top-level health probe used by load balancers and Docker health checks."""
    return {"status": "ok"}
