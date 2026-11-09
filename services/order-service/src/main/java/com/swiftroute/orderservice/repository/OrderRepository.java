package com.swiftroute.orderservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.enums.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * All orders belonging to a tenant.
     * Used by the tenant operations dashboard.
     */
    List<Order> findAllByTenantId(UUID tenantId);

    List<Order> findAllByStatus(OrderStatus status);

    List<Order> findAllByTenantIdAndStatus(UUID tenantId, OrderStatus status);

    List<Order> findAllByTenantIdAndStatusInOrderByUpdatedAtDesc(UUID tenantId, List<OrderStatus> statuses);

    List<Order> findByTenant_IdAndAssignedDriverIdAndCodStatusOrderByUpdatedAtDesc(UUID tenantId, UUID driverId, String codStatus);

    List<Order> findByTenant_IdAndAssignedDriverIdOrderByUpdatedAtDesc(UUID tenantId, UUID driverId);

    List<Order> findByTenant_IdAndCodStatusOrderByUpdatedAtDesc(UUID tenantId, String codStatus);

    List<Order> findByCodHandoverBatchIdOrderByUpdatedAtDesc(UUID batchId);

    List<Order> findByTenant_IdAndCodHandoverBatchIdIn(UUID tenantId, List<UUID> batchIds);

    /**
     * Find by public tracking token for unauthenticated recipient tracking.
     */
    Optional<Order> findByTrackingToken(String trackingToken);

    /**
     * Find by tenant and external merchant order code (idempotent lookup).
     */
    Optional<Order> findByTenant_IdAndExternalOrderCode(UUID tenantId, String externalOrderCode);

    /**
     * Find orders by recipient phone.
     */
    List<Order> findAllByRecipientPhone(String recipientPhone);

    /**
     * Active orders assigned to a specific driver, ordered by delivery window start.
     * A driver should only see orders in PLANNED or IN_TRANSIT states.
     * Orders are sorted by time_window_start so the driver sees the earliest
     * deadline first (time-window constraint for route optimisation).
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.assignedDriverId = :driverId
              AND o.status IN :statuses
            ORDER BY o.timeWindowStart ASC
            """)
    List<Order> findAssignedDriverOrders(
            @Param("driverId") UUID driverId,
            @Param("statuses") List<OrderStatus> statuses);

    /**
     * Calculates total revenue for a tenant from delivered orders.
     */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.tenant.id = :tenantId
              AND o.status = com.swiftroute.orderservice.entity.enums.OrderStatus.DELIVERED
            """)
    java.math.BigDecimal calculateVerifiedRevenueByTenant(@Param("tenantId") UUID tenantId);
}
