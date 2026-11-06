package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.orderservice.entity.enums.DeliveryTaskStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "delivery_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "task_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "driver_id", nullable = false)
    private UUID driverId;
    @Column(name = "dispatch_plan_id")
    private UUID dispatchPlanId;
    @Column(name = "task_type", nullable = false)
    @Builder.Default
    private String taskType = "DELIVERY";
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DeliveryTaskStatus status = DeliveryTaskStatus.ASSIGNED;
    @Version
    private Long version;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
