# main.py
"""
SwiftRoute Routing Engine — FastAPI application entry point.
Runs on port 8084. API Gateway forwards /api/routing/** to this service.
Includes background RabbitMQ consumer for asynchronous order processing.
"""
from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.routes import router
from service.rabbitmq_consumer import start_consumer


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Manages application lifecycle:
      - Starts RabbitMQ consumer in background on startup
      - Gracefully terminates consumer task on shutdown
    """
    stop_event = asyncio.Event()
    consumer_task = asyncio.create_task(start_consumer(stop_event))
    yield
    stop_event.set()
    consumer_task.cancel()
    try:
        await consumer_task
    except (asyncio.CancelledError, Exception):
        pass


app = FastAPI(
    title="SwiftRoute Routing Engine",
    version="1.0.0",
    description=(
        "TSP/VRP route optimisation engine with Held-Karp Bitmask DP, "
        "Nearest Neighbor heuristic and Genetic Algorithm. "
        "Accepts depot + delivery stops, returns an optimised route with distances and ETA. "
        "Integrates with RabbitMQ for event-driven shift planning and plan persistence."
    ),
    lifespan=lifespan,
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
