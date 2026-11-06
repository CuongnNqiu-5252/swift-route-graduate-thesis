package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @Column(name = "to_driver_id", nullable = false)
    private UUID toDriverId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "REQUESTED";

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    private Instant senderConfirmedAt;
    private Instant receiverConfirmedAt;
    private Instant completedAt;
    private Instant cancelledAt;

    @Column(length = 1000)
    private String notes;

    @Version
    private Long version;

    @OneToMany(mappedBy = "handover", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PackageHandoverItem> items = new ArrayList<>();

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
