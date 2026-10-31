package com.swiftroute.orderservice.repository;

import java.util.List;
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

    /**
     * Orders created by a specific customer.
     * Used for the customer "My Orders" screen.
     */
    List<Order> findAllByCustomerId(UUID customerId);

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
}

