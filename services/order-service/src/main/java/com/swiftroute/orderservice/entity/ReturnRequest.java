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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "return_request_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Linked incident that triggered this return request (optional). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private DeliveryIncident incident;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Target return destination warehouse/location. Defaults to pickup location. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_location_id")
    private PickupLocation returnLocation;

    /** Current physical holder of the undelivered package (Driver ID). */
    @Column(name = "current_holder_id")
    private UUID currentHolderId;

    @Column(name = "request_type", nullable = false, length = 30)
    private String requestType;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "REQUESTED";

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_task_id")
    private DeliveryTask returnTask;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "completion_notes", length = 1000)
    private String completionNotes;

    // Discrepancy investigation fields
    @Column(name = "discrepancy_type", length = 50)
    private String discrepancyType;

    @Column(name = "actual_quantity")
    private Integer actualQuantity;

    @Column(name = "package_condition", length = 100)
    private String packageCondition;

    @Column(name = "discrepancy_notes", length = 1000)
    private String discrepancyNotes;

    @Column(name = "discrepancy_evidence_url", length = 1000)
    private String discrepancyEvidenceUrl;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
