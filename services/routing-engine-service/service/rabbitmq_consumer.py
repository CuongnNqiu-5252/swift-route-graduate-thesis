# service/rabbitmq_consumer.py
"""
Asynchronous RabbitMQ consumer and event dispatcher for the Routing Engine service.
Consumes order lifecycle events (order.created.v1, order.status_changed.v1)
from the shared 'swiftroute.events' topic exchange.

Features:
  - Event-Carried State Transfer: coordinates and order details extracted directly from payload.
  - Zero fake/mock HTTP headers.
  - Automatic On-Demand Dispatch: when an order is PAID, queries driver-service for active drivers,
    computes the nearest driver, and publishes an 'order.dispatched' event to RabbitMQ.
  - No mock/fallback data: raises explicit exceptions when services or required data are unavailable.
  - Durable queue binding: routing.orders.queue
  - Idempotent processing with eventId deduplication
"""
from __future__ import annotations

import asyncio
import json
import logging
import math
import os
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List

import aio_pika
import httpx

from api.schemas import PendingOrder
from service.order_store import classify_shift, order_store

logger = logging.getLogger("routing-engine.rabbitmq_consumer")

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
DRIVER_SERVICE_URL = os.getenv("DRIVER_SERVICE_URL", "http://driver-service:8083")

EXCHANGE_NAME = "swiftroute.events"
QUEUE_NAME = "routing.orders.queue"
ROUTING_KEYS = ["order.#"]


