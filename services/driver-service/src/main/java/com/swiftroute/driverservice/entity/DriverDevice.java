package com.swiftroute.driverservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "driver_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "device_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "installation_id", nullable = false, length = 128)
    private String installationId;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "device_os", length = 30)
    private String deviceOs;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "device_model", length = 120)
    private String deviceModel;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "activated_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant activatedAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    @Builder.Default
    private Instant lastSeenAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
