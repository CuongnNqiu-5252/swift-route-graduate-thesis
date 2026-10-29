package com.swiftroute.driverservice.entity;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import com.swiftroute.driverservice.entity.enums.DriverStatus;
import com.swiftroute.driverservice.entity.enums.DriverType;
import com.swiftroute.driverservice.entity.enums.VehicleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "driver_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone", nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "license_plate", nullable = false, unique = true, length = 20)
    private String licensePlate;

    /** Driver's current GPS latitude — updated via Location events */
    @Column(name = "current_lat")
    private Double currentLat;

    /** Driver's current GPS longitude — updated via Location events */
    @Column(name = "current_lng")
    private Double currentLng;

    @Column(name = "vehicle_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "shift_start")
    private LocalTime shiftStart;

    @Column(name = "shift_end")
    private LocalTime shiftEnd;

    @Column(name = "driver_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private DriverType driverType;

    /** Logical status of the driver (ACTIVE, SUSPENDED, etc.) */
    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DriverStatus status = DriverStatus.ACTIVE;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
