package com.swiftroute.orderservice.controller;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.swiftroute.orderservice.dto.request.RegisterTenantRequest;
import com.swiftroute.orderservice.dto.response.*;
import com.swiftroute.orderservice.entity.*;
import com.swiftroute.orderservice.security.TenantIdentityClient;
import com.swiftroute.orderservice.service.*;
import com.swiftroute.orderservice.exception.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/tenants","/api/v1/tenants"})
public class TenantWorkflowController {
    private final TenantWorkflowService workflow;
    private final TenantIdentityClient identities;
    private final OperationalReadinessService readiness;
    private final TenantProvisioningService provisioning;
    private final TenantProvisioningStore provisioningStore;
    private UUID actor(Map<String,Object> c) { return UUID.fromString(c.get("user_id").toString()); }
    private Map<String,Object> owner(String auth,UUID id) { var c=identities.context(auth,id);identities.requireOwner(c);provisioningStore.requireReady(id);return c; }
    private Map<String,Object> admin(String auth) {
        var c=identities.context(auth,null);if(!TenantIdentityClient.systemAdmin(c))throw new AppException(ErrorCode.ACCESS_DENIED);return c;
    }
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String,Object>>> create(@RequestHeader("Authorization") String auth,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RegisterTenantRequest request) {
        var context=identities.context(auth,null);
        Tenant t=workflow.create(request,context,key);
        var state=provisioning.provision(t.getId());
        boolean ready="READY".equals(state.status());
        return ResponseEntity.status(ready?HttpStatus.CREATED:HttpStatus.ACCEPTED).body(ApiResponse.success(Map.of("tenant",TenantResponse.fromEntity(t),"tenant_id",t.getId(),"next_action",state.nextAction(),"workspace_status",state.status())));
    }
    private void creator(String auth,UUID id) {
        var context=identities.context(auth,null);
        if(!actor(context).equals(workflow.find(id).getOwnerUserId())) throw new AppException(ErrorCode.ACCESS_DENIED);
    }
    @io.swagger.v3.oas.annotations.Operation(summary="Get workspace provisioning state for its creator")
    @GetMapping("/{tenantId}/provisioning")
    public ApiResponse<TenantProvisioningStore.State> provisioning(@PathVariable UUID tenantId,@RequestHeader("Authorization") String auth) {
        creator(auth,tenantId);return ApiResponse.success(provisioningStore.state(tenantId));
    }
    @io.swagger.v3.oas.annotations.Operation(summary="Retry recoverable OWNER provisioning")
    @PostMapping("/{tenantId}/provisioning/retry")
    public ApiResponse<TenantProvisioningStore.State> retry(@PathVariable UUID tenantId,@RequestHeader("Authorization") String auth) {
        creator(auth,tenantId);return ApiResponse.success(provisioning.provision(tenantId));
    }
    @io.swagger.v3.oas.annotations.Operation(summary="Search and paginate system administrator verification queue")
    @GetMapping("/verification-requests/page")
    public ApiResponse<PageResponse<Map<String,Object>>> queue(@RequestHeader("Authorization") String auth,
        @RequestParam(required=false) String status,@RequestParam(required=false) String search,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        admin(auth);return ApiResponse.success(workflow.queue(status,search,page,size));
    }
    @io.swagger.v3.oas.annotations.Operation(summary="Read submitted snapshot and administrative review history")
    @GetMapping("/verification-requests/{requestId}")
    public ApiResponse<Map<String,Object>> detail(@PathVariable UUID requestId,@RequestHeader("Authorization") String auth) {
        admin(auth);return ApiResponse.success(workflow.detail(requestId));
    }

    @GetMapping("/{tenantId}") public ApiResponse<TenantResponse> tenant(@PathVariable UUID tenantId,@RequestHeader("Authorization") String auth) {
        identities.context(auth,tenantId);return ApiResponse.success(TenantResponse.fromEntity(workflow.find(tenantId)));
    }
    @GetMapping("/{tenantId}/onboarding") public ApiResponse<Map<String,Object>> progress(@PathVariable UUID tenantId,@RequestHeader("Authorization") String auth) {
        identities.context(auth,tenantId);provisioningStore.requireReady(tenantId);return ApiResponse.success(workflow.onboarding(tenantId));
    }
    @PutMapping("/{tenantId}/onboarding") public ApiResponse<Map<String,Object>> setup(@PathVariable UUID tenantId,@RequestHeader("Authorization") String auth,@RequestBody TenantWorkflowService.Setup request) {
        owner(auth,tenantId);workflow.configure(tenantId,request);return ApiResponse.success(workflow.onboarding(tenantId));
    }
    @GetMapping("/{tenantId}/operational-readiness") public ApiResponse<Map<String,Object>> readiness(@PathVariable UUID tenantId,@RequestHeader("Authorization") String auth) {
        identities.context(auth,tenantId);return ApiResponse.success(readiness.check(workflow.find(tenantId)));
    }
    @GetMapping("/{tenantId}/verification-requests") public ApiResponse<List<Map<String,Object>>> history(@PathVariable UUID tenantId,@RequestHeader("Authorization") String auth) {
        identities.context(auth,tenantId);return ApiResponse.success(workflow.history(tenantId));
    }
    @PostMapping("/{tenantId}/verification-requests") public ApiResponse<Map<String,Object>> submit(@PathVariable UUID tenantId,@RequestHeader("Authorization") String auth,@RequestHeader("Idempotency-Key") String key) {
        var c=owner(auth,tenantId);return ApiResponse.success(workflow.submit(tenantId,actor(c),key));
    }
    @GetMapping("/verification-requests") public ApiResponse<List<Map<String,Object>>> reviews(@RequestHeader("Authorization") String auth,@RequestParam(required=false) String status) {
        admin(auth);return ApiResponse.success(workflow.reviews(status));
    }
    @PostMapping("/verification-requests/{requestId}/review") public ApiResponse<Map<String,Object>> review(@PathVariable UUID requestId,@RequestHeader("Authorization") String auth,@RequestBody TenantWorkflowService.Decision decision) {
        var c=admin(auth);return ApiResponse.success(workflow.review(requestId,decision,actor(c)));
    }
    public record Lifecycle(String status,String reason) {}
    @PatchMapping("/{tenantId}/status") public ApiResponse<TenantResponse> lifecycle(@PathVariable UUID tenantId,@RequestHeader("Authorization") String auth,@RequestBody Lifecycle request) {
        var c=admin(auth);
        if(!Set.of("SUSPENDED","APPROVED").contains(String.valueOf(request.status()))) throw new AppException(ErrorCode.INVALID_TENANT_STATUS_TRANSITION);
        return ApiResponse.success(TenantResponse.fromEntity(workflow.suspend(tenantId,request.reason(),actor(c),"APPROVED".equals(request.status()))));
    }
}