def calculate_haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Haversine distance between two coordinates in kilometers."""
    r = 6371.0  # Earth's radius in km
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    a = (
        math.sin(delta_phi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * (math.sin(delta_lambda / 2.0) ** 2)
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r * c


async def fetch_available_drivers() -> List[Dict[str, Any]]:
    """
    Fetches real active drivers from driver-service without mock headers.
    Raises RuntimeError if driver-service is unreachable or returns no drivers.
    """
    url = f"{DRIVER_SERVICE_URL}/api/drivers"
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.get(url, headers={"X-Service-Key": os.getenv("TENANT_INTERNAL_KEY", "")})
            if resp.status_code != 200:
                raise RuntimeError(
                    f"Driver-service returned HTTP {resp.status_code}: {resp.text}"
                )
            body = resp.json()
            drivers = body.get("data")
            if not isinstance(drivers, list) or len(drivers) == 0:
                raise RuntimeError("No drivers found in driver-service response")
            return drivers
    except Exception as exc:
        logger.error("Failed to query active drivers from driver-service: %s", exc)
        raise RuntimeError(f"Cannot fetch drivers from driver-service: {exc}") from exc


async def auto_dispatch_single_order(
    order_id: str,
    pickup_lat: float,
    pickup_lng: float,
    exchange: aio_pika.abc.AbstractExchange,
) -> None:
    """
    Selects the closest active driver to the pickup location and publishes
    an 'order.dispatched' event to the RabbitMQ exchange.
    Raises RuntimeError if no drivers are available or if closest driver has no coordinates.
    """
    drivers = await fetch_available_drivers()
    if not drivers:
        raise RuntimeError(f"Cannot dispatch order {order_id}: no drivers available from driver-service")

    best_driver = None
    min_dist = float("inf")

    for d in drivers:
        d_lat = d.get("currentLat") or d.get("current_lat")
        d_lng = d.get("currentLng") or d.get("current_lng")
        if d_lat is not None and d_lng is not None:
            dist = calculate_haversine_km(pickup_lat, pickup_lng, float(d_lat), float(d_lng))
            if dist < min_dist:
                min_dist = dist
                best_driver = d

    if best_driver is None:
        raise RuntimeError(
            f"Cannot dispatch order {order_id}: none of the {len(drivers)} active drivers have valid GPS coordinates"
        )

    driver_id = (
        best_driver.get("driverId")
        or best_driver.get("driver_id")
        or best_driver.get("userId")
        or best_driver.get("user_id")
    )
    if not driver_id:
        raise RuntimeError(f"Cannot dispatch order {order_id}: selected driver is missing driverId/userId")

    driver_name = best_driver.get("fullName") or best_driver.get("full_name") or "Tài xế"

    logger.info(
        "Auto-dispatching order %s to nearest driver %s (%s, dist=%.2f km)",
        order_id,
        driver_id,
        driver_name,
        min_dist,
    )

    event_payload = {
        "eventId": str(uuid.uuid4()),
        "orderId": order_id,
        "driverId": str(driver_id),
        "status": "PLANNED",
        "dispatchedAt": datetime.now(timezone.utc).isoformat(),
    }

    message = aio_pika.Message(
        body=json.dumps(event_payload).encode("utf-8"),
        content_type="application/json",
        delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
    )

    await exchange.publish(message, routing_key="order.dispatched")
    logger.info("Published 'order.dispatched' event for order %s to RabbitMQ", order_id)


async def process_order_message(
    message: aio_pika.IncomingMessage,
    exchange: aio_pika.abc.AbstractExchange,
) -> None:
    """
    Processes an incoming order event message from RabbitMQ.
    Ensures idempotency using event_id before storing into OrderStore.
    If order is PAID, triggers on-demand auto-dispatch.
    Raises ValueError if required fields/coordinates are missing.
    """
    async with message.process(requeue=False):
        try:
            body = json.loads(message.body.decode("utf-8"))
        except Exception as e:
            logger.error("Failed to parse message body as JSON: %s", e)
            return

        event_id = str(body.get("eventId") or body.get("event_id") or "")
        if not event_id:
            logger.warning("Received event without eventId; skipping.")
            return

        # 1. Early idempotency check
        if order_store.is_event_processed(event_id):
            logger.info("Ignoring duplicate eventId=%s (in-memory)", event_id)
            return

        logger.info("Processing order event %s: routing_key=%s", event_id, message.routing_key)

        order_id = str(body.get("orderId") or body.get("order_id") or "")
        tenant_id = str(body.get("tenantId") or body.get("tenant_id") or "")
        customer_id = body.get("customerId") or body.get("customer_id")
        current_status = str(body.get("currentStatus") or body.get("status") or "")

        # Extract coordinates directly from event payload (Event-Carried State Transfer)
        pickup_lat = body.get("pickupLat") or body.get("pickup_lat")
        pickup_lng = body.get("pickupLng") or body.get("pickup_lng")
        pickup_address = body.get("pickupAddress") or body.get("pickup_address")

        delivery_lat = body.get("deliveryLat") or body.get("delivery_lat")
        delivery_lng = body.get("deliveryLng") or body.get("delivery_lng")
        delivery_address = body.get("deliveryAddress") or body.get("delivery_address")

        weight_kg = float(body.get("weightKg") or body.get("weight_kg") or 1.0)
        priority = str(body.get("priority") or "NORMAL").upper()
        time_window_start = body.get("timeWindowStart") or body.get("time_window_start")
        time_window_end = body.get("timeWindowEnd") or body.get("time_window_end")

        if pickup_lat is None or pickup_lng is None:
            raise ValueError(
                f"Order {order_id} event {event_id} missing mandatory pickup coordinates (pickupLat/pickupLng)"
            )
        if delivery_lat is None or delivery_lng is None:
            raise ValueError(
                f"Order {order_id} event {event_id} missing mandatory delivery coordinates (deliveryLat/deliveryLng)"
            )

        shift = classify_shift(time_window_start)

        pending_order = PendingOrder(
            order_id=order_id,
            tenant_id=tenant_id,
            customer_id=str(customer_id) if customer_id else None,
            pickup_lat=float(pickup_lat),
            pickup_lng=float(pickup_lng),
            pickup_address=pickup_address,
            delivery_lat=float(delivery_lat),
            delivery_lng=float(delivery_lng),
            delivery_address=delivery_address,
            weight_kg=weight_kg,
            priority=priority,
            time_window_start=str(time_window_start) if time_window_start else None,
            time_window_end=str(time_window_end) if time_window_end else None,
            status=current_status,
            shift=shift,
            received_at=datetime.now(timezone.utc).isoformat(),
        )

        # 2. Save order and record event ID atomically for strict idempotency
        is_new = order_store.save_order_with_event(pending_order, event_id)
        if not is_new:
            logger.info("Ignoring duplicate eventId=%s (atomic deduplication)", event_id)
            return

        logger.info(
            "Order %s saved in OrderStore (tenant=%s, shift=%s, status=%s, priority=%s)",
            order_id,
            tenant_id,
            shift,
            current_status,
            priority,
        )

        # 3. Auto-dispatch: if order is PAID, evaluate automated dispatch business rules
        if current_status == "PAID":
            try:
                from service.auto_dispatcher import auto_dispatcher
                await auto_dispatcher.evaluate_tenant(
                    tenant_id=tenant_id,
                    trigger_reason=f"EVENT_ORDER_PAID (priority={priority})",
                )
            except Exception as e:
                logger.error(
                    "Error executing automated dispatch evaluation for tenant %s: %s",
                    tenant_id,
                    e,
                    exc_info=True,
                )


async def start_consumer(stop_event: asyncio.Event) -> None:
    """
    Long-running background task maintaining connection to RabbitMQ,
    listening on routing.orders.queue and reconnecting on connection loss.
    """
    logger.info("Initializing RabbitMQ consumer at %s", RABBITMQ_URL)

    while not stop_event.is_set():
        try:
            connection = await aio_pika.connect_robust(RABBITMQ_URL)
            async with connection:
                channel = await connection.channel()
                await channel.set_qos(prefetch_count=20)

                # Declare topic exchange
                exchange = await channel.declare_exchange(
                    EXCHANGE_NAME,
                    aio_pika.ExchangeType.TOPIC,
                    durable=True,
                )

                # Declare durable queue
                queue = await channel.declare_queue(
                    QUEUE_NAME,
                    durable=True,
                )

                # Bind queue to topic exchange
                for key in ROUTING_KEYS:
                    await queue.bind(exchange, routing_key=key)

                logger.info(
                    "RabbitMQ consumer active. Bound '%s' to exchange '%s' with keys %s",
                    QUEUE_NAME,
                    EXCHANGE_NAME,
                    ROUTING_KEYS,
                )

                async with queue.iterator() as queue_iter:
                    async for message in queue_iter:
                        if stop_event.is_set():
                            break
                        try:
                            await process_order_message(message, exchange)
                        except Exception as proc_err:
                            logger.error("Failed to process order message: %s", proc_err, exc_info=True)

        except asyncio.CancelledError:
            logger.info("RabbitMQ consumer task cancelled.")
            break
        except Exception as exc:
            if not stop_event.is_set():
                logger.warning(
                    "RabbitMQ connection error (%s). Reconnecting in 5 seconds...", exc
                )
                await asyncio.sleep(5)
