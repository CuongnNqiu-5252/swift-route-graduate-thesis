package com.swiftroute.driverservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.driverservice.dto.event.DriverAvailabilityEventPayload;
import com.swiftroute.driverservice.dto.event.DriverLocationUpdatedEvent;
import com.swiftroute.driverservice.dto.request.RegisterDriverRequest;
import com.swiftroute.driverservice.dto.request.UpdateAvailabilityRequest;
import com.swiftroute.driverservice.dto.request.UpdateDriverRequest;
import com.swiftroute.driverservice.dto.request.UpdateLocationRequest;
import com.swiftroute.driverservice.dto.response.DriverResponse;
import com.swiftroute.driverservice.entity.Driver;
import com.swiftroute.driverservice.entity.OutboxEvent;
import com.swiftroute.driverservice.entity.enums.DriverStatus;
import com.swiftroute.driverservice.exception.AppException;
import com.swiftroute.driverservice.exception.ErrorCode;
import com.swiftroute.driverservice.repository.DriverRepository;
import com.swiftroute.driverservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private static final Set<String> TENANT_MANAGEMENT_ROLES = Set.of("SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN");
    private static final Set<String> OPERATOR_ROLES = Set.of("SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR");

    private final DriverRepository driverRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DriverLocationCache locationCache;

    @Transactional
    public DriverResponse registerDriver(RegisterDriverRequest request, UUID requesterId, String requesterRole) {
        if (!OPERATOR_ROLES.contains(requesterRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        com.swiftroute.driverservice.security.JwtAuthenticationFilter.assertTenant(request.tenantId());
        if (driverRepository.existsByPhone(request.phone())) {
            throw new AppException(ErrorCode.DRIVER_ALREADY_EXISTS);
        }

        String plate = request.licensePlate() == null || request.licensePlate().isBlank()
                ? null : request.licensePlate().trim().toUpperCase(java.util.Locale.ROOT);
        if (plate != null && driverRepository.existsByLicensePlate(plate)) {
            throw new AppException(ErrorCode.DRIVER_ALREADY_EXISTS);
        }

        Driver driver = Driver.builder()
                .tenantId(request.tenantId())
                .userId(request.userId())
                .fullName(request.fullName())
                .phone(request.phone())
                .licensePlate(plate)
                .vehicleType(request.vehicleType())
                .capacity(request.capacity())
                .driverType(request.driverType())
                .shiftStart(request.shiftStart())
                .shiftEnd(request.shiftEnd())
                .status(DriverStatus.INACTIVE)
                .isAvailable(false)
                .build();

        driver = driverRepository.save(driver);

        saveOutboxEvent(
                "DRIVER",
                driver.getId(),
                "driver.availability_changed.v1",
                new DriverAvailabilityEventPayload(
                        driver.getId(),
                        driver.getTenantId(),
                        driver.getUserId(),
                        driver.getIsAvailable(),
                        Instant.now()
                )
        );

        return DriverResponse.fromEntity(driver);
    }

    @Transactional(readOnly = true)
    public DriverResponse getMyProfile(UUID userId) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        return toResponse(driver);
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> getAllDrivers(UUID tenantId, String requesterRole) {
        if (tenantId == null && requesterRole != null && !"SYSTEM_ADMIN".equals(requesterRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        com.swiftroute.driverservice.security.JwtAuthenticationFilter.assertTenant(tenantId);
        List<Driver> drivers;
        if (tenantId != null) {
            drivers = driverRepository.findAllByTenantId(tenantId);
        } else {
            drivers = driverRepository.findAll();
        }
        return drivers.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DriverResponse updateDriver(UUID driverId, UpdateDriverRequest request, UUID requesterId,
            String requesterRole) {
        if (!OPERATOR_ROLES.contains(requesterRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        com.swiftroute.driverservice.security.JwtAuthenticationFilter.assertTenant(driver.getTenantId());

        if (request.fullName() != null && request.fullName().isBlank()) throw new AppException(ErrorCode.VALIDATION_ERROR);
        if (request.fullName() != null)
            driver.setFullName(request.fullName());
        if (request.phone() != null && !request.phone().equals(driver.getPhone())) {
            if (driverRepository.existsByPhone(request.phone())) {
                throw new AppException(ErrorCode.DRIVER_ALREADY_EXISTS);
            }
            driver.setPhone(request.phone());
        }
        if (request.licensePlate() != null && !request.licensePlate().equals(driver.getLicensePlate())) {
            if (driverRepository.existsByLicensePlate(request.licensePlate())) {
                throw new AppException(ErrorCode.DRIVER_ALREADY_EXISTS);
            }
            driver.setLicensePlate(request.licensePlate());
        }
        if (request.vehicleType() != null)
            driver.setVehicleType(request.vehicleType());
        if (request.capacity() != null)
            driver.setCapacity(request.capacity());
        if (request.driverType() != null)
            driver.setDriverType(request.driverType());
        if (request.shiftStart() != null)
            driver.setShiftStart(request.shiftStart());
        if (request.shiftEnd() != null)
            driver.setShiftEnd(request.shiftEnd());

        if (driver.getStatus() != DriverStatus.SUSPENDED) driver.setStatus(DriverStatus.INACTIVE);
        driver.setIsAvailable(false);
        driver.setReviewNote(null);
        driverRepository.save(driver);
        publishAvailability(driver);
        return toResponse(driver);
    }

    @Transactional
    public DriverResponse updateMyLocation(UUID userId, UpdateLocationRequest request) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new AppException(ErrorCode.DRIVER_SUSPENDED);
        }

        return updateLocationState(driver, request);
    }

    @Transactional
    public DriverResponse updateMyAvailability(UUID userId, UpdateAvailabilityRequest request) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new AppException(ErrorCode.DRIVER_SUSPENDED);
        }

        if (Boolean.TRUE.equals(request.isAvailable()) && !isOperationallyReady(driver)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        driver.setIsAvailable(request.isAvailable());
        driver = driverRepository.save(driver);

        saveOutboxEvent(
                "DRIVER",
                driver.getId(),
                "driver.availability_changed.v1",
                new DriverAvailabilityEventPayload(
                        driver.getId(),
                        driver.getTenantId(),
                        driver.getUserId(),
                        driver.getIsAvailable(),
                        Instant.now()
                )
        );

        return toResponse(driver);
    }

    @Transactional
    public DriverResponse completeMyProfile(UUID userId, UpdateDriverRequest request) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        if (driver.getStatus() == DriverStatus.SUSPENDED) throw new AppException(ErrorCode.ACCESS_DENIED);
        // Reuse duplicate validation; self identity is resolved from the authenticated principal.
        updateDriver(driver.getId(), request, userId, "OWNER");
        driver.setStatus(DriverStatus.INACTIVE);
        driver.setIsAvailable(false);
        driver.setReviewNote(null);
        driverRepository.save(driver);
        publishAvailability(driver);
        return toResponse(driver);
    }

    @Transactional
    public DriverResponse submitMyProfile(UUID userId) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        if (driver.getStatus() == DriverStatus.SUSPENDED || !hasCompleteLinkedProfile(driver)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (driver.getStatus() == DriverStatus.ACTIVE || driver.getStatus() == DriverStatus.PENDING_APPROVAL) {
            return toResponse(driver);
        }
        driver.setStatus(DriverStatus.PENDING_APPROVAL);
        driver.setIsAvailable(false);
        driver.setReviewNote(null);
        driverRepository.save(driver);
        publishAvailability(driver);
        return toResponse(driver);
    }

    @Transactional
    public DriverResponse reviewProfile(UUID driverId, boolean approved, String note, UUID requesterId, String role) {
        if (!TENANT_MANAGEMENT_ROLES.contains(role)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        com.swiftroute.driverservice.security.JwtAuthenticationFilter.assertTenant(driver.getTenantId());
        if (driver.getStatus() != DriverStatus.PENDING_APPROVAL || (approved && !hasCompleteLinkedProfile(driver))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (!approved && (note == null || note.isBlank())) throw new AppException(ErrorCode.VALIDATION_ERROR);
        driver.setStatus(approved ? DriverStatus.ACTIVE : DriverStatus.INACTIVE);
        driver.setReviewNote(note == null ? null : note.trim());
        driver.setIsAvailable(false);
        driverRepository.save(driver);
        publishAvailability(driver);
        if (driver.getUserId() != null) {
            saveOutboxEvent("DRIVER", driver.getId(), "driver.profile_reviewed", java.util.Map.of(
                    "tenant_id", driver.getTenantId().toString(),
                    "driver_id", driver.getUserId().toString(),
                    "driver_entity_id", driver.getId().toString(),
                    "approved", approved,
                    "note", driver.getReviewNote() != null ? driver.getReviewNote() : ""
            ));
        }
        return toResponse(driver);
    }

    private void publishAvailability(Driver driver) {
        saveOutboxEvent("DRIVER", driver.getId(), "driver.availability_changed.v1",
                new DriverAvailabilityEventPayload(driver.getId(), driver.getTenantId(), driver.getUserId(),
                        driver.getIsAvailable(), Instant.now()));
    }

    private boolean hasCompleteLinkedProfile(Driver driver) {
        return driver.getUserId() != null
                && driver.getFullName() != null && !driver.getFullName().isBlank()
                && driver.getPhone() != null && driver.getPhone().matches("^\\+?[0-9]{9,15}$")
                && driver.getLicensePlate() != null && !driver.getLicensePlate().isBlank()
                && driver.getVehicleType() != null
                && driver.getCapacity() != null && driver.getCapacity() > 0;
    }

    private boolean isOperationallyReady(Driver driver) {
        return driver.getStatus() == DriverStatus.ACTIVE && hasCompleteLinkedProfile(driver);
    }

    private void saveOutboxEvent(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        saveOutboxEvent(UUID.randomUUID(), aggregateType, aggregateId, eventType, payload);
    }

    private DriverResponse updateLocationState(Driver driver, UpdateLocationRequest request) {
        if (!locationCache.accept(driver, request)) {
            log.debug("Ignored stale location update for driver {} at sequence {}", driver.getUserId(), request.effectiveSequence());
            return toResponse(driver);
        }

        Instant recordedAt = request.effectiveRecordedAt();
        long sequence = request.effectiveSequence();

        UUID eventId = UUID.randomUUID();
        saveOutboxEvent(
                eventId,
                "DRIVER",
                driver.getId(),
                "driver.location.updated.v1",
                new DriverLocationUpdatedEvent(
                        eventId,
                        driver.getId(),
                        driver.getUserId(),
                        driver.getTenantId(),
                        request.lat(),
                        request.lng(),
                        request.accuracyM(),
                        request.speedMps(),
                        request.bearing(),
                        recordedAt,
                        sequence));
        return DriverResponse.builder()
                .id(driver.getId())
                .tenantId(driver.getTenantId())
                .userId(driver.getUserId())
                .fullName(driver.getFullName())
                .phone(driver.getPhone())
                .licensePlate(driver.getLicensePlate())
                .vehicleType(driver.getVehicleType())
                .capacity(driver.getCapacity())
                .driverType(driver.getDriverType())
                .shiftStart(driver.getShiftStart())
                .shiftEnd(driver.getShiftEnd())
                .currentLat(request.lat())
                .currentLng(request.lng())
                .isAvailable(driver.getIsAvailable())
                .status(driver.getStatus())
                .reviewNote(driver.getReviewNote())
                .build();
    }

    private DriverResponse toResponse(Driver driver) {
        locationCache.enrichWithCachedLocation(driver);
        return DriverResponse.fromEntity(driver);
    }

    private void saveOutboxEvent(UUID eventId, String aggregateType, UUID aggregateId, String eventType, Object payload) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .build();
        outboxEventRepository.save(outboxEvent);
    }
}
