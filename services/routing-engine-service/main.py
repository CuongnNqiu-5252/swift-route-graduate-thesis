# main.py
"""
SwiftRoute Routing Engine — FastAPI application entry point.
Runs on port 8084. API Gateway forwards /api/routing/** to this service.
Includes background RabbitMQ consumer for asynchronous order processing.
"""
from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.routes import router
from service.rabbitmq_consumer import start_consumer

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")


logger = logging.getLogger("routing-engine.main")


async def periodic_dispatch_evaluator(stop_event: asyncio.Event) -> None:
    """
    Background timer loop that evaluates automated dispatch business rules
    (timeout, batch thresholds) every 15 seconds across all active tenants.
    """
    from service.auto_dispatcher import auto_dispatcher

    logger.info("Periodic automated dispatch evaluator loop started (interval=15s).")
    while not stop_event.is_set():
        try:
            await asyncio.sleep(15)
            if not stop_event.is_set():
                await auto_dispatcher.evaluate_all_tenants()
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error("Error in periodic automated dispatch evaluation: %s", e, exc_info=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Manages application lifecycle:
      - Starts RabbitMQ consumer in background on startup
      - Starts periodic automated dispatch evaluator in background
      - Gracefully terminates background tasks on shutdown
    """
    stop_event = asyncio.Event()
    consumer_task = asyncio.create_task(start_consumer(stop_event))
    evaluator_task = asyncio.create_task(periodic_dispatch_evaluator(stop_event))
    yield
    stop_event.set()
    consumer_task.cancel()
    evaluator_task.cancel()
    try:
        await asyncio.gather(consumer_task, evaluator_task, return_exceptions=True)
    except Exception:
        pass


app = FastAPI(
    title="SwiftRoute Routing Engine",
    version="1.0.0",
    description=(
        "Industrial TSP/VRP route optimisation engine powered by Google OR-Tools. "
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
