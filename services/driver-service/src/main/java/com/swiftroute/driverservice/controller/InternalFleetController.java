package com.swiftroute.driverservice.controller;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import com.swiftroute.driverservice.repository.DriverRepository;
import com.swiftroute.driverservice.entity.enums.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/fleet")
public class InternalFleetController {
    private final DriverRepository drivers;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.swiftroute.driverservice.service.DriverActivationManagementService activationManagementService;
    @Value("${tenant.internal-key:}") private String key;

    private void authenticate(String supplied) {
        if (key.isBlank() || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
    }
    public record LinkProfile(UUID userId) {}
    @GetMapping("/{tenantId}/simulation-profile/{driverId}")
    public Map<String, Object> simulationProfile(@PathVariable UUID tenantId, @PathVariable UUID driverId,
            @RequestHeader("X-Service-Key") String supplied) {
        authenticate(supplied);
        var driver = drivers.findById(driverId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if (!tenantId.equals(driver.getTenantId()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return Map.of("tracking_id", driver.getUserId() != null ? driver.getUserId() : driver.getId());
    }
    public record ProvisionProfile(UUID userId, String fullName, String phone) {}
    @GetMapping("/{tenantId}/profiles/{driverId}")
    public void validateProfile(@PathVariable UUID tenantId, @PathVariable UUID driverId,
            @RequestHeader("X-Service-Key") String supplied) {
        authenticate(supplied);
        var driver = drivers.findById(driverId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if (!driver.getTenantId().equals(tenantId) || driver.getUserId() != null)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);
    }
    @PostMapping("/{tenantId}/profiles/{driverId}/link")
    @org.springframework.transaction.annotation.Transactional
    public void linkProfile(@PathVariable UUID tenantId, @PathVariable UUID driverId,
            @RequestBody LinkProfile request, @RequestHeader("X-Service-Key") String supplied) {
        authenticate(supplied);
        if (request.userId() == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        var driver = drivers.findLockedById(driverId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if (!driver.getTenantId().equals(tenantId) || (driver.getUserId() != null && !driver.getUserId().equals(request.userId())))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);
        var existing = drivers.findByUserId(request.userId());
        if (existing.isPresent() && !existing.get().getId().equals(driverId))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);
        if (driver.getUserId() == null) {
            driver.setUserId(request.userId());
            driver.setIsAvailable(false);
            driver.setStatus(DriverStatus.INACTIVE);
            drivers.save(driver);
        }
    }
    @PostMapping("/{tenantId}/profiles/provision")
    @org.springframework.transaction.annotation.Transactional
    public void provisionProfile(@PathVariable UUID tenantId, @RequestBody ProvisionProfile request,
            @RequestHeader("X-Service-Key") String supplied) {
        authenticate(supplied);
        if (request.userId() == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        jdbc.execute("SELECT pg_advisory_xact_lock(" + (long) request.userId().hashCode() + ")");
        var existing = drivers.findByUserId(request.userId());
        if (existing.isPresent()) {
            if (!existing.get().getTenantId().equals(tenantId))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);
            return;
        }
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        if (phone != null && !phone.matches("^\\+?[0-9]{9,15}$"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        if (phone != null && drivers.existsByPhone(phone))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);
        String name = request.fullName() == null || request.fullName().isBlank() ? "Tài xế mới" : request.fullName().trim();
        drivers.save(com.swiftroute.driverservice.entity.Driver.builder()
                .tenantId(tenantId).userId(request.userId()).fullName(name).phone(phone)
                .driverType(DriverType.DEDICATED).status(DriverStatus.INACTIVE).isAvailable(false).build());
    }
    @GetMapping("/{tenantId}")
    public List<Map<String,Object>> eligible(@PathVariable UUID tenantId,@RequestHeader("X-Service-Key") String supplied) {
        if (key.isBlank() || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        return drivers.findAllByTenantId(tenantId).stream()
            .filter(x -> x.getStatus()==DriverStatus.ACTIVE && Boolean.TRUE.equals(x.getIsAvailable())
                    && x.getDriverType()==DriverType.DEDICATED && x.getUserId()!=null
                    && x.getVehicleType()!=null && x.getLicensePlate()!=null && !x.getLicensePlate().isBlank()
                    && x.getCapacity()!=null && x.getCapacity()>0)
            .map(x -> Map.<String,Object>of("driver_id",x.getId(),"user_id",x.getUserId())).toList();
    }

    public record ActivateDeviceRequest(
            @com.fasterxml.jackson.annotation.JsonAlias({"userId", "user_id"}) UUID userId,
            @com.fasterxml.jackson.annotation.JsonAlias({"installationId", "installation_id"}) String installationId,
            @com.fasterxml.jackson.annotation.JsonAlias({"sessionId", "session_id"}) UUID sessionId,
            @com.fasterxml.jackson.annotation.JsonAlias({"deviceOs", "device_os"}) String deviceOs,
            @com.fasterxml.jackson.annotation.JsonAlias({"osVersion", "os_version"}) String osVersion,
            @com.fasterxml.jackson.annotation.JsonAlias({"appVersion", "app_version"}) String appVersion,
            @com.fasterxml.jackson.annotation.JsonAlias({"deviceModel", "device_model"}) String deviceModel) {
    }

    @PostMapping("/{tenantId}/drivers/{driverId}/activate-device")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> activateDevice(
            @PathVariable UUID tenantId,
            @PathVariable UUID driverId,
            @RequestBody ActivateDeviceRequest request,
            @RequestHeader("X-Service-Key") String supplied) {
        authenticate(supplied);
        if (request.userId() == null || request.sessionId() == null
                || request.installationId() == null || request.installationId().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        UUID revokedSessionId = activationManagementService.registerActivatedDevice(
                tenantId,
                driverId,
                new com.swiftroute.driverservice.service.DriverActivationManagementService.ActivateDeviceInternalCommand(
                        request.userId(),
                        request.installationId().trim(),
                        request.sessionId(),
                        request.deviceOs(),
                        request.osVersion(),
                        request.appVersion(),
                        request.deviceModel()));
        var result = new LinkedHashMap<String, Object>();
        result.put("driver_id", driverId);
        result.put("revoked_session_id", revokedSessionId);
        return result;
    }
}
