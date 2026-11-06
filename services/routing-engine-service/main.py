# main.py
"""
SwiftRoute Routing Engine — FastAPI application entry point.
Runs on port 8084. API Gateway forwards /api/routing/** to this service.
Stateless route optimisation microservice called by Order Service.
"""
from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.routes import router

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")

logger = logging.getLogger("routing-engine.main")


app = FastAPI(
    title="SwiftRoute Routing Engine",
    version="1.0.0",
    description=(
        "Industrial TSP/VRP route optimisation engine powered by Google OR-Tools. "
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

# Include router with both /api/v1 prefix and root prefix for Gateway compatibility
app.include_router(router, prefix="/api/v1")
app.include_router(router)


@app.get("/health", tags=["Health"])
def root_health():
    """Top-level health probe used by load balancers and Docker health checks."""
    return {"status": "ok"}
