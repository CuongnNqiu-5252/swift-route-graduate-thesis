package com.swiftroute.orderservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.swiftroute.orderservice.entity.OrderTrackingLink;

@Repository
public interface OrderTrackingLinkRepository
        extends JpaRepository<OrderTrackingLink, UUID> {

    Optional<OrderTrackingLink> findByOrderId(UUID orderId);

}
