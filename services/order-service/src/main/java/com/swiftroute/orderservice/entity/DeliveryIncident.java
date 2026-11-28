package com.swiftroute.orderservice.entity;

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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "delivery_incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "incident_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatch_plan_id")
    private DispatchPlan dispatchPlan;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "incident_type", nullable = false, length = 60)
    private String incidentType;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "evidence_urls", columnDefinition = "TEXT")
    private String evidenceUrls;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "assigned_operator_id")
    private UUID assignedOperatorId;

    @Column(name = "resolution_type", length = 50)
    private String resolutionType;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
