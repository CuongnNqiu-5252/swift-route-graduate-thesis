package com.swiftroute.driverservice.entity;

import java.time.Instant;
import java.util.UUID;

import com.swiftroute.driverservice.entity.enums.VehicleStatus;
import com.swiftroute.driverservice.entity.enums.VehicleType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Vehicle {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "vehicle_id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "license_plate", nullable = false, unique = true, length = 20) private String licensePlate;
    @Enumerated(EnumType.STRING) @Column(name = "vehicle_type", nullable = false) private VehicleType vehicleType;
    private Integer capacity;
    private String make;
    private String model;
    @Enumerated(EnumType.STRING) @Column(nullable = false) @Builder.Default
    private VehicleStatus status = VehicleStatus.AVAILABLE;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_driver_id", unique = true)
    private Driver assignedDriver;
    @Column(name = "created_at", nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) @Builder.Default private Instant updatedAt = Instant.now();
}
