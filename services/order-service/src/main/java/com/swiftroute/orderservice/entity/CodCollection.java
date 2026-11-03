package com.swiftroute.orderservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cod_collections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodCollection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "collection_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    private UUID tenantId;
    private UUID driverId;
    private BigDecimal collectedAmount;
    @Builder.Default
    private String status = "PENDING_HANDOVER";
    private String notes;
    private BigDecimal declaredAmount;
    private Instant declaredAt;
    private UUID verifiedBy;
    private Instant verifiedAt;
    private String resolutionNotes;
    @Builder.Default
    private Instant collectedAt = Instant.now();
}
