package com.swiftroute.orderservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftroute.orderservice.entity.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Lấy danh sách đơn hàng của một Tenant cụ thể.
     * Dùng cho màn hình quản lý đơn của Doanh nghiệp.
     */
    List<Order> findAllByTenantId(UUID tenantId);

    /**
     * Lấy danh sách đơn hàng của một Customer cụ thể.
     * Dùng cho màn hình "Đơn hàng của tôi" trên app khách hàng.
     */
    List<Order> findAllByCustomerId(UUID customerId);
}
