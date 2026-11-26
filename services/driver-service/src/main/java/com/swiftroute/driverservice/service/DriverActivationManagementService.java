package com.swiftroute.driverservice.service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.swiftroute.driverservice.dto.event.DriverAvailabilityEventPayload;
import com.swiftroute.driverservice.dto.request.UpdateDriverDeviceRequest;
import com.swiftroute.driverservice.dto.response.DriverActivationKeyResponse;
import com.swiftroute.driverservice.dto.response.DriverActivationStatusResponse;
import com.swiftroute.driverservice.dto.response.PageResponse;
import com.swiftroute.driverservice.entity.Driver;
import com.swiftroute.driverservice.entity.DriverDevice;
import com.swiftroute.driverservice.entity.OutboxEvent;
import com.swiftroute.driverservice.entity.enums.DriverStatus;
import com.swiftroute.driverservice.exception.AppException;
import com.swiftroute.driverservice.exception.ErrorCode;
import com.swiftroute.driverservice.repository.DriverDeviceRepository;
import com.swiftroute.driverservice.repository.DriverRepository;
import com.swiftroute.driverservice.repository.OutboxEventRepository;
import com.swiftroute.driverservice.security.JwtAuthenticationFilter;
import com.swiftroute.driverservice.security.TenantPermissionRegistry;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DriverActivationManagementService {

    private static final Set<String> READ_ROLES = Set.of("SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR", "VIEWER");
    private static final Set<String> MANAGE_ROLES = Set.of("SYSTEM_ADMIN", "OWNER", "TENANT_ADMIN", "OPERATOR");
    private static final Duration REVOKED_SESSION_REDIS_TTL = Duration.ofHours(2);

    private final DriverRepository driverRepository;
    private final DriverDeviceRepository driverDeviceRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuthActivationClient authActivationClient;
    private final StringRedisTemplate redisTemplate;

    public DriverActivationManagementService(
            DriverRepository driverRepository,
            DriverDeviceRepository driverDeviceRepository,
            OutboxEventRepository outboxEventRepository,
            AuthActivationClient authActivationClient,
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.driverRepository = driverRepository;
        this.driverDeviceRepository = driverDeviceRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.authActivationClient = authActivationClient;
        this.redisTemplate = redisTemplate;
    }

    public record ActivateDeviceInternalCommand(
            UUID userId,
            String installationId,
            UUID sessionId,
            String deviceOs,
            String osVersion,
            String appVersion,
            String deviceModel) {
    }

    @Transactional(readOnly = true)
    public PageResponse<DriverActivationStatusResponse> getActivationStatusPage(
            UUID tenantId,
            String requesterRole,
            String query,
            String statusFilter,
            int page,
            int size) {
        if (requesterRole == null || !READ_ROLES.contains(requesterRole) || tenantId == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        JwtAuthenticationFilter.assertTenant(tenantId);

        List<Driver> allDrivers = new ArrayList<>(driverRepository.findAllByTenantId(tenantId));
        allDrivers.sort(Comparator.comparing(Driver::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        List<UUID> driverIds = allDrivers.stream().map(Driver::getId).toList();
        Map<UUID, DriverDevice> activeDeviceByDriver = new LinkedHashMap<>();
        Map<UUID, DriverDevice> latestDeviceByDriver = new LinkedHashMap<>();
        if (!driverIds.isEmpty()) {
            List<DriverDevice> devices = driverDeviceRepository
                    .findByTenantIdAndDriverIdInOrderByActivatedAtDesc(tenantId, driverIds);
            for (DriverDevice device : devices) {
                latestDeviceByDriver.putIfAbsent(device.getDriverId(), device);
                if (device.getRevokedAt() == null) {
                    activeDeviceByDriver.putIfAbsent(device.getDriverId(), device);
                }
            }
        }

        Map<UUID, AuthActivationClient.KeyStatePayload> keyStates = Map.of();
        if (!driverIds.isEmpty()) {
            try {
                keyStates = authActivationClient.queryKeyStates(tenantId, driverIds);
            } catch (Exception ex) {
                log.warn("Failed to query activation key states from auth-service for tenant {}: {}",
                        tenantId, ex.getMessage());
            }
        }

        String normalizedQuery = normalizeSearch(query);
        String normalizedStatus = statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)
                ? statusFilter.trim().toUpperCase(Locale.ROOT)
                : null;

        List<DriverActivationStatusResponse> filtered = new ArrayList<>();
        for (Driver driver : allDrivers) {
            DriverDevice activeDevice = activeDeviceByDriver.get(driver.getId());
            DriverDevice latestDevice = latestDeviceByDriver.get(driver.getId());
            AuthActivationClient.KeyStatePayload keyState = keyStates.get(driver.getId());

            String derivedStatus = deriveActivationStatus(activeDevice, latestDevice, keyState);
            if (normalizedStatus != null && !normalizedStatus.equals(derivedStatus)) {
                continue;
            }

            String driverCode = formatDriverCode(driver.getId());
            if (!normalizedQuery.isEmpty() && !matchesSearch(driver, driverCode, normalizedQuery)) {
                continue;
            }

            DriverDevice displayDevice = activeDevice != null ? activeDevice : latestDevice;
            String operationalStatus = (driver.getStatus() == DriverStatus.ACTIVE && Boolean.TRUE.equals(driver.getIsAvailable()))
                    ? "AVAILABLE"
                    : "OFFLINE";

            filtered.add(DriverActivationStatusResponse.builder()
                    .driverId(driver.getId())
                    .driverCode(driverCode)
                    .userId(driver.getUserId())
                    .fullName(driver.getFullName())
                    .phone(driver.getPhone())
                    .email(driver.getEmail())
                    .licensePlate(driver.getLicensePlate())
                    .vehicleType(driver.getVehicleType() != null ? driver.getVehicleType().name() : null)
                    .operationalStatus(operationalStatus)
                    .profileStatus(driver.getStatus() != null ? driver.getStatus().name() : "INACTIVE")
                    .activationStatus(derivedStatus)
                    .keyCreatedAt(keyState != null ? keyState.created_at() : null)
                    .keyExpiresAt(keyState != null
                            && ("KEY_CREATED".equals(derivedStatus) || "KEY_EXPIRED".equals(derivedStatus))
                                    ? keyState.expires_at()
                                    : null)
                    .appVersion(displayDevice != null ? displayDevice.getAppVersion() : null)
                    .deviceOs(displayDevice != null ? displayDevice.getDeviceOs() : null)
                    .osVersion(displayDevice != null ? displayDevice.getOsVersion() : null)
                    .deviceModel(displayDevice != null ? displayDevice.getDeviceModel() : null)
                    .activatedAt(displayDevice != null ? displayDevice.getActivatedAt() : null)
                    .lastSeenAt(displayDevice != null ? displayDevice.getLastSeenAt() : null)
                    .deactivatedAt(displayDevice != null ? displayDevice.getRevokedAt() : null)
                    .build());
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        int totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<DriverActivationStatusResponse> slice = filtered.subList(fromIndex, toIndex);

        return new PageResponse<>(
                slice,
                safePage,
                safeSize,
                totalElements,
                totalPages,
                safePage + 1 < totalPages,
                safePage > 0);
    }

    @Transactional
    public DriverActivationKeyResponse generateActivationKey(
            UUID driverId,
            boolean sendEmail,
            String overrideEmail,
            UUID requesterId,
            String requesterRole) {
        assertManageRole(requesterRole);
        Driver driver = driverRepository.findLockedById(driverId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        JwtAuthenticationFilter.assertTenant(driver.getTenantId());

        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new AppException(ErrorCode.DRIVER_SUSPENDED);
        }
        if (!hasRequiredOperationalFields(driver)) {
            throw new AppException(ErrorCode.DRIVER_PROFILE_INCOMPLETE);
        }

        if (overrideEmail != null && !overrideEmail.isBlank()) {
            driver.setEmail(overrideEmail.trim().toLowerCase(Locale.ROOT));
        }

        String effectiveEmail = driver.getEmail();
        if (sendEmail && (effectiveEmail == null || effectiveEmail.isBlank())) {
            throw new AppException(ErrorCode.DRIVER_EMAIL_REQUIRED);
        }

        Instant now = Instant.now();
        UUID previousSessionId = null;
        DriverDevice activeDevice = driverDeviceRepository
                .findByTenantIdAndDriverIdAndRevokedAtIsNull(driver.getTenantId(), driver.getId())
                .orElse(null);
        if (activeDevice != null) {
            previousSessionId = activeDevice.getSessionId();
            activeDevice.setRevokedAt(now);
            driverDeviceRepository.saveAndFlush(activeDevice);
            markSessionRevokedLocallyAndRedis(previousSessionId);
            if (Boolean.TRUE.equals(driver.getIsAvailable())) {
                driver.setIsAvailable(false);
                publishAvailabilityChanged(driver);
            }
        }

        AuthActivationClient.IssuedKeyPayload issued;
        try {
            issued = authActivationClient.issueKey(
                    driver.getTenantId(),
                    driver.getId(),
                    driver.getUserId(),
                    driver.getFullName(),
                    driver.getPhone(),
                    effectiveEmail,
                    requesterId,
                    previousSessionId,
                    sendEmail);
        } catch (RestClientResponseException ex) {
            log.warn("auth-service rejected activation key issuance for driverId={}: status={}",
                    driverId, ex.getStatusCode().value());
            throw new AppException(ErrorCode.ACTIVATION_ISSUANCE_FAILED);
        }

        if (issued.user_id() != null && driver.getUserId() == null) {
            driver.setUserId(issued.user_id());
        }
        if (driver.getUserId() != null && driver.getStatus() != DriverStatus.SUSPENDED) {
            driver.setStatus(DriverStatus.ACTIVE);
        }
        driver.setUpdatedAt(now);
        driverRepository.save(driver);

        return DriverActivationKeyResponse.builder()
                .driverId(driver.getId())
                .driverName(driver.getFullName())
                .email(driver.getEmail())
                .activationKey(issued.activation_key())
                .formattedActivationKey(issued.formatted_activation_key())
                .status("KEY_CREATED")
                .expiresAt(issued.expires_at())
                .emailSent(issued.email_queued())
                .build();
    }

    @Transactional
    public List<DriverActivationKeyResponse> bulkGenerateActivationKeys(
            UUID tenantId,
            List<UUID> driverIds,
            boolean sendEmail,
            UUID requesterId,
            String requesterRole) {
        assertManageRole(requesterRole);
        if (tenantId == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        JwtAuthenticationFilter.assertTenant(tenantId);

        List<DriverActivationKeyResponse> results = new ArrayList<>();
        for (UUID driverId : driverIds) {
            if (driverId == null) {
                continue;
            }
            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
            if (!tenantId.equals(driver.getTenantId())) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            boolean shouldEmail = sendEmail && driver.getEmail() != null && !driver.getEmail().isBlank();
            results.add(generateActivationKey(driverId, shouldEmail, null, requesterId, requesterRole));
        }
        return results;
    }

    @Transactional
    public void deactivateDriver(UUID driverId, UUID requesterId, String requesterRole) {
        assertManageRole(requesterRole);
        Driver driver = driverRepository.findLockedById(driverId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        JwtAuthenticationFilter.assertTenant(driver.getTenantId());
        deactivateDriversInternal(driver.getTenantId(), List.of(driver));
    }

    @Transactional
    public void bulkDeactivateDrivers(
            UUID tenantId,
            List<UUID> driverIds,
            UUID requesterId,
            String requesterRole) {
        assertManageRole(requesterRole);
        if (tenantId == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        JwtAuthenticationFilter.assertTenant(tenantId);

        List<Driver> targets = new ArrayList<>();
        for (UUID driverId : driverIds) {
            if (driverId == null) {
                continue;
            }
            Driver driver = driverRepository.findLockedById(driverId)
                    .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
            if (!tenantId.equals(driver.getTenantId())) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            targets.add(driver);
        }
        if (!targets.isEmpty()) {
            deactivateDriversInternal(tenantId, targets);
        }
    }

    private void deactivateDriversInternal(UUID tenantId, List<Driver> drivers) {
        Instant now = Instant.now();
        List<UUID> driverIds = new ArrayList<>(drivers.size());
        List<AuthActivationClient.SessionRevokeItem> sessionsToRevoke = new ArrayList<>();

        for (Driver driver : drivers) {
            driverIds.add(driver.getId());
            DriverDevice activeDevice = driverDeviceRepository
                    .findByTenantIdAndDriverIdAndRevokedAtIsNull(tenantId, driver.getId())
                    .orElse(null);
            if (activeDevice != null) {
                activeDevice.setRevokedAt(now);
                driverDeviceRepository.saveAndFlush(activeDevice);
                markSessionRevokedLocallyAndRedis(activeDevice.getSessionId());
                sessionsToRevoke.add(new AuthActivationClient.SessionRevokeItem(
                        driver.getId(),
                        activeDevice.getUserId(),
                        activeDevice.getSessionId()));
            }
            if (Boolean.TRUE.equals(driver.getIsAvailable())) {
                driver.setIsAvailable(false);
                driver.setUpdatedAt(now);
                driverRepository.save(driver);
                publishAvailabilityChanged(driver);
            }
        }

        authActivationClient.deactivateSessions(tenantId, driverIds, sessionsToRevoke);
    }

    /**
     * Invoked internally by auth-service when a driver submits a valid 12-digit activation code.
     */
    @Transactional
    public UUID registerActivatedDevice(UUID tenantId, UUID driverId, ActivateDeviceInternalCommand command) {
        Driver driver = driverRepository.findLockedById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));
        if (!tenantId.equals(driver.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant mismatch");
        }
        if (driver.getStatus() != DriverStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver profile is not active");
        }
        if (driver.getUserId() != null && !driver.getUserId().equals(command.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver user mismatch");
        }
        if (driver.getUserId() == null) {
            driver.setUserId(command.userId());
            driver.setUpdatedAt(Instant.now());
            driverRepository.save(driver);
        }

        Instant now = Instant.now();
        UUID replacedSessionId = null;
        DriverDevice existingActive = driverDeviceRepository
                .findByTenantIdAndDriverIdAndRevokedAtIsNull(tenantId, driverId)
                .orElse(null);
        if (existingActive != null) {
            replacedSessionId = existingActive.getSessionId();
            existingActive.setRevokedAt(now);
            driverDeviceRepository.saveAndFlush(existingActive);
            markSessionRevokedLocallyAndRedis(replacedSessionId);
        }

        DriverDevice newDevice = DriverDevice.builder()
                .tenantId(tenantId)
                .driverId(driverId)
                .userId(command.userId())
                .installationId(command.installationId())
                .sessionId(command.sessionId())
                .deviceOs(command.deviceOs())
                .osVersion(command.osVersion())
                .appVersion(command.appVersion())
                .deviceModel(command.deviceModel())
                .activatedAt(now)
                .lastSeenAt(now)
                .build();
        driverDeviceRepository.save(newDevice);
        return replacedSessionId;
    }

    /**
     * Updates installed app version, OS/model metadata, and last_seen_at for the authenticated driver's active device.
     */
    @Transactional
    public void updateMyDevice(UUID userId, UUID sessionId, UpdateDriverDeviceRequest request) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new AppException(ErrorCode.DRIVER_SUSPENDED);
        }

        DriverDevice device = null;
        if (sessionId != null) {
            device = driverDeviceRepository.findBySessionId(sessionId)
                    .filter(d -> d.getRevokedAt() == null && d.getDriverId().equals(driver.getId()))
                    .orElse(null);
        }
        if (device == null) {
            device = driverDeviceRepository
                    .findByTenantIdAndDriverIdAndRevokedAtIsNull(driver.getTenantId(), driver.getId())
                    .orElse(null);
        }
        if (device == null) {
            return;
        }

        if (request.deviceId() != null && !request.deviceId().isBlank()) {
            device.setInstallationId(request.deviceId().trim());
        }
        if (request.deviceOs() != null && !request.deviceOs().isBlank()) {
            device.setDeviceOs(request.deviceOs().trim());
        }
        if (request.osVersion() != null && !request.osVersion().isBlank()) {
            device.setOsVersion(request.osVersion().trim());
        }
        if (request.appVersion() != null && !request.appVersion().isBlank()) {
            device.setAppVersion(request.appVersion().trim());
        }
        if (request.deviceModel() != null && !request.deviceModel().isBlank()) {
            device.setDeviceModel(request.deviceModel().trim());
        }
        device.setLastSeenAt(Instant.now());
        driverDeviceRepository.save(device);
    }

    static String deriveActivationStatus(
            DriverDevice activeDevice,
            DriverDevice latestDevice,
            AuthActivationClient.KeyStatePayload keyState) {
        if (activeDevice != null) {
            return "ACTIVATED";
        }
        if (keyState != null) {
            if ("KEY_CREATED".equals(keyState.key_state())) {
                return "KEY_CREATED";
            }
            if ("KEY_EXPIRED".equals(keyState.key_state())) {
                if (latestDevice == null
                        || latestDevice.getRevokedAt() == null
                        || (keyState.created_at() != null && keyState.created_at().isAfter(latestDevice.getRevokedAt()))) {
                    return "KEY_EXPIRED";
                }
            }
        }
        if (latestDevice != null && latestDevice.getRevokedAt() != null) {
            return "DEACTIVATED";
        }
        return "NOT_ACTIVATED";
    }

    private void markSessionRevokedLocallyAndRedis(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        long expSec = (System.currentTimeMillis() / 1000L) + REVOKED_SESSION_REDIS_TTL.toSeconds();
        TenantPermissionRegistry.recordRevokedSession(sessionId, expSec);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(
                        "auth:revoked-session:" + sessionId,
                        "1",
                        REVOKED_SESSION_REDIS_TTL);
            } catch (Exception ex) {
                log.warn("Failed to write revoked sessionId={} to Redis: {}", sessionId, ex.getMessage());
            }
        }
    }

    private void publishAvailabilityChanged(Driver driver) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("DRIVER")
                .aggregateId(driver.getId())
                .eventType("driver.availability_changed.v1")
                .payload(new DriverAvailabilityEventPayload(
                        driver.getId(),
                        driver.getTenantId(),
                        driver.getUserId(),
                        driver.getIsAvailable(),
                        Instant.now()))
                .build();
        outboxEventRepository.save(outboxEvent);
    }

    private static void assertManageRole(String requesterRole) {
        if (requesterRole == null || !MANAGE_ROLES.contains(requesterRole)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static boolean hasRequiredOperationalFields(Driver driver) {
        return driver.getFullName() != null && !driver.getFullName().isBlank()
                && driver.getPhone() != null && driver.getPhone().matches("^\\+?[0-9]{9,15}$")
                && driver.getLicensePlate() != null && !driver.getLicensePlate().isBlank()
                && driver.getVehicleType() != null
                && driver.getCapacity() != null && driver.getCapacity() > 0;
    }

    private static String formatDriverCode(UUID driverId) {
        if (driverId == null) {
            return "";
        }
        String compact = driverId.toString().replace("-", "").toUpperCase(Locale.ROOT);
        return "DRV-" + compact.substring(0, 6);
    }

    private static boolean matchesSearch(Driver driver, String driverCode, String normalizedQuery) {
        return normalizeSearch(driver.getFullName()).contains(normalizedQuery)
                || normalizeSearch(driverCode).contains(normalizedQuery)
                || normalizeSearch(driver.getId() != null ? driver.getId().toString() : "").contains(normalizedQuery)
                || normalizeSearch(driver.getPhone()).contains(normalizedQuery)
                || normalizeSearch(driver.getEmail()).contains(normalizedQuery)
                || normalizeSearch(driver.getLicensePlate()).contains(normalizedQuery);
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String ascii = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return ascii.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
