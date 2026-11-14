package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "package_handovers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageHandover {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "handover_id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "from_driver_id", nullable = false)
    private UUID fromDriverId;

    @Column(name = "to_driver_id")
    private UUID toDriverId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "REQUESTED";

    @Column(nullable = false, length = 1000)
    private String reason;

    private UUID assignedBy;
    private Instant assignedAt;
    private UUID rejectedBy;
    private Instant rejectedAt;
    private Instant completedAt;
    private UUID cancelledBy;
    private Instant cancelledAt;

    @Column(length = 1000)
    private String adminNotes;

    @Column(length = 1000)
    private String receiverNotes;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
