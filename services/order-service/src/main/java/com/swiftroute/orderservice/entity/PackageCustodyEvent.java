package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "package_custody_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageCustodyEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "custody_event_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "from_custodian_id")
    private UUID fromCustodianId;

    @Column(name = "to_custodian_id")
    private UUID toCustodianId;

    @Column(name = "handover_id")
    private UUID handoverId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(length = 1000)
    private String reason;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
