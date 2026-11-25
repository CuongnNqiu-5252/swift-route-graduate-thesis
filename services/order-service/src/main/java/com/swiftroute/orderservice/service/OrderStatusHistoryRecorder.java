package com.swiftroute.orderservice.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.OrderStatusHistory;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.repository.OrderStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Records order status transitions into the existing order_status_history audit table.
 */
@Service
@RequiredArgsConstructor
public class OrderStatusHistoryRecorder {

    private static final int MAX_ROLE_LENGTH = 30;
    private static final int MAX_REASON_LENGTH = 500;

    private final OrderStatusHistoryRepository historyRepository;

    /**
     * Persists a status transition row when an order is created or moves to a different status.
     */
    public void record(
            Order order,
            OrderStatus from,
            OrderStatus to,
            UUID actorId,
            String actorRole,
            String reason) {
        if (order == null || order.getId() == null || order.getTenant() == null || to == null) {
            return;
        }
        if (from != null && from == to) {
            return;
        }
        historyRepository.save(OrderStatusHistory.builder()
                .order(order)
                .tenantId(order.getTenant().getId())
                .fromStatus(from != null ? from.name() : null)
                .toStatus(to.name())
                .changedByUserId(actorId)
                .changedByRole(normalizeRole(actorRole))
                .reason(truncate(reason, MAX_REASON_LENGTH))
                .build());
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.length() > MAX_ROLE_LENGTH ? normalized.substring(0, MAX_ROLE_LENGTH) : normalized;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
