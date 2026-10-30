# service/rabbitmq_consumer.py
"""
Asynchronous RabbitMQ consumer for the Routing Engine service.
Consumes order lifecycle events (order.created.v1, order.status_changed.v1)
from the shared 'swiftroute.events' topic exchange.

Implements:
  - Durable queue binding: routing.orders.queue
  - Idempotent processing with eventId deduplication
  - Automatic reconnection on failure
  - Order enrichment fallback if coordinates are missing in the event
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
from datetime import datetime, timezone
from typing import Any, Dict, Optional

import aio_pika
import httpx

from api.schemas import PendingOrder
from service.order_store import classify_shift, order_store

logger = logging.getLogger("routing-engine.rabbitmq_consumer")

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
ORDER_SERVICE_URL = os.getenv("ORDER_SERVICE_URL", "http://order-service:8082")

EXCHANGE_NAME = "swiftroute.events"
QUEUE_NAME = "routing.orders.queue"
ROUTING_KEYS = ["order.#"]


async def fetch_order_details(order_id: str) -> Optional[Dict[str, Any]]:
    """Fetches full order details from order-service when event payload is incomplete."""
    url = f"{ORDER_SERVICE_URL}/api/orders/{order_id}"
    headers = {
        "X-User-Id": "a0000000-0000-0000-0000-000000000001",
        "X-User-Role": "ADMIN",
    }
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.get(url, headers=headers)
            if resp.status_code == 200:
                body = resp.json()
                return body.get("data")
    except Exception as exc:
        logger.warning("Could not fetch order details for %s: %s", order_id, exc)
    return None


async def process_order_message(message: aio_pika.IncomingMessage) -> None:
    """
    Processes an incoming order event message from RabbitMQ.
    Ensures idempotency using event_id before storing into OrderStore.
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

        # 1. Idempotency Check
        if order_store.is_event_processed(event_id):
            logger.info("Ignoring duplicate eventId=%s", event_id)
            return

        logger.info("Processing order event %s: routing_key=%s", event_id, message.routing_key)

        order_id = str(body.get("orderId") or body.get("order_id") or "")
        tenant_id = str(body.get("tenantId") or body.get("tenant_id") or "a1111111-1111-1111-1111-111111111111")
        customer_id = body.get("customerId") or body.get("customer_id")
        current_status = str(body.get("currentStatus") or body.get("status") or "PENDING_PAYMENT")

        # Extract coordinates
        pickup_lat = body.get("pickupLat") or body.get("pickup_lat")
        pickup_lng = body.get("pickupLng") or body.get("pickup_lng")
        pickup_address = body.get("pickupAddress") or body.get("pickup_address")

        delivery_lat = body.get("deliveryLat") or body.get("delivery_lat")
        delivery_lng = body.get("deliveryLng") or body.get("delivery_lng")
        delivery_address = body.get("deliveryAddress") or body.get("delivery_address")

        weight_kg = float(body.get("weightKg") or body.get("weight_kg") or 1.0)
        time_window_start = body.get("timeWindowStart") or body.get("time_window_start")
        time_window_end = body.get("timeWindowEnd") or body.get("time_window_end")

        # If coordinates are missing, fetch from order-service
        if pickup_lat is None or delivery_lat is None:
            details = await fetch_order_details(order_id)
            if details:
                pickup_lat = details.get("pickup_lat", 10.7769)
                pickup_lng = details.get("pickup_lng", 106.7009)
                pickup_address = details.get("pickup_address", pickup_address)
                delivery_lat = details.get("delivery_lat", 10.7890)
                delivery_lng = details.get("delivery_lng", 106.6920)
                delivery_address = details.get("delivery_address", delivery_address)
                weight_kg = float(details.get("weight_kg") or 1.0)
                time_window_start = details.get("time_window_start", time_window_start)
                time_window_end = details.get("time_window_end", time_window_end)
                current_status = details.get("status", current_status)
            else:
                # Fallback to sensible Saigon defaults for demo resilience
                pickup_lat = pickup_lat or 10.7769
                pickup_lng = pickup_lng or 106.7009
                delivery_lat = delivery_lat or 10.7890
                delivery_lng = delivery_lng or 106.6920

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
            time_window_start=str(time_window_start) if time_window_start else None,
            time_window_end=str(time_window_end) if time_window_end else None,
            status=current_status,
            shift=shift,
            received_at=datetime.now(timezone.utc).isoformat(),
        )

        # 2. Save order and record event ID for idempotency
        order_store.save_or_update_order(pending_order)
        order_store.mark_event_processed(event_id)
        logger.info(
            "Order %s saved successfully in OrderStore (tenant=%s, shift=%s, status=%s)",
            order_id,
            tenant_id,
            shift,
            current_status,
        )


async def start_consumer(stop_event: asyncio.Event) -> None:
    """
    Long-running background task that maintains a connection to RabbitMQ
    and listens on the routing.orders.queue. Reconnects on connection loss.
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
                        await process_order_message(message)

        except asyncio.CancelledError:
            logger.info("RabbitMQ consumer task cancelled.")
            break
        except Exception as exc:
            if not stop_event.is_set():
                logger.warning(
                    "RabbitMQ connection error (%s). Reconnecting in 5 seconds...", exc
                )
                await asyncio.sleep(5)

