package com.swiftroute.authservice.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.swiftroute.authservice.service.DriverActivationService;

import lombok.RequiredArgsConstructor;

/**
 * Internal service-to-service endpoints invoked by driver-service (authenticated via X-Service-Key)
 * to issue activation keys, query key states, and revoke activation device sessions.
 */
@RestController
@RequestMapping("/internal/driver-activations")
@RequiredArgsConstructor
public class InternalDriverActivationController {

    private final DriverActivationService driverActivationService;

    @Value("${tenant.internal-key:}")
    private String internalKey;

    private void authenticate(String supplied) {
        if (internalKey == null || internalKey.isBlank() || supplied == null
                || !MessageDigest.isEqual(
                        internalKey.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal service key");
        }
    }

    public record InternalIssueKeyRequest(
            @JsonAlias({"existingUserId", "existing_user_id"}) UUID existingUserId,
            @JsonAlias({"fullName", "full_name"}) String fullName,
            String phone,
            String email,
            @JsonAlias({"createdBy", "created_by"}) UUID createdBy,
            @JsonAlias({"revokeSessionId", "revoke_session_id"}) UUID revokeSessionId,
            @JsonAlias({"sendEmail", "send_email"}) boolean sendEmail) {
    }

    public record InternalDeactivateRequest(
            @JsonAlias({"driverIds", "driver_ids"}) List<UUID> driverIds,
            @JsonAlias({"sessions"}) List<DriverActivationService.SessionRevokeTarget> sessions) {
    }

    public record InternalKeyStateQueryRequest(
            @JsonAlias({"driverIds", "driver_ids"}) List<UUID> driverIds) {
    }

    @PostMapping("/{tenantId}/drivers/{driverId}/keys")
    public DriverActivationService.IssuedKeyResult issueKey(
            @PathVariable UUID tenantId,
            @PathVariable UUID driverId,
            @RequestBody InternalIssueKeyRequest request,
            @RequestHeader("X-Service-Key") String suppliedKey) {
        authenticate(suppliedKey);
        return driverActivationService.issueActivationKey(new DriverActivationService.IssueKeyCommand(
                tenantId,
                driverId,
                request.existingUserId(),
                request.fullName(),
                request.phone(),
                request.email(),
                request.createdBy(),
                request.revokeSessionId(),
                request.sendEmail()));
    }

    @PostMapping("/{tenantId}/deactivate")
    public void deactivateSessions(
            @PathVariable UUID tenantId,
            @RequestBody InternalDeactivateRequest request,
            @RequestHeader("X-Service-Key") String suppliedKey) {
        authenticate(suppliedKey);
        driverActivationService.deactivateDrivers(
                tenantId,
                request.driverIds(),
                request.sessions());
    }

    @PostMapping("/{tenantId}/key-states")
    public Map<UUID, DriverActivationService.KeyStateSnapshot> queryKeyStates(
            @PathVariable UUID tenantId,
            @RequestBody InternalKeyStateQueryRequest request,
            @RequestHeader("X-Service-Key") String suppliedKey) {
        authenticate(suppliedKey);
        return driverActivationService.getLatestKeyStates(tenantId, request.driverIds());
    }
}
