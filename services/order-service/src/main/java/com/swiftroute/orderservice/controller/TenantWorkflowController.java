package com.swiftroute.orderservice.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.request.RegisterTenantRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.PageResponse;
import com.swiftroute.orderservice.dto.response.TenantResponse;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.security.TenantIdentityClient;
import com.swiftroute.orderservice.service.OperationalReadinessService;
import com.swiftroute.orderservice.service.TenantProvisioningService;
import com.swiftroute.orderservice.service.TenantProvisioningStore;
import com.swiftroute.orderservice.service.TenantWorkflowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller for tenant onboarding lifecycle, self-service registration,
 * readiness checklist, and administrative KYC verification requests.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({ "/api/tenants", "/api/v1/tenants" })
@Tag(name = "Tenant Workflow", description = "Tenant self-service onboarding, provisioning, and KYC verification queue")
public class TenantWorkflowController {

    private final TenantWorkflowService workflow;
    private final TenantIdentityClient identities;
    private final OperationalReadinessService readiness;
    private final TenantProvisioningService provisioning;
    private final TenantProvisioningStore provisioningStore;

    public record Lifecycle(String status, String reason) {
    }

    // =========================================================================
    // 1. TENANT REGISTRATION & PROVISIONING
    // =========================================================================

    @Operation(summary = "Register a new tenant and initialize workspace provisioning")
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestHeader("Authorization") String auth,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RegisterTenantRequest request) {

        var context = identities.context(auth, null);
        Tenant tenant = workflow.create(request, context, key);
        var state = provisioning.provision(tenant.getId());
        boolean ready = "READY".equals(state.status());

        return ResponseEntity
                .status(ready ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(ApiResponse.success(Map.of(
                        "tenant", TenantResponse.fromEntity(tenant),
                        "tenant_id", tenant.getId(),
                        "next_action", state.nextAction(),
                        "workspace_status", state.status())));
    }

    @Operation(summary = "Get workspace provisioning state for its creator")
    @GetMapping("/{tenantId}/provisioning")
    public ApiResponse<TenantProvisioningStore.State> provisioning(
            @PathVariable UUID tenantId,
            @RequestHeader("Authorization") String auth) {
        verifyCreator(auth, tenantId);
        return ApiResponse.success(provisioningStore.state(tenantId));
    }

    @Operation(summary = "Retry recoverable OWNER provisioning")
    @PostMapping("/{tenantId}/provisioning/retry")
    public ApiResponse<TenantProvisioningStore.State> retry(
            @PathVariable UUID tenantId,
            @RequestHeader("Authorization") String auth) {
        verifyCreator(auth, tenantId);
        return ApiResponse.success(provisioning.provision(tenantId));
    }

    // =========================================================================
    // 2. TENANT ONBOARDING & READINESS
    // =========================================================================

    @Operation(summary = "Get tenant details by ID")
    @GetMapping("/{tenantId}")
    public ApiResponse<TenantResponse> tenant(
            @PathVariable UUID tenantId,
            @RequestHeader("Authorization") String auth) {
        identities.statelessContext(auth, tenantId);
        return ApiResponse.success(TenantResponse.fromEntity(workflow.find(tenantId)));
    }

    @Operation(summary = "Get onboarding progress checklist for tenant")
    @GetMapping("/{tenantId}/onboarding")
    public ApiResponse<Map<String, Object>> progress(
            @PathVariable UUID tenantId,
            @RequestHeader("Authorization") String auth) {
        identities.statelessContext(auth, tenantId);
        provisioningStore.requireReady(tenantId);
        return ApiResponse.success(workflow.onboarding(tenantId));
    }

    @Operation(summary = "Update tenant setup during onboarding")
    @PutMapping("/{tenantId}/onboarding")
    public ApiResponse<Map<String, Object>> setup(
            @PathVariable UUID tenantId,
            @RequestHeader("Authorization") String auth,
            @RequestBody TenantWorkflowService.Setup request) {
        requireOwner(auth, tenantId);
        workflow.configure(tenantId, request);
        return ApiResponse.success(workflow.onboarding(tenantId));
    }

    @Operation(summary = "Check operational readiness score and pending requirements")
    @GetMapping("/{tenantId}/operational-readiness")
    public ApiResponse<Map<String, Object>> readiness(
            @PathVariable UUID tenantId,
            @RequestHeader("Authorization") String auth) {
        identities.statelessContext(auth, tenantId);
        return ApiResponse.success(readiness.check(workflow.find(tenantId)));
    }

    // =========================================================================
    // 3. KYC VERIFICATION REQUESTS (SUBMIT & REVIEW)
    // =========================================================================

    @Operation(summary = "Get KYC verification history for tenant")
    @GetMapping("/{tenantId}/verification-requests")
    public ApiResponse<List<Map<String, Object>>> history(
            @PathVariable UUID tenantId,
            @RequestHeader("Authorization") String auth) {
        identities.statelessContext(auth, tenantId);
        return ApiResponse.success(workflow.history(tenantId));
    }

    @Operation(summary = "Submit KYC verification request for platform review")
    @PostMapping("/{tenantId}/verification-requests")
    public ApiResponse<Map<String, Object>> submit(
            @PathVariable UUID tenantId,
            @RequestHeader("Authorization") String auth,
            @RequestHeader("Idempotency-Key") String key) {
        var context = requireOwner(auth, tenantId);
        return ApiResponse.success(workflow.submit(tenantId, extractActorId(context), key));
    }

    @Operation(summary = "Search and paginate system administrator verification queue")
    @GetMapping("/verification-requests/page")
    public ApiResponse<PageResponse<Map<String, Object>>> queue(
            @RequestHeader("Authorization") String auth,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(auth);
        return ApiResponse.success(workflow.queue(status, search, page, size));
    }

    @Operation(summary = "List verification reviews with optional status filter")
    @GetMapping("/verification-requests")
    public ApiResponse<List<Map<String, Object>>> reviews(
            @RequestHeader("Authorization") String auth,
            @RequestParam(required = false) String status) {
        requireAdmin(auth);
        return ApiResponse.success(workflow.reviews(status));
    }

    @Operation(summary = "Read submitted snapshot and administrative review history")
    @GetMapping("/verification-requests/{requestId}")
    public ApiResponse<Map<String, Object>> detail(
            @PathVariable UUID requestId,
            @RequestHeader("Authorization") String auth) {
        requireAdmin(auth);
        return ApiResponse.success(workflow.detail(requestId));
    }

    @Operation(summary = "Submit admin review decision (APPROVE / REJECT)")
    @PostMapping("/verification-requests/{requestId}/review")
    public ApiResponse<Map<String, Object>> review(
            @PathVariable UUID requestId,
            @RequestHeader("Authorization") String auth,
            @RequestBody TenantWorkflowService.Decision decision) {
        var context = requireAdmin(auth);
        return ApiResponse.success(workflow.review(requestId, decision, extractActorId(context)));
    }

    @Operation(summary = "Update tenant lifecycle status (SUSPENDED / APPROVED)")
    @PatchMapping("/{tenantId}/status")
    public ApiResponse<TenantResponse> lifecycle(
            @PathVariable UUID tenantId,
            @RequestHeader("Authorization") String auth,
            @RequestBody Lifecycle request) {
        var context = requireAdmin(auth);
        if (!Set.of("SUSPENDED", "APPROVED").contains(String.valueOf(request.status()))) {
            throw new AppException(ErrorCode.INVALID_TENANT_STATUS_TRANSITION);
        }
        return ApiResponse.success(TenantResponse.fromEntity(
                workflow.suspend(tenantId, request.reason(), extractActorId(context),
                        "APPROVED".equals(request.status()))));
    }

    // =========================================================================
    // HELPER SECURITY METHODS
    // =========================================================================

    private UUID extractActorId(Map<String, Object> context) {
        return UUID.fromString(context.get("user_id").toString());
    }

    private Map<String, Object> requireOwner(String auth, UUID tenantId) {
        var context = identities.statelessContext(auth, tenantId);
        boolean jwtOwner = "OWNER".equals(context.get("membership_role"))
                && tenantId.toString().equals(String.valueOf(context.get("tenant_id")));
        if (!jwtOwner && !extractActorId(context).equals(workflow.find(tenantId).getOwnerUserId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        provisioningStore.requireReady(tenantId);
        return context;
    }

    private Map<String, Object> requireAdmin(String auth) {
        var context = identities.statelessContext(auth, null);
        if (!TenantIdentityClient.systemAdmin(context)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return context;
    }

    private void verifyCreator(String auth, UUID tenantId) {
        var context = identities.statelessContext(auth, null);
        if (!extractActorId(context).equals(workflow.find(tenantId).getOwnerUserId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
