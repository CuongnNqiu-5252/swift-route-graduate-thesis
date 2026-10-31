package com.swiftroute.driverservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.driverservice.dto.event.DriverAvailabilityEventPayload;
import com.swiftroute.driverservice.dto.event.DriverDeliveryReportEventPayload;
import com.swiftroute.driverservice.dto.request.RegisterDriverRequest;
import com.swiftroute.driverservice.dto.request.ReportDeliveryEventRequest;
import com.swiftroute.driverservice.dto.request.UpdateAvailabilityRequest;
import com.swiftroute.driverservice.dto.request.UpdateDriverRequest;
import com.swiftroute.driverservice.dto.request.UpdateLocationRequest;
import com.swiftroute.driverservice.dto.response.DriverPublicResponse;
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

    private final DriverRepository driverRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public DriverResponse registerDriver(RegisterDriverRequest request, UUID requesterId, String requesterRole) {
        if (!List.of("ADMIN", "OPS_STAFF", "OWNER").contains(requesterRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (driverRepository.existsByPhone(request.phone())) {
            throw new AppException(ErrorCode.DRIVER_ALREADY_EXISTS);
        }

        if (driverRepository.existsByLicensePlate(request.licensePlate())) {
            throw new AppException(ErrorCode.DRIVER_ALREADY_EXISTS);
        }

        Driver driver = Driver.builder()
                .tenantId(request.tenantId())
                .userId(request.userId())
                .fullName(request.fullName())
                .phone(request.phone())
                .licensePlate(request.licensePlate())
                .vehicleType(request.vehicleType())
                .capacity(request.capacity())
                .driverType(request.driverType())
                .shiftStart(request.shiftStart())
                .shiftEnd(request.shiftEnd())
                .status(DriverStatus.ACTIVE)
                .isAvailable(true)
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
        return DriverResponse.fromEntity(driver);
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriverById(UUID driverId, UUID requesterId, String requesterRole) {
        return getDriverById(driverId, requesterId, requesterRole, null);
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriverById(UUID driverId, UUID requesterId, String requesterRole, UUID requesterTenantId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

        if ("ADMIN".equals(requesterRole)) {
            return DriverResponse.fromEntity(driver);
        }

        if (List.of("OPS_STAFF", "OWNER").contains(requesterRole)) {
            if (requesterTenantId != null && !driver.getTenantId().equals(requesterTenantId)) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            return DriverResponse.fromEntity(driver);
        }

        boolean isSelf = (driver.getUserId() != null && driver.getUserId().equals(requesterId))
                || (driver.getId() != null && driver.getId().equals(requesterId));
        if (!isSelf) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        return DriverResponse.fromEntity(driver);
    }

    @Transactional(readOnly = true)
    public DriverPublicResponse getDriverPublicInfo(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        return DriverPublicResponse.fromEntity(driver);
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> getDriversByTenant(UUID tenantId, UUID requesterId, String requesterRole) {
        if (!List.of("ADMIN", "OPS_STAFF", "OWNER").contains(requesterRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        return driverRepository.findAllByTenantId(tenantId).stream()
                .map(DriverResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> getAllDrivers(UUID tenantId, String requesterRole) {
        List<Driver> drivers;
        if (tenantId != null) {
            drivers = driverRepository.findAllByTenantId(tenantId);
        } else {
            drivers = driverRepository.findAll();
        }
        return drivers.stream()
                .map(DriverResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> getAvailableDriversByTenant(UUID tenantId) {
        return driverRepository.findAllByTenantIdAndIsAvailableTrue(tenantId).stream()
                .map(DriverResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public DriverResponse updateDriver(UUID driverId, UpdateDriverRequest request, UUID requesterId,
            String requesterRole) {
        if (!List.of("ADMIN", "OPS_STAFF", "OWNER").contains(requesterRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

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

        return DriverResponse.fromEntity(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse updateMyLocation(UUID userId, UpdateLocationRequest request) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new AppException(ErrorCode.DRIVER_SUSPENDED);
        }

        driver.setCurrentLat(request.lat());
        driver.setCurrentLng(request.lng());

        return DriverResponse.fromEntity(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse updateLocation(UUID driverId, UpdateLocationRequest request, UUID requesterId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new AppException(ErrorCode.DRIVER_SUSPENDED);
        }

        boolean isSelf = (driver.getUserId() != null && driver.getUserId().equals(requesterId))
                || (driver.getId() != null && driver.getId().equals(requesterId));
        if (!isSelf) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        driver.setCurrentLat(request.lat());
        driver.setCurrentLng(request.lng());

        return DriverResponse.fromEntity(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse updateMyAvailability(UUID userId, UpdateAvailabilityRequest request) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new AppException(ErrorCode.DRIVER_SUSPENDED);
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

        return DriverResponse.fromEntity(driver);
    }

    @Transactional
    public DriverResponse updateAvailability(UUID driverId, UpdateAvailabilityRequest request, UUID requesterId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new AppException(ErrorCode.DRIVER_SUSPENDED);
        }

        boolean isSelf = (driver.getUserId() != null && driver.getUserId().equals(requesterId))
                || (driver.getId() != null && driver.getId().equals(requesterId));
        if (!isSelf) {
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

        return DriverResponse.fromEntity(driver);
    }

    @Transactional
    public void reportDeliveryEvent(UUID userId, ReportDeliveryEventRequest request) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new AppException(ErrorCode.DRIVER_SUSPENDED);
        }

        saveOutboxEvent(
                "DRIVER",
                driver.getId(),
                "driver.delivery_reported.v1",
                new DriverDeliveryReportEventPayload(
                        driver.getId(),
                        driver.getTenantId(),
                        request.orderId(),
                        request.eventType(),
                        request.reason(),
                        request.notes(),
                        Instant.now()
                )
        );
    }

    @Transactional
    public DriverResponse changeStatus(UUID driverId, DriverStatus newStatus, UUID requesterId, String requesterRole) {
        if (!List.of("ADMIN", "OPS_STAFF", "OWNER").contains(requesterRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

        driver.setStatus(newStatus);
        if (newStatus == DriverStatus.SUSPENDED || newStatus == DriverStatus.INACTIVE) {
            driver.setIsAvailable(false);
        }

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

    private void saveOutboxEvent(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .build();
        outboxEventRepository.save(outboxEvent);
    }
}
