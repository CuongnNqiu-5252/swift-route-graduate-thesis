package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "package_units")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "package_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "package_number", nullable = false)
    private Integer packageNumber;

    @Column(name = "package_code", nullable = false, length = 100)
    private String packageCode;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING_PICKUP";

    @Column(name = "current_custodian_id")
    private UUID currentCustodianId;

    @Version
    private Long version;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
