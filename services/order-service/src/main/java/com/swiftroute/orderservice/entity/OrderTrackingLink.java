package com.swiftroute.orderservice.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_tracking_links")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OrderTrackingLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID trackingId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    private OffsetDateTime expiresAt;

    private OffsetDateTime revokedAt;

    private OffsetDateTime createdAt;
}
