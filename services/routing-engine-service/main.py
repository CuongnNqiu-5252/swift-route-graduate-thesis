# main.py
"""
SwiftRoute Routing Engine — FastAPI application entry point.
Runs on port 8084. API Gateway forwards /api/routing/** to this service.
Stateless route optimisation microservice called by Order Service.
"""
from __future__ import annotations

from contextlib import asynccontextmanager
import logging
import os
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.routes import router
from service.grpc_server import start_grpc_server, stop_grpc_server

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")

logger = logging.getLogger("routing-engine.main")


def _load_root_env() -> None:
    """Populate missing env vars from workspace root .env for local non-Docker runs."""
    env_path = Path(__file__).resolve().parents[2] / ".env"
    if not env_path.is_file():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


_load_root_env()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Starts the gRPC server alongside FastAPI on startup and stops it on shutdown."""
    start_grpc_server()
    try:
        yield
    finally:
        stop_grpc_server()


app = FastAPI(
    title="SwiftRoute Routing Engine",
    version="1.0.0",
    description=(
        "Industrial TSP/VRP route optimisation engine powered by Google OR-Tools. "
        "Accepts depot + delivery stops, returns an optimised route with distances and ETA."
    ),
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
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
