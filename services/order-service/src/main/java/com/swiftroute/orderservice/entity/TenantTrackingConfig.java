package com.swiftroute.orderservice.entity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.swiftroute.orderservice.entity.enums.TrackingEmailTrigger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
@Entity
@Table(name = "tenant_tracking_configs")
@Getter
@Setter
public class TenantTrackingConfig {

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "disclose_eta", nullable = false)
    private Boolean discloseEta;

    @Column(name = "email_trigger")
    @Enumerated(EnumType.STRING)
    private TrackingEmailTrigger emailTrigger;

    @Column(name = "email_sender_name")
    private String emailSenderName;
    @Column(name = "email_body", columnDefinition = "TEXT")
    private String emailBody;
    @Column(name = "email_subject")
    private String emailSubject;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

}
