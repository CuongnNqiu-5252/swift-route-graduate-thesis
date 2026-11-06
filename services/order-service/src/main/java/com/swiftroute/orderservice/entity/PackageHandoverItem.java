package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "package_handover_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageHandoverItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "handover_item_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handover_id", nullable = false)
    private PackageHandover handover;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    private PackageUnit packageUnit;

    @Column(name = "previous_status", nullable = false, length = 30)
    private String previousStatus;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Builder.Default
    private Instant createdAt = Instant.now();
}
